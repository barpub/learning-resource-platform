<template>
  <div class="folder-agent-page">
    <section class="folder-agent-header panel">
      <div>
        <span class="eyebrow">Analysis Agent</span>
        <h1>分析 Agent</h1>
        <p class="muted">当前对象：{{ resourceId ? folderPathLabel : '未选择虚拟文件夹' }}</p>
      </div>
      <div class="folder-agent-actions">
        <el-button @click="router.push('/search')">资源搜索</el-button>
        <el-button v-if="resourceId" @click="backToFolder">返回文件夹</el-button>
        <el-button type="primary" :loading="loading" :disabled="!resourceId" @click="loadSummary">重新分析</el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      class="agent-alert"
      :title="errorMessage"
      type="warning"
      show-icon
      :closable="false"
    />

    <section v-if="!resourceId" class="panel empty-agent-panel">
      <el-empty description="请先进入用户上传的虚拟文件夹，再点击“分析当前文件夹”。">
        <el-button type="primary" @click="router.push('/resources')">进入资源库</el-button>
      </el-empty>
    </section>

    <section v-else class="folder-agent-workbench">
      <aside class="folder-agent-metrics panel" v-loading="loading">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Scope</span>
            <h2>分析范围</h2>
          </div>
          <el-tag type="info">{{ contentSourceLabel }}</el-tag>
        </div>

        <div class="metric-list">
          <div>
            <span>虚拟路径</span>
            <strong>{{ folderPathLabel }}</strong>
          </div>
          <div>
            <span>文件数</span>
            <strong>{{ metadata.fileCount ?? '-' }}</strong>
          </div>
          <div>
            <span>直属文件</span>
            <strong>{{ metadata.directFileCount ?? '-' }}</strong>
          </div>
          <div>
            <span>子目录</span>
            <strong>{{ metadata.childFolderCount ?? '-' }}</strong>
          </div>
          <div>
            <span>已读正文</span>
            <strong>{{ metadata.extractedFileCount ?? '-' }}</strong>
          </div>
          <div>
            <span>总大小</span>
            <strong>{{ formatSize(Number(metadata.totalSize || 0)) }}</strong>
          </div>
        </div>
      </aside>

      <main class="folder-agent-summary panel" v-loading="loading">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Folder Summary</span>
            <h2>{{ summary?.title || '等待分析结果' }}</h2>
          </div>
          <el-tag :type="confidenceType">{{ confidenceLabel }}</el-tag>
        </div>

        <template v-if="summary">
          <section class="summary-block">
            <h3>摘要</h3>
            <p>{{ summary.summary }}</p>
          </section>

          <section class="summary-block">
            <h3>知识点</h3>
            <ul>
              <li v-for="point in summary.knowledgePoints" :key="point">{{ point }}</li>
            </ul>
          </section>

          <section class="summary-block">
            <h3>文件结构</h3>
            <ol>
              <li v-for="item in summary.outline" :key="item">{{ item }}</li>
            </ol>
          </section>

          <section class="summary-block" v-if="summary.evidenceSnippets?.length">
            <h3>分析依据</h3>
            <p v-for="item in summary.evidenceSnippets" :key="item" class="evidence-line">{{ item }}</p>
          </section>

          <section class="summary-block warning" v-if="summary.limitations?.length">
            <h3>当前限制</h3>
            <p v-for="item in summary.limitations" :key="item">{{ item }}</p>
          </section>

          <section class="summary-block">
            <h3>下一步</h3>
            <div class="next-action-row">
              <span v-for="item in summary.nextActions" :key="item">{{ item }}</span>
            </div>
          </section>
        </template>

        <el-empty v-else-if="!loading" description="暂无分析结果" />
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resourceAgentApi } from '../api'
import { formatSize } from '../utils/fileIcon'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const summary = ref(null)
const errorMessage = ref('')

const resourceId = computed(() => route.query.resourceId || route.query.folderId || '')
const folderPath = computed(() => normalizePath(route.query.path || ''))
const folderPathLabel = computed(() => folderPath.value || '/')
const metadata = computed(() => summary.value?.metadata || {})
const contentSourceLabel = computed(() => ({
  'folder-files+content': '正文 + 目录',
  'folder-files': '目录结构'
})[summary.value?.contentSource] || '待分析')

const confidenceLabel = computed(() => {
  if (!summary.value) return '待分析'
  return `可信度 ${Math.round(Number(summary.value.confidence || 0) * 100)}%`
})

const confidenceType = computed(() => {
  const value = Number(summary.value?.confidence || 0)
  if (value >= 0.7) return 'success'
  if (value >= 0.5) return 'warning'
  return 'info'
})

function normalizePath(path = '') {
  return String(path).replace(/\\/g, '/').split('/').map((part) => part.trim()).filter(Boolean).join('/')
}

async function loadSummary() {
  if (!resourceId.value) return
  loading.value = true
  errorMessage.value = ''
  try {
    summary.value = await resourceAgentApi.summarizeFolder(resourceId.value, {
      path: folderPath.value || undefined
    })
  } catch (error) {
    summary.value = null
    errorMessage.value = error.message || '文件夹分析失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function backToFolder() {
  if (!resourceId.value) {
    router.push('/resources')
    return
  }
  router.push({
    path: `/resources/${resourceId.value}`,
    query: folderPath.value ? { path: folderPath.value } : {}
  })
}

watch(() => [route.query.resourceId, route.query.folderId, route.query.path], () => {
  if (resourceId.value) {
    loadSummary()
  } else {
    summary.value = null
    errorMessage.value = ''
  }
}, { immediate: true })
</script>

<style scoped>
.folder-agent-page {
  display: grid;
  gap: 18px;
}

.folder-agent-header {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
}

.folder-agent-header p {
  max-width: 760px;
  margin: 8px 0 0;
}

.folder-agent-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.agent-alert {
  border-radius: 8px;
}

.empty-agent-panel {
  min-height: 320px;
  display: grid;
  place-items: center;
}

.folder-agent-workbench {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.folder-agent-metrics,
.folder-agent-summary {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.metric-list {
  display: grid;
  gap: 10px;
}

.metric-list div {
  display: grid;
  gap: 5px;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.66);
}

.metric-list span {
  color: var(--muted);
  font-size: 12px;
}

.metric-list strong {
  overflow: hidden;
  color: var(--primary-strong);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-block {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.68);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.58);
}

.summary-block.warning {
  border-color: rgba(213, 147, 47, 0.28);
  background: rgba(255, 248, 232, 0.72);
}

.summary-block h3 {
  margin: 0;
  color: var(--primary-strong);
  font-size: 15px;
}

.summary-block p,
.summary-block ul,
.summary-block ol {
  margin: 0;
  color: #344054;
  line-height: 1.7;
}

.evidence-line {
  color: var(--muted) !important;
}

.next-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.next-action-row span {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(238, 246, 246, 0.96);
  color: var(--primary-strong);
  font-size: 12px;
}

@media (max-width: 960px) {
  .folder-agent-header,
  .folder-agent-workbench {
    grid-template-columns: 1fr;
  }

  .folder-agent-header {
    display: grid;
  }

  .folder-agent-actions {
    justify-content: stretch;
  }

  .folder-agent-actions .el-button {
    flex: 1;
  }
}
</style>
