<template>
  <div class="beta-recommend-page">
    <section class="beta-hero">
      <div>
        <span class="eyebrow">Beta Lab</span>
        <h1>资源随机推送</h1>
        <p class="muted">
          通过对象标记法把本地资源和远程资源统一放入 beta 推荐池，按人物喜好向量进行预测推送。
        </p>
      </div>
      <div class="beta-status">
        <strong>{{ records.length }}</strong>
        <span>本轮推荐</span>
        <em>隔离测试版</em>
      </div>
    </section>

    <section class="beta-layout">
      <aside class="beta-control panel">
        <div class="panel-title">人物喜好</div>
        <el-input
          v-model="tagInput"
          placeholder="输入喜好标签，回车加入"
          clearable
          @keyup.enter="addTag"
        />
        <div class="tag-cloud">
          <button
            v-for="tag in seedTags"
            :key="tag"
            type="button"
            :class="{ active: form.preferenceTags.includes(tag) }"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </button>
        </div>
        <div class="selected-tags">
          <el-tag
            v-for="tag in form.preferenceTags"
            :key="tag"
            closable
            @close="removeTag(tag)"
          >
            {{ tag }}
          </el-tag>
        </div>

        <div class="panel-title compact-heading">对象标记</div>
        <el-input
          v-model="objectTagInput"
          placeholder="课程/文件/主题等对象标签"
          clearable
          @keyup.enter="addObjectTag"
        />
        <div class="selected-tags">
          <el-tag
            v-for="tag in form.objectTags"
            :key="tag"
            type="info"
            closable
            @close="removeObjectTag(tag)"
          >
            {{ tag }}
          </el-tag>
        </div>

        <div class="panel-title compact-heading">推送范围</div>
        <el-input v-model="form.keyword" placeholder="关键词过滤" clearable />
        <div class="switch-stack">
          <el-checkbox v-model="form.includeLocal">本地资源</el-checkbox>
          <el-checkbox v-model="form.includeRemote">远程资源</el-checkbox>
        </div>
        <el-slider v-model="form.limit" :min="6" :max="30" :step="3" />
        <div class="control-actions">
          <el-button type="primary" :loading="loading" @click="loadRecommendations">
            随机推送
          </el-button>
          <el-button @click="resetForm">重置</el-button>
        </div>
      </aside>

      <main class="beta-results">
        <section class="beta-vector panel">
          <div>
            <span>用户向量标签</span>
            <strong>{{ vectorTags.join(' / ') || '等待生成' }}</strong>
          </div>
          <div>
            <span>候选池</span>
            <strong>{{ summary.candidateCount || 0 }}</strong>
          </div>
          <div>
            <span>远程异常</span>
            <strong>{{ summary.remoteConnectionErrors || 0 }}</strong>
          </div>
        </section>

        <section class="recommend-grid" v-loading="loading">
          <article
            v-for="item in records"
            :key="item.betaId"
            class="recommend-card"
            :class="{ remote: item.source === 'REMOTE' }"
          >
            <div class="recommend-head">
              <span class="file-badge">{{ badge(item) }}</span>
              <em>{{ item.source === 'REMOTE' ? '远程' : '本地' }}</em>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.description || '暂无描述' }}</p>
            <div class="score-row">
              <strong>{{ item.score?.toFixed ? item.score.toFixed(2) : item.score }}</strong>
              <span>预测分</span>
              <small>距离 {{ item.vectorDistance }}</small>
            </div>
            <div class="object-tags">
              <span v-for="tag in item.objectTags.slice(0, 6)" :key="tag">{{ tag }}</span>
            </div>
            <ul class="reason-list">
              <li v-for="reason in item.matchReasons.slice(0, 3)" :key="reason">{{ reason }}</li>
            </ul>
            <div class="recommend-footer">
              <span>{{ item.categoryName || item.remoteConnectionName || '-' }}</span>
              <div>
                <el-button v-if="item.previewUrl" link @click="openPreview(item)">预览</el-button>
                <el-button v-if="item.downloadUrl" link @click="openDownload(item)">下载</el-button>
                <el-button link @click="openItem(item)">打开</el-button>
              </div>
            </div>
          </article>
          <el-empty v-if="!loading && records.length === 0" description="暂无推荐结果" />
        </section>
      </main>
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { betaRecommendationApi } from '../api'
import { fileLabel } from '../utils/fileIcon'

const router = useRouter()
const loading = ref(false)
const records = ref([])
const summary = ref({})
const vectorTags = ref([])
const tagInput = ref('')
const objectTagInput = ref('')
const seedTags = ['视频', '文档', '算法', '前端', '后端', '数据库', '论文', '实践', '考试', '项目']

const defaultForm = {
  preferenceTags: ['文档', '实践'],
  objectTags: [],
  keyword: '',
  includeLocal: true,
  includeRemote: true,
  limit: 18
}

const form = reactive({ ...defaultForm })

function addUnique(list, value) {
  const tag = value.trim()
  if (!tag) return
  if (!list.includes(tag)) list.push(tag)
}

function addTag() {
  addUnique(form.preferenceTags, tagInput.value)
  tagInput.value = ''
}

function addObjectTag() {
  addUnique(form.objectTags, objectTagInput.value)
  objectTagInput.value = ''
}

function toggleTag(tag) {
  if (form.preferenceTags.includes(tag)) {
    removeTag(tag)
  } else {
    form.preferenceTags.push(tag)
  }
}

function removeTag(tag) {
  form.preferenceTags.splice(0, form.preferenceTags.length, ...form.preferenceTags.filter((item) => item !== tag))
}

function removeObjectTag(tag) {
  form.objectTags.splice(0, form.objectTags.length, ...form.objectTags.filter((item) => item !== tag))
}

function resetForm() {
  form.preferenceTags.splice(0, form.preferenceTags.length, ...defaultForm.preferenceTags)
  form.objectTags.splice(0, form.objectTags.length)
  form.keyword = defaultForm.keyword
  form.includeLocal = defaultForm.includeLocal
  form.includeRemote = defaultForm.includeRemote
  form.limit = defaultForm.limit
  loadRecommendations()
}

async function loadRecommendations() {
  loading.value = true
  try {
    const result = await betaRecommendationApi.recommend({
      preferenceTags: form.preferenceTags,
      objectTags: form.objectTags,
      keyword: form.keyword,
      includeLocal: form.includeLocal,
      includeRemote: form.includeRemote,
      limit: form.limit
    })
    records.value = result.records || []
    summary.value = result.summary || {}
    vectorTags.value = result.userVectorTags || []
  } catch (error) {
    ElMessage.error(error.message || '推荐生成失败')
  } finally {
    loading.value = false
  }
}

function badge(item) {
  return fileLabel(item.fileName || item.title, item.resourceType === 'FOLDER')
}

function openItem(item) {
  if (item.source === 'LOCAL' && item.localResourceId) {
    router.push(`/resources/${item.localResourceId}`)
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

function openDownload(item) {
  window.open(apiUrl(item.downloadUrl), '_blank')
}

function apiUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}${url}`
}

onMounted(loadRecommendations)
</script>

<style scoped>
.beta-recommend-page {
  display: grid;
  gap: 18px;
}

.beta-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 18px;
  padding: 28px;
  border: 1px solid rgba(196, 213, 228, 0.66);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 14px 36px rgba(26, 39, 68, 0.065);
  backdrop-filter: blur(12px);
}

.beta-hero p {
  max-width: 720px;
  margin: 10px 0 0;
  line-height: 1.8;
}

.beta-status {
  display: grid;
  place-items: center;
  min-width: 170px;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.beta-status strong {
  color: var(--primary-strong);
  font-size: 38px;
  line-height: 1;
}

.beta-status span,
.beta-status em {
  color: var(--muted);
  font-style: normal;
  font-size: 12px;
}

.beta-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.beta-control {
  display: grid;
  gap: 12px;
}

.tag-cloud,
.selected-tags,
.switch-stack,
.control-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-cloud button {
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(196, 213, 228, 0.78);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.74);
  color: #344054;
  cursor: pointer;
}

.tag-cloud button:hover,
.tag-cloud button.active {
  border-color: rgba(28, 124, 125, 0.36);
  background: var(--soft);
  color: var(--primary-strong);
}

.switch-stack {
  display: grid;
  gap: 4px;
}

.beta-results {
  display: grid;
  gap: 16px;
}

.beta-vector {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 140px;
  gap: 12px;
  padding: 16px;
}

.beta-vector div {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.beta-vector span {
  color: var(--muted);
  font-size: 12px;
}

.beta-vector strong {
  overflow: hidden;
  color: var(--ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 280px;
}

.recommend-card {
  display: grid;
  gap: 12px;
  align-content: start;
  min-height: 340px;
  padding: 16px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 10px 26px rgba(26, 39, 68, 0.06);
}

.recommend-card:hover {
  transform: translateY(-3px);
  border-color: rgba(28, 124, 125, 0.34);
  box-shadow: 0 16px 36px rgba(26, 39, 68, 0.1);
}

.recommend-card.remote {
  border-color: rgba(47, 128, 237, 0.28);
}

.recommend-head,
.recommend-footer,
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.recommend-head em {
  color: var(--muted);
  font-style: normal;
  font-size: 12px;
  font-weight: 800;
}

.recommend-card h3 {
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-card p {
  display: -webkit-box;
  min-height: 44px;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.score-row {
  justify-content: start;
  padding: 10px;
  border-radius: 8px;
  background: rgba(238, 246, 246, 0.8);
}

.score-row strong {
  color: var(--primary-strong);
  font-size: 24px;
}

.score-row span,
.score-row small {
  color: var(--muted);
  font-size: 12px;
}

.object-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.object-tags span {
  max-width: 120px;
  overflow: hidden;
  padding: 5px 8px;
  border-radius: 8px;
  background: rgba(23, 32, 51, 0.06);
  color: #344054;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.reason-list {
  display: grid;
  gap: 5px;
  min-height: 54px;
  margin: 0;
  padding-left: 18px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.recommend-footer {
  align-self: end;
  padding-top: 8px;
  border-top: 1px solid rgba(196, 213, 228, 0.64);
}

.recommend-footer > span {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-footer div {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
}

@media (max-width: 960px) {
  .beta-hero,
  .beta-layout {
    grid-template-columns: 1fr;
  }

  .beta-hero {
    display: grid;
  }

  .beta-vector {
    grid-template-columns: 1fr;
  }
}
</style>
