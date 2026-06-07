<template>
  <article class="resource-card">
    <router-link :to="resourceLink" class="resource-card-link">
      <div class="resource-card-head">
        <span class="file-badge">{{ fileLabel }}</span>
        <span class="score">评分 {{ ratingText }}</span>
      </div>

      <div class="resource-card-body">
        <h3>{{ resource.title }}</h3>
        <p>{{ resource.description || '暂无描述' }}</p>
      </div>

      <div class="resource-meta">
        <span>{{ resource.username || '-' }}</span>
        <span v-if="isFolder">文件 {{ resource.fileCount || 0 }}</span>
        <span v-else>浏览 {{ resource.viewCount || 0 }}</span>
        <span>下载 {{ resource.downloadCount || 0 }}</span>
      </div>

      <div v-if="resourceTags.length" class="resource-tags">
        <span v-for="tag in resourceTags" :key="tag">#{{ tag }}</span>
      </div>

      <div v-if="isFolder" class="folder-card-note">
        点击进入目录树查看子文件
      </div>
    </router-link>

    <div class="resource-footer">
      <span>上传者：{{ resource.username || '-' }}</span>
      <span class="resource-card-actions">
        <button type="button" class="share-button" @click="shareResource(resource)">分享</button>
        <router-link :to="resourceLink" class="open-hint">{{ isFolder ? '打开文件夹' : '打开预览' }}</router-link>
      </span>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { shareResource } from '../utils/shareResource'
import { fileLabel as detectFileLabel } from '../utils/fileIcon'

const props = defineProps({
  resource: {
    type: Object,
    required: true
  }
})

const isFolder = computed(() => props.resource.resourceType === 'FOLDER')
const resourceLink = computed(() => `/resources/${props.resource.id}`)

const fileLabel = computed(() => detectFileLabel(props.resource.fileName || props.resource.title, isFolder.value))
const resourceTags = computed(() => String(props.resource.tags || '').split(',').map((tag) => tag.trim()).filter(Boolean).slice(0, 6))

const ratingText = computed(() => {
  const rating = Number(props.resource.rating || 0)
  return rating.toFixed(2)
})
</script>

<style scoped>
.resource-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.resource-tags span {
  max-width: 100%;
  overflow: hidden;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(238, 246, 246, 0.9);
  color: var(--primary-strong);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
