<template>
  <div class="form-page">
    <section class="panel form-panel upload-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Upload Workspace</span>
          <h1>上传资源</h1>
          <p class="muted">使用面包屑在目录间切换，点文件查看本地预览，勾选后一次性上传。</p>
        </div>
      </div>

      <el-form :model="form" label-position="top">
        <el-form-item label="资源标题">
          <el-input v-model="form.title" size="large" placeholder="例如：Java 第 1 讲课程资料" />
        </el-form-item>
        <el-form-item label="资源描述">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="说明资源内容、课程章节或使用方式" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="form.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :multiple-limit="6"
            placeholder="输入标签后回车，最多 6 个，每个不超过 16 字"
            size="large"
            style="width: 100%"
            @change="normalizeFormTags"
          >
          </el-select>
        </el-form-item>

        <div class="selection-summary">
          <div>
            <strong>已选 {{ selectedInfo.count }} / {{ batchFiles.length }} 个文件</strong>
            <span>{{ formatSize(selectedInfo.size) }} / {{ formatSize(totalSize) }}</span>
            <small v-if="workspaceSavedAt">草稿已保存：{{ workspaceSavedAt }}</small>
          </div>
          <div class="summary-actions">
            <el-button size="small" @click="selectAllFiles">全选</el-button>
            <el-button size="small" @click="clearSelectedFiles">取消全选</el-button>
            <el-button size="small" type="danger" plain @click="clearBatch">清空工作区</el-button>
          </div>
        </div>

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
          <label class="inline-upload-button">
            <input type="file" multiple @change="addFilesAtCurrentPath" />
            <span>上传文件</span>
          </label>
          <label class="inline-upload-button">
            <input type="file" multiple webkitdirectory directory @change="addFolderAtCurrentPath" />
            <span>上传文件夹</span>
          </label>
          <el-button size="small" type="primary" plain @click="createFolder">新建文件夹</el-button>
        </div>

        <div class="directory-toolbar">
          <span>当前位置：{{ currentPath || '全部文件' }} · {{ currentDirectory.folder.fileCount }} 个文件</span>
          <div>
            <el-button size="small" @click="selectCurrentFolder">选择当前目录</el-button>
            <el-button size="small" @click="unselectCurrentFolder">取消当前目录</el-button>
            <el-button size="small" @click="moveSelectedFilesToCurrentFolder">已选移入此处</el-button>
          </div>
        </div>

        <div class="grid">
          <FileCard
            v-for="folder in currentDirectory.folders"
            :key="folder.path"
            :title="folder.name"
            :description="`${folder.fileCount} 个文件 · ${formatSize(folder.totalSize)}`"
            :is-folder="true"
            footer-label="待上传目录"
            @click="openFolder(folder.path)"
          >
            <template #actions>
              <el-button size="small" text @click.stop="renameFolder(folder.path)">重命名</el-button>
              <el-button size="small" text @click.stop="moveFolder(folder.path)">移动</el-button>
              <el-button size="small" text type="danger" @click.stop="removeFolder(folder.path)">删除</el-button>
            </template>
          </FileCard>

          <FileCard
            v-for="item in currentDirectory.files"
            :key="item.key"
            :title="item.name"
            :description="item.relativePath"
            :file-name="item.name"
            :extras="[formatSize(item.size), selectedKeys.has(item.key) ? '已勾选' : '']"
            footer-label="本地待上传"
            :active="selectedKey === item.key || selectedKeys.has(item.key)"
            @click="previewFile(item)"
          >
            <template #actions>
              <el-checkbox
                :model-value="selectedKeys.has(item.key)"
                @click.stop
                @change="(checked) => toggleFile(item.key, checked)"
              />
              <el-button size="small" text @click.stop="renameFile(item.key)">改名</el-button>
              <el-button size="small" text @click.stop="moveFile(item.key)">移动</el-button>
              <el-button size="small" text type="danger" @click.stop="removeBatchFile(item.key)">删除</el-button>
            </template>
          </FileCard>

          <el-empty
            v-if="!currentDirectory.folders.length && !currentDirectory.files.length"
            description="当前目录为空，使用上方按钮添加文件或文件夹"
          />
        </div>

        <el-dialog
          v-model="targetDialog.visible"
          :title="targetDialog.title"
          width="460px"
          append-to-body
          :modal-append-to-body="true"
          class="move-target-dialog"
          @close="cancelTargetDialog"
        >
          <div class="target-folder-list">
            <button
              v-for="folder in targetFolderOptions"
              :key="folder.path || 'root'"
              type="button"
              class="target-folder-item"
              :class="{ active: targetDialog.path === folder.path }"
              :style="{ paddingLeft: `${12 + folder.depth * 14}px` }"
              @click="targetDialog.path = folder.path"
            >
              <span>{{ folder.path ? '📁' : '🏠' }}</span>
              <strong>{{ folder.name }}</strong>
              <em>{{ folder.fileCount }}</em>
            </button>
          </div>
          <template #footer>
            <el-button @click="cancelTargetDialog">取消</el-button>
            <el-button type="primary" @click="confirmTargetDialog">移动到这里</el-button>
          </template>
        </el-dialog>

        <el-progress v-if="progress > 0" :percentage="progress" />
        <el-button class="full-button" type="primary" size="large" @click="submit">
          上传已选文件（{{ selectedInfo.count }} 个）
        </el-button>
      </el-form>

      <el-drawer
        v-model="previewVisible"
        size="72%"
        direction="rtl"
        :with-header="true"
        :title="selectedFile?.name || '本地预览'"
        append-to-body
        :modal-append-to-body="true"
        destroy-on-close
      >
        <FilePreviewPanel
          v-if="previewVisible && selectedFile"
          :file="previewFileObject"
          :kind="previewKind"
          :preview-url="localPreviewUrl"
          :fetch-binary="fetchLocalBinary"
          :fetch-text="fetchLocalText"
          @download="() => {}"
          @open="() => {}"
        />
      </el-drawer>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourceApi } from '../api'
import {
  buildFileTree,
  childFolderPath,
  collectFolderPaths,
  folderKeys,
  listDirectory,
  moveFileToFolder,
  moveFolderToFolder,
  pathBreadcrumb,
  removeFolderEntries,
  renameFilePath,
  renameFolderPath,
  selectedStats,
  uniqueFilePath
} from '../utils/fileTree.mjs'
import {
  clearUploadWorkspace,
  loadUploadWorkspace,
  saveUploadWorkspace
} from '../utils/uploadWorkspace'
import { detectPreviewKind as detectKind, formatSize } from '../utils/fileIcon'
import FileCard from '../components/FileCard.vue'
import FilePreviewPanel from '../components/FilePreviewPanel.vue'

const router = useRouter()
const progress = ref(0)
const batchFiles = ref([])
const virtualFolders = ref([])
const selectedKeys = ref(new Set())
const selectedKey = ref('')
const currentPath = ref('')
const localPreviewUrl = ref('')
const restored = ref(false)
const workspaceSavedAt = ref('')
const previewVisible = ref(false)
const form = reactive({ title: '', description: '', tags: [] })
const targetDialog = reactive({
  visible: false,
  title: '',
  excludePath: '',
  path: '',
  resolve: null
})

const tree = computed(() => buildFileTree(batchFiles.value, virtualFolders.value))
const folderOptions = computed(() => collectFolderPaths(tree.value))
const targetFolderOptions = computed(() => folderOptions.value.filter((folder) => {
  const excluded = normalizePath(targetDialog.excludePath)
  return !excluded || (folder.path !== excluded && !folder.path.startsWith(`${excluded}/`))
}))
const currentDirectory = computed(() => listDirectory(tree.value, currentPath.value))
const breadcrumbs = computed(() => pathBreadcrumb(currentPath.value))
const selectedInfo = computed(() => selectedStats(batchFiles.value, selectedKeys.value))
const totalSize = computed(() => batchFiles.value.reduce((sum, item) => sum + item.size, 0))
const selectedFile = computed(() => batchFiles.value.find((item) => item.key === selectedKey.value) || null)

// Shape the workspace file to look like the preview-panel's expected File prop.
const previewFileObject = computed(() => {
  if (!selectedFile.value) return null
  return {
    name: selectedFile.value.name,
    fileName: selectedFile.value.name,
    path: selectedFile.value.relativePath,
    type: selectedFile.value.file.type,
    size: selectedFile.value.size,
    directory: false
  }
})
const previewKind = computed(() => selectedFile.value ? detectKind(previewFileObject.value) : 'unsupported')

function openFolder(path) {
  currentPath.value = path
}

function goUpFolder() {
  const parts = String(currentPath.value || '').split('/').filter(Boolean)
  parts.pop()
  currentPath.value = parts.join('/')
}

function onCardClick(item) {
  toggleFile(item.key, !selectedKeys.value.has(item.key))
  selectedKey.value = item.key
}

function previewFile(item) {
  selectedKey.value = item.key
  previewVisible.value = true
}

// Provide ArrayBuffer / text straight from the File object so the preview panel
// never has to call the backend for locally staged files.
async function fetchLocalBinary() {
  if (!selectedFile.value) throw new Error('未选择文件')
  return selectedFile.value.file.arrayBuffer()
}

async function fetchLocalText() {
  if (!selectedFile.value) return ''
  return selectedFile.value.file.text()
}

function toggleFile(key, checked) {
  const next = new Set(selectedKeys.value)
  checked ? next.add(key) : next.delete(key)
  selectedKeys.value = next
}

function selectAllFiles() {
  selectedKeys.value = new Set(batchFiles.value.map((item) => item.key))
}

function clearSelectedFiles() {
  selectedKeys.value = new Set()
}

function selectCurrentFolder() {
  const next = new Set(selectedKeys.value)
  folderKeys(tree.value, currentPath.value).forEach((key) => next.add(key))
  selectedKeys.value = next
}

function unselectCurrentFolder() {
  const next = new Set(selectedKeys.value)
  folderKeys(tree.value, currentPath.value).forEach((key) => next.delete(key))
  selectedKeys.value = next
}

function addFilesAtCurrentPath(event) {
  appendFiles(Array.from(event.target.files || []), false)
  event.target.value = ''
}

function addFolderAtCurrentPath(event) {
  appendFiles(Array.from(event.target.files || []), true)
  event.target.value = ''
}

function appendFiles(files, preserveFolderPath) {
  if (!files.length) return
  const basePath = currentPath.value
  const usedPaths = new Set(batchFiles.value.map((item) => normalizePath(item.relativePath).toLowerCase()))
  const created = files.map((file, index) => {
    const sourcePath = preserveFolderPath ? (file.webkitRelativePath || file.name) : file.name
    let relativePath = uniqueFilePath(batchFiles.value, joinPath(basePath, sourcePath))
    while (usedPaths.has(relativePath.toLowerCase())) {
      relativePath = uniqueFilePath(
        [...batchFiles.value, ...Array.from(usedPaths).map((path) => ({ relativePath: path }))],
        relativePath
      )
    }
    usedPaths.add(relativePath.toLowerCase())
    return createWorkspaceFile(file, relativePath, index)
  })
  const existing = new Set(batchFiles.value.map((item) => item.key))
  const nextFiles = [...batchFiles.value]
  created.forEach((item) => {
    let key = item.key
    let suffix = 1
    while (existing.has(key)) {
      key = `${item.key}-${suffix}`
      suffix += 1
    }
    item.key = key
    existing.add(key)
    nextFiles.push(item)
  })
  batchFiles.value = nextFiles
  selectedKeys.value = new Set([...selectedKeys.value, ...created.map((item) => item.key)])
  selectedKey.value = created[0]?.key || selectedKey.value
  if (!form.title) {
    form.title = preserveFolderPath ? folderTitle(created[0].relativePath) : stripExtension(created[0].name)
  }
  ElMessage.success(`已添加 ${created.length} 个文件`)
}

function createWorkspaceFile(file, relativePath, index = 0) {
  const normalized = normalizePath(relativePath)
  const name = normalized.split('/').pop() || file.name
  return {
    key: `${normalized}-${file.size}-${file.lastModified}-${Date.now()}-${index}`,
    file,
    name,
    size: file.size,
    relativePath: normalized
  }
}

async function createFolder() {
  const name = await promptText('新建文件夹', '请输入文件夹名称', '例如：第一章')
  if (!name) return
  try {
    const path = childFolderPath(currentPath.value, name)
    if (folderOptions.value.some((folder) => folder.path === path)) {
      ElMessage.warning('当前目录已存在同名文件夹')
      return
    }
    virtualFolders.value = [...virtualFolders.value, path]
    currentPath.value = path
    ElMessage.success('文件夹已创建')
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function renameFile(key) {
  const item = batchFiles.value.find((file) => file.key === key)
  if (!item) return
  const name = await promptText('重命名文件', '请输入新的文件名', item.name, item.name)
  if (!name) return
  try {
    batchFiles.value = renameFilePath(batchFiles.value, key, name)
    ElMessage.success('文件已重命名')
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function renameFolder(path) {
  const currentName = path.split('/').pop()
  const name = await promptText('重命名文件夹', '请输入新的文件夹名', currentName, currentName)
  if (!name) return
  try {
    const result = renameFolderPath(batchFiles.value, virtualFolders.value, path, name)
    batchFiles.value = result.files
    virtualFolders.value = result.virtualFolders
    currentPath.value = result.path
    ElMessage.success('文件夹已重命名')
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function moveFile(key) {
  const target = await chooseTargetFolder('移动文件')
  if (target === null) return
  try {
    batchFiles.value = moveFileToFolder(batchFiles.value, key, target)
    selectedKey.value = key
    ElMessage.success(`已移动到 ${target || '全部文件'}`)
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function moveFolder(path) {
  const target = await chooseTargetFolder('移动文件夹', path)
  if (target === null) return
  try {
    const result = moveFolderToFolder(batchFiles.value, virtualFolders.value, path, target)
    batchFiles.value = result.files
    virtualFolders.value = result.virtualFolders
    currentPath.value = result.path
    ElMessage.success('文件夹已移动')
  } catch (error) {
    ElMessage.warning(error.message)
  }
}

async function removeFolder(path) {
  try {
    await ElMessageBox.confirm('删除该文件夹会同时移除其中待上传文件，确定继续吗？', '删除文件夹', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  const removedKeys = folderKeys(tree.value, path)
  const result = removeFolderEntries(batchFiles.value, virtualFolders.value, path)
  batchFiles.value = result.files
  virtualFolders.value = result.virtualFolders
  const nextSelected = new Set(selectedKeys.value)
  removedKeys.forEach((key) => nextSelected.delete(key))
  selectedKeys.value = nextSelected
  if (selectedKey.value && removedKeys.includes(selectedKey.value)) {
    selectedKey.value = batchFiles.value[0]?.key || ''
  }
  currentPath.value = ''
  ElMessage.success('文件夹已删除')
}

function moveSelectedFilesToCurrentFolder() {
  const keys = Array.from(selectedKeys.value)
  if (!keys.length) {
    ElMessage.info('请先勾选要移动的文件')
    return
  }
  batchFiles.value = keys.reduce((items, key) => moveFileToFolder(items, key, currentPath.value), batchFiles.value)
  ElMessage.success(`已移动 ${keys.length} 个文件`)
}

function removeBatchFile(key) {
  const index = batchFiles.value.findIndex((item) => item.key === key)
  batchFiles.value = batchFiles.value.filter((item) => item.key !== key)
  const next = new Set(selectedKeys.value)
  next.delete(key)
  selectedKeys.value = next
  if (selectedKey.value === key) {
    selectedKey.value = batchFiles.value[Math.min(index, batchFiles.value.length - 1)]?.key || ''
  }
}

async function clearBatch() {
  try {
    await ElMessageBox.confirm('确定清空当前上传工作区吗？', '清空工作区', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  batchFiles.value = []
  virtualFolders.value = []
  selectedKeys.value = new Set()
  selectedKey.value = ''
  currentPath.value = ''
  workspaceSavedAt.value = ''
  clearLocalPreview()
  restored.value = false
  await clearUploadWorkspace()
  restored.value = true
}

async function submit() {
  if (!form.title) {
    ElMessage.warning('请输入标题')
    return
  }
  normalizeFormTags()
  const uploadItems = batchFiles.value.filter((item) => selectedKeys.value.has(item.key))
  if (!uploadItems.length) {
    ElMessage.warning('请至少勾选一个要上传的文件')
    return
  }
  const data = new FormData()
  data.append('title', form.title)
  data.append('description', form.description)
  if (form.tags.length) {
    data.append('tags', form.tags.join(','))
  }
  const isPlainSingleFile = uploadItems.length === 1 && uploadItems[0].relativePath === uploadItems[0].file.name
  if (isPlainSingleFile) {
    data.append('file', uploadItems[0].file)
    const resource = await resourceApi.upload(data, updateProgress)
    ElMessage.success('上传成功')
    await clearUploadWorkspace()
    router.push(`/resources/${resource.id}`)
    return
  }
  uploadItems.forEach((item) => {
    data.append('files', item.file)
    data.append('relativePaths', item.relativePath)
  })
  const resource = await resourceApi.uploadBatch(data, updateProgress)
  ElMessage.success('文件夹上传成功')
  await clearUploadWorkspace()
  router.push(`/resources/${resource.id}`)
}

function updateProgress(event) {
  progress.value = Math.round((event.loaded / event.total) * 100)
}

function stripExtension(name = '') {
  return name.replace(/\.[^.]+$/, '')
}

function folderTitle(relativePath = '') {
  const parts = relativePath.split(/[\\/]/).filter(Boolean)
  return parts.length > 1 ? parts[0] : '课程资料'
}

function normalizeFormTags() {
  form.tags = normalizeTags(form.tags)
}

function normalizeTags(values) {
  const source = Array.isArray(values) ? values : String(values || '').split(/[,，;；\s]+/)
  const result = []
  const seen = new Set()
  source.forEach((value) => {
    const tag = String(value || '').replace(/[#<>"'`]/g, '').trim().slice(0, 16)
    const key = tag.toLowerCase()
    if (!tag || seen.has(key) || result.length >= 6) return
    seen.add(key)
    result.push(tag)
  })
  return result
}

function normalizePath(path = '') {
  return String(path).replace(/\\/g, '/').split('/').filter(Boolean).join('/')
}

function joinPath(base = '', path = '') {
  return [base, path].map(normalizePath).filter(Boolean).join('/')
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

function clearLocalPreview() {
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value)
  }
  localPreviewUrl.value = ''
}

async function restoreWorkspace() {
  const draft = await loadUploadWorkspace()
  if (!draft) return
  form.title = draft.form?.title || ''
  form.description = draft.form?.description || ''
  form.tags = normalizeTags(draft.form?.tags || [])
  batchFiles.value = (draft.files || []).filter((item) => item.file).map((item) => ({
    ...item,
    size: item.size ?? item.file.size,
    name: item.name || item.file.name
  }))
  virtualFolders.value = draft.virtualFolders || []
  selectedKeys.value = new Set(draft.selectedKeys || batchFiles.value.map((item) => item.key))
  selectedKey.value = draft.selectedKey || batchFiles.value[0]?.key || ''
  currentPath.value = draft.currentPath || ''
  workspaceSavedAt.value = draft.savedAt ? new Date(draft.savedAt).toLocaleTimeString() : ''
}

async function persistWorkspace() {
  if (!restored.value) return
  await saveUploadWorkspace({
    form: { ...form },
    files: batchFiles.value,
    virtualFolders: virtualFolders.value,
    selectedKeys: Array.from(selectedKeys.value),
    selectedKey: selectedKey.value,
    currentPath: currentPath.value
  })
  workspaceSavedAt.value = new Date().toLocaleTimeString()
}

watch(selectedFile, async (item) => {
  clearLocalPreview()
  if (!item) return
  // Generate an object URL for binary previews (image/pdf/audio/video)
  // regardless of kind — the preview panel itself decides how to use it.
  localPreviewUrl.value = URL.createObjectURL(item.file)
})

watch(
  [batchFiles, virtualFolders, selectedKeys, selectedKey, currentPath, () => form.title, () => form.description, () => form.tags],
  persistWorkspace,
  { deep: true }
)

onMounted(async () => {
  await restoreWorkspace()
  restored.value = true
})

onBeforeUnmount(clearLocalPreview)
</script>

<style scoped>
.upload-panel :deep(.el-form-item__label) {
  font-weight: 600;
}

.inline-upload-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(28, 124, 125, 0.3);
  border-radius: 6px;
  background: rgba(238, 246, 246, 0.8);
  color: var(--primary-strong);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms ease;
}

.inline-upload-button:hover {
  background: var(--primary);
  color: #ffffff;
}

.inline-upload-button input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.target-folder-list {
  max-height: 55vh;
  overflow: auto;
  padding: 4px;
}

.target-folder-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  text-align: left;
  cursor: pointer;
  font-size: 13px;
}

.target-folder-item:hover,
.target-folder-item.active {
  border-color: rgba(28, 124, 125, 0.34);
  background: rgba(238, 246, 246, 0.8);
  color: var(--primary-strong);
}

.target-folder-item em {
  color: var(--muted);
  font-style: normal;
  font-size: 12px;
}
</style>
