<template>
  <div class="audio-player" :class="{ playing: isPlaying }">
    <audio
      ref="audioRef"
      :src="src"
      preload="metadata"
      :loop="loop"
      crossorigin="anonymous"
      @loadedmetadata="onLoaded"
      @timeupdate="onTimeUpdate"
      @progress="onProgress"
      @play="handlePlay"
      @pause="isPlaying = false"
      @ended="onEnded"
      @volumechange="onVolumeChange"
    />

    <div class="album-panel">
      <div class="album-cover">
        <canvas ref="visualizerRef" class="visualizer-canvas" width="360" height="360"></canvas>
        <span>{{ coverText }}</span>
        <div class="cover-rings">
          <i></i>
          <i></i>
          <i></i>
        </div>
      </div>
      <div class="track-copy">
        <span class="track-kicker">Music Player</span>
        <h3>{{ displayTitle }}</h3>
        <p>{{ subtitle || fileName || '在线音频资源' }}</p>
        <div class="quality-stack">
          <span>{{ fileLabel }}</span>
          <span>{{ sizeLabel }}</span>
          <span>{{ audioRateLabel }}</span>
          <span>{{ processingLabel }}</span>
        </div>
      </div>
    </div>

    <section class="visual-stage">
      <div class="stage-head">
        <div>
          <strong>音乐可视化视频</strong>
          <span>实时频谱 / 波形渲染</span>
        </div>
        <select v-model="visualMode" class="mode-select" title="可视化模式">
          <option value="bars">频谱条</option>
          <option value="radial">环形声场</option>
          <option value="wave">波形视频</option>
        </select>
      </div>
      <canvas ref="wideVisualizerRef" class="wide-visualizer" width="960" height="260"></canvas>
    </section>

    <div class="time-row">
      <span>{{ formatTime(currentTime) }}</span>
      <input
        class="seek-slider"
        type="range"
        min="0"
        :max="duration || 0"
        step="0.01"
        :value="currentTime"
        @input="seek"
        :style="seekStyle"
      />
      <span>{{ formatTime(duration) }}</span>
    </div>

    <div class="control-row">
      <button type="button" class="icon-button" title="后退 10 秒" @click="skip(-10)">
        -10
      </button>
      <button type="button" class="play-button" :title="isPlaying ? '暂停' : '播放'" @click="togglePlay">
        <span v-if="isPlaying">II</span>
        <span v-else>PLAY</span>
      </button>
      <button type="button" class="icon-button" title="前进 10 秒" @click="skip(10)">
        +10
      </button>

      <div class="volume-group">
        <button type="button" class="icon-button" :title="isMuted ? '取消静音' : '静音'" @click="toggleMute">
          {{ isMuted || volume === 0 ? 'M' : 'V' }}
        </button>
        <input
          class="volume-slider"
          type="range"
          min="0"
          max="1"
          step="0.01"
          v-model.number="volume"
          title="音量"
        />
      </div>

      <select v-model.number="playbackRate" class="speed-select" title="播放速度">
        <option :value="0.75">0.75x</option>
        <option :value="1">1x</option>
        <option :value="1.25">1.25x</option>
        <option :value="1.5">1.5x</option>
        <option :value="2">2x</option>
      </select>

      <button type="button" class="toggle-button" :class="{ active: loop }" title="循环播放" @click="loop = !loop">
        LOOP
      </button>
    </div>

    <section class="enhance-panel">
      <div class="enhance-card">
        <div>
          <strong>人声分离强化</strong>
          <span>弱化背景声和杂音，突出人声主体，压峰、抬细节、减少忽大忽小</span>
        </div>
        <button type="button" class="switch-button" :class="{ active: dynamicRepair }" @click="dynamicRepair = !dynamicRepair">
          {{ dynamicRepair ? 'ON' : 'OFF' }}
        </button>
      </div>
      <div class="enhance-card">
        <div>
          <strong>Hi-Res 听感提升</strong>
          <span>高频空气感、低频收束、人声清晰度</span>
        </div>
        <button type="button" class="switch-button" :class="{ active: hiResEnhance }" @click="hiResEnhance = !hiResEnhance">
          {{ hiResEnhance ? 'ON' : 'OFF' }}
        </button>
      </div>
      <div class="enhance-card">
        <div>
          <strong>母带保护</strong>
          <span>输出限幅，避免增强后爆音</span>
        </div>
        <button type="button" class="switch-button" :class="{ active: limiterEnabled }" @click="limiterEnabled = !limiterEnabled">
          {{ limiterEnabled ? 'ON' : 'OFF' }}
        </button>
      </div>
    </section>

    <div class="meta-row">
      <span>音量 {{ Math.round(volume * 100) }}%</span>
      <span>{{ isPlaying ? '播放中' : '已暂停' }}</span>
      <span>峰值 {{ peakLevel }}%</span>
      <span>RMS {{ rmsLevel }}%</span>
    </div>

    <div class="action-row">
      <button type="button" @click="$emit('download')">下载音频</button>
      <button type="button" @click="$emit('open')">新窗口打开</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useMediaAudioEnhancer } from '../composables/useMediaAudioEnhancer'

const props = defineProps({
  src: { type: String, required: true },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  fileName: { type: String, default: '' },
  fileSize: { type: [Number, String], default: 0 }
})

defineEmits(['download', 'open'])

const audioRef = ref(null)
const visualizerRef = ref(null)
const wideVisualizerRef = ref(null)
const isPlaying = ref(false)
const isMuted = ref(false)
const loop = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const buffered = ref(0)
const volume = ref(0.86)
const playbackRate = ref(1)
const visualMode = ref('bars')
let frequencyData = null
let timeData = null
let rafId = 0

const {
  dynamicRepair,
  hiResEnhance,
  limiterEnabled,
  peakLevel,
  rmsLevel,
  audioRateLabel,
  ensureAudioGraph: ensureMediaAudioGraph,
  resumeAudio,
  sampleAnalyser
} = useMediaAudioEnhancer()

const displayTitle = computed(() => props.title || props.fileName || '未命名音频')
const coverText = computed(() => {
  const source = displayTitle.value.trim()
  if (!source) return 'MP3'
  const ascii = source.match(/[a-zA-Z0-9]/g)?.slice(0, 2).join('')
  if (ascii) return ascii.toUpperCase()
  return source.slice(0, 2)
})
const fileLabel = computed(() => {
  const name = (props.fileName || props.title || '').toLowerCase()
  if (name.endsWith('.wav')) return 'WAV'
  if (name.endsWith('.ogg')) return 'OGG'
  if (name.endsWith('.flac')) return 'FLAC'
  if (name.endsWith('.m4a')) return 'M4A'
  return 'MP3'
})
const sizeLabel = computed(() => formatSize(props.fileSize))
const playedPercent = computed(() => duration.value > 0 ? Math.min(100, (currentTime.value / duration.value) * 100) : 0)
const bufferedPercent = computed(() => duration.value > 0 ? Math.min(100, (buffered.value / duration.value) * 100) : 0)
const seekStyle = computed(() => ({
  background: `linear-gradient(90deg, #1c7c7d 0%, #1c7c7d ${playedPercent.value}%, rgba(28, 124, 125, 0.22) ${playedPercent.value}%, rgba(28, 124, 125, 0.22) ${bufferedPercent.value}%, rgba(196, 213, 228, 0.62) ${bufferedPercent.value}%, rgba(196, 213, 228, 0.62) 100%)`
}))
const processingLabel = computed(() => {
  const active = []
  if (dynamicRepair.value) active.push('Vocal+')
  if (hiResEnhance.value) active.push('Hi-Res')
  if (limiterEnabled.value) active.push('Limiter')
  return active.length ? active.join(' / ') : '原始输出'
})

async function ensureAudioGraph() {
  const audio = audioRef.value
  if (!audio) return
  await ensureMediaAudioGraph(audio)
  startVisualizer()
}

async function togglePlay() {
  const audio = audioRef.value
  if (!audio) return
  await ensureAudioGraph()
  await resumeAudio()
  if (audio.paused) {
    audio.play()
  } else {
    audio.pause()
  }
}

async function handlePlay() {
  isPlaying.value = true
  await ensureAudioGraph()
  await resumeAudio()
  startVisualizer()
}

function skip(seconds) {
  const audio = audioRef.value
  if (!audio || !duration.value) return
  audio.currentTime = Math.max(0, Math.min(duration.value, audio.currentTime + seconds))
}

function seek(event) {
  const audio = audioRef.value
  if (!audio) return
  audio.currentTime = Number(event.target.value || 0)
}

function toggleMute() {
  const audio = audioRef.value
  if (!audio) return
  audio.muted = !audio.muted
}

function onLoaded() {
  const audio = audioRef.value
  if (!audio) return
  duration.value = Number.isFinite(audio.duration) ? audio.duration : 0
  audio.volume = volume.value
  audio.playbackRate = playbackRate.value
  onProgress()
  drawIdleVisuals()
}

function onTimeUpdate() {
  const audio = audioRef.value
  if (!audio) return
  currentTime.value = audio.currentTime || 0
}

function onProgress() {
  const audio = audioRef.value
  if (!audio || !audio.buffered?.length) return
  buffered.value = audio.buffered.end(audio.buffered.length - 1)
}

function onVolumeChange() {
  const audio = audioRef.value
  if (!audio) return
  volume.value = audio.volume
  isMuted.value = audio.muted
}

function onEnded() {
  isPlaying.value = false
  if (!loop.value) currentTime.value = 0
}

function startVisualizer() {
  if (rafId) return
  const render = () => {
    rafId = requestAnimationFrame(render)
    drawVisuals()
  }
  render()
}

function drawVisuals() {
  const sample = sampleAnalyser()
  if (!sample) {
    drawIdleVisuals()
    return
  }
  frequencyData = sample.frequencyData
  timeData = sample.timeData
  drawCoverCanvas()
  drawWideCanvas()
}

function drawCoverCanvas() {
  const canvas = visualizerRef.value
  if (!canvas || !frequencyData) return
  const ctx = canvas.getContext('2d')
  const { width, height } = canvas
  ctx.clearRect(0, 0, width, height)
  const cx = width / 2
  const cy = height / 2
  const radius = 82
  const count = 96
  for (let i = 0; i < count; i++) {
    const value = frequencyData[Math.floor((i / count) * frequencyData.length)] / 255
    const angle = (i / count) * Math.PI * 2
    const length = 18 + value * 72
    const x1 = cx + Math.cos(angle) * radius
    const y1 = cy + Math.sin(angle) * radius
    const x2 = cx + Math.cos(angle) * (radius + length)
    const y2 = cy + Math.sin(angle) * (radius + length)
    ctx.strokeStyle = `rgba(${47 + value * 70}, ${128 + value * 70}, ${237 - value * 80}, ${0.34 + value * 0.52})`
    ctx.lineWidth = 2
    ctx.beginPath()
    ctx.moveTo(x1, y1)
    ctx.lineTo(x2, y2)
    ctx.stroke()
  }
}

function drawWideCanvas() {
  const canvas = wideVisualizerRef.value
  if (!canvas || !frequencyData || !timeData) return
  const ctx = canvas.getContext('2d')
  const { width, height } = canvas
  ctx.clearRect(0, 0, width, height)
  const bg = ctx.createLinearGradient(0, 0, width, height)
  bg.addColorStop(0, '#f7fbff')
  bg.addColorStop(1, '#edf6f6')
  ctx.fillStyle = bg
  ctx.fillRect(0, 0, width, height)

  if (visualMode.value === 'wave') drawWave(ctx, width, height)
  else if (visualMode.value === 'radial') drawRadial(ctx, width, height)
  else drawBars(ctx, width, height)
}

function drawBars(ctx, width, height) {
  const barCount = 72
  const gap = 4
  const barWidth = (width - gap * (barCount - 1)) / barCount
  for (let i = 0; i < barCount; i++) {
    const value = frequencyData[Math.floor((i / barCount) * frequencyData.length)] / 255
    const h = 12 + value * (height - 34)
    const x = i * (barWidth + gap)
    const y = height - h
    const grad = ctx.createLinearGradient(0, y, 0, height)
    grad.addColorStop(0, '#2f80ed')
    grad.addColorStop(1, '#1c7c7d')
    ctx.fillStyle = grad
    roundRect(ctx, x, y, barWidth, h, 7)
    ctx.fill()
  }
}

function drawWave(ctx, width, height) {
  ctx.lineWidth = 3
  ctx.strokeStyle = '#1c7c7d'
  ctx.beginPath()
  for (let i = 0; i < timeData.length; i++) {
    const x = (i / (timeData.length - 1)) * width
    const y = (timeData[i] / 255) * height
    if (i === 0) ctx.moveTo(x, y)
    else ctx.lineTo(x, y)
  }
  ctx.stroke()
  ctx.lineWidth = 1
  ctx.strokeStyle = 'rgba(47, 128, 237, 0.32)'
  ctx.beginPath()
  ctx.moveTo(0, height / 2)
  ctx.lineTo(width, height / 2)
  ctx.stroke()
}

function drawRadial(ctx, width, height) {
  const cx = width / 2
  const cy = height / 2
  const radius = Math.min(width, height) * 0.22
  const count = 140
  ctx.lineCap = 'round'
  for (let i = 0; i < count; i++) {
    const value = frequencyData[Math.floor((i / count) * frequencyData.length)] / 255
    const angle = (i / count) * Math.PI * 2
    const inner = radius
    const outer = radius + 12 + value * 88
    ctx.strokeStyle = `rgba(28, 124, 125, ${0.24 + value * 0.68})`
    ctx.lineWidth = 2 + value * 3
    ctx.beginPath()
    ctx.moveTo(cx + Math.cos(angle) * inner, cy + Math.sin(angle) * inner)
    ctx.lineTo(cx + Math.cos(angle) * outer, cy + Math.sin(angle) * outer)
    ctx.stroke()
  }
}

function drawIdleVisuals() {
  const canvas = wideVisualizerRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const { width, height } = canvas
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = '#f7fbff'
  ctx.fillRect(0, 0, width, height)
  ctx.fillStyle = '#667085'
  ctx.font = '16px Arial'
  ctx.textAlign = 'center'
  ctx.fillText('点击播放后启动实时音乐可视化', width / 2, height / 2)
}

function roundRect(ctx, x, y, width, height, radius) {
  const r = Math.min(radius, width / 2, height / 2)
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + width, y, x + width, y + height, r)
  ctx.arcTo(x + width, y + height, x, y + height, r)
  ctx.arcTo(x, y + height, x, y, r)
  ctx.arcTo(x, y, x + width, y, r)
  ctx.closePath()
}

function formatTime(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return '00:00'
  const total = Math.floor(seconds)
  const min = Math.floor(total / 60)
  const sec = total % 60
  const hour = Math.floor(min / 60)
  if (hour > 0) {
    return `${String(hour).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
  }
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function formatSize(size = 0) {
  const value = Number(size) || 0
  if (value <= 0) return '未知大小'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

watch(volume, (next) => {
  const audio = audioRef.value
  if (!audio) return
  audio.volume = next
  if (next > 0 && audio.muted) audio.muted = false
})

watch(playbackRate, (next) => {
  const audio = audioRef.value
  if (!audio) return
  audio.playbackRate = next
})

watch(() => props.src, () => {
  currentTime.value = 0
  duration.value = 0
  buffered.value = 0
  isPlaying.value = false
  frequencyData = null
  timeData = null
})

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<style scoped>
.audio-player {
  display: grid;
  gap: 16px;
  width: min(920px, 100%);
  margin: 0 auto;
  padding: 22px;
  border: 1px solid rgba(196, 213, 228, 0.78);
  border-radius: 8px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.96), rgba(244, 249, 251, 0.9)),
    #ffffff;
  box-shadow: 0 18px 46px rgba(26, 39, 68, 0.11);
}

.album-panel {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 20px;
  align-items: center;
}

.album-cover {
  position: relative;
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(18, 63, 74, 0.96), rgba(28, 124, 125, 0.9)),
    #123f4a;
  color: #ffffff;
  overflow: hidden;
  box-shadow: 0 16px 34px rgba(18, 63, 74, 0.2);
}

.visualizer-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.album-cover > span {
  position: relative;
  z-index: 2;
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0;
}

.cover-rings {
  position: absolute;
  inset: 18px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.34);
}

.cover-rings i {
  position: absolute;
  inset: calc(var(--i, 0) * 18px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 50%;
}

.cover-rings i:nth-child(1) {
  --i: 1;
}

.cover-rings i:nth-child(2) {
  --i: 2;
}

.cover-rings i:nth-child(3) {
  --i: 3;
}

.playing .album-cover {
  animation: coverPulse 2200ms ease-in-out infinite;
}

.track-copy {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.track-kicker {
  color: var(--primary);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.track-copy h3 {
  overflow: hidden;
  margin: 0;
  font-size: 28px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-copy p {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--muted);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.quality-stack,
.meta-row,
.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quality-stack span,
.meta-row span {
  padding: 6px 9px;
  border-radius: 8px;
  background: rgba(23, 32, 51, 0.06);
  color: var(--muted);
  font-size: 12px;
}

.visual-stage {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.7);
  border-radius: 8px;
  background: rgba(247, 250, 252, 0.74);
}

.stage-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.stage-head div {
  display: grid;
  gap: 3px;
}

.stage-head span {
  color: var(--muted);
  font-size: 12px;
}

.wide-visualizer {
  width: 100%;
  min-height: 210px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: #f7fbff;
}

.time-row {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) 54px;
  gap: 10px;
  align-items: center;
  color: var(--muted);
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
}

.seek-slider,
.volume-slider {
  width: 100%;
  height: 6px;
  border-radius: 999px;
  outline: none;
  appearance: none;
}

.seek-slider::-webkit-slider-thumb,
.volume-slider::-webkit-slider-thumb {
  width: 15px;
  height: 15px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 2px 8px rgba(28, 124, 125, 0.28);
  appearance: none;
}

.volume-slider {
  max-width: 92px;
  background: rgba(196, 213, 228, 0.62);
  accent-color: var(--primary);
}

.control-row,
.meta-row,
.action-row {
  justify-content: center;
  align-items: center;
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.icon-button,
.toggle-button,
.play-button,
.action-row button,
.speed-select,
.mode-select,
.switch-button {
  min-height: 38px;
  border: 1px solid rgba(196, 213, 228, 0.8);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.82);
  color: #344054;
  font-weight: 800;
  cursor: pointer;
}

.icon-button {
  min-width: 42px;
  padding: 0 10px;
}

.play-button {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  border-color: transparent;
  background: var(--primary);
  color: #ffffff;
  font-size: 18px;
  box-shadow: 0 12px 26px rgba(28, 124, 125, 0.24);
}

.play-button:hover,
.action-row button:hover,
.toggle-button.active,
.switch-button.active {
  border-color: var(--primary);
  background: var(--primary);
  color: #ffffff;
}

.volume-group {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 150px;
}

.speed-select,
.mode-select {
  padding: 0 10px;
}

.toggle-button,
.switch-button {
  padding: 0 12px;
}

.enhance-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.enhance-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
}

.enhance-card div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.enhance-card span {
  color: var(--muted);
  font-size: 12px;
  line-height: 1.45;
}

.action-row button {
  padding: 0 14px;
}

@keyframes coverPulse {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-3px);
  }
}

@media (max-width: 800px) {
  .audio-player {
    padding: 16px;
  }

  .album-panel,
  .enhance-panel {
    grid-template-columns: 1fr;
  }

  .album-cover {
    width: min(190px, 70vw);
    margin: 0 auto;
  }

  .track-copy h3,
  .track-copy p,
  .track-kicker {
    text-align: center;
  }

  .quality-stack {
    justify-content: center;
  }

  .time-row {
    grid-template-columns: 44px minmax(0, 1fr) 44px;
  }
}
</style>
