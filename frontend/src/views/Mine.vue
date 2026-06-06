<template>
  <div class="mine-page">
    <section class="mine-hero panel">
      <div>
        <span class="eyebrow">My Workspace</span>
        <h1>我的</h1>
        <p>集中管理我的上传、浏览表现、下载趋势和收藏数据。</p>
      </div>
      <div class="mine-actions">
        <router-link to="/upload" class="primary-link">上传资源</router-link>
        <router-link to="/favorites" class="ghost-link">我的收藏</router-link>
      </div>
    </section>

    <section class="mine-stat-grid">
      <article v-for="item in statCards" :key="item.label" class="mine-stat-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
        <em>{{ item.hint }}</em>
      </article>
    </section>

    <section class="mine-charts">
      <article class="panel mine-chart-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Categories</span>
            <h2>上传分类分布</h2>
          </div>
        </div>
        <div v-if="categoryStats.length" class="mine-bar-list">
          <div v-for="item in categoryStats" :key="item.categoryId" class="mine-bar-row">
            <span>{{ item.name || '未分类' }}</span>
            <div class="mine-bar-track">
              <i :style="{ width: `${barPercent(item.count, maxCategoryCount)}%` }"></i>
            </div>
            <em>{{ item.count }}</em>
          </div>
        </div>
        <el-empty v-else description="暂无分类统计" />
      </article>

      <article class="panel mine-chart-panel">
        <div class="section-heading">
          <div>
            <span class="eyebrow">Downloads</span>
            <h2>近 7 天下载趋势</h2>
          </div>
        </div>
        <div class="mine-trend">
          <div v-for="item in downloadTrend" :key="item.date" class="mine-trend-item">
            <div class="mine-trend-column">
              <i :style="{ height: `${barPercent(item.count, maxTrendCount)}%` }"></i>
            </div>
            <span>{{ formatDay(item.date) }}</span>
            <em>{{ item.count }}</em>
          </div>
        </div>
      </article>
    </section>

    <section class="panel mine-upload-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Uploads</span>
          <h2>我的上传</h2>
        </div>
        <router-link to="/resources" class="text-action">查看资源库</router-link>
      </div>

      <div class="grid">
        <ResourceCard v-for="item in uploads" :key="item.id" :resource="item" />
      </div>
      <el-empty v-if="!loading && uploads.length === 0" description="还没有上传资源" />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userApi } from '../api'
import ResourceCard from '../components/ResourceCard.vue'

const loading = ref(false)
const dashboard = ref({
  summary: {},
  uploads: [],
  categoryStats: [],
  downloadTrend: []
})

const summary = computed(() => dashboard.value.summary || {})
const uploads = computed(() => dashboard.value.uploads || [])
const categoryStats = computed(() => dashboard.value.categoryStats || [])
const downloadTrend = computed(() => dashboard.value.downloadTrend || [])
const maxCategoryCount = computed(() => Math.max(1, ...categoryStats.value.map((item) => Number(item.count || 0))))
const maxTrendCount = computed(() => Math.max(1, ...downloadTrend.value.map((item) => Number(item.count || 0))))

const statCards = computed(() => [
  { label: '上传资源', value: summary.value.uploadCount || 0, hint: '顶层资源与文件夹' },
  { label: '浏览量', value: summary.value.totalViews || 0, hint: '我的资源累计浏览' },
  { label: '下载量', value: summary.value.totalDownloads || 0, hint: '我的资源累计下载' },
  { label: '我的收藏', value: summary.value.myFavoriteCount || 0, hint: '已收藏资源' },
  { label: '被收藏', value: summary.value.receivedFavoriteCount || 0, hint: '我的资源被收藏' }
])

function barPercent(value, max) {
  return Math.max(4, Math.round((Number(value || 0) / max) * 100))
}

function formatDay(date = '') {
  const parts = String(date).split('-')
  return parts.length === 3 ? `${parts[1]}/${parts[2]}` : date
}

async function load() {
  loading.value = true
  try {
    dashboard.value = await userApi.dashboard()
  } catch (error) {
    ElMessage.error(error.message || '加载我的工作台失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
