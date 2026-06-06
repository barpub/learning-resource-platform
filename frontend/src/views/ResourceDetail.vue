<template>
  <div class="detail-page" v-if="resource">
    <section class="panel detail-hero">
      <div class="detail-heading">
        <el-button class="detail-back-button" plain size="small" aria-label="返回上一页" @click="goBack">
          返回上一页
        </el-button>
        <span class="eyebrow">Resource Detail</span>
        <h1>{{ resource.title }}</h1>
        <p class="muted">{{ resource.description || '暂无描述' }}</p>
        <div class="detail-meta">
          <span>{{ resource.categoryName || '未分类' }}</span>
          <span>上传者：{{ resource.username }}</span>
          <span v-if="isFolder">文件 {{ resource.fileCount || children.length }}</span>
          <span>浏览 {{ resource.viewCount || 0 }}</span>
          <span>下载 {{ resource.downloadCount || 0 }}</span>
          <span>评分 {{ resource.rating || '0.00' }}</span>
        </div>
      </div>
      <div class="detail-actions">
        <el-button @click="shareResource(resource)">分享</el-button>
        <el-button type="primary" @click="downloadResource">{{ isFolder ? '下载整个文件夹' : '下载资源' }}</el-button>
        <el-button @click="openPreview">新窗口打开</el-button>
        <el-button @click="toggleFavorite">{{ resource.favorite ? '取消收藏' : '收藏资源' }}</el-button>
        <el-button v-if="isFolder && canDeleteResource" @click="editingFolder = !editingFolder">
          {{ editingFolder ? '退出编辑' : '编辑文件夹' }}
        </el-button>
        <el-button v-if="canDeleteResource" type="danger" plain @click="deleteResource(resource)">
          {{ isFolder ? '删除文件夹' : '删除资源' }}
        </el-button>
      </div>
    </section>

    <section class="panel preview-section">
      <div class="preview-header">
        <h2>{{ isFolder ? '文件夹浏览' : '在线预览' }}</h2>
        <el-tag type="info">{{ previewLabel }}</el-tag>
      </div>

      <div v-if="isFolder" class="local-folder-browser">
        <div class="ftp-breadcrumb">
          <button
            v-for="crumb in breadcrumbs"
            :key="crumb.path || 'root'"
            type="button"
            :class="{ active: currentPath === crumb.path }"
            @click="openFolder(crumb.path)"
          >
            {{ crumb.label === '/' ? '根目录' : crumb.label }}
          </button>
          <span class="toolbar-push"></span>
          <el-button v-if="currentPath" size="small" @click="goUpFolder">返回上级</el-button>
          <label v-if="editingFolder" class="inline-upload-button">
            <input type="file" multiple @change="appendFilesAtCurrentPath" />
            <span>追加文件</span>
          </label>
          <label v-if="editingFolder" class="inline-upload-button">
            <input type="file" multiple webkitdirectory directory @change="appendFolderAtCurrentPath" />
            <span>追加文件夹</span>
          </label>
        </div>

        <div class="directory-toolbar">
          <span>{{ currentDirectory.folder.fileCount }} 个文件 · {{ currentDirectory.folders.length }} 个目录</span>
          <el-button size="small" type="primary" plain :disabled="!currentAnalysisFileCount" @click="analyzeCurrentFolder">分析当前文件夹</el-button>
          <el-button size="small" @click="selectFirstInCurrentDirectory" :disabled="!currentDirectory.files.length">选择预览</el-button>
        </div>

        <div class="grid">
          <FileCard
            v-for="folder in currentDirectory.folders"
            :key="folder.path"
            :title="folder.name"
            :description="`${folder.fileCount} 个文件 · ${formatSize(folder.totalSize)}`"
            :is-folder="true"
            footer-label="本地目录"
            @click="openFolder(folder.path)"
          >
            <template v-if="editingFolder" #actions>
              <el-button size="small" text @click.stop="renameLogicalFolder(folder.path)">重命名</el-button>
              <el-button size="small" text @click.stop="moveLogicalFolder(folder.path)">移动</el-button>
              <el-button size="small" text type="danger" @click.stop="deleteLogicalFolder(folder.path)">删除</el-button>
            </template>
          </FileCard>

          <FileCard
            v-for="item in currentDirectory.files"
            :key="item.key"
            :title="item.fileName"
            :description="item.relativePath || item.fileName"
            :file-name="item.fileName"
            :extras="[formatSize(item.fileSize)]"
            :footer-label="`${resource.title} · 本地文件`"
            :active="selectedChildId === item.id"
            @click="selectChild(item.id)"
          >
            <template v-if="editingFolder || canDelete(item)" #actions>
              <el-button v-if="editingFolder" size="small" text @click.stop="renameChildFile(item)">重命名</el-button>
              <el-button v-if="editingFolder" size="small" text @click.stop="moveChildFile(item)">移动</el-button>
              <el-button v-if="canDelete(item)" size="small" text type="danger" @click.stop="deleteResource(item)">删除</el-button>
            </template>
          </FileCard>

          <el-empty
            v-if="!currentDirectory.folders.length && !currentDirectory.files.length"
            description="当前目录为空"
          />
        </div>
      </div>

      <main class="folder-preview-pane full-preview-pane">
        <div v-if="previewTarget" class="selected-file-bar">
          <div>
            <strong>{{ previewTarget.fileName || previewTarget.title }}</strong>
            <span>{{ previewTarget.relativePath || previewTarget.fileName || resource.title }}</span>
          </div>
          <div class="selected-actions">
            <el-button size="small" @click="downloadPreviewTarget">下载</el-button>
            <el-button
              v-if="canDeletePreviewTarget"
              size="small"
              type="danger"
              plain
              @click="deleteResource(previewTarget)"
            >
              删除
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="previewKind === 'docx' || previewKind === 'pptx'"
          class="preview-tip"
          title="Office 文档由浏览器端 JS 渲染，复杂排版、动画和母版效果可能与 Office/WPS 不完全一致"
          type="info"
          :closable="false"
          show-icon
        />

        <div v-if="previewKind === 'image'" class="preview-box image-preview">
          <img :src="previewUrl" :alt="previewTarget?.title || resource.title" />
        </div>

        <iframe
          v-else-if="previewKind === 'pdf'"
          class="preview-box document-preview"
          :src="previewUrl"
          title="资源预览"
        />

        <div v-else-if="previewKind === 'docx'" class="preview-box office-preview">
          <div ref="docxContainer" class="docx-preview-container"></div>
        </div>

        <div v-else-if="previewKind === 'pptx'" class="preview-box office-preview pptx-preview">
          <div v-if="pptxSlides.length" class="pptx-slide-list">
            <section v-for="slide in pptxSlides" :key="slide.name" class="pptx-slide">
              <div class="pptx-slide-title">{{ slide.title }}</div>
              <div v-if="slide.images.length" class="pptx-images">
                <img v-for="image in slide.images" :key="image.src" :src="image.src" :alt="image.name" />
              </div>
              <p v-for="(text, index) in slide.texts" :key="index">{{ text }}</p>
              <el-empty v-if="!slide.texts.length && !slide.images.length" description="该页没有可提取的文本或图片" />
            </section>
          </div>
          <el-empty v-else description="正在解析 PPTX，或该文件没有可预览内容" />
        </div>

        <div v-else-if="previewKind === 'video'" class="preview-box media-preview">
          <DanmakuPlayer
            v-if="previewTarget"
            :resource-id="previewTarget.id"
            :src="previewUrl"
            :enabled="danmakuEnabled"
            :permission="danmakuPermission"
            :current-user="currentUser"
            :is-owner-or-admin="danmakuOwnerOrAdmin"
            @open-config="danmakuConfigDialog = true"
          />
        </div>

        <div v-else-if="previewKind === 'audio'" class="preview-box audio-preview">
          <AudioPlayer
            v-if="previewTarget"
            :src="previewUrl"
            :title="previewTarget.title || previewTarget.fileName"
            :subtitle="previewTarget.relativePath || previewTarget.description || resource.title"
            :file-name="previewTarget.fileName"
            :file-size="previewTarget.fileSize"
            @download="downloadPreviewTarget"
            @open="openPreview"
          />
        </div>

        <pre v-else-if="previewKind === 'text'" class="preview-box text-preview">{{ previewText }}</pre>

        <div v-else-if="previewKind === 'legacy-office'" class="preview-box document-preview legacy-frame">
          <div v-if="legacyLoading" class="legacy-loading">正在解析老版 Office 文档……</div>
          <div v-else-if="legacyError" class="legacy-error">
            <p>{{ legacyError }}</p>
            <el-button type="primary" @click="reloadLegacy">重试</el-button>
          </div>
          <iframe
            v-else-if="legacyHtml"
            class="legacy-iframe"
            :srcdoc="legacyHtml"
            title="老版 Office 预览"
            sandbox="allow-same-origin"
          />
        </div>

        <el-empty
          v-else
          class="preview-empty"
          description="该文件类型设置为仅下载，不进行在线预览"
        >
          <div class="empty-actions">
            <el-button v-if="previewTarget" @click="downloadPreviewTarget">下载文件</el-button>
            <el-button
              v-if="canDeletePreviewTarget"
              type="danger"
              plain
              @click="deleteResource(previewTarget)"
            >
              删除文件
            </el-button>
          </div>
          </el-empty>
        </main>

      <el-dialog
        v-model="targetDialog.visible"
        :title="targetDialog.title"
        width="420px"
        class="move-target-dialog"
        @close="cancelTargetDialog"
      >
        <div class="target-folder-list">
          <button
            v-for="folder in targetFolderOptions"
            :key="folder.path || 'root'"
            type="button"
            class="file-tree-item"
            :class="{ active: targetDialog.path === folder.path }"
            :style="{ paddingLeft: `${12 + folder.depth * 14}px` }"
            @click="targetDialog.path = folder.path"
          >
            <span>{{ folder.path ? '▸' : '⌂' }}</span>
            <strong>{{ folder.name }}</strong>
            <em>{{ folder.fileCount }}</em>
          </button>
        </div>
        <template #footer>
          <el-button @click="cancelTargetDialog">取消</el-button>
          <el-button type="primary" @click="confirmTargetDialog">移动到这里</el-button>
        </template>
      </el-dialog>
    </section>

    <el-dialog
      v-model="danmakuConfigDialog"
      title="弹幕设置"
      width="420px"
      append-to-body
      :modal-append-to-body="true"
    >
      <el-form label-width="100px">
        <el-form-item label="启用弹幕">
          <el-switch v-model="danmakuForm.enabled" />
        </el-form-item>
        <el-form-item label="发送权限">
          <el-radio-group v-model="danmakuForm.permission" :disabled="!danmakuForm.enabled">
            <el-radio value="EVERYONE">所有人</el-radio>
            <el-radio value="LOGGED">登录用户</el-radio>
            <el-radio value="OWNER">仅发布者</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="danmakuConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDanmakuConfig" :loading="savingDanmaku">保存</el-button>
      </template>
    </el-dialog>

    <section class="panel comments-panel">
      <h2>评论与评分</h2>
      <el-form v-if="store.state.token" :model="commentForm">
        <el-form-item>
          <el-rate v-model="commentForm.rating" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="commentForm.content" type="textarea" placeholder="写下评论" />
        </el-form-item>
        <el-button type="primary" @click="submitComment">发表评论</el-button>
      </el-form>
      <el-empty v-else description="登录后可评论和评分" />
      <el-timeline style="margin-top: 18px">
        <el-timeline-item v-for="item in comments" :key="item.id" :timestamp="item.createTime">
          <strong>{{ item.username }}</strong>
          <el-rate :model-value="item.rating || 0" disabled size="small" />
          <div>{{ item.content }}</div>
        </el-timeline-item>
      </el-timeline>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderAsync } from 'docx-preview'
import JSZip from 'jszip'
import { commentApi, danmakuApi, favoriteApi, resourceApi } from '../api'
import { buildFileTree, collectFolderPaths, listDirectory, pathBreadcrumb } from '../utils/fileTree.mjs'
import { legacyKind, renderDoc, renderPpt, renderSpreadsheet } from '../utils/legacyOffice'
import { shareResource } from '../utils/shareResource'
import AudioPlayer from '../components/AudioPlayer.vue'
import FileCard from '../components/FileCard.vue'
import DanmakuPlayer from '../components/DanmakuPlayer.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()
const resource = ref(null)
const children = ref([])
const selectedChildId = ref(null)
const currentPath = ref('')
const comments = ref([])
const previewText = ref('')
const legacyHtml = ref('')
const legacyLoading = ref(false)
const legacyError = ref('')
const pptxSlides = ref([])
const docxContainer = ref(null)
const editingFolder = ref(false)
const objectUrls = []
const commentForm = reactive({ content: '', rating: 5 })
const targetDialog = reactive({
  visible: false,
  title: '',
  excludePath: '',
  path: '',
  resolve: null
})

const isFolder = computed(() => resource.value?.resourceType === 'FOLDER')
const currentUser = computed(() => store.state.user)

const danmakuConfigDialog = ref(false)
const savingDanmaku = ref(false)
const danmakuForm = reactive({ enabled: true, permission: 'LOGGED' })
const danmakuEnabled = computed(() => resource.value?.danmakuEnabled !== 0)
const danmakuPermission = computed(() => resource.value?.danmakuPermission || 'LOGGED')
const danmakuOwnerOrAdmin = computed(() => {
  const user = currentUser.value
  if (!user || !resource.value) return false
  return user.role === 'ADMIN' || Number(resource.value.userId) === Number(user.id)
})

watch(resource, (value) => {
  if (!value) return
  danmakuForm.enabled = value.danmakuEnabled !== 0
  danmakuForm.permission = value.danmakuPermission || 'LOGGED'
}, { immediate: true })

async function saveDanmakuConfig() {
  if (!resource.value) return
  savingDanmaku.value = true
  try {
    const updated = await danmakuApi.updateConfig(resource.value.id, {
      enabled: danmakuForm.enabled,
      permission: danmakuForm.permission
    })
    resource.value = { ...resource.value, ...updated }
    ElMessage.success('弹幕设置已更新')
    danmakuConfigDialog.value = false
  } finally {
    savingDanmaku.value = false
  }
}
const treeFiles = computed(() => children.value.map((item) => ({
  ...item,
  key: String(item.id),
  name: item.fileName,
  size: item.fileSize,
  relativePath: item.relativePath || item.fileName
})))
const tree = computed(() => buildFileTree(treeFiles.value))
const folderOptions = computed(() => collectFolderPaths(tree.value))
const targetFolderOptions = computed(() => folderOptions.value.filter((folder) => {
  const excluded = normalizePath(targetDialog.excludePath)
  return !excluded || (folder.path !== excluded && !folder.path.startsWith(`${excluded}/`))
}))
const currentDirectory = computed(() => listDirectory(tree.value, currentPath.value))
const currentAnalysisFileCount = computed(() => treeFiles.value.filter((item) => isPathInFolder(item.relativePath || item.fileName, currentPath.value)).length)
const breadcrumbs = computed(() => pathBreadcrumb(currentPath.value))
const selectedChild = computed(() => children.value.find((item) => item.id === selectedChildId.value) || null)
const previewTarget = computed(() => isFolder.value ? selectedChild.value : resource.value)
const previewUrl = computed(() => previewTarget.value ? resourceApi.previewUrl(previewTarget.value.id) : '')
const previewKind = computed(() => detectPreviewKind(previewTarget.value))
const canDeleteResource = computed(() => canDelete(resource.value))
const canDeletePreviewTarget = computed(() => canDelete(previewTarget.value))
const previewLabel = computed(() => {
  const labels = {
    image: '图片',
    pdf: 'PDF',
    docx: 'DOCX',
    pptx: 'PPTX',
    'legacy-office': '老版 Office',
    video: '视频',
    audio: '音频',
    text: '文本',
    unsupported: '仅下载'
  }
  return labels[previewKind.value] || labels.unsupported
})

async function load() {
  resource.value = await resourceApi.get(route.params.id)
  if (isFolder.value) {
    children.value = await resourceApi.children(route.params.id)
    if (route.query.path !== undefined) {
      currentPath.value = normalizePath(route.query.path)
    }
    if (!children.value.some((item) => item.id === selectedChildId.value)) {
      selectedChildId.value = children.value[0]?.id || null
    }
  } else {
    children.value = []
    selectedChildId.value = null
  }
  comments.value = await commentApi.list(route.params.id)
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push('/resources')
}

function canDelete(item) {
  if (!item || !currentUser.value) return false
  return currentUser.value.role === 'ADMIN' || Number(item.userId) === Number(currentUser.value.id)
}

function detectPreviewKind(item) {
  if (!item) return 'unsupported'
  if (item.resourceType === 'FOLDER') return 'unsupported'
  const type = (item.fileType || '').toLowerCase()
  const name = (item.fileName || '').toLowerCase()
  if (type.startsWith('image/') || /\.(png|jpe?g|gif|webp)$/i.test(name)) return 'image'
  if (type === 'application/pdf' || name.endsWith('.pdf')) return 'pdf'
  if (name.endsWith('.docx')) return 'docx'
  if (name.endsWith('.pptx')) return 'pptx'
  if (/\.(doc|ppt|xls|xlsx|xlsm|xlsb|csv|ods)$/i.test(name)) return 'legacy-office'
  if (type.startsWith('video/') || /\.(mp4|mov|webm)$/i.test(name)) return 'video'
  if (type.startsWith('audio/') || /\.(mp3|wav|ogg)$/i.test(name)) return 'audio'
  if (type.startsWith('text/') || name.endsWith('.txt')) return 'text'
  return 'unsupported'
}

function fileLabel(name = '') {
  const lower = name.toLowerCase()
  if (lower.endsWith('.pdf')) return 'PDF'
  if (lower.endsWith('.docx')) return 'DOC'
  if (lower.endsWith('.pptx')) return 'PPT'
  if (/\.(png|jpe?g|gif|webp)$/.test(lower)) return 'IMG'
  if (/\.(mp4|mov|webm)$/.test(lower)) return 'VID'
  if (/\.(mp3|wav|ogg)$/.test(lower)) return 'AUD'
  if (lower.endsWith('.txt')) return 'TXT'
  return 'FILE'
}

function formatSize(size = 0) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function normalizePath(path = '') {
  return String(path).replace(/\\/g, '/').split('/').map((part) => part.trim()).filter(Boolean).join('/')
}

function joinPath(base = '', path = '') {
  return [base, path].map(normalizePath).filter(Boolean).join('/')
}

function parentPath(path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  parts.pop()
  return parts.join('/')
}

function isPathInFolder(relativePath = '', folderPath = '') {
  const relative = normalizePath(relativePath)
  const target = normalizePath(folderPath)
  if (!relative) return false
  if (!target) return true
  return relative.startsWith(`${target}/`)
}

async function promptText(title, message, placeholder, value = '') {
  try {
    const result = await ElMessageBox.prompt(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: value,
      inputPlaceholder: placeholder,
      inputValidator: (input) => {
        if (!input || !input.trim()) return '名称不能为空'
        if (/[<>:"|?*]/.test(input)) return '名称不能包含 <>:"|?*'
        return true
      }
    })
    return result.value
  } catch {
    return ''
  }
}

function folderKeysFromPath(path) {
  const target = normalizePath(path)
  return children.value
    .filter((item) => {
      const relativePath = normalizePath(item.relativePath || item.fileName)
      return relativePath === target || relativePath.startsWith(`${target}/`)
    })
    .map((item) => String(item.id))
}

async function chooseTargetFolder(title, excludePath = '') {
  targetDialog.title = title
  targetDialog.excludePath = excludePath
  const excluded = normalizePath(excludePath)
  const current = normalizePath(currentPath.value)
  targetDialog.path = excluded && (current === excluded || current.startsWith(`${excluded}/`)) ? '' : current
  targetDialog.visible = true
  return new Promise((resolve) => {
    targetDialog.resolve = resolve
  })
}

function confirmTargetDialog() {
  const resolve = targetDialog.resolve
  const path = normalizePath(targetDialog.path)
  resetTargetDialog()
  resolve?.(path)
}

function cancelTargetDialog() {
  const resolve = targetDialog.resolve
  if (!resolve) return
  resetTargetDialog()
  resolve?.(null)
}

function resetTargetDialog() {
  targetDialog.visible = false
  targetDialog.title = ''
  targetDialog.excludePath = ''
  targetDialog.path = ''
  targetDialog.resolve = null
}

function openFolder(path) {
  currentPath.value = path
}

function goUpFolder() {
  const parts = String(currentPath.value || '').split('/').filter(Boolean)
  parts.pop()
  currentPath.value = parts.join('/')
}

function selectChild(id) {
  selectedChildId.value = id
}

function selectFirstInCurrentDirectory() {
  const first = currentDirectory.value.files[0]
  if (!first) {
    ElMessage.info('当前目录没有可预览文件')
    return
  }
  selectedChildId.value = first.id
}

function analyzeCurrentFolder() {
  if (!resource.value || !isFolder.value) {
    ElMessage.warning('分析 Agent 只支持用户上传的虚拟文件夹')
    return
  }
  if (!currentAnalysisFileCount.value) {
    ElMessage.info('当前虚拟文件夹下没有可分析文件')
    return
  }
  router.push({
    path: '/agent/analyze',
    query: {
      resourceId: resource.value.id,
      path: currentPath.value || undefined
    }
  })
}

async function appendFilesAtCurrentPath(event) {
  const input = event.target
  try {
    await appendChildren(Array.from(input.files || []), false)
  } catch (error) {
    ElMessage.error(error.message || '追加文件失败')
  } finally {
    input.value = ''
  }
}

async function appendFolderAtCurrentPath(event) {
  const input = event.target
  try {
    await appendChildren(Array.from(input.files || []), true)
  } catch (error) {
    ElMessage.error(error.message || '追加文件夹失败')
  } finally {
    input.value = ''
  }
}

async function appendChildren(files, preserveFolderPath) {
  if (!resource.value || !files.length) return
  const data = new FormData()
  files.forEach((file) => {
    const sourcePath = preserveFolderPath ? (file.webkitRelativePath || file.name) : file.name
    data.append('files', file)
    data.append('relativePaths', joinPath(currentPath.value, sourcePath))
  })
  const created = await resourceApi.appendChildren(resource.value.id, data)
  selectedChildId.value = created[0]?.id || selectedChildId.value
  ElMessage.success(`已追加 ${created.length} 个文件`)
  await load()
}

async function renameChildFile(item) {
  const name = await promptText('重命名文件', '请输入新的文件名', item.fileName, item.fileName)
  if (!name) return
  const relativePath = joinPath(parentPath(item.relativePath || item.fileName), name)
  const updated = await resourceApi.updateFilePath(item.id, { fileName: name, relativePath })
  selectedChildId.value = updated.id
  ElMessage.success('文件已重命名')
  await load()
}

async function moveChildFile(item) {
  const target = await chooseTargetFolder('移动文件')
  if (target === null) return
  const relativePath = joinPath(target, item.fileName)
  const updated = await resourceApi.updateFilePath(item.id, { fileName: item.fileName, relativePath })
  selectedChildId.value = updated.id
  ElMessage.success(`已移动到 ${target || '全部文件'}`)
  await load()
}

async function renameLogicalFolder(path) {
  const currentName = path.split('/').pop()
  const name = await promptText('重命名文件夹', '请输入新的文件夹名', currentName, currentName)
  if (!name || !resource.value) return
  const targetPath = joinPath(parentPath(path), name)
  await resourceApi.updateFolderPath(resource.value.id, { sourcePath: path, targetPath })
  currentPath.value = targetPath
  ElMessage.success('文件夹已重命名')
  await load()
}

async function moveLogicalFolder(path) {
  if (!resource.value) return
  const target = await chooseTargetFolder('移动文件夹', path)
  if (target === null) return
  const targetPath = joinPath(target, path.split('/').pop())
  await resourceApi.updateFolderPath(resource.value.id, { sourcePath: path, targetPath })
  currentPath.value = targetPath
  ElMessage.success('文件夹已移动')
  await load()
}

async function deleteLogicalFolder(path) {
  if (!resource.value) return
  try {
    await ElMessageBox.confirm('删除该文件夹会同时删除其中所有文件，确定继续吗？', '删除文件夹', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  const keys = new Set(folderKeysFromPath(path))
  const targets = children.value.filter((item) => keys.has(String(item.id)))
  await Promise.all(targets.map((item) => resourceApi.remove(item.id)))
  selectedChildId.value = null
  currentPath.value = ''
  ElMessage.success('文件夹已删除')
  await load()
}

function downloadResource() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (!resource.value) return
  window.open(resourceApi.downloadUrl(resource.value.id), '_blank')
}

function downloadPreviewTarget() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (!previewTarget.value || previewTarget.value.resourceType === 'FOLDER') {
    ElMessage.warning('请先选择文件')
    return
  }
  window.open(resourceApi.downloadUrl(previewTarget.value.id), '_blank')
}

function openPreview() {
  if (isFolder.value && !selectedChild.value) {
    ElMessage.info('请先选择文件')
    return
  }
  if (previewKind.value === 'unsupported') {
    ElMessage.info('该文件类型仅支持下载')
    return
  }
  window.open(previewUrl.value, '_blank')
}

async function deleteResource(item) {
  if (!item || !canDelete(item)) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.fileName || item.title}」吗？删除后列表中将不再显示。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await resourceApi.remove(item.id)
  ElMessage.success('删除成功')
  if (Number(item.id) === Number(resource.value.id)) {
    router.push('/resources')
    return
  }
  selectedChildId.value = null
  await load()
}

async function toggleFavorite() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (resource.value.favorite) {
    await favoriteApi.removeByResource(resource.value.id)
    ElMessage.success('已取消收藏')
  } else {
    await favoriteApi.create(resource.value.id)
    ElMessage.success('收藏成功')
  }
  await load()
}

async function submitComment() {
  await commentApi.create({ resourceId: Number(route.params.id), content: commentForm.content, rating: commentForm.rating })
  commentForm.content = ''
  ElMessage.success('评论成功')
  await load()
}

async function loadDocxPreview() {
  await nextTick()
  if (!docxContainer.value || !previewTarget.value) return
  docxContainer.value.innerHTML = ''
  try {
    const buffer = await fetchPreviewArrayBuffer()
    await renderAsync(buffer, docxContainer.value, null, {
      className: 'docx',
      inWrapper: true,
      ignoreWidth: false,
      ignoreHeight: false,
      breakPages: true
    })
  } catch (error) {
    ElMessage.error(error.message || 'DOCX 预览失败')
  }
}

async function loadPptxPreview() {
  clearObjectUrls()
  pptxSlides.value = []
  try {
    const buffer = await fetchPreviewArrayBuffer()
    const zip = await JSZip.loadAsync(buffer)
    const slideNames = Object.keys(zip.files)
      .filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
      .sort((a, b) => slideNumber(a) - slideNumber(b))
    const mediaMap = await buildPptxMediaMap(zip)
    pptxSlides.value = await Promise.all(slideNames.map(async (name, index) => {
      const xml = await zip.file(name).async('string')
      const rels = await readSlideRelationships(zip, slideNumber(name))
      const texts = extractPptxText(xml)
      const images = extractPptxImages(xml, rels, mediaMap)
      return { name, title: `第 ${index + 1} 页`, texts, images }
    }))
  } catch (error) {
    ElMessage.error(error.message || 'PPTX 预览失败')
  }
}

async function fetchPreviewArrayBuffer() {
  const response = await fetch(previewUrl.value)
  if (!response.ok) {
    throw new Error(`预览文件加载失败：${response.status}`)
  }
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const data = await response.json()
    throw new Error(data.message || '预览文件加载失败')
  }
  return response.arrayBuffer()
}

function extractPptxText(xml) {
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  return Array.from(document.getElementsByTagName('a:t'))
    .map((node) => node.textContent.trim())
    .filter(Boolean)
}

async function readSlideRelationships(zip, slideNo) {
  const file = zip.file(`ppt/slides/_rels/slide${slideNo}.xml.rels`)
  if (!file) return {}
  const xml = await file.async('string')
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  const rels = {}
  Array.from(document.getElementsByTagName('Relationship')).forEach((node) => {
    rels[node.getAttribute('Id')] = node.getAttribute('Target')
  })
  return rels
}

async function buildPptxMediaMap(zip) {
  const entries = Object.keys(zip.files).filter((name) => name.startsWith('ppt/media/'))
  const result = {}
  await Promise.all(entries.map(async (name) => {
    const blob = await zip.file(name).async('blob')
    const src = URL.createObjectURL(blob)
    objectUrls.push(src)
    result[name] = { name, src }
  }))
  return result
}

function extractPptxImages(xml, rels, mediaMap) {
  const ids = Array.from(xml.matchAll(/r:embed="([^"]+)"/g)).map((match) => match[1])
  return ids
    .map((id) => rels[id])
    .filter(Boolean)
    .map((target) => target.replace(/^\.\.\//, 'ppt/'))
    .map((name) => mediaMap[name])
    .filter(Boolean)
}

function slideNumber(path) {
  return Number(path.match(/slide(\d+)\.xml/)?.[1] || 0)
}

function clearObjectUrls() {
  while (objectUrls.length) {
    URL.revokeObjectURL(objectUrls.pop())
  }
}

watch([previewKind, previewTarget], async ([kind]) => {
  previewText.value = ''
  pptxSlides.value = []
  legacyHtml.value = ''
  legacyError.value = ''
  clearObjectUrls()
  if (kind === 'text' && previewTarget.value) {
    previewText.value = await resourceApi.previewText(previewTarget.value.id)
  }
  if (kind === 'docx') {
    await loadDocxPreview()
  }
  if (kind === 'pptx') {
    await loadPptxPreview()
  }
  if (kind === 'legacy-office') {
    await loadLegacyPreview()
  }
})

async function loadLegacyPreview() {
  legacyHtml.value = ''
  legacyError.value = ''
  if (!previewTarget.value) return
  const name = previewTarget.value.fileName || previewTarget.value.title || ''
  const kind = legacyKind(name)
  if (!kind) {
    legacyError.value = '不支持的老版 Office 格式'
    return
  }
  legacyLoading.value = true
  try {
    const response = await fetch(previewUrl.value)
    if (!response.ok) throw new Error(`加载失败：${response.status}`)
    const buffer = await response.arrayBuffer()
    if (kind === 'spreadsheet') {
      legacyHtml.value = renderSpreadsheet(buffer, name)
    } else if (kind === 'doc') {
      legacyHtml.value = renderDoc(buffer, name)
    } else if (kind === 'ppt') {
      legacyHtml.value = renderPpt(buffer, name)
    }
  } catch (error) {
    legacyError.value = error.message || '解析失败'
  } finally {
    legacyLoading.value = false
  }
}

function reloadLegacy() {
  loadLegacyPreview()
}

onMounted(load)
onBeforeUnmount(clearObjectUrls)
</script>
