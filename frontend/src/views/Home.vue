<template>
  <div class="home-page">
    <section class="home-hero distribution-hero">
      <div class="hero-intro">
        <div class="hero-copy">
          <span class="eyebrow">Resource Distribution</span>
          <h1>学习资源分发首页</h1>
          <p>把头部作品、热门排行、分类入口和全局搜索放在首屏，用户进来就能判断资源质量和进入目标分区。</p>
        </div>

        <div class="hero-search-panel">
          <el-input
            v-model="keyword"
            class="hero-search"
            placeholder="搜索课程、文件名、关键词"
            clearable
            @keyup.enter="goSearch"
          />
          <el-button type="primary" @click="goSearch">搜索</el-button>
          <el-button @click="goUpload" plain>上传</el-button>
        </div>

        <div class="hero-quick-actions">
          <button class="quick-action" @click="openLibrary('rating')">
            <span>高评分</span>
            <em>优先看质量</em>
          </button>
          <button class="quick-action" @click="openLibrary('downloadCount')">
            <span>下载最多</span>
            <em>按热度排序</em>
          </button>
          <button class="quick-action" @click="openLibrary('createTime')">
            <span>最新上传</span>
            <em>查看新资料</em>
          </button>
        </div>
      </div>

      <main class="featured-carousel-panel">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Featured</span>
            <h2>头部作品轮播</h2>
          </div>
          <router-link to="/resources?sort=rating" class="text-action">更多作品</router-link>
        </div>

        <el-carousel
          class="featured-carousel"
          height="330px"
          indicator-position="outside"
          arrow="always"
        >
          <el-carousel-item v-for="(item, index) in featuredWorks" :key="item.id || index">
            <router-link :to="item.id ? `/resources/${item.id}` : '/resources'" class="featured-slide">
              <div class="featured-art" :style="{ '--feature-accent': categoryAccent(index) }">
                <span>{{ fileLabel(item.fileName || item.title) }}</span>
                <strong>{{ categoryInitial(item.title || '资源') }}</strong>
              </div>
              <div class="featured-copy">
                <span>{{ item.categoryName || '精选资源' }}</span>
                <h2>{{ item.title || '等待上传头部作品' }}</h2>
                <p>{{ item.description || '高评分、高热度或最新上传的资源会在这里轮流展示。' }}</p>
                <div class="featured-meta">
                  <em>评分 {{ ratingText(item) }}</em>
                  <em>浏览 {{ item.viewCount || 0 }}</em>
                  <em>下载 {{ item.downloadCount || 0 }}</em>
                </div>
              </div>
            </router-link>
          </el-carousel-item>
        </el-carousel>
      </main>

      <aside class="hero-rank-panel">
        <div class="rank-head">
          <div>
            <span class="eyebrow">Top</span>
            <h2>小排行榜</h2>
          </div>
          <router-link to="/resources?sort=downloadCount" class="text-action">总榜</router-link>
        </div>
        <router-link
          v-for="(item, index) in hot.slice(0, 5)"
          :key="item.id"
          :to="`/resources/${item.id}`"
          class="mini-rank-item"
        >
          <strong>{{ index + 1 }}</strong>
          <span>
            <em>{{ item.categoryName || fileLabel(item.fileName) }}</em>
            {{ item.title }}
          </span>
          <small>{{ item.downloadCount || 0 }}</small>
        </router-link>
        <el-empty v-if="hot.length === 0" description="暂无排行" />
      </aside>
    </section>

    <section class="home-categories">
      <div class="section-heading">
        <div>
          <span class="eyebrow">Categories</span>
          <h2>分类导航</h2>
        </div>
        <router-link to="/resources" class="text-action">进入资源库</router-link>
      </div>

      <div class="category-ribbon category-showcase">
        <button
          v-for="(item, index) in categories"
          :key="item.id"
          class="category-tab"
          :style="{ '--category-accent': categoryAccent(index) }"
          @click="openCategory(item.id)"
        >
          <span class="category-mark">{{ categoryInitial(item.name) }}</span>
          <span class="category-copy">
            <strong>{{ item.name }}</strong>
          </span>
          <span class="category-count">{{ item.resourceCount || 0 }}</span>
        </button>
        <button
          class="category-tab"
          :style="{ '--category-accent': '#5b4ac8' }"
          @click="openRemote"
        >
          <span class="category-mark">远</span>
          <span class="category-copy">
            <strong>远程资源库</strong>
          </span>
          <span class="category-count">FTP</span>
        </button>
      </div>
    </section>

    <section class="home-workbench">
      <main class="main-feed">
        <section class="feed-section">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Recommended</span>
              <h2>推荐资源</h2>
            </div>
            <router-link to="/resources" class="text-action">查看全部</router-link>
          </div>

          <div class="grid feature-grid">
            <ResourceCard v-for="item in recommended" :key="item.id" :resource="item" />
          </div>
          <el-empty v-if="recommended.length === 0" description="暂无推荐资源" />
        </section>

        <section class="feed-section latest-panel">
          <div class="section-heading">
            <div>
              <span class="eyebrow">Latest</span>
              <h2>最新上传</h2>
            </div>
          </div>

          <div class="latest-list">
            <router-link v-for="item in latest" :key="item.id" :to="`/resources/${item.id}`" class="latest-item">
              <span class="latest-type">{{ fileLabel(item.fileName) }}</span>
              <span>
                <strong>{{ item.title }}</strong>
                <em>{{ item.categoryName || '未分类' }}</em>
              </span>
              <small>浏览 {{ item.viewCount || 0 }}</small>
            </router-link>
          </div>
        </section>
      </main>

      <aside class="home-side">
        <section class="side-section hot-panel">
          <div>
            <span class="eyebrow">Hot</span>
            <h2>热门推荐</h2>
          </div>

          <router-link
            v-for="(item, index) in hot"
            :key="item.id"
            :to="`/resources/${item.id}`"
            class="hot-item"
          >
            <strong>{{ index + 1 }}</strong>
            <span>
              <em>{{ item.categoryName || '资源' }}</em>
              {{ item.title }}
            </span>
            <small>{{ item.downloadCount || 0 }} 下载</small>
          </router-link>
        </section>

        <section class="side-section capability-panel">
          <span class="eyebrow">Preview</span>
          <h2>在线浏览能力</h2>
          <div class="capability-list">
            <span>DOCX 文档</span>
            <span>PPTX 课件</span>
            <span>PDF 阅读</span>
            <span>图片 / 音视频</span>
          </div>
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { categoryApi, resourceApi } from '../api'
import ResourceCard from '../components/ResourceCard.vue'

const router = useRouter()
const store = useStore()
const keyword = ref('')
const categories = ref([])
const recommended = ref([])
const latest = ref([])
const hot = ref([])
const totalResources = ref(0)
const accents = ['#1c7c7d', '#2f80ed', '#6a778a', '#91724c', '#4d7c5b', '#7466b8']

const isLoggedIn = computed(() => Boolean(store.state.token))
const featuredWorks = computed(() => {
  const pool = [...recommended.value, ...hot.value, ...latest.value]
  const seen = new Set()
  const unique = pool.filter((item) => {
    if (!item?.id || seen.has(item.id)) return false
    seen.add(item.id)
    return true
  })
  if (unique.length) return unique.slice(0, 5)
  return [{
    id: null,
    title: '学习资源发现中心',
    description: '上传头部作品后，系统会按评分、热度和最新上传自动填充轮播。',
    categoryName: '平台入口',
    fileName: 'featured.pdf',
    rating: 0,
    viewCount: 0,
    downloadCount: 0
  }]
})

function goSearch() {
  router.push({ path: '/resources', query: { keyword: keyword.value } })
}

function goUpload() {
  router.push(isLoggedIn.value ? '/upload' : '/login?redirect=%2Fupload')
}

function openLibrary(sort) {
  router.push({ path: '/resources', query: { sort } })
}

function openCategory(categoryId) {
  router.push({ path: '/resources', query: { categoryId } })
}

function openRemote() {
  router.push({ path: '/resources', query: { source: 'ftp' } })
}

function categoryAccent(index) {
  return accents[index % accents.length]
}

function categoryInitial(name = '') {
  return name.trim().slice(0, 1) || '资'
}

function fileLabel(fileName = '') {
  const name = fileName.toLowerCase()
  if (name.endsWith('.pdf')) return 'PDF'
  if (name.endsWith('.docx')) return 'DOC'
  if (name.endsWith('.pptx')) return 'PPT'
  if (/\.(png|jpe?g|gif|webp)$/.test(name)) return 'IMG'
  if (/\.(mp4|mov|avi)$/.test(name)) return 'VID'
  if (/\.(mp3|wav)$/.test(name)) return 'AUD'
  if (name.endsWith('.txt')) return 'TXT'
  return 'FILE'
}

function ratingText(item = {}) {
  return Number(item.rating || 0).toFixed(2)
}

onMounted(async () => {
  try {
    categories.value = await categoryApi.list()
    const [recommendPage, latestPage, hotPage] = await Promise.all([
      resourceApi.page({ page: 1, size: 6, sort: 'rating', order: 'desc' }),
      resourceApi.page({ page: 1, size: 6, sort: 'createTime', order: 'desc' }),
      resourceApi.page({ page: 1, size: 5, sort: 'downloadCount', order: 'desc' })
    ])
    recommended.value = recommendPage.records || []
    latest.value = latestPage.records || []
    hot.value = hotPage.records || []
    totalResources.value = recommendPage.total || 0
  } catch (error) {
    categories.value = []
    recommended.value = []
    latest.value = []
    hot.value = []
  }
})
</script>
