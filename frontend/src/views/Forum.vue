<template>
  <div class="forum-page">
    <section class="forum-head panel">
      <div>
        <span class="eyebrow">Resource Forum</span>
        <h1>资源论坛</h1>
        <p class="muted">发帖讨论学习资源，支持文字、配图、关联资源，并把资源整理进帖子自己的虚拟文件夹。</p>
      </div>
      <div class="forum-search">
        <el-input v-model="query.keyword" clearable placeholder="搜索帖子标题、内容或作者" @keyup.enter="loadPosts" />
        <el-button type="primary" :loading="loading" @click="loadPosts">搜索</el-button>
      </div>
    </section>

    <section class="forum-layout">
      <div class="forum-feed panel" v-loading="loading">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Threads</span>
            <h2>帖子 {{ total }}</h2>
          </div>
          <el-button link @click="resetSearch">刷新</el-button>
        </div>

        <article
          v-for="item in posts"
          :key="item.post.id"
          class="forum-post-item"
          :class="{ active: selected?.post?.id === item.post.id }"
          @click="selectPost(item.post.id)"
        >
          <div class="post-avatar">{{ userInitial(item.post) }}</div>
          <div class="post-main">
            <div class="post-title-row">
              <strong>{{ item.post.title }}</strong>
              <span>{{ formatDate(item.post.createTime) }}</span>
            </div>
            <p>{{ item.post.content }}</p>
            <div v-if="item.imageUrls?.length" class="thumb-row">
              <img v-for="url in item.imageUrls.slice(0, 3)" :key="url" :src="assetUrl(url)" alt="post image" />
            </div>
            <div class="post-meta-row">
              <span>{{ item.post.nickname || item.post.username || '匿名用户' }}</span>
              <span>{{ item.resources?.length || 0 }} 个资源</span>
              <span>{{ item.post.commentCount || 0 }} 条评论</span>
            </div>
          </div>
        </article>

        <el-empty v-if="!loading && !posts.length" description="暂无帖子" />

        <el-pagination
          v-if="total > query.size"
          layout="prev, pager, next"
          :total="total"
          :page-size="query.size"
          v-model:current-page="query.page"
          @current-change="loadPosts"
        />
      </div>

      <main class="forum-detail panel" v-loading="detailLoading">
        <template v-if="selected">
          <div class="detail-head">
            <div>
              <span class="eyebrow">Discussion</span>
              <h2>{{ selected.post.title }}</h2>
              <p>{{ selected.post.nickname || selected.post.username || '匿名用户' }} · {{ formatDate(selected.post.createTime) }}</p>
            </div>
            <el-tag type="success">{{ selected.resources?.length || 0 }} resources</el-tag>
          </div>

          <p class="detail-content">{{ selected.post.content }}</p>

          <div v-if="selected.imageUrls?.length" class="image-grid">
            <img v-for="url in selected.imageUrls" :key="url" :src="assetUrl(url)" alt="post image" />
          </div>

          <section class="linked-resources" v-if="selected.resources?.length">
            <div class="section-heading compact">
              <div>
                <span class="eyebrow">Linked Resources</span>
                <h3>关联资源与虚拟文件夹</h3>
              </div>
            </div>
            <div v-for="group in groupedResources" :key="group.path" class="resource-folder-group">
              <div class="folder-label">{{ group.path || '默认文件夹' }}</div>
              <router-link
                v-for="resource in group.items"
                :key="resource.id"
                class="linked-resource"
                :to="`/resources/${resource.resourceId}`"
              >
                <span class="file-badge">{{ fileLabel(resource.fileName || resource.title, resource.resourceType === 'FOLDER') }}</span>
                <div>
                  <strong>{{ resource.title }}</strong>
                  <small>{{ resource.categoryName || resource.fileName || '-' }} · {{ formatSize(resource.fileSize) }}</small>
                </div>
              </router-link>
            </div>
          </section>

          <section class="comment-area">
            <div class="section-heading compact">
              <div>
                <span class="eyebrow">Comments</span>
                <h3>评论 {{ selected.comments?.length || 0 }}</h3>
              </div>
            </div>

            <div v-if="isLoggedIn" class="comment-box">
              <el-input v-model="commentForm.content" type="textarea" :rows="3" placeholder="写下你的评论" />
              <div class="composer-row">
                <input ref="commentImagesRef" type="file" accept="image/*" multiple @change="onCommentImages" />
                <el-button type="primary" :loading="commenting" @click="submitComment">发表评论</el-button>
              </div>
            </div>
            <el-alert v-else type="info" show-icon :closable="false" title="登录后可以评论" />

            <article v-for="comment in selected.comments" :key="comment.id" class="comment-item">
              <div class="post-avatar small">{{ userInitial(comment) }}</div>
              <div>
                <div class="comment-title"><strong>{{ comment.nickname || comment.username || '匿名用户' }}</strong><span>{{ formatDate(comment.createTime) }}</span></div>
                <p>{{ comment.content }}</p>
                <div v-if="commentImageUrls(comment).length" class="thumb-row">
                  <img v-for="url in commentImageUrls(comment)" :key="url" :src="assetUrl(url)" alt="comment image" />
                </div>
              </div>
            </article>
          </section>
        </template>
        <el-empty v-else description="选择左侧帖子查看详情" />
      </main>

      <aside class="forum-composer panel">
        <div class="section-heading compact">
          <div>
            <span class="eyebrow">Compose</span>
            <h2>发布帖子</h2>
          </div>
        </div>

        <template v-if="isLoggedIn">
          <el-form label-position="top">
            <el-form-item label="标题">
              <el-input v-model="postForm.title" maxlength="80" show-word-limit placeholder="帖子标题" />
            </el-form-item>
            <el-form-item label="内容">
              <el-input v-model="postForm.content" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="分享学习心得、资源说明或问题" />
            </el-form-item>
            <el-form-item label="配图">
              <input ref="postImagesRef" type="file" accept="image/*" multiple @change="onPostImages" />
              <div v-if="postImageNames.length" class="file-name-row">
                <span v-for="name in postImageNames" :key="name">{{ name }}</span>
              </div>
            </el-form-item>
          </el-form>

          <section class="resource-picker">
            <div class="section-heading compact">
              <div>
                <span class="eyebrow">Attach</span>
                <h3>关联资源</h3>
              </div>
            </div>
            <div class="resource-search-row">
              <el-input v-model="resourceKeyword" placeholder="搜索资源" clearable @keyup.enter="searchResources" />
              <el-button @click="searchResources">搜索</el-button>
            </div>
            <div class="resource-pick-list" v-loading="resourceLoading">
              <button v-for="item in resourceOptions" :key="item.id" type="button" @click="addResource(item)">
                <span class="file-badge mini">{{ fileLabel(item.fileName || item.title, item.resourceType === 'FOLDER') }}</span>
                <span>{{ item.title }}</span>
              </button>
            </div>
            <div class="chosen-resources" v-if="chosenResources.length">
              <div v-for="item in chosenResources" :key="item.id" class="chosen-resource">
                <strong>{{ item.title }}</strong>
                <el-input v-model="item.folderPath" size="small" placeholder="虚拟文件夹，如 课程/第一章" />
                <el-button link type="danger" @click="removeResource(item.id)">移除</el-button>
              </div>
            </div>
          </section>

          <el-button class="submit-post" type="primary" :loading="posting" @click="submitPost">发布到论坛</el-button>
        </template>
        <el-alert v-else type="info" show-icon :closable="false" title="登录后可以发帖并关联资源" />
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useStore } from 'vuex'
import { forumApi, resourceApi } from '../api'
import { fileLabel, formatSize } from '../utils/fileIcon'

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const store = useStore()
const isLoggedIn = computed(() => Boolean(store.state.token))

const loading = ref(false)
const detailLoading = ref(false)
const posting = ref(false)
const commenting = ref(false)
const resourceLoading = ref(false)
const posts = ref([])
const selected = ref(null)
const total = ref(0)
const resourceKeyword = ref('')
const resourceOptions = ref([])
const chosenResources = ref([])
const postImages = ref([])
const commentImages = ref([])
const postImagesRef = ref(null)
const commentImagesRef = ref(null)

const query = reactive({ page: 1, size: 10, keyword: '' })
const postForm = reactive({ title: '', content: '' })
const commentForm = reactive({ content: '' })

const postImageNames = computed(() => postImages.value.map((file) => file.name))
const groupedResources = computed(() => {
  const groups = new Map()
  for (const item of selected.value?.resources || []) {
    const path = item.folderPath || ''
    if (!groups.has(path)) groups.set(path, [])
    groups.get(path).push(item)
  }
  return Array.from(groups.entries()).map(([path, items]) => ({ path, items }))
})

async function loadPosts() {
  loading.value = true
  try {
    const page = await forumApi.page({ page: query.page, size: query.size, keyword: query.keyword || undefined })
    posts.value = page.records || []
    total.value = page.total || 0
    if (!selected.value && posts.value.length) {
      await selectPost(posts.value[0].post.id)
    }
  } finally {
    loading.value = false
  }
}

async function selectPost(id) {
  detailLoading.value = true
  try {
    selected.value = await forumApi.get(id)
  } finally {
    detailLoading.value = false
  }
}

function resetSearch() {
  query.keyword = ''
  query.page = 1
  loadPosts()
}

async function searchResources() {
  resourceLoading.value = true
  try {
    const page = await resourceApi.page({ page: 1, size: 8, keyword: resourceKeyword.value || undefined })
    resourceOptions.value = page.records || []
  } finally {
    resourceLoading.value = false
  }
}

function addResource(item) {
  if (chosenResources.value.some((resource) => resource.id === item.id)) return
  chosenResources.value.push({ ...item, folderPath: item.relativePath ? folderOf(item.relativePath) : '' })
}

function removeResource(id) {
  chosenResources.value = chosenResources.value.filter((item) => item.id !== id)
}

function onPostImages(event) {
  postImages.value = Array.from(event.target.files || [])
}

function onCommentImages(event) {
  commentImages.value = Array.from(event.target.files || [])
}

async function submitPost() {
  if (!postForm.title.trim() || !postForm.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  posting.value = true
  try {
    const data = new FormData()
    data.append('title', postForm.title.trim())
    data.append('content', postForm.content.trim())
    for (const file of postImages.value) data.append('images', file)
    for (const resource of chosenResources.value) {
      data.append('resourceIds', resource.id)
      data.append('resourceFolderPaths', resource.folderPath || '')
    }
    const created = await forumApi.createPost(data)
    postForm.title = ''
    postForm.content = ''
    postImages.value = []
    chosenResources.value = []
    if (postImagesRef.value) postImagesRef.value.value = ''
    ElMessage.success('帖子已发布')
    query.page = 1
    await loadPosts()
    if (created?.post?.id) await selectPost(created.post.id)
  } finally {
    posting.value = false
  }
}

async function submitComment() {
  if (!selected.value || !commentForm.content.trim()) {
    ElMessage.warning('请填写评论内容')
    return
  }
  commenting.value = true
  try {
    const data = new FormData()
    data.append('content', commentForm.content.trim())
    for (const file of commentImages.value) data.append('images', file)
    await forumApi.createComment(selected.value.post.id, data)
    commentForm.content = ''
    commentImages.value = []
    if (commentImagesRef.value) commentImagesRef.value.value = ''
    await selectPost(selected.value.post.id)
  } finally {
    commenting.value = false
  }
}

function assetUrl(url) {
  if (!url) return ''
  return url.startsWith('http') ? url : `${API_BASE}${url}`
}

function commentImageUrls(comment) {
  return String(comment.imageUrls || '').split('\n').map((item) => item.trim()).filter(Boolean)
}

function folderOf(path = '') {
  const parts = String(path).split('/').filter(Boolean)
  parts.pop()
  return parts.join('/')
}

function userInitial(user = {}) {
  const name = user.nickname || user.username || '?'
  return String(name).slice(0, 1).toUpperCase()
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(() => {
  loadPosts()
  searchResources()
})
</script>

<style scoped>
.forum-page,
.forum-feed,
.forum-detail,
.forum-composer {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.forum-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
}

.forum-head p {
  margin: 8px 0 0;
  color: var(--muted);
}

.forum-search,
.resource-search-row,
.composer-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.forum-search {
  min-width: min(460px, 100%);
}

.forum-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.35fr) 360px;
  gap: 18px;
  align-items: start;
}

.forum-post-item {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
  cursor: pointer;
}

.forum-post-item:hover,
.forum-post-item.active {
  border-color: rgba(28, 124, 125, 0.38);
  background: rgba(238, 246, 246, 0.88);
  transform: translateY(-1px);
}

.post-avatar {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #123f4a;
  color: #fff;
  font-weight: 900;
}

.post-avatar.small {
  width: 34px;
  height: 34px;
  font-size: 13px;
}

.post-main,
.post-main strong,
.post-main p {
  min-width: 0;
}

.post-title-row,
.post-meta-row,
.comment-title,
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.post-title-row strong,
.detail-head h2 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-title-row span,
.post-meta-row span,
.comment-title span,
.detail-head p,
.linked-resource small {
  color: var(--muted);
  font-size: 12px;
}

.forum-post-item p,
.detail-content,
.comment-item p {
  color: #344054;
  line-height: 1.65;
  white-space: pre-wrap;
}

.forum-post-item p {
  display: -webkit-box;
  margin: 6px 0;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.thumb-row,
.post-meta-row,
.file-name-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.thumb-row img {
  width: 72px;
  height: 54px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(196, 213, 228, 0.72);
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.image-grid img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid rgba(196, 213, 228, 0.72);
}

.linked-resources,
.comment-area,
.resource-picker {
  display: grid;
  gap: 12px;
}

.resource-folder-group {
  display: grid;
  gap: 8px;
}

.folder-label {
  color: var(--primary-strong);
  font-size: 13px;
  font-weight: 800;
}

.linked-resource,
.resource-pick-list button,
.chosen-resource,
.comment-item {
  display: grid;
  gap: 10px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
}

.linked-resource {
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  padding: 10px;
  color: inherit;
  text-decoration: none;
}

.file-badge {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: #123f4a;
  color: #fff;
  font-size: 11px;
  font-weight: 900;
}

.file-badge.mini {
  width: 30px;
  height: 30px;
}

.comment-box {
  display: grid;
  gap: 10px;
}

.comment-item {
  grid-template-columns: 34px minmax(0, 1fr);
  padding: 12px;
}

.resource-pick-list {
  display: grid;
  gap: 8px;
  max-height: 220px;
  overflow: auto;
}

.resource-pick-list button {
  grid-template-columns: 30px minmax(0, 1fr);
  align-items: center;
  padding: 8px;
  color: #344054;
  text-align: left;
  cursor: pointer;
}

.resource-pick-list button span:last-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chosen-resources {
  display: grid;
  gap: 8px;
}

.chosen-resource {
  padding: 10px;
}

.file-name-row span {
  padding: 5px 8px;
  border-radius: 8px;
  background: rgba(238, 246, 246, 0.88);
  color: #344054;
  font-size: 12px;
}

.submit-post {
  width: 100%;
}

@media (max-width: 1200px) {
  .forum-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .forum-head,
  .forum-search,
  .resource-search-row,
  .composer-row,
  .detail-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
