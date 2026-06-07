<template>
  <div>
    <section class="panel page-heading-panel">
      <span class="eyebrow">Notes</span>
      <h1>我的笔记</h1>
      <p class="muted">记录学习心得，整合知识点</p>
    </section>

    <div class="panel" style="margin-bottom: 20px;">
      <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
        <el-input v-model="keyword" placeholder="搜索笔记" style="width: 200px;" clearable @change="loadNotes" />
        <el-input v-model="category" placeholder="分类筛选" style="width: 160px;" clearable @change="loadNotes" />
        <el-button type="primary" @click="openDialog()">新建笔记</el-button>
        <el-button @click="showMergeDialog = true" :disabled="selectedIds.length < 2">整合笔记</el-button>
        <el-button @click="exportNotes">导出笔记</el-button>
      </div>
    </div>

    <div class="panel">
      <el-checkbox-group v-model="selectedIds">
        <div v-for="note in notes" :key="note.id" style="margin-bottom: 15px; padding: 15px; border: 1px solid #eee; border-radius: 4px;">
          <div style="display: flex; align-items: start; gap: 10px;">
            <el-checkbox :label="note.id" />
            <div style="flex: 1;">
              <h3 style="margin: 0 0 5px 0; cursor: pointer;" @click="openDialog(note)">{{ note.title }}</h3>
              <div style="color: #666; font-size: 12px; margin-bottom: 5px;">
                <span v-if="note.category">分类: {{ note.category }}</span>
                <span v-if="note.resourceTitle"> | 关联资源: {{ note.resourceTitle }}</span>
                <span v-if="note.isFavorite === 1"> | 已收藏</span>
                <span> | {{ note.createTime }}</span>
              </div>
              <div style="margin-bottom: 5px;">
                <el-tag v-for="tag in note.tags || []" :key="tag.id" size="small" style="margin-right: 5px;">{{ tag.name }}</el-tag>
              </div>
              <button v-if="hasAnchorPreview(note)" type="button" class="note-snippet-card" @click.stop="confirmJumpToNote(note)">
                <img v-if="note.anchorImage" :src="note.anchorImage" :alt="note.title" />
                <span>
                  <strong>{{ anchorTypeLabel(note.anchorType) }}</strong>
                  {{ note.anchorText || anchorFallback(note) }}
                </span>
              </button>
              <div style="color: #999; font-size: 13px;">{{ previewContent(note.content) }}</div>
            </div>
            <el-button size="small" type="primary" plain @click="shareNote(note)">分享</el-button>
            <el-button size="small" @click="deleteNote(note.id)">删除</el-button>
          </div>
        </div>
      </el-checkbox-group>
      <el-empty v-if="notes.length === 0" description="暂无笔记" />
    </div>

    <el-dialog v-model="showDialog" :title="editingNote ? '编辑笔记' : '新建笔记'" width="700px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="请输入笔记标题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：前端、后端、数据库" />
        </el-form-item>
        <el-form-item label="收藏">
          <el-checkbox v-model="form.favorite">标记为重点笔记</el-checkbox>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="form.tagNames" multiple filterable allow-create placeholder="选择或创建标签" style="width: 100%;">
            <el-option v-for="tag in allTags" :key="tag.id" :label="tag.name" :value="tag.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="form.content" type="textarea" :rows="10" placeholder="支持Markdown格式" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="saveNote">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showMergeDialog" title="整合笔记" width="500px">
      <el-form :model="mergeForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="mergeForm.title" placeholder="整合后的笔记标题" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMergeDialog = false">取消</el-button>
        <el-button type="primary" @click="mergeNotes">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noteApi } from '../api'

const router = useRouter()
const notes = ref([])
const allTags = ref([])
const keyword = ref('')
const category = ref('')
const selectedIds = ref([])
const showDialog = ref(false)
const showMergeDialog = ref(false)
const editingNote = ref(null)
const form = ref({
  title: '',
  content: '',
  category: '',
  favorite: false,
  tagNames: []
})
const mergeForm = ref({
  title: ''
})

onMounted(() => {
  loadNotes()
  loadTags()
})

async function loadNotes() {
  const params = {}
  if (keyword.value) params.keyword = keyword.value
  if (category.value) params.category = category.value
  notes.value = await noteApi.list(params)
}

async function loadTags() {
  allTags.value = await noteApi.listTags()
}

function openDialog(note = null) {
  editingNote.value = note
  if (note) {
    form.value = {
      title: note.title,
      content: note.content,
      category: note.category || '',
      favorite: note.isFavorite === 1,
      tagNames: (note.tags || []).map(t => t.name)
    }
  } else {
    form.value = { title: '', content: '', category: category.value || '', favorite: false, tagNames: [] }
  }
  showDialog.value = true
}

async function saveNote() {
  if (!form.value.title || !form.value.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  const payload = {
    title: form.value.title,
    content: form.value.content,
    category: form.value.category || null,
    isFavorite: form.value.favorite ? 1 : 0,
    tagNames: form.value.tagNames
  }
  if (editingNote.value) {
    await noteApi.update(editingNote.value.id, payload)
    ElMessage.success('更新成功')
  } else {
    await noteApi.create(payload)
    ElMessage.success('创建成功')
  }
  showDialog.value = false
  loadNotes()
  loadTags()
}

async function deleteNote(id) {
  await ElMessageBox.confirm('确定删除这条笔记吗？', '提示')
  await noteApi.remove(id)
  ElMessage.success('删除成功')
  loadNotes()
}

async function shareNote(note) {
  const share = await noteApi.share(note.id)
  const url = `${window.location.origin}/notes/share/${share.token}`
  const title = note.title || '学习笔记'
  const text = `分享笔记：${title}`

  try {
    if (navigator.share) {
      await navigator.share({ title, text, url })
      return
    }
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url)
      ElMessage.success('分享链接已复制')
      return
    }
    await ElMessageBox.alert(url, '分享链接')
  } catch (error) {
    if (error?.name === 'AbortError') return
    ElMessage.error('分享失败，请稍后重试')
  }
}

async function confirmJumpToNote(note) {
  if (!note.resourceId) {
    ElMessage.info('这条笔记没有关联资源')
    return
  }
  await ElMessageBox.confirm('是否跳转到这条笔记记录的资源片段？', '跳转确认', {
    confirmButtonText: '跳转',
    cancelButtonText: '取消'
  })
  router.push({
    path: `/resources/${note.resourceId}`,
    query: {
      noteId: note.id,
      t: note.anchorSeconds !== null && note.anchorSeconds !== undefined ? note.anchorSeconds : undefined
    }
  })
}

async function mergeNotes() {
  if (!mergeForm.value.title) {
    ElMessage.warning('请输入整合后的标题')
    return
  }
  await noteApi.merge({ noteIds: selectedIds.value, title: mergeForm.value.title })
  ElMessage.success('整合成功')
  showMergeDialog.value = false
  selectedIds.value = []
  mergeForm.value.title = ''
  loadNotes()
}

async function exportNotes() {
  const params = {}
  if (category.value) params.category = category.value
  const markdown = await noteApi.export(params)
  const blob = new Blob([markdown], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `笔记导出_${new Date().getTime()}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

function previewContent(content = '') {
  const value = String(content || '').trim()
  return value.length > 100 ? `${value.substring(0, 100)}...` : value
}

function hasAnchorPreview(note) {
  return Boolean(note?.anchorImage || note?.anchorText || note?.anchorSeconds !== null && note?.anchorSeconds !== undefined)
}

function anchorFallback(note) {
  if (note?.anchorSeconds !== null && note?.anchorSeconds !== undefined) {
    return `视频 ${formatAnchorSeconds(note.anchorSeconds)} 处`
  }
  return note?.resourceTitle || '资源片段'
}

function anchorTypeLabel(type) {
  return ({
    VIDEO: '视频画面',
    TEXT: '文本片段',
    DOCUMENT: '文档片段',
    RESOURCE: '资源片段'
  }[type] || '资源片段')
}

function formatAnchorSeconds(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds || 0)))
  const hour = Math.floor(total / 3600)
  const minute = Math.floor((total % 3600) / 60)
  const second = total % 60
  if (hour > 0) {
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  return `${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
}
</script>

<style scoped>
.note-snippet-card {
  display: grid;
  grid-template-columns: minmax(96px, 150px) minmax(0, 1fr);
  gap: 10px;
  width: 100%;
  margin: 8px 0;
  padding: 10px;
  border: 1px solid rgba(196, 213, 228, 0.9);
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: border-color 150ms ease, background 150ms ease;
}

.note-snippet-card:hover {
  border-color: var(--primary);
  background: #eef7f7;
}

.note-snippet-card img {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 4px;
  object-fit: cover;
  background: #111827;
}

.note-snippet-card:not(:has(img)) {
  grid-template-columns: minmax(0, 1fr);
}

.note-snippet-card span {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  word-break: break-word;
}

.note-snippet-card strong {
  display: block;
  margin-bottom: 4px;
  color: #0f766e;
}

@media (max-width: 640px) {
  .note-snippet-card {
    grid-template-columns: 1fr;
  }
}
</style>
