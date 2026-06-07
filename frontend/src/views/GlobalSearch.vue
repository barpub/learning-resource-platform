<template>
  <div class="global-search-page">
    <section class="search-hero panel">
      <div>
        <span class="eyebrow">Global Search</span>
        <h1>资源搜索</h1>
        <p class="muted">统一搜索本地资源、文件夹内文件和远程 FTP 资源，快速定位可预览或可下载的学习资料。</p>
      </div>
      <div class="search-box">
        <el-input
          v-model="query.keyword"
          placeholder="输入资源名、文件名、标签、作者或远程路径"
          clearable
          @keyup.enter="runSearch"
        />
        <el-select v-model="query.source" style="width: 128px" @change="runSearch">
          <el-option label="全部" value="all" />
          <el-option label="本地" value="local" />
          <el-option label="远程" value="remote" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="runSearch">搜索</el-button>
      </div>
    </section>

    <section class="search-summary">
      <div>
        <span>结果</span>
        <strong>{{ records.length }}</strong>
      </div>
      <div>
        <span>范围</span>
        <strong>{{ sourceLabel }}</strong>
      </div>
      <div>
        <span>远程异常</span>
        <strong>{{ summary.remoteConnectionErrors || 0 }}</strong>
      </div>
      <div>
        <span>远程深度</span>
        <strong>{{ summary.remoteDepthLimit ?? '-' }}</strong>
      </div>
    </section>

    <section class="search-results" v-loading="loading">
      <article
        v-for="item in records"
        :key="item.id"
        class="search-result-card"
        :class="{ remote: item.source === 'REMOTE' }"
      >
        <div class="result-icon">
          <span>{{ badge(item) }}</span>
          <em>{{ item.source === 'REMOTE' ? '远程' : '本地' }}</em>
        </div>
        <div class="result-main">
          <h3>{{ item.title }}</h3>
          <p>{{ item.description || '暂无描述' }}</p>
          <div class="result-meta">
            <span>{{ item.ownerName || item.remoteConnectionName || '-' }}</span>
            <span>{{ item.resourceType === 'FOLDER' ? '目录' : formatSize(item.fileSize) }}</span>
            <span>相关度 {{ item.score }}</span>
          </div>
          <div v-if="splitTags(item.tags).length" class="tag-row">
            <span v-for="tag in splitTags(item.tags)" :key="tag">#{{ tag }}</span>
          </div>
          <div class="highlight-row">
            <span v-for="hit in item.highlights" :key="hit">{{ hit }}</span>
          </div>
          <div v-if="item.remotePath" class="path-row">{{ item.remotePath }}</div>
        </div>
        <div class="result-actions">
          <el-button v-if="item.previewUrl" @click="openPreview(item)">预览</el-button>
          <el-button v-if="item.downloadUrl" @click="openDownload(item)">下载</el-button>
          <el-button v-if="item.source === 'LOCAL'" @click="understandResource(item)">理解</el-button>
          <el-button type="primary" plain @click="openItem(item)">打开</el-button>
        </div>
      </article>

      <el-empty
        v-if="!loading && !records.length"
        :description="query.keyword ? '没有找到匹配资源' : '输入关键词后开始搜索，也可以直接搜索查看最新资源'"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { globalSearchApi } from '../api'
import { fileLabel, formatSize } from '../utils/fileIcon'

const route = useRoute()
const router = useRouter()
const store = useStore()
const loading = ref(false)
const records = ref([])
const summary = ref({})

const query = reactive({
  keyword: route.query.keyword || '',
  source: route.query.source || 'all',
  limit: 40
})

const sourceLabel = computed(() => ({
  all: '全部',
  local: '本地',
  remote: '远程'
})[query.source] || '全部')

async function loadResults() {
  loading.value = true
  try {
    const result = await globalSearchApi.search({
      keyword: query.keyword,
      source: query.source,
      limit: query.limit
    })
    records.value = result.records || []
    summary.value = result.summary || {}
  } catch (error) {
    ElMessage.error(error.message || '搜索失败')
  } finally {
    loading.value = false
  }
}

function runSearch() {
  router.replace({
    path: '/search',
    query: {
      keyword: query.keyword || undefined,
      source: query.source === 'all' ? undefined : query.source
    }
  })
  loadResults()
}

function badge(item) {
  return fileLabel(item.fileName || item.title, item.resourceType === 'FOLDER')
}

function splitTags(tags = '') {
  return String(tags || '').split(',').map((tag) => tag.trim()).filter(Boolean).slice(0, 6)
}

function openItem(item) {
  if (item.source === 'LOCAL' && item.localResourceId) {
    router.push(`/resources/${item.localResourceId}`)
    return
  }
  if (item.source === 'REMOTE' && item.remoteConnectionId && item.remotePath) {
    router.push({
      path: '/resources',
      query: {
        source: 'ftp',
        connectionId: item.remoteConnectionId,
        path: item.remotePath
      }
    })
    return
  }
  if (item.openUrl?.startsWith('/')) {
    router.push(item.openUrl)
    return
  }
  window.open(item.openUrl, '_blank')
}

function openPreview(item) {
  window.open(apiUrl(item.previewUrl), '_blank')
}

async function openDownload(item) {
  if (item.source !== 'LOCAL') {
    window.open(apiUrl(item.downloadUrl), '_blank')
    return
  }
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  try {
    const response = await fetch(apiUrl(item.downloadUrl), {
      headers: {
        Authorization: `Bearer ${store.state.token}`
      }
    })
    const contentType = response.headers.get('content-type') || ''
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`)
    }
    if (contentType.includes('application/json')) {
      const data = await response.json()
      throw new Error(data.message || '下载失败')
    }
    const blob = await response.blob()
    saveBlob(blob, parseDownloadFilename(response.headers.get('content-disposition')) || fallbackDownloadName(item))
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

function parseDownloadFilename(disposition = '') {
  const utf8Name = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Name) {
    try {
      return decodeURIComponent(utf8Name[1])
    } catch {
      return utf8Name[1]
    }
  }
  return disposition.match(/filename="?([^"]+)"?/i)?.[1] || ''
}

function fallbackDownloadName(item) {
  const name = item.fileName || item.title || 'download'
  if (item.resourceType === 'FOLDER' && !name.toLowerCase().endsWith('.zip')) {
    return `${safeDownloadName(name)}.zip`
  }
  return safeDownloadName(name)
}

function safeDownloadName(name) {
  return String(name || 'download').replace(/[\\/:*?"<>|]/g, '_')
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function understandResource(item) {
  if (!item.localResourceId) return
  router.push({
    path: '/agent/understand',
    query: {
      resourceId: item.localResourceId,
      scope: item.resourceType === 'FOLDER' ? 'folder' : undefined
    }
  })
}

function apiUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${url}`
}

watch(() => route.query, (next) => {
  query.keyword = next.keyword || ''
  query.source = next.source || 'all'
})

onMounted(loadResults)
</script>

<style scoped>
.global-search-page {
  display: grid;
  gap: 18px;
}

.search-hero {
  display: grid;
  gap: 18px;
}

.search-hero p {
  max-width: 760px;
  margin: 8px 0 0;
  line-height: 1.75;
}

.search-box {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-box .el-input {
  flex: 1;
}

.search-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.search-summary div {
  display: grid;
  gap: 6px;
  min-height: 74px;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 8px 22px rgba(26, 39, 68, 0.045);
}

.search-summary span {
  color: var(--muted);
  font-size: 12px;
}

.search-summary strong {
  color: var(--primary-strong);
  font-size: 22px;
}

.search-results {
  display: grid;
  gap: 12px;
  min-height: 280px;
}

.search-result-card {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 8px 22px rgba(26, 39, 68, 0.045);
}

.search-result-card:hover {
  transform: translateY(-2px);
  border-color: rgba(28, 124, 125, 0.34);
  box-shadow: 0 14px 32px rgba(26, 39, 68, 0.09);
}

.search-result-card.remote {
  border-color: rgba(47, 128, 237, 0.28);
}

.result-icon {
  display: grid;
  place-items: center;
  gap: 6px;
}

.result-icon span {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border: 1px solid rgba(28, 124, 125, 0.22);
  border-radius: 8px;
  background: var(--soft);
  color: var(--primary-strong);
  font-weight: 900;
  font-size: 13px;
}

.result-icon em {
  color: var(--muted);
  font-style: normal;
  font-size: 12px;
}

.result-main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.result-main h3 {
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-main p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.result-meta,
.tag-row,
.highlight-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.result-meta span,
.tag-row span,
.highlight-row span {
  max-width: 180px;
  overflow: hidden;
  padding: 5px 8px;
  border-radius: 8px;
  background: rgba(23, 32, 51, 0.06);
  color: #344054;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.highlight-row span {
  background: rgba(238, 246, 246, 0.96);
  color: var(--primary-strong);
}

.tag-row span {
  background: rgba(238, 246, 246, 0.96);
  color: var(--primary-strong);
  font-weight: 700;
}

.path-row {
  overflow: hidden;
  color: var(--muted);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .search-summary,
  .search-result-card {
    grid-template-columns: 1fr;
  }

  .search-box,
  .result-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .search-box .el-select {
    width: 100% !important;
  }
}
</style>
