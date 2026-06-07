<template>
  <div class="danmaku-player">
    <div class="video-stage" ref="stageRef" :class="{ fullscreen: isFullscreen }">
      <video
        ref="videoRef"
        :src="src"
        class="danmaku-video"
        crossorigin="anonymous"
        playsinline
        @loadedmetadata="onMeta"
        @timeupdate="onTimeUpdate"
        @seeking="onSeek"
        @seeked="onSeek"
        @play="onPlay"
        @pause="isPlaying = false"
        @volumechange="onVolumeChange"
        @click="togglePlay"
        @dblclick.prevent="toggleFullscreen"
      />
      <div v-if="hasDanmakuResource" class="danmaku-track" :class="{ hidden: !showDanmaku }" ref="trackRef">
        <div
          v-for="entry in scrollActive"
          :key="`s-${entry.key}`"
          class="danmaku scroll"
          :style="entry.layoutStyle"
        >
          <span class="danmaku-inner" :style="entry.colorStyle" :title="entry.text">{{ entry.text }}</span>
        </div>
        <div
          v-for="entry in topActive"
          :key="`t-${entry.key}`"
          class="danmaku pinned top"
          :style="entry.layoutStyle"
        >
          <span class="danmaku-inner" :style="entry.colorStyle" :title="entry.text">{{ entry.text }}</span>
        </div>
        <div
          v-for="entry in bottomActive"
          :key="`b-${entry.key}`"
          class="danmaku pinned bottom"
          :style="entry.layoutStyle"
        >
          <span class="danmaku-inner" :style="entry.colorStyle" :title="entry.text">{{ entry.text }}</span>
        </div>
      </div>

      <!-- Custom control bar -->
      <div class="control-bar" :class="{ visible: controlsVisible }" @click.stop>
        <!-- Progress -->
        <div class="progress-row" @click="seekTo($event)" ref="progressRef">
          <div class="progress-track">
            <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }"></div>
            <div class="progress-played" :style="{ width: playedPercent + '%' }"></div>
            <div class="progress-handle" :style="{ left: playedPercent + '%' }"></div>
          </div>
        </div>

        <!-- Controls -->
        <div class="control-main">
          <button type="button" class="ctrl-btn" @click="togglePlay" :title="isPlaying ? '暂停' : '播放'">
            <span v-if="isPlaying">❚❚</span>
            <span v-else>▶</span>
          </button>

          <div class="volume-wrap">
            <button type="button" class="ctrl-btn" @click="toggleMute" :title="isMuted ? '取消静音' : '静音'">
              <span v-if="isMuted || volume === 0">🔇</span>
              <span v-else-if="volume < 0.5">🔉</span>
              <span v-else>🔊</span>
            </button>
            <input type="range" min="0" max="1" step="0.01" v-model.number="volume" class="volume-slider" />
          </div>

          <div class="time-display">
            {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
          </div>

          <!-- Danmaku composer inline -->
          <div class="composer-inline" v-if="hasDanmakuResource && canSend && enabled" ref="composerRef">
            <span class="color-indicator" :style="indicatorStyle" title="当前弹幕颜色" @click.stop="paletteOpen = !paletteOpen"></span>
            <select v-model="composer.type" class="type-select" title="弹幕类型">
              <option value="scroll">滚动</option>
              <option value="top">顶部</option>
              <option value="bottom">底部</option>
            </select>
            <input
              type="text"
              class="danmaku-input"
              v-model="composer.content"
              :placeholder="enabled ? '发送弹幕...' : '弹幕已关闭'"
              maxlength="50"
              @keyup.enter="submit"
            />
            <button type="button" class="send-btn" @click="submit" :disabled="sending || !composer.content.trim()">
              发送
            </button>
          </div>

          <div class="control-right">
            <select
              v-if="hasDanmakuResource && hasShareToken"
              v-model="visibilityMode"
              class="view-select"
              title="弹幕可见范围"
            >
              <option value="SHARE_NOTE">分享者笔记</option>
              <option value="ALL">全部弹幕</option>
            </select>
            <div class="quality-controls" :title="processingLabel">
              <button
                type="button"
                class="ctrl-btn quality-btn"
                :class="{ active: vocalEnhance }"
                title="人声增强：弱化背景杂音，强化人声，并压峰、波峰平缓、抬细节、减少忽大忽小"
                @click="vocalEnhance = !vocalEnhance"
              >
                Vox
              </button>
              <button
                type="button"
                class="ctrl-btn quality-btn"
                :class="{ active: hiResEnhance }"
                title="Hi-Res listen enhance"
                @click="hiResEnhance = !hiResEnhance"
              >
                Hi
              </button>
              <button
                type="button"
                class="ctrl-btn quality-btn"
                :class="{ active: limiterEnabled }"
                title="波峰平缓：输出限幅和压峰，减少忽大忽小"
                @click="limiterEnabled = !limiterEnabled"
              >
                Lim
              </button>
            </div>
            <button
              v-if="hasDanmakuResource"
              type="button"
              class="ctrl-btn"
              :class="{ active: showDanmaku }"
              @click="showDanmaku = !showDanmaku"
              :title="showDanmaku ? '隐藏弹幕' : '显示弹幕'"
            >
              弹
            </button>
            <button
              v-if="hasDanmakuResource && isOwnerOrAdmin"
              type="button"
              class="ctrl-btn"
              @click="$emit('open-config')"
              title="弹幕设置"
            >
              ⚙
            </button>
            <button type="button" class="ctrl-btn" @click="toggleFullscreen" :title="isFullscreen ? '退出全屏' : '全屏'">
              <span v-if="isFullscreen">⛶</span>
              <span v-else>⛶</span>
            </button>
          </div>
        </div>

        <!-- Color palette (slides up from the bar when the input is focused) -->
        <transition name="palette-slide">
          <div v-show="paletteOpen && hasDanmakuResource && canSend && enabled" class="palette-panel" @click.stop>
            <div class="palette-tabs">
              <button
                type="button"
                v-for="tab in paletteTabs"
                :key="tab.value"
                class="palette-tab"
                :class="{ active: paletteMode === tab.value }"
                @click="paletteMode = tab.value"
              >{{ tab.label }}</button>
            </div>

            <div class="palette-swatches" v-if="paletteMode === 'solid'">
              <button
                type="button"
                v-for="color in solidPresets"
                :key="color"
                class="swatch solid"
                :class="{ active: composer.color === color }"
                :style="{ background: color, borderColor: color === '#ffffff' ? '#d0dde8' : color }"
                :title="color"
                @click="pickColor(color)"
              ></button>
              <label class="swatch custom" :class="{ active: composer.color.startsWith('#') && !solidPresets.includes(composer.color) }">
                <input type="color" :value="customPickerValue" @input="onCustomColor" />
                <span>自定义</span>
              </label>
            </div>

            <div class="palette-swatches" v-else-if="paletteMode === 'gradient'">
              <button
                type="button"
                v-for="preset in gradientPresets"
                :key="preset.value"
                class="swatch gradient"
                :class="{ active: composer.color === preset.value }"
                :style="swatchStyle(preset.value)"
                :title="preset.label"
                @click="pickColor(preset.value)"
              >{{ preset.label }}</button>
              <button
                type="button"
                class="swatch gradient custom-gradient"
                :class="{ active: isCustomGradient('grad') }"
                :style="customGradientSwatchStyle"
                title="自定义渐变"
                @click="openCustomGradient('gradient')"
              >自定义</button>
            </div>

            <div class="palette-swatches" v-else>
              <button
                type="button"
                v-for="preset in animatedPresets"
                :key="preset.value"
                class="swatch gradient animated"
                :class="{ active: composer.color === preset.value }"
                :style="swatchStyle(preset.value)"
                :title="preset.label"
                @click="pickColor(preset.value)"
              >{{ preset.label }}</button>
              <button
                type="button"
                class="swatch gradient animated custom-gradient"
                :class="{ active: isCustomGradient('anim') }"
                :style="customAnimSwatchStyle"
                title="自定义动态"
                @click="openCustomGradient('animated')"
              >自定义</button>
            </div>
          </div>
        </transition>
      </div>

      <!-- Center play button overlay for a clear visual state -->
      <div v-if="!isPlaying" class="center-play" @click="togglePlay">
        <span>▶</span>
      </div>
    </div>

    <div class="danmaku-info" v-if="hasDanmakuResource && (!canSend || !enabled)">
      <el-alert
        v-if="!enabled"
        type="info"
        :closable="false"
        title="该资源已关闭弹幕"
        show-icon
      />
      <el-alert
        v-else-if="!canSend"
        type="info"
        :closable="false"
        :title="permissionHint"
        show-icon
      />
    </div>

    <div class="danmaku-meta">
      <span v-if="hasDanmakuResource">共 {{ danmakus.length }} 条弹幕</span>
      <span v-if="hasDanmakuResource" class="danmaku-permission">权限：{{ permissionLabel }}</span>
      <span v-if="hasDanmakuResource && hasShareToken" class="danmaku-permission">视图：{{ visibilityLabel }}</span>
      <span class="quality-meter">Audio {{ processingLabel }} · Peak {{ peakLevel }}% · RMS {{ rmsLevel }}%</span>
    </div>

    <el-dialog
      v-model="customGradientDialog"
      :title="customGradientMode === 'animated' ? '自定义动态渐变' : '自定义静态渐变'"
      width="480px"
      append-to-body
      :modal-append-to-body="true"
    >
      <div class="gradient-builder">
        <div class="gradient-stops">
          <div
            v-for="(stop, index) in customStops"
            :key="index"
            class="gradient-stop"
          >
            <span class="stop-index">{{ index + 1 }}</span>
            <input type="color" :value="stop" @input="(e) => updateCustomStop(index, e.target.value)" />
            <span class="stop-hex">{{ stop }}</span>
            <el-button
              size="small"
              text
              type="danger"
              :disabled="customStops.length <= 2"
              @click="removeCustomStop(index)"
            >删除</el-button>
          </div>
        </div>

        <div class="gradient-actions">
          <el-button size="small" :disabled="customStops.length >= 6" @click="addCustomStop">+ 添加色停点</el-button>
          <el-button size="small" @click="resetCustomStops">重置</el-button>
          <span class="muted" style="font-size: 12px">2-6 个颜色，首尾会自动闭环</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="customGradientDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmCustomGradient">应用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { danmakuApi } from '../api'
import { useMediaAudioEnhancer } from '../composables/useMediaAudioEnhancer'
import { ANIMATED_PRESETS, GRADIENT_PRESETS, SOLID_PRESETS, buildColorStyle, parseDanmakuColor } from '../utils/danmakuColor'

const props = defineProps({
  resourceId: { type: [Number, String], default: null },
  src: { type: String, required: true },
  enabled: { type: Boolean, default: true },
  permission: { type: String, default: 'LOGGED' },
  shareToken: { type: String, default: '' },
  currentUser: { type: Object, default: null },
  isOwnerOrAdmin: { type: Boolean, default: false }
})

const emit = defineEmits(['open-config'])

const videoRef = ref(null)
const trackRef = ref(null)
const stageRef = ref(null)
const composerRef = ref(null)
const progressRef = ref(null)

const danmakus = ref([])
const showDanmaku = ref(true)
const visibilityMode = ref(props.shareToken ? 'SHARE_NOTE' : 'ALL')
const sending = ref(false)
const composer = reactive({ content: '', type: 'scroll', color: '#ffffff' })
const paletteMode = ref('solid')
const paletteOpen = ref(false)

// Player state
const isPlaying = ref(false)
const isFullscreen = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const volume = ref(1)
const isMuted = ref(false)
const buffered = ref(0)
const controlsVisible = ref(true)
let hideControlsTimer = null

const {
  vocalEnhance,
  hiResEnhance,
  limiterEnabled,
  peakLevel,
  rmsLevel,
  processingLabel,
  ensureAudioGraph,
  resumeAudio,
  sampleAnalyser
} = useMediaAudioEnhancer()

// Danmaku rendering state
const scrollActive = ref([])
const topActive = ref([])
const bottomActive = ref([])

const SCROLL_DURATION = 8
const PINNED_DURATION = 4
const LANE_HEIGHT = 30
const SCROLL_LANES = 8
const PINNED_LANES = 4

const scrollLanes = new Array(SCROLL_LANES).fill(-Infinity)
const topLanes = new Array(PINNED_LANES).fill(-Infinity)
const bottomLanes = new Array(PINNED_LANES).fill(-Infinity)

const scheduleMap = new Map()
const colorStyleCache = new Map()

function getColorStyle(d) {
  let cached = colorStyleCache.get(d.id)
  if (!cached || cached.__spec !== d.color) {
    cached = buildColorStyle(d.color)
    cached.__spec = d.color
    colorStyleCache.set(d.id, cached)
  }
  return cached
}

let rafHandle = 0
let stageWidth = 0

// Presets
const solidPresets = SOLID_PRESETS
const gradientPresets = GRADIENT_PRESETS
const animatedPresets = ANIMATED_PRESETS
const paletteTabs = [
  { label: '纯色', value: 'solid' },
  { label: '静态渐变', value: 'gradient' },
  { label: '动态渐变', value: 'animated' }
]

// Custom gradient builder
const customGradientDialog = ref(false)
const customGradientMode = ref('gradient')
const DEFAULT_CUSTOM_STOPS = ['#ff6b6b', '#feca57', '#48dbfb']
const customStops = ref([...DEFAULT_CUSTOM_STOPS])

// Computeds
const permissionLabel = computed(() => ({
  EVERYONE: '所有人',
  LOGGED: '登录用户',
  OWNER: '仅发布者'
}[props.permission] || '登录用户'))

const canSend = computed(() => {
  if (!props.enabled) return false
  if (props.permission === 'EVERYONE') return true
  if (props.permission === 'LOGGED') return Boolean(props.currentUser)
  if (props.permission === 'OWNER') return props.isOwnerOrAdmin
  return false
})

const permissionHint = computed(() => {
  if (!props.enabled) return '该资源已关闭弹幕'
  if (props.permission === 'LOGGED') return '请登录后再发送弹幕'
  if (props.permission === 'OWNER') return '仅发布者可以发送弹幕'
  return ''
})

const hasDanmakuResource = computed(() => props.resourceId !== null && props.resourceId !== undefined && props.resourceId !== '')
const hasShareToken = computed(() => Boolean(String(props.shareToken || '').trim()))
const visibilityLabel = computed(() => visibilityMode.value === 'SHARE_NOTE' ? '分享者笔记' : '全部弹幕')
const playedPercent = computed(() => duration.value > 0 ? (currentTime.value / duration.value) * 100 : 0)
const bufferedPercent = computed(() => duration.value > 0 ? (buffered.value / duration.value) * 100 : 0)

const indicatorStyle = computed(() => {
  const desc = parseDanmakuColor(composer.color)
  if (desc.kind === 'solid') {
    return { background: desc.value, borderColor: desc.value === '#ffffff' ? '#d0dde8' : desc.value }
  }
  const raw = desc.stops || ['#ff004e', '#9d00ff']
  const stops = raw[0] === raw[raw.length - 1] ? raw : [...raw, raw[0]]
  return { backgroundImage: `linear-gradient(90deg, ${stops.join(', ')})`, borderColor: 'transparent' }
})

const customPickerValue = computed(() => {
  const desc = parseDanmakuColor(composer.color)
  return desc.kind === 'solid' && /^#[0-9a-fA-F]{6}$/.test(desc.value) ? desc.value : '#ffffff'
})

const customGradientSwatchStyle = computed(() => {
  const raw = customStops.value
  const stops = raw[0] === raw[raw.length - 1] ? raw : [...raw, raw[0]]
  return {
    backgroundImage: `linear-gradient(90deg, ${stops.join(', ')})`,
    color: '#ffffff'
  }
})

const customAnimSwatchStyle = computed(() => ({
  ...customGradientSwatchStyle.value,
  backgroundSize: '100% 100%'
}))

// ----- Video controls -----

async function activateAudioEnhancer() {
  const video = videoRef.value
  if (!video) return
  await ensureAudioGraph(video)
  await resumeAudio()
  sampleAnalyser()
}

async function togglePlay() {
  const video = videoRef.value
  if (!video) return
  if (video.paused) {
    await activateAudioEnhancer()
    await video.play()
  }
  else video.pause()
}

async function onPlay() {
  isPlaying.value = true
  await activateAudioEnhancer()
}

function toggleMute() {
  const video = videoRef.value
  if (!video) return
  video.muted = !video.muted
}

function onVolumeChange() {
  const video = videoRef.value
  if (!video) return
  volume.value = video.volume
  isMuted.value = video.muted
}

watch(volume, (v) => {
  const video = videoRef.value
  if (!video) return
  video.volume = v
  if (v > 0 && video.muted) video.muted = false
})

function seekTo(event) {
  const bar = progressRef.value
  const video = videoRef.value
  if (!bar || !video || !duration.value) return
  const rect = bar.getBoundingClientRect()
  const x = event.clientX - rect.left
  const ratio = Math.max(0, Math.min(1, x / rect.width))
  video.currentTime = ratio * duration.value
}

function seekToTime(seconds) {
  const video = videoRef.value
  const target = Number(seconds)
  if (!video || !Number.isFinite(target)) return
  video.currentTime = Math.max(0, Math.min(target, video.duration || target))
}

function getCurrentTime() {
  return Number((videoRef.value?.currentTime || 0).toFixed(3))
}

function captureSnapshot() {
  const video = videoRef.value
  if (!video || !video.videoWidth || !video.videoHeight) return ''
  const maxWidth = 420
  const scale = Math.min(1, maxWidth / video.videoWidth)
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(video.videoWidth * scale))
  canvas.height = Math.max(1, Math.round(video.videoHeight * scale))
  const context = canvas.getContext('2d')
  if (!context) return ''
  try {
    context.drawImage(video, 0, 0, canvas.width, canvas.height)
    return canvas.toDataURL('image/jpeg', 0.62)
  } catch {
    return ''
  }
}

function toggleFullscreen() {
  const stage = stageRef.value
  if (!stage) return
  if (document.fullscreenElement === stage) {
    document.exitFullscreen()
  } else {
    stage.requestFullscreen().catch(() => {})
  }
}

function formatTime(seconds) {
  if (!Number.isFinite(seconds)) return '00:00'
  const s = Math.floor(seconds)
  const m = Math.floor(s / 60)
  const sec = s % 60
  if (m >= 60) {
    const h = Math.floor(m / 60)
    return `${String(h).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

// Auto-hide controls during playback
function showControlsTemporarily() {
  controlsVisible.value = true
  if (hideControlsTimer) clearTimeout(hideControlsTimer)
  if (isPlaying.value && !paletteOpen.value) {
    hideControlsTimer = setTimeout(() => {
      controlsVisible.value = false
    }, 3000)
  }
}

function onStageMouseMove() {
  showControlsTemporarily()
}

// ----- Danmaku loading/render -----

async function loadDanmakus() {
  if (!hasDanmakuResource.value) {
    danmakus.value = []
    rebuildSchedule()
    return
  }
  try {
    const params = visibilityMode.value === 'SHARE_NOTE'
      ? { mode: 'SHARE_NOTE', shareToken: props.shareToken }
      : undefined
    const list = await danmakuApi.list(props.resourceId, params)
    danmakus.value = (list || [])
      .map((item) => ({ ...item, timeSeconds: Number(item.timeSeconds || 0) }))
      .sort((a, b) => a.timeSeconds - b.timeSeconds)
    rebuildSchedule()
  } catch (error) {
    danmakus.value = []
  }
}

function measureStage() {
  stageWidth = stageRef.value?.clientWidth || 0
}

function onMeta() {
  measureStage()
  const video = videoRef.value
  if (video) {
    duration.value = video.duration
    volume.value = video.volume
    isMuted.value = video.muted
  }
}

function onTimeUpdate() {
  const video = videoRef.value
  if (video) {
    currentTime.value = video.currentTime
    if (video.buffered && video.buffered.length) {
      buffered.value = video.buffered.end(video.buffered.length - 1)
    }
  }
  render()
}

function onSeek() {
  rebuildSchedule()
  // Do NOT wipe the full cache — that causes every visible danmaku to receive
  // a new style object on the next render and Vue rewrites the `style` attr,
  // which restarts (and therefore visibly freezes) the CSS animation. Only
  // drop entries that are no longer in the schedule so future re-entries get
  // a fresh one-shot animation.
  for (const id of Array.from(colorStyleCache.keys())) {
    if (!scheduleMap.has(id)) colorStyleCache.delete(id)
  }
  render()
}

function rebuildSchedule() {
  scrollLanes.fill(-Infinity)
  topLanes.fill(-Infinity)
  bottomLanes.fill(-Infinity)
  scheduleMap.clear()
  for (const d of danmakus.value) {
    const start = d.timeSeconds
    const end = start + (d.type === 'scroll' ? SCROLL_DURATION : PINNED_DURATION)
    const lanes = d.type === 'top' ? topLanes : d.type === 'bottom' ? bottomLanes : scrollLanes
    let lane = -1
    for (let i = 0; i < lanes.length; i++) {
      if (lanes[i] <= start) { lane = i; break }
    }
    if (lane < 0) lane = lanes.indexOf(Math.min(...lanes))
    lanes[lane] = end
    scheduleMap.set(d.id, { lane, start, end })
  }
}

function render() {
  if (!stageWidth) measureStage()
  const video = videoRef.value
  if (!video) return
  const t = video.currentTime
  const nextScroll = []
  const nextTop = []
  const nextBottom = []
  const list = danmakus.value
  for (let i = 0; i < list.length; i++) {
    const d = list[i]
    const sched = scheduleMap.get(d.id)
    if (!sched) continue
    if (t < sched.start) break
    if (t > sched.end) continue
    const progress = (t - sched.start) / (d.type === 'scroll' ? SCROLL_DURATION : PINNED_DURATION)
    if (d.type === 'scroll') {
      const width = stageWidth
      const travel = width + 300
      const x = width - progress * travel
      nextScroll.push({
        key: d.id,
        text: d.content,
        layoutStyle: {
          top: `${6 + sched.lane * LANE_HEIGHT}px`,
          transform: `translateX(${x}px)`
        },
        colorStyle: getColorStyle(d)
      })
    } else {
      const list = d.type === 'top' ? nextTop : nextBottom
      list.push({
        key: d.id,
        text: d.content,
        layoutStyle: {
          [d.type]: `${6 + sched.lane * LANE_HEIGHT}px`
        },
        colorStyle: getColorStyle(d)
      })
    }
  }
  scrollActive.value = nextScroll
  topActive.value = nextTop
  bottomActive.value = nextBottom
}

function loop() {
  sampleAnalyser()
  render()
  rafHandle = requestAnimationFrame(loop)
}

// ----- Send / delete -----

async function submit() {
  const content = composer.content.trim()
  if (!content || !hasDanmakuResource.value) return
  const payload = {
    content,
    type: composer.type,
    color: composer.color || '#ffffff',
    timeSeconds: Number((videoRef.value?.currentTime || 0).toFixed(3))
  }
  sending.value = true
  try {
    const created = await danmakuApi.send(props.resourceId, payload)
    const normalized = { ...created, timeSeconds: Number(created.timeSeconds || 0), color: payload.color }
    const next = [...danmakus.value, normalized].sort((a, b) => a.timeSeconds - b.timeSeconds)
    danmakus.value = next
    rebuildSchedule()
    composer.content = ''
    paletteOpen.value = false
    render()
  } finally {
    sending.value = false
  }
}

// ----- Palette / custom gradient -----

function pickColor(value) {
  composer.color = value
}

function onCustomColor(event) {
  composer.color = event.target.value
  paletteMode.value = 'solid'
}

function swatchStyle(spec) {
  const desc = parseDanmakuColor(spec)
  if (desc.kind === 'solid') return { background: desc.value }
  // Preview swatches always show the color spread across the tile as a
  // horizontal gradient — this gives users a clear "what colors are in this
  // preset?" picture for both static and animated options. The actual
  // animated danmaku will cycle through these colors one at a time on the
  // real text; the swatch is a palette preview, not a motion preview.
  const stops = desc.stops[0] === desc.stops[desc.stops.length - 1]
    ? desc.stops
    : [...desc.stops, desc.stops[0]]
  return {
    backgroundImage: `linear-gradient(90deg, ${stops.join(', ')})`,
    backgroundSize: '100% 100%',
    color: '#ffffff'
  }
}

function buildCustomColorSpec(mode) {
  const prefix = mode === 'animated' ? 'anim' : 'grad'
  return `${prefix}:${customStops.value.join(',')}`
}

function isCustomGradient(prefix) {
  const spec = composer.color
  if (typeof spec !== 'string' || !spec.startsWith(`${prefix}:`)) return false
  const presets = prefix === 'anim' ? animatedPresets : gradientPresets
  return !presets.some((preset) => preset.value === spec)
}

function openCustomGradient(mode) {
  customGradientMode.value = mode
  const desc = parseDanmakuColor(composer.color)
  if ((mode === 'gradient' && desc.kind === 'gradient') || (mode === 'animated' && desc.kind === 'animated')) {
    const stops = desc.stops.slice()
    while (stops.length > 2 && stops[0] === stops[stops.length - 1]) stops.pop()
    customStops.value = stops.length ? stops : [...DEFAULT_CUSTOM_STOPS]
  } else {
    customStops.value = [...DEFAULT_CUSTOM_STOPS]
  }
  customGradientDialog.value = true
}

function updateCustomStop(index, value) {
  customStops.value = customStops.value.map((stop, i) => (i === index ? value : stop))
}

function addCustomStop() {
  if (customStops.value.length >= 6) return
  customStops.value = [...customStops.value, customStops.value[customStops.value.length - 1] || '#ffffff']
}

function removeCustomStop(index) {
  if (customStops.value.length <= 2) return
  customStops.value = customStops.value.filter((_, i) => i !== index)
}

function resetCustomStops() {
  customStops.value = [...DEFAULT_CUSTOM_STOPS]
}

function confirmCustomGradient() {
  composer.color = buildCustomColorSpec(customGradientMode.value)
  customGradientDialog.value = false
}

// ----- Outside click / fullscreen -----

function onDocumentClick(event) {
  if (!paletteOpen.value) return
  if (!composerRef.value) return
  if (composerRef.value.contains(event.target)) return
  // Also skip if click is inside palette panel (which is inside .control-bar)
  const palette = stageRef.value?.querySelector('.palette-panel')
  if (palette && palette.contains(event.target)) return
  paletteOpen.value = false
}

function onFullscreenChange() {
  measureStage()
  isFullscreen.value = document.fullscreenElement === stageRef.value
}

onMounted(async () => {
  await loadDanmakus()
  await nextTick()
  measureStage()
  loop()
  const stage = stageRef.value
  if (stage) stage.addEventListener('mousemove', onStageMouseMove)
  window.addEventListener('resize', measureStage)
  document.addEventListener('click', onDocumentClick, true)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  if (rafHandle) cancelAnimationFrame(rafHandle)
  if (hideControlsTimer) clearTimeout(hideControlsTimer)
  const stage = stageRef.value
  if (stage) stage.removeEventListener('mousemove', onStageMouseMove)
  window.removeEventListener('resize', measureStage)
  document.removeEventListener('click', onDocumentClick, true)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})

watch(() => props.resourceId, async () => {
  await loadDanmakus()
})

watch(() => props.shareToken, async (token) => {
  visibilityMode.value = token ? 'SHARE_NOTE' : 'ALL'
  await loadDanmakus()
})

watch(visibilityMode, async () => {
  await loadDanmakus()
})

watch(() => props.src, () => {
  scrollActive.value = []
  topActive.value = []
  bottomActive.value = []
})

defineExpose({
  reload: loadDanmakus,
  getCurrentTime,
  seekToTime,
  captureSnapshot
})
</script>

<style scoped>
.danmaku-player {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.video-stage {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
}

.video-stage.fullscreen,
.video-stage:fullscreen {
  border-radius: 0;
  aspect-ratio: unset;
  width: 100vw;
  height: 100vh;
}

.danmaku-video {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: contain;
  background: #000;
}

.danmaku-track {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  color: #fff;
  font-family: "Microsoft YaHei", "PingFang SC", Arial, sans-serif;
}

.danmaku-track.hidden {
  display: none;
}

.danmaku {
  position: absolute;
  padding: 2px 6px;
  white-space: nowrap;
  font-weight: 700;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.86), 0 0 3px rgba(0, 0, 0, 0.7);
  font-size: 18px;
  line-height: 1.25;
  will-change: transform;
}

.danmaku.scroll {
  left: 0;
  top: 0;
}

.danmaku.pinned {
  left: 50%;
  transform: translateX(-50%);
}

.danmaku-inner {
  display: inline-block;
  will-change: background-position;
}

/* Center play overlay */
.center-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 5;
  background: rgba(0, 0, 0, 0.25);
  transition: background 200ms ease;
}

.center-play:hover {
  background: rgba(0, 0, 0, 0.4);
}

.center-play span {
  display: grid;
  place-items: center;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #1c7c7d;
  font-size: 28px;
  padding-left: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
}

/* Control bar */
.control-bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.85), rgba(0, 0, 0, 0.4) 70%, transparent);
  color: #ffffff;
  padding: 8px 12px 10px;
  transition: opacity 200ms ease, transform 200ms ease;
  opacity: 1;
  transform: translateY(0);
}

.control-bar:not(.visible) {
  opacity: 0;
  transform: translateY(8px);
  pointer-events: none;
}

.progress-row {
  height: 16px;
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 6px 0;
}

.progress-track {
  position: relative;
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 2px;
}

.progress-buffered {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.45);
  border-radius: 2px;
}

.progress-played {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: #1c7c7d;
  border-radius: 2px;
}

.progress-handle {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  margin-left: -6px;
  transform: translateY(-50%);
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4);
  opacity: 0;
  transition: opacity 150ms ease;
}

.progress-row:hover .progress-handle {
  opacity: 1;
}

.control-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.ctrl-btn {
  min-width: 32px;
  height: 30px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background 150ms ease;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

.ctrl-btn.active {
  background: rgba(28, 124, 125, 0.8);
}

.quality-controls {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.24);
}

.quality-btn {
  min-width: 28px;
  height: 26px;
  padding: 0 6px;
  font-size: 11px;
}

.volume-wrap {
  display: flex;
  align-items: center;
  gap: 4px;
}

.volume-slider {
  width: 80px;
  height: 4px;
  cursor: pointer;
  accent-color: #1c7c7d;
}

.time-display {
  font-size: 12px;
  font-family: ui-monospace, "Courier New", monospace;
  color: rgba(255, 255, 255, 0.88);
  min-width: 90px;
}

.composer-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.color-indicator {
  flex-shrink: 0;
  display: inline-block;
  width: 22px;
  height: 22px;
  border: 2px solid rgba(255, 255, 255, 0.7);
  border-radius: 4px;
  cursor: pointer;
  transition: transform 150ms ease;
}

.color-indicator:hover {
  transform: scale(1.12);
}

.type-select {
  height: 26px;
  padding: 0 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.35);
  color: #ffffff;
  font-size: 12px;
  cursor: pointer;
  outline: none;
}

.type-select option {
  color: #000;
}

.danmaku-input {
  flex: 1;
  min-width: 80px;
  height: 26px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 13px;
  background: rgba(0, 0, 0, 0.35);
  color: #ffffff;
  font-size: 13px;
  outline: none;
  transition: border-color 150ms ease, background 150ms ease;
}

.danmaku-input:focus {
  border-color: #1c7c7d;
  background: rgba(0, 0, 0, 0.5);
}

.danmaku-input::placeholder {
  color: rgba(255, 255, 255, 0.55);
}

.send-btn {
  height: 26px;
  padding: 0 14px;
  border: 0;
  border-radius: 13px;
  background: #1c7c7d;
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background 150ms ease;
}

.send-btn:hover:not(:disabled) {
  background: #145d5e;
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.control-right {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.view-select {
  max-width: 108px;
  height: 26px;
  padding: 0 6px;
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.35);
  color: #ffffff;
  font-size: 12px;
  outline: none;
}

.view-select option {
  color: #111827;
}

/* Palette panel */
.palette-panel {
  margin-top: 8px;
  padding: 10px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 180px;
  overflow: hidden;
}

.palette-slide-enter-active,
.palette-slide-leave-active {
  transition: max-height 220ms ease, opacity 180ms ease, margin-top 200ms ease;
  max-height: 180px;
}

.palette-slide-enter-from,
.palette-slide-leave-to {
  max-height: 0;
  opacity: 0;
  margin-top: 0;
}

.palette-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  align-self: flex-start;
}

.palette-tab {
  padding: 4px 12px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}

.palette-tab:hover {
  color: #ffffff;
}

.palette-tab.active {
  background: #1c7c7d;
  color: #ffffff;
}

.palette-swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.swatch {
  min-width: 30px;
  height: 26px;
  padding: 0 10px;
  border: 2px solid transparent;
  border-radius: 5px;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 150ms ease, box-shadow 150ms ease;
}

.swatch:hover {
  transform: translateY(-1px);
}

.swatch.solid {
  min-width: 26px;
  padding: 0;
}

.swatch.gradient {
  background-repeat: no-repeat;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.55);
}

/* Animated swatch intentionally left static. The swatch shows the base gradient
 * colors; the hue-rotate animation only runs on real danmaku so the preview
 * matches what users actually see when their danmaku first appears. */

.swatch.active {
  border-color: #1c7c7d;
  box-shadow: 0 0 0 2px rgba(28, 124, 125, 0.45);
}

.swatch.custom {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff 0 50%, #cad6e2 50% 100%);
  color: #344054;
  min-width: 54px;
  padding: 0 8px;
}

.swatch.custom input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.swatch.custom-gradient {
  min-width: 60px;
  padding: 0 10px;
}

/* Meta */
.danmaku-info {
  margin-top: 4px;
}

.danmaku-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  color: var(--muted);
  font-size: 12px;
  padding: 2px 4px;
}

.quality-meter {
  font-family: ui-monospace, "Courier New", monospace;
}

/* Gradient builder dialog */
.gradient-builder {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.gradient-stops {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.gradient-stop {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border: 1px solid rgba(196, 213, 228, 0.7);
  border-radius: 6px;
  background: rgba(247, 250, 252, 0.88);
}

.gradient-stop .stop-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--primary);
  color: #ffffff;
  font-size: 12px;
  font-weight: 700;
}

.gradient-stop input[type="color"] {
  width: 40px;
  height: 28px;
  border: 1px solid rgba(196, 213, 228, 0.8);
  border-radius: 4px;
  background: transparent;
  cursor: pointer;
}

.gradient-stop .stop-hex {
  flex: 1;
  color: #344054;
  font-size: 12px;
  font-family: ui-monospace, "Courier New", monospace;
}

.gradient-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
