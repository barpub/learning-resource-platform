<template>
  <div class="resource-list-page">
    <section class="list-toolbar panel">
      <div>
        <span class="eyebrow">Library</span>
        <h1>资源库</h1>
        <p class="muted">统一浏览本地资源和远程 FTP 资源，支持搜索、排序、预览和下载。</p>
      </div>

      <div class="toolbar-controls" v-if="mode === 'local'">
        <el-input v-model="query.keyword" placeholder="搜索标题、描述、作者或标签" clearable @keyup.enter="search" />
        <el-select v-model="query.sort" style="width: 150px" @change="search">
          <el-option label="最新上传" value="createTime" />
          <el-option label="下载最多" value="downloadCount" />
          <el-option label="评分最高" value="rating" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <div class="toolbar-controls" v-else-if="mode === 'ftp' && ftpConnection">
        <el-input
          v-model="ftpKeyword"
          placeholder="递归搜索当前目录和子文件夹"
          clearable
          style="width: 280px"
          @keyup.enter="searchFtp"
          @clear="clearFtpSearch"
        />
        <el-select v-model="ftpSort" style="width: 150px">
          <el-option label="按名称" value="name" />
          <el-option label="按大小" value="size" />
          <el-option label="按修改时间" value="modified" />
          <el-option label="按类型" value="type" />
        </el-select>
        <el-button @click="ftpOrder = ftpOrder === 'asc' ? 'desc' : 'asc'">
          {{ ftpOrder === 'asc' ? '升序' : '降序' }}
        </el-button>
        <el-button :loading="ftpSearching" @click="searchFtp">搜索</el-button>
      </div>

      <div class="toolbar-controls" v-else-if="mode === 'ftp'">
        <el-input v-model="connectionKeyword" placeholder="搜索 FTP 连接" clearable style="width: 240px" />
      </div>
    </section>

    <section class="content-layout list-layout">
      <aside class="category-panel">
        <div class="panel-title">来源</div>
        <button class="category-pill" :class="{ active: mode === 'local' }" @click="useLocal">
          <span>本地全部资源</span>
          <em>{{ localTotal }}</em>
        </button>

        <div class="panel-title" style="margin-top: 18px">远程</div>
        <button class="category-pill" :class="{ active: mode === 'ftp' }" @click="useFtp()">
          <span>远程资源库</span>
          <em>{{ connections.length }}</em>
        </button>
      </aside>

      <main>
        <template v-if="mode === 'local'">
          <div class="grid">
            <ResourceCard v-for="item in records" :key="item.id" :resource="item" />
          </div>
          <el-empty v-if="records.length === 0" description="暂无匹配资源" />
          <el-pagination
            class="pagination-row"
            background
            layout="prev, pager, next"
            :page-size="query.size"
            :total="localTotal"
            v-model:current-page="query.page"
            @current-change="loadLocal"
          />
        </template>

        <template v-else>
          <div class="ftp-breadcrumb">
            <button type="button" @click="openFtpHome">远程资源库</button>
            <template v-if="ftpConnection">
              <span class="crumb-sep">/</span>
              <button type="button" @click="openFtpRoot">{{ ftpConnection.name }}</button>
              <template v-for="(seg, index) in ftpSegments" :key="seg.path">
                <span class="crumb-sep">/</span>
                <button type="button" :class="{ active: index === ftpSegments.length - 1 }" @click="openFtpFolder(seg.path)">
                  {{ seg.label }}
                </button>
              </template>
            </template>
            <span v-if="ftpConnection" class="ftp-toolbar-push"></span>
            <el-button v-if="ftpConnection" size="small" @click="goUpFtp" :disabled="!ftpParent">返回上级</el-button>
            <el-button v-if="ftpConnection" size="small" @click="reloadFtp">刷新</el-button>
          </div>

          <div v-if="ftpConnection && ftpSearchActive" class="ftp-search-status">
            <span>
              正在显示“{{ ftpSearchKeyword }}”的递归搜索结果，共 {{ ftpEntries.length }} 项
            </span>
            <el-button size="small" text @click="clearFtpSearch">返回当前目录</el-button>
          </div>

          <div v-if="!ftpConnection" class="grid" v-loading="ftpLoading">
            <FileCard
              v-for="conn in filteredConnections"
              :key="conn.id"
              :title="conn.name"
              :description="conn.description || `${conn.host}:${conn.port}`"
              :is-folder="true"
              badge-override="FTP"
              :extras="[`${conn.host}:${conn.port}`, `账号 ${conn.username || 'anonymous'}`, conn.passiveMode === 0 ? '主动模式' : '被动模式']"
              :footer-label="`起始目录 ${conn.homePath || '/'}`"
              @click="openConnection(conn)"
            />
            <el-empty v-if="!filteredConnections.length && !ftpLoading" :description="connectionKeyword ? '没有匹配的 FTP 连接' : '暂无可用 FTP 连接，请在后台配置'" />
          </div>

          <div v-else class="grid" v-loading="ftpLoading">
            <FileCard
              v-for="entry in ftpFolders"
              :key="entry.path"
              :title="entry.name"
              :description="entry.modified ? `修改时间 ${entry.modified}` : '点击进入目录'"
              :is-folder="true"
              :extras="[entry.modified || '']"
              footer-label="远程目录"
              @click="openFtpFolder(entry.path)"
            />
            <FileCard
              v-for="entry in ftpFiles"
              :key="entry.path"
              :title="entry.name"
              :description="`${entry.modified || ''} · ${formatSize(entry.size)}`"
              :file-name="entry.name"
              :extras="[formatSize(entry.size), entry.modified || '']"
              :footer-label="`${ftpConnection.name} 远程文件`"
              :active="selectedFtpFile?.path === entry.path"
              @click="openFtpFile(entry)"
            />
            <el-empty v-if="!ftpLoading && !ftpFolders.length && !ftpFiles.length" :description="ftpKeyword ? '当前目录没有匹配项' : '当前目录为空'" />
          </div>
        </template>
      </main>
    </section>

    <el-drawer v-model="previewVisible" size="72%" direction="rtl" :with-header="true" :title="selectedFtpFile?.name || '文件预览'" append-to-body :modal-append-to-body="true">
      <FilePreviewPanel
        :file="selectedFtpFile"
        :kind="ftpPreviewKind"
        :preview-url="ftpPreviewUrl"
        :fetch-text="fetchFtpText"
        :fetch-binary="fetchFtpBinary"
        @download="downloadFtpFile"
        @open="openFtpFileInNewTab"
      />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ftpApi, resourceApi } from '../api'
import ResourceCard from '../components/ResourceCard.vue'
import FileCard from '../components/FileCard.vue'
import FilePreviewPanel from '../components/FilePreviewPanel.vue'
import { detectPreviewKind, formatSize } from '../utils/fileIcon'

const route = useRoute()
const mode = ref('local')
const records = ref([])
const localTotal = ref(0)
const query = reactive({
  page: 1,
  size: 9,
  keyword: route.query.keyword || '',
  sort: route.query.sort || 'createTime',
  order: 'desc'
})

async function loadLocal() {
  try {
    const page = await resourceApi.page(query)
    records.value = page.records || []
    localTotal.value = page.total || 0
  } catch (error) {
    records.value = []
    localTotal.value = 0
  }
}

function search() {
  query.page = 1
  loadLocal()
}

function useLocal() {
  mode.value = 'local'
  query.page = 1
  loadLocal()
}

const connections = ref([])
const ftpConnection = ref(null)
const ftpPath = ref('/')
const ftpEntries = ref([])
const ftpBreadcrumbs = ref([])
const ftpParent = ref(null)
const ftpLoading = ref(false)
const ftpSearching = ref(false)
const selectedFtpFile = ref(null)
const previewVisible = ref(false)
const connectionKeyword = ref('')
const ftpKeyword = ref('')
const ftpSearchActive = ref(false)
const ftpSearchKeyword = ref('')
const pendingFtpFocusPath = ref('')
const ftpSort = ref('name')
const ftpOrder = ref('asc')

const filteredConnections = computed(() => {
  const keyword = connectionKeyword.value.trim().toLowerCase()
  if (!keyword) return connections.value
  return connections.value.filter((conn) => [conn.name, conn.host, conn.description].some((field) => (field || '').toLowerCase().includes(keyword)))
})

const filteredFtpEntries = computed(() => {
  const keyword = ftpSearchActive.value ? '' : ftpKeyword.value.trim().toLowerCase()
  const list = keyword
    ? ftpEntries.value.filter((entry) => matchFtpEntry(entry, keyword))
    : ftpEntries.value.slice()
  const dir = ftpOrder.value === 'asc' ? 1 : -1
  list.sort((a, b) => {
    if (a.directory !== b.directory) return a.directory ? -1 : 1
    switch (ftpSort.value) {
      case 'size':
        return (Number(a.size || 0) - Number(b.size || 0)) * dir
      case 'modified':
        return String(a.modified || '').localeCompare(String(b.modified || '')) * dir
      case 'type': {
        const res = extName(a.name).localeCompare(extName(b.name))
        return (res !== 0 ? res : a.name.localeCompare(b.name, 'zh-CN')) * dir
      }
      default:
        return a.name.localeCompare(b.name, 'zh-CN') * dir
    }
  })
  return list
})

function matchFtpEntry(entry, keyword) {
  return [entry.name, entry.path, entry.type].some((field) => (field || '').toLowerCase().includes(keyword))
}

function extName(name = '') {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

const ftpFolders = computed(() => filteredFtpEntries.value.filter((entry) => entry.directory))
const ftpFiles = computed(() => filteredFtpEntries.value.filter((entry) => !entry.directory))
const ftpSegments = computed(() => ftpBreadcrumbs.value.filter((crumb) => crumb.path !== '/'))
const ftpPreviewKind = computed(() => detectPreviewKind(selectedFtpFile.value))
const ftpPreviewUrl = computed(() => selectedFtpFile.value && ftpConnection.value ? ftpApi.previewUrl(ftpConnection.value.id, selectedFtpFile.value.path) : '')

async function useFtp(options = {}) {
  const { autoOpenSingle = true } = options
  mode.value = 'ftp'
  ftpConnection.value = null
  selectedFtpFile.value = null
  previewVisible.value = false
  const availableConnections = connections.value.length ? connections.value : await loadConnections()
  if (autoOpenSingle && availableConnections.length === 1) {
    await openConnection(availableConnections[0])
  }
}

async function loadConnections() {
  try {
    ftpLoading.value = true
    connections.value = (await ftpApi.listConnections()) || []
    return connections.value
  } catch (error) {
    ElMessage.error(error.message || '加载 FTP 连接列表失败')
    return []
  } finally {
    ftpLoading.value = false
  }
}

async function openConnection(conn) {
  ftpConnection.value = conn
  resetFtpSearch()
  const focusPath = pendingFtpFocusPath.value
  pendingFtpFocusPath.value = ''
  if (focusPath) {
    await openFtpPath(focusPath)
  } else {
    await loadFtpDirectory('')
  }
}

async function loadFtpDirectory(path) {
  if (!ftpConnection.value) return
  ftpLoading.value = true
  try {
    const result = await ftpApi.listDirectory(ftpConnection.value.id, path)
    ftpPath.value = result.path || '/'
    ftpEntries.value = result.entries || []
    ftpBreadcrumbs.value = result.breadcrumbs || [{ label: '/', path: '/' }]
    ftpParent.value = result.parent || null
  } catch (error) {
    ElMessage.error(error.message || '读取远程目录失败')
  } finally {
    ftpLoading.value = false
  }
}

async function searchFtp() {
  if (!ftpConnection.value) return
  const keyword = ftpKeyword.value.trim()
  if (!keyword) {
    clearFtpSearch()
    return
  }
  ftpSearching.value = true
  ftpLoading.value = true
  selectedFtpFile.value = null
  previewVisible.value = false
  try {
    const result = await ftpApi.searchDirectory(ftpConnection.value.id, {
      path: ftpPath.value || '/',
      keyword,
      limit: 20,
      maxDepth: 8
    })
    ftpPath.value = result.path || ftpPath.value
    ftpEntries.value = result.entries || []
    ftpBreadcrumbs.value = result.breadcrumbs || ftpBreadcrumbs.value
    ftpParent.value = result.parent || ftpParent.value
    ftpSearchActive.value = true
    ftpSearchKeyword.value = keyword
  } catch (error) {
    ElMessage.error(error.message || '搜索远程资源失败')
  } finally {
    ftpSearching.value = false
    ftpLoading.value = false
  }
}

function clearFtpSearch() {
  if (!ftpSearchActive.value && !ftpKeyword.value) return
  resetFtpSearch()
  if (ftpConnection.value) loadFtpDirectory(ftpPath.value)
}

function resetFtpSearch() {
  ftpKeyword.value = ''
  ftpSearchActive.value = false
  ftpSearchKeyword.value = ''
}

function parentRemotePath(path = '') {
  const normalized = normalizeFtpPath(path)
  if (!normalized || normalized === '/') return ''
  const index = normalized.lastIndexOf('/')
  return index <= 0 ? '' : normalized.slice(0, index)
}

function normalizeFtpPath(path = '') {
  const parts = String(path || '')
    .replace(/\\/g, '/')
    .split('/')
    .map((part) => part.trim())
    .filter(Boolean)
  return parts.length ? `/${parts.join('/')}` : '/'
}

async function openFtpPath(path = '') {
  const targetPath = normalizeFtpPath(path)
  if (targetPath === '/') {
    await loadFtpDirectory('')
    return
  }
  await loadFtpDirectory(parentRemotePath(targetPath))
  const match = ftpEntries.value.find((entry) => normalizeFtpPath(entry.path) === targetPath)
  if (!match) return
  if (match.directory) {
    await loadFtpDirectory(match.path)
  } else {
    selectedFtpFile.value = match
    previewVisible.value = true
  }
}

function openFtpFolder(path) {
  selectedFtpFile.value = null
  previewVisible.value = false
  resetFtpSearch()
  loadFtpDirectory(path || '/')
}

function openFtpRoot() {
  openFtpFolder('')
}

function goUpFtp() {
  if (ftpParent.value) openFtpFolder(ftpParent.value)
}

function openFtpHome() {
  ftpConnection.value = null
  ftpEntries.value = []
  selectedFtpFile.value = null
  previewVisible.value = false
  ftpKeyword.value = ''
  connectionKeyword.value = ''
}

function reloadFtp() {
  if (ftpConnection.value) {
    if (ftpSearchActive.value) {
      searchFtp()
    } else {
      loadFtpDirectory(ftpPath.value)
    }
  }
}

function openFtpFile(entry) {
  selectedFtpFile.value = entry
  previewVisible.value = true
}

function downloadFtpFile() {
  if (!selectedFtpFile.value || !ftpConnection.value) return
  window.open(ftpApi.downloadUrl(ftpConnection.value.id, selectedFtpFile.value.path), '_blank')
}

function openFtpFileInNewTab() {
  if (ftpPreviewUrl.value) window.open(ftpPreviewUrl.value, '_blank')
}

async function fetchFtpText(file) {
  if (!file || !ftpConnection.value) return ''
  return ftpApi.previewText(ftpConnection.value.id, file.path)
}

async function fetchFtpBinary(file) {
  if (!file || !ftpConnection.value) throw new Error('未选择 FTP 文件')
  return ftpApi.previewBinary(ftpConnection.value.id, file.path)
}

watch(() => route.query.source, (source) => {
  if (source === 'ftp' && mode.value !== 'ftp') useFtp()
})

watch(() => route.query, async (next) => {
  if (next.source !== 'ftp') return
  const connectionId = next.connectionId ? Number(next.connectionId) : null
  const remotePath = next.path ? String(next.path) : ''
  if (!connectionId || !remotePath) return
  mode.value = 'ftp'
  const availableConnections = connections.value.length ? connections.value : await loadConnections()
  const target = availableConnections.find((conn) => Number(conn.id) === connectionId)
  if (!target) return
  pendingFtpFocusPath.value = remotePath
  await openConnection(target)
}, { immediate: false })

onMounted(async () => {
  if (route.query.source === 'ftp') {
    const connectionId = route.query.connectionId ? Number(route.query.connectionId) : null
    const remotePath = route.query.path ? String(route.query.path) : ''
    if (connectionId && remotePath) {
      await loadConnections()
      const target = connections.value.find((conn) => Number(conn.id) === connectionId)
      if (target) {
        pendingFtpFocusPath.value = remotePath
        await useFtp({ autoOpenSingle: false })
        await openConnection(target)
      } else {
        await useFtp()
      }
    } else {
      await useFtp()
    }
  } else {
    await loadLocal()
    try {
      await loadConnections()
    } catch (error) {
      connections.value = []
    }
  }
})
</script>

<style scoped>
.list-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
}

.list-toolbar p {
  margin: 6px 0 0;
}

.toolbar-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.toolbar-controls .el-input {
  width: min(320px, 100%);
}

.ftp-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 16px;
  padding: 10px 14px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(247, 250, 252, 0.92);
}

.ftp-breadcrumb button {
  padding: 4px 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #344054;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
}

.ftp-breadcrumb button:hover {
  background: rgba(28, 124, 125, 0.12);
  color: var(--primary-strong);
}

.ftp-breadcrumb button.active {
  background: var(--primary);
  color: #ffffff;
}

.ftp-breadcrumb .crumb-sep {
  color: var(--muted);
  font-weight: 400;
}

.ftp-breadcrumb .ftp-toolbar-push {
  flex: 1;
}

.ftp-search-status {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin: -6px 0 14px;
  padding: 8px 12px;
  border: 1px solid rgba(47, 128, 237, 0.22);
  border-radius: 8px;
  background: rgba(235, 246, 255, 0.82);
  color: #344054;
  font-size: 13px;
}

@media (max-width: 760px) {
  .list-toolbar,
  .toolbar-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-controls .el-input,
  .toolbar-controls .el-select {
    width: 100% !important;
  }
}
</style>
