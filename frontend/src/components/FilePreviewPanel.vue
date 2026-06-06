<template>
  <div class="file-preview-panel">
    <div v-if="file" class="selected-file-bar">
      <div>
        <strong>{{ file.name || file.fileName }}</strong>
        <span>{{ file.path || file.relativePath || '' }}</span>
      </div>
      <div class="selected-actions">
        <el-button size="small" @click="$emit('download')">下载</el-button>
        <el-button
          size="small"
          @click="$emit('open')"
          :disabled="kind === 'unsupported'"
        >
          新窗口打开
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="kind === 'docx' || kind === 'pptx'"
      class="preview-tip"
      title="Office 文档由浏览器端 JS 渲染，复杂排版、动画和母版效果可能与 Office/WPS 不完全一致"
      type="info"
      :closable="false"
      show-icon
    />

    <div v-if="kind === 'image'" class="preview-box image-preview">
      <img :src="binaryUrl || previewUrl" :alt="file?.name || ''" />
    </div>

    <iframe
      v-else-if="kind === 'pdf'"
      class="preview-box document-preview"
      :src="binaryUrl || previewUrl"
      title="文件预览"
    />

    <div v-else-if="kind === 'docx'" class="preview-box office-preview">
      <div ref="docxContainer" class="docx-preview-container"></div>
    </div>

    <div v-else-if="kind === 'pptx'" class="preview-box office-preview pptx-preview">
      <div v-if="pptxSlides.length" class="pptx-slide-list">
        <section v-for="slide in pptxSlides" :key="slide.name" class="pptx-slide">
          <div class="pptx-slide-title">{{ slide.title }}</div>
          <div v-if="slide.images.length" class="pptx-images">
            <img v-for="image in slide.images" :key="image.src" :src="image.src" :alt="image.name" />
          </div>
          <p v-for="(text, index) in slide.texts" :key="index">{{ text }}</p>
          <el-empty v-if="!slide.texts.length && !slide.images.length" description="该页没有可提取的文本或图片" />
        </section>
      </div>
      <el-empty v-else description="正在解析 PPTX，或该文件没有可预览内容" />
    </div>

    <div v-else-if="kind === 'video'" class="preview-box media-preview">
      <DanmakuPlayer
        :resource-id="danmakuResourceId"
        :src="binaryUrl || previewUrl"
        :enabled="danmakuEnabled"
        :permission="danmakuPermission"
        :current-user="danmakuUser"
        :is-owner-or-admin="danmakuOwner"
        @open-config="$emit('open-danmaku-config')"
      />
    </div>

    <div v-else-if="kind === 'audio'" class="preview-box audio-preview">
      <AudioPlayer
        :src="binaryUrl || previewUrl"
        :title="file?.title || file?.name || file?.fileName"
        :subtitle="file?.path || file?.relativePath || file?.description"
        :file-name="file?.name || file?.fileName"
        :file-size="file?.size || file?.fileSize"
        @download="$emit('download')"
        @open="$emit('open')"
      />
    </div>

    <pre v-else-if="kind === 'text'" class="preview-box text-preview">{{ textContent }}</pre>

    <div v-else-if="kind === 'legacy-office'" class="preview-box document-preview legacy-frame">
      <div v-if="legacyLoading" class="legacy-loading">正在解析老版 Office 文档，首次打开稍慢……</div>
      <div v-else-if="legacyError" class="legacy-error">
        <p>{{ legacyError }}</p>
        <el-button type="primary" @click="loadLegacy">重试</el-button>
        <el-button @click="$emit('download')">改为下载</el-button>
      </div>
      <iframe
        v-else-if="legacyHtml"
        class="legacy-iframe"
        :srcdoc="legacyHtml"
        title="老版 Office 预览"
        sandbox="allow-same-origin"
      />
    </div>

    <el-empty
      v-else-if="file"
      class="preview-empty"
      description="该文件类型仅支持下载，暂不在线预览"
    >
      <div class="empty-actions">
        <el-button @click="$emit('download')">下载文件</el-button>
      </div>
    </el-empty>

    <el-empty
      v-else
      class="preview-empty"
      description="选择一个文件即可在此预览"
    />
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { renderAsync } from 'docx-preview'
import JSZip from 'jszip'
import { legacyKind, renderDoc, renderPpt, renderSpreadsheet } from '../utils/legacyOffice'
import AudioPlayer from './AudioPlayer.vue'
import DanmakuPlayer from './DanmakuPlayer.vue'

const props = defineProps({
  file: { type: Object, default: null },
  kind: { type: String, default: 'unsupported' },
  previewUrl: { type: String, default: '' },
  // Optional loader for text previews (used when auth headers are required).
  fetchText: { type: Function, default: null },
  // Optional loader returning ArrayBuffer. When provided it overrides the
  // default fetch(previewUrl) for binary previews, which lets callers attach
  // authentication headers (e.g. JWT) that <iframe> cannot carry.
  fetchBinary: { type: Function, default: null },
  // Danmaku-related props. Only meaningful for local video previews that own
  // a real resource id. FTP videos and upload-workspace videos leave these
  // blank so the plain <video> element is rendered.
  danmakuResourceId: { type: [Number, String], default: null },
  danmakuEnabled: { type: Boolean, default: true },
  danmakuPermission: { type: String, default: 'LOGGED' },
  danmakuUser: { type: Object, default: null },
  danmakuOwner: { type: Boolean, default: false }
})

defineEmits(['download', 'open', 'open-danmaku-config'])

const docxContainer = ref(null)
const pptxSlides = ref([])
const textContent = ref('')
const legacyHtml = ref('')
const legacyLoading = ref(false)
const legacyError = ref('')
const binaryUrl = ref('')
const objectUrls = []

function registerObjectUrl(url) {
  if (url) objectUrls.push(url)
  return url
}

async function loadDocx() {
  await nextTick()
  if (!docxContainer.value || !props.previewUrl) return
  docxContainer.value.innerHTML = ''
  try {
    const buffer = await fetchArrayBuffer()
    await renderAsync(buffer, docxContainer.value, null, {
      className: 'docx',
      inWrapper: true,
      breakPages: true
    })
  } catch (error) {
    ElMessage.error(error.message || 'DOCX 预览失败')
  }
}

async function loadPptx() {
  clearObjectUrls()
  pptxSlides.value = []
  try {
    const buffer = await fetchArrayBuffer()
    const zip = await JSZip.loadAsync(buffer)
    const slideNames = Object.keys(zip.files)
      .filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
      .sort((a, b) => slideNumber(a) - slideNumber(b))
    if (!slideNames.length) {
      throw new Error('未找到任何幻灯片内容')
    }
    const mediaMap = await buildMediaMap(zip)
    pptxSlides.value = await Promise.all(slideNames.map(async (name, index) => {
      const slide = zip.file(name)
      if (!slide) return { name, title: `第 ${index + 1} 页`, texts: [], images: [] }
      const xml = await slide.async('string')
      const rels = await readRels(zip, slideNumber(name))
      return {
        name,
        title: `第 ${index + 1} 页`,
        texts: extractText(xml),
        images: extractImages(xml, rels, mediaMap)
      }
    }))
  } catch (error) {
    ElMessage.error(error.message || 'PPTX 预览失败')
  }
}

async function loadText() {
  textContent.value = ''
  try {
    if (props.fetchText) {
      textContent.value = await props.fetchText(props.file)
    } else if (props.previewUrl) {
      const res = await fetch(props.previewUrl)
      textContent.value = await res.text()
    }
  } catch (error) {
    ElMessage.error(error.message || '加载文本预览失败')
  }
}

async function loadBinaryStream() {
  binaryUrl.value = ''
  if (!props.fetchBinary || !props.file) return
  // When the caller already provides a blob: URL (upload workspace, in-memory
  // files), we can hand it straight to <video> / <img> / <iframe>. Re-wrapping
  // through ArrayBuffer would duplicate the whole payload in memory and, for
  // video, loses the original MIME so the seek bar stops working.
  if (props.previewUrl && props.previewUrl.startsWith('blob:')) return
  try {
    const buffer = await props.fetchBinary(props.file)
    const mime = {
      image: detectImageMime(props.file.name || props.file.fileName),
      pdf: 'application/pdf',
      video: 'video/mp4',
      audio: 'audio/mpeg'
    }[props.kind] || 'application/octet-stream'
    const blob = new Blob([buffer], { type: mime })
    binaryUrl.value = registerObjectUrl(URL.createObjectURL(blob))
  } catch (error) {
    ElMessage.error(error.message || '加载预览失败')
  }
}

function detectImageMime(name = '') {
  const lower = name.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.gif')) return 'image/gif'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.bmp')) return 'image/bmp'
  return 'image/jpeg'
}

async function loadLegacy() {
  legacyHtml.value = ''
  legacyError.value = ''
  if (!props.file) return
  const kind = legacyKind(props.file.name || props.file.fileName)
  if (!kind) {
    legacyError.value = '不支持在线预览该文件，请下载'
    return
  }
  legacyLoading.value = true
  try {
    const buffer = await fetchArrayBuffer()
    const displayName = props.file.name || props.file.fileName || ''
    if (kind === 'spreadsheet') {
      legacyHtml.value = renderSpreadsheet(buffer, displayName)
    } else if (kind === 'doc') {
      legacyHtml.value = renderDoc(buffer, displayName)
    } else if (kind === 'ppt') {
      legacyHtml.value = renderPpt(buffer, displayName)
    }
  } catch (error) {
    legacyError.value = error.message || '解析失败'
  } finally {
    legacyLoading.value = false
  }
}

async function fetchArrayBuffer() {
  if (props.fetchBinary) {
    return props.fetchBinary(props.file)
  }
  const response = await fetch(props.previewUrl)
  if (!response.ok) throw new Error(`预览文件加载失败：${response.status}`)
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const data = await response.json()
    throw new Error(data.message || '预览文件加载失败')
  }
  return response.arrayBuffer()
}

function extractText(xml) {
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  return Array.from(doc.getElementsByTagName('a:t'))
    .map((node) => node.textContent.trim())
    .filter(Boolean)
}

async function readRels(zip, no) {
  const file = zip.file(`ppt/slides/_rels/slide${no}.xml.rels`)
  if (!file) return {}
  const xml = await file.async('string')
  const doc = new DOMParser().parseFromString(xml, 'application/xml')
  const rels = {}
  Array.from(doc.getElementsByTagName('Relationship')).forEach((node) => {
    rels[node.getAttribute('Id')] = node.getAttribute('Target')
  })
  return rels
}

async function buildMediaMap(zip) {
  const entries = Object.keys(zip.files).filter((name) => name.startsWith('ppt/media/') && !zip.files[name].dir)
  const map = {}
  await Promise.all(entries.map(async (name) => {
    const entry = zip.file(name)
    if (!entry) return
    const blob = await entry.async('blob')
    const src = URL.createObjectURL(blob)
    objectUrls.push(src)
    map[name] = { name, src }
  }))
  return map
}

function extractImages(xml, rels, mediaMap) {
  const ids = Array.from(xml.matchAll(/r:embed="([^"]+)"/g)).map((m) => m[1])
  return ids
    .map((id) => rels[id])
    .filter(Boolean)
    .map((target) => target.replace(/^\.\.\//, 'ppt/'))
    .map((name) => mediaMap[name])
    .filter(Boolean)
}

function slideNumber(path) {
  return Number(path.match(/slide(\d+)\.xml/)?.[1] || 0)
}

function clearObjectUrls() {
  while (objectUrls.length) URL.revokeObjectURL(objectUrls.pop())
}

watch(() => [props.kind, props.previewUrl, props.file?.path, props.file?.id], async () => {
  textContent.value = ''
  pptxSlides.value = []
  legacyHtml.value = ''
  legacyError.value = ''
  binaryUrl.value = ''
  clearObjectUrls()
  if (!props.file) return
  if (['image', 'pdf', 'video', 'audio'].includes(props.kind)) await loadBinaryStream()
  if (props.kind === 'text') await loadText()
  if (props.kind === 'docx') await loadDocx()
  if (props.kind === 'pptx') await loadPptx()
  if (props.kind === 'legacy-office') await loadLegacy()
}, { immediate: true })

onBeforeUnmount(clearObjectUrls)
</script>

<style scoped>
.file-preview-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 100%;
}

.file-preview-panel :deep(.preview-box) {
  flex: 1;
  min-height: 480px;
}

.file-preview-panel .preview-empty {
  padding: 48px 0;
}

.legacy-frame {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #f5f7fb;
  border-radius: 8px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  overflow: hidden;
}

.legacy-iframe {
  flex: 1;
  width: 100%;
  min-height: 540px;
  border: 0;
  background: #ffffff;
}

.legacy-loading {
  padding: 48px;
  color: var(--muted);
  text-align: center;
}

.legacy-error {
  display: grid;
  gap: 12px;
  place-items: center;
  padding: 48px;
  color: #b23a2a;
}
</style>
