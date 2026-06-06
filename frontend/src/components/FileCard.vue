<template>
  <article class="resource-card file-card" :class="{ 'is-folder': isFolder, 'is-active': active }" @click="$emit('click')">
    <div class="resource-card-head">
      <span class="file-badge">{{ badge }}</span>
      <span v-if="meta" class="score">{{ meta }}</span>
    </div>

    <div class="resource-card-body">
      <h3>{{ title }}</h3>
      <p>{{ description || (isFolder ? '点击进入目录' : '点击预览文件') }}</p>
    </div>

    <div v-if="extras.length" class="resource-meta">
      <span v-for="(item, index) in extras" :key="index">{{ item }}</span>
    </div>

    <div class="resource-footer file-card-footer">
      <span v-if="footerLabel" class="footer-label">{{ footerLabel }}</span>
      <span v-if="hasActions" class="file-card-actions" @click.stop>
        <slot name="actions"></slot>
      </span>
      <span class="open-hint">{{ isFolder ? '进入' : '预览' }}</span>
    </div>
  </article>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import { fileLabel } from '../utils/fileIcon'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  isFolder: { type: Boolean, default: false },
  fileName: { type: String, default: '' },
  badgeOverride: { type: String, default: '' },
  meta: { type: String, default: '' },
  extras: { type: Array, default: () => [] },
  footerLabel: { type: String, default: '' },
  active: { type: Boolean, default: false }
})

defineEmits(['click'])

const slots = useSlots()
const hasActions = computed(() => Boolean(slots.actions))

const badge = computed(() => {
  if (props.badgeOverride) return props.badgeOverride
  return fileLabel(props.fileName || props.title, props.isFolder)
})
</script>

<style scoped>
.file-card {
  cursor: pointer;
}

.file-card.is-folder .file-badge {
  background: linear-gradient(145deg, #fff4df, #fffaf0);
  color: #8a5d25;
  border-color: rgba(138, 93, 37, 0.24);
}

.file-card.is-active {
  border-color: var(--primary);
  box-shadow: 0 14px 30px rgba(28, 124, 125, 0.2);
}

.file-card-footer {
  flex-wrap: wrap;
  gap: 8px;
  row-gap: 6px;
}

.file-card-footer .footer-label {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-card-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.file-card-actions :deep(.el-button) {
  padding: 0 6px;
  min-height: 26px;
  font-size: 12px;
}

.file-card-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.file-card-actions :deep(.el-checkbox) {
  margin-right: 2px;
}
</style>
