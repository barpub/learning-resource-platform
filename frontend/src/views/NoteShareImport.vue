<template>
  <div class="note-share-import">
    <section class="panel page-heading-panel">
      <span class="eyebrow">Shared Note</span>
      <h1>导入分享笔记</h1>
      <p class="muted">确认内容后保存到你的笔记账号</p>
    </section>

    <section class="panel share-panel" v-loading="loading">
      <el-result v-if="error" icon="warning" title="分享不可用" :sub-title="error">
        <template #extra>
          <el-button @click="router.push('/')">返回首页</el-button>
        </template>
      </el-result>

      <div v-else-if="share" class="share-layout">
        <div class="note-preview">
          <div class="note-header">
            <div>
              <span class="eyebrow">Preview</span>
              <h2>{{ share.title }}</h2>
            </div>
            <el-button type="primary" :loading="importing" @click="handleImport">
              {{ isLoggedIn ? '导入到我的笔记' : '登录后导入' }}
            </el-button>
          </div>

          <div class="meta-row">
            <span v-if="share.category">分类：{{ share.category }}</span>
            <span v-if="share.resourceTitle">关联资源：{{ share.resourceTitle }}</span>
            <span v-if="share.shareTime">分享时间：{{ formatTime(share.shareTime) }}</span>
          </div>

          <div v-if="tagNames.length" class="tag-row">
            <el-tag v-for="tag in tagNames" :key="tag" size="small">{{ tag }}</el-tag>
          </div>

          <button v-if="hasAnchorPreview(share)" type="button" class="snippet-card" @click="confirmJumpToSource">
            <img v-if="share.anchorImage" :src="share.anchorImage" :alt="share.title" />
            <span>
              <strong>{{ anchorTypeLabel(share.anchorType) }}</strong>
              {{ share.anchorText || anchorFallback(share) }}
            </span>
          </button>

          <div class="content-block">{{ share.content }}</div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { noteApi } from '../api'

const route = useRoute()
const router = useRouter()
const store = useStore()
const loading = ref(false)
const importing = ref(false)
const share = ref(null)
const error = ref('')
const token = computed(() => String(route.params.token || ''))
const isLoggedIn = computed(() => Boolean(store.state.token))
const tagNames = computed(() => share.value?.tagNames || [])

onMounted(loadShare)

async function loadShare() {
  loading.value = true
  error.value = ''
  try {
    share.value = await noteApi.getShare(token.value)
  } catch (err) {
    share.value = null
    error.value = err?.message || '分享链接不存在或已失效'
  } finally {
    loading.value = false
  }
}

async function handleImport() {
  if (!isLoggedIn.value) {
    router.push(`/login?redirect=${encodeURIComponent(route.fullPath)}`)
    return
  }

  importing.value = true
  try {
    await noteApi.importShare(token.value)
    ElMessage.success('导入成功')
    router.push('/notes')
  } finally {
    importing.value = false
  }
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

async function confirmJumpToSource() {
  if (!share.value?.resourceId) {
    ElMessage.info('这条分享笔记没有关联资源')
    return
  }
  try {
    await ElMessageBox.confirm('是否跳转到这条笔记记录的资源片段？', '跳转确认', {
      confirmButtonText: '跳转',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  router.push({
    path: `/resources/${share.value.resourceId}`,
    query: {
      shareToken: token.value,
      t: share.value.anchorSeconds !== null && share.value.anchorSeconds !== undefined ? share.value.anchorSeconds : undefined
    }
  })
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
.note-share-import {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.share-panel {
  min-height: 320px;
}

.share-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 18px;
}

.note-preview {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.note-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.note-header h2 {
  margin: 4px 0 0;
  color: #1f2937;
  font-size: 26px;
  line-height: 1.25;
  word-break: break-word;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  color: #64748b;
  font-size: 13px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.content-block {
  min-height: 180px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fafafa;
  color: #1f2937;
  font-size: 15px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.snippet-card {
  display: grid;
  grid-template-columns: minmax(120px, 190px) minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  padding: 10px;
  border: 1px solid #dbe4ee;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition: border-color 150ms ease, background 150ms ease;
}

.snippet-card:hover {
  border-color: var(--primary);
  background: #eef7f7;
}

.snippet-card img {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 4px;
  object-fit: cover;
  background: #111827;
}

.snippet-card:not(:has(img)) {
  grid-template-columns: minmax(0, 1fr);
}

.snippet-card span {
  display: -webkit-box;
  overflow: hidden;
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  word-break: break-word;
}

.snippet-card strong {
  display: block;
  margin-bottom: 4px;
  color: #0f766e;
}

@media (max-width: 640px) {
  .note-header {
    flex-direction: column;
  }

  .note-header .el-button {
    width: 100%;
  }

  .snippet-card {
    grid-template-columns: 1fr;
  }
}
</style>
