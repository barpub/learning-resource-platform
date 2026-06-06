<template>
  <div class="resource-agent-page">
    <section class="agent-toolbar panel">
      <div>
        <span class="eyebrow">Search Agent</span>
        <h1>资源搜索 Agent</h1>
        <p class="muted">
          搜索本地和远程资源，并对视频、PPT、MP3、Word 等资源生成内容摘要、知识点和分析依据。
        </p>
      </div>
      <div class="agent-search-box">
        <el-input
          v-model="query.keyword"
          placeholder="输入课程名、知识点、文件名、作者或路径"
          clearable
          @keyup.enter="runSearch"
        />
        <el-select v-model="query.source" style="width: 126px" @change="runSearch">
          <el-option label="全部" value="all" />
          <el-option label="本地" value="local" />
          <el-option label="远程" value="remote" />
        </el-select>
        <el-button type="primary" :loading="searching" @click="runSearch">搜索</el-button>
      </div>
    </section>

    <section class="agent-answer panel">
      <div>
        <span class="eyebrow">Agent Answer</span>
        <p>{{ agentAnswer || '输入关键词后，Agent 会先检索资源，再选择资源生成知识点总结。' }}</p>
      </div>
      <div class="agent-step-row">
        <span v-for="step in agentSteps" :key="step">{{ step }}</span>
      </div>
    </section>

    <section class="agent-workbench">
      <div class="agent-results panel" v-loading="searching">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Candidates</span>
            <h2>候选资源 {{ records.length }}</h2>
          </div>
          <el-tag type="info">{{ sourceLabel }}</el-tag>
        </div>

        <div class="suggestion-row" v-if="suggestedQueries.length">
          <button
            v-for="item in suggestedQueries"
            :key="item"
            type="button"
            @click="searchSuggestion(item)"
          >
            {{ item }}
          </button>
        </div>

        <article
          v-for="item in records"
          :key="item.id"
          class="agent-result-item"
          :class="{ active: selected?.id === item.id, remote: item.source === 'REMOTE' }"
          @click="openResult(item)"
        >
          <span class="agent-file-badge">{{ badge(item) }}</span>
          <div>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description || '暂无描述' }}</p>
            <div class="agent-meta-row">
              <span>{{ item.source === 'REMOTE' ? '远程' : '本地' }}</span>
              <span>{{ item.categoryName || item.remoteConnectionName || '-' }}</span>
              <span>{{ formatSize(item.fileSize) }}</span>
              <span>相关度 {{ item.score || 0 }}</span>
            </div>
          </div>
          <el-button size="small" :loading="summarizing && selected?.id === item.id" @click.stop="selectAndSummarize(item)">
            总结
          </el-button>
        </article>

        <el-empty
          v-if="!searching && !records.length"
          description="暂无候选资源"
        />
      </div>

      <aside class="agent-summary panel" v-loading="summarizing">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Content Summary</span>
            <h2>{{ summary?.title || '资源内容分析' }}</h2>
          </div>
          <el-tag :type="confidenceType">{{ confidenceLabel }}</el-tag>
        </div>

        <template v-if="summary">
          <div class="summary-kind-row">
            <span>{{ mediaKindLabel(summary.mediaKind) }}</span>
            <span>{{ contentSourceLabel(summary.contentSource) }}</span>
            <span>{{ summary.fileName || '-' }}</span>
          </div>

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
            <h3>内容结构</h3>
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

          <div class="summary-actions">
            <el-button v-if="selected?.previewUrl" @click="openUrl(selected.previewUrl)">预览</el-button>
            <el-button v-if="selected?.downloadUrl" @click="openUrl(selected.downloadUrl)">下载</el-button>
            <el-button v-if="selected?.localResourceId" type="primary" plain @click="router.push(`/resources/${selected.localResourceId}`)">打开详情</el-button>
            <el-button v-else-if="selected?.remoteConnectionId && selected?.remotePath" type="primary" plain @click="openRemoteInLibrary(selected)">
              打开远程位置
            </el-button>
          </div>
        </template>

        <el-empty
          v-else
          description="选择左侧资源后生成摘要"
        />
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resourceAgentApi } from '../api'
import { fileLabel, formatSize } from '../utils/fileIcon'

const route = useRoute()
const router = useRouter()
const searching = ref(false)
const summarizing = ref(false)
const records = ref([])
const selected = ref(null)
const summary = ref(null)
const agentAnswer = ref('')
const agentSteps = ref([])
const suggestedQueries = ref([])

const query = reactive({
  keyword: route.query.keyword || '',
  source: route.query.source || 'all',
  limit: 30
})

const sourceLabel = computed(() => ({
  all: '全部来源',
  local: '本地资源',
  remote: '远程资源'
})[query.source] || '全部来源')

const confidenceLabel = computed(() => {
  if (!summary.value) return '待分析'
  return `可信度 ${Math.round(Number(summary.value.confidence || 0) * 100)}%`
})

const confidenceType = computed(() => {
  const value = Number(summary.value?.confidence || 0)
  if (value >= 0.75) return 'success'
  if (value >= 0.5) return 'warning'
  return 'info'
})

async function loadResults() {
  searching.value = true
  try {
    const result = await resourceAgentApi.search({
      keyword: query.keyword,
      source: query.source,
      limit: query.limit
    })
    records.value = result.records || []
    agentAnswer.value = result.answer || ''
    agentSteps.value = result.agentSteps || []
    suggestedQueries.value = result.suggestedQueries || []
  } catch (error) {
    ElMessage.error(error.message || 'Agent 搜索失败')
  } finally {
    searching.value = false
  }
}

function runSearch() {
  router.replace({
    path: '/agent/search',
    query: {
      keyword: query.keyword || undefined,
      source: query.source === 'all' ? undefined : query.source
    }
  })
  summary.value = null
  selected.value = null
  loadResults()
}

function searchSuggestion(item) {
  query.keyword = item
  runSearch()
}

async function selectAndSummarize(item) {
  selected.value = item
  summarizing.value = true
  try {
    summary.value = item.source === 'LOCAL' && item.localResourceId
      ? await resourceAgentApi.summarizeLocal(item.localResourceId)
      : await resourceAgentApi.summarizeRemote(remotePayload(item))
  } catch (error) {
    ElMessage.error(error.message || '资源总结失败')
  } finally {
    summarizing.value = false
  }
}

function openResult(item) {
  if (item.source === 'REMOTE' && item.remoteConnectionId && item.remotePath) {
    openRemoteInLibrary(item)
    return
  }
  selectAndSummarize(item)
}

function remotePayload(item) {
  return {
    title: item.title,
    description: item.description,
    fileName: item.fileName,
    fileType: item.fileType,
    resourceType: item.resourceType,
    fileSize: item.fileSize,
    remoteConnectionId: item.remoteConnectionId,
    remoteConnectionName: item.remoteConnectionName,
    remotePath: item.remotePath
  }
}

function badge(item) {
  return fileLabel(item.fileName || item.title, item.resourceType === 'FOLDER')
}

function mediaKindLabel(kind) {
  return ({
    VIDEO: '视频',
    AUDIO: '音频',
    PPT: 'PPT',
    WORD: 'Word',
    TEXT: '文本',
    PDF: 'PDF',
    OTHER: '其他'
  })[kind] || kind || '未知'
}

function contentSourceLabel(source) {
  if (source?.includes('+llm')) {
    const baseSource = source.replace('+llm', '')
    return `${contentSourceLabel(baseSource)} + LLM`
  }
  return ({
    'docx-text': '正文抽取',
    'pptx-slides': '幻灯片抽取',
    'plain-text': '文本抽取',
    'media-metadata': '媒体元数据',
    'remote-metadata': '远程元数据',
    'folder-structure': '目录结构',
    metadata: '元数据'
  })[source] || source || '分析来源'
}

function openUrl(url) {
  if (!url) return
  window.open(url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${url}`, '_blank')
}

function openRemoteInLibrary(item) {
  router.push({
    path: '/resources',
    query: {
      source: 'ftp',
      connectionId: item.remoteConnectionId,
      path: item.remotePath
    }
  })
}

watch(() => route.query, (next) => {
  query.keyword = next.keyword || ''
  query.source = next.source || 'all'
})

onMounted(loadResults)
</script>

<style scoped>
.resource-agent-page {
  display: grid;
  gap: 18px;
}

.agent-toolbar {
  display: grid;
  gap: 18px;
}

.agent-toolbar p {
  max-width: 820px;
  margin: 8px 0 0;
  line-height: 1.7;
}

.agent-search-box {
  display: flex;
  gap: 10px;
  align-items: center;
}

.agent-search-box .el-input {
  flex: 1;
}

.agent-answer {
  display: grid;
  gap: 12px;
}

.agent-answer p {
  margin: 0;
  color: #344054;
  line-height: 1.7;
}

.agent-step-row,
.suggestion-row,
.summary-kind-row,
.agent-meta-row,
.next-action-row,
.summary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-step-row span,
.summary-kind-row span,
.agent-meta-row span,
.next-action-row span,
.suggestion-row button {
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  color: #344054;
  font-size: 12px;
}

.agent-step-row span,
.summary-kind-row span,
.agent-meta-row span,
.next-action-row span {
  padding: 6px 9px;
}

.suggestion-row button {
  min-height: 30px;
  padding: 0 10px;
  cursor: pointer;
}

.suggestion-row button:hover {
  border-color: rgba(28, 124, 125, 0.34);
  background: var(--soft);
  color: var(--primary-strong);
}

.agent-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 420px;
  gap: 18px;
  align-items: start;
}

.agent-results,
.agent-summary {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.section-heading.compact {
  align-items: center;
  margin-bottom: 0;
}

.agent-result-item {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.64);
  cursor: pointer;
}

.agent-result-item:hover,
.agent-result-item.active {
  transform: translateY(-2px);
  border-color: rgba(28, 124, 125, 0.38);
  background: rgba(238, 246, 246, 0.86);
}

.agent-result-item.remote {
  border-color: rgba(47, 128, 237, 0.26);
}

.agent-file-badge {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border-radius: 8px;
  background: #123f4a;
  color: #ffffff;
  font-size: 12px;
  font-weight: 900;
}

.agent-result-item strong,
.agent-result-item p {
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-result-item strong {
  display: block;
  white-space: nowrap;
}

.agent-result-item p {
  display: -webkit-box;
  margin: 5px 0 8px;
  color: var(--muted);
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.agent-meta-row span {
  max-width: 132px;
  overflow: hidden;
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
  border-color: rgba(230, 162, 60, 0.36);
  background: rgba(255, 248, 232, 0.72);
}

.summary-block h3 {
  font-size: 15px;
}

.summary-block p,
.summary-block li {
  color: #344054;
  line-height: 1.65;
}

.summary-block p {
  margin: 0;
}

.summary-block ul,
.summary-block ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
}

.evidence-line {
  padding-left: 10px;
  border-left: 3px solid rgba(28, 124, 125, 0.32);
}

.summary-actions {
  justify-content: flex-end;
}

@media (max-width: 1080px) {
  .agent-workbench {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 680px) {
  .agent-search-box,
  .summary-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .agent-search-box .el-select {
    width: 100% !important;
  }

  .agent-result-item {
    grid-template-columns: 1fr;
  }
}
</style>
