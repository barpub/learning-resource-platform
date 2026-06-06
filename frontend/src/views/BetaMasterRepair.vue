<template>
  <div class="master-repair-page">
    <section class="repair-head">
      <div>
        <span class="eyebrow">Beta Lab</span>
        <h1>母版动态修复</h1>
        <p>用干净 MP3 伴奏替换失真伴奏，与原始 FLAC 人声离线合成，导出独立 24-bit WAV。</p>
      </div>
      <div class="repair-badge">
        <strong>{{ result ? 'READY' : running ? 'RUNNING' : 'LOCAL' }}</strong>
        <span>不覆盖原资源</span>
      </div>
    </section>

    <section class="repair-layout">
      <aside class="repair-panel source-panel">
        <div class="section-title">
          <span>素材</span>
          <small>离线处理</small>
        </div>

        <label class="file-slot" :class="{ selected: files.vocal }">
          <input type="file" accept=".flac,audio/flac,audio/x-flac,audio/*" @change="pickFile('vocal', $event)" />
          <span>FLAC 人声</span>
          <strong>{{ files.vocal?.name || '选择文件' }}</strong>
          <small>{{ formatSize(files.vocal?.size) }}</small>
        </label>

        <label class="file-slot" :class="{ selected: files.accompaniment }">
          <input type="file" accept=".mp3,audio/mpeg,audio/*" @change="pickFile('accompaniment', $event)" />
          <span>MP3 干净伴奏</span>
          <strong>{{ files.accompaniment?.name || '选择文件' }}</strong>
          <small>{{ formatSize(files.accompaniment?.size) }}</small>
        </label>

        <label class="file-slot optional" :class="{ selected: files.reference }">
          <input type="file" accept=".flac,.wav,audio/flac,audio/wav,audio/*" @change="pickFile('reference', $event)" />
          <span>失真 FLAC 伴奏参考</span>
          <strong>{{ files.reference?.name || '可选' }}</strong>
          <small>{{ files.reference ? formatSize(files.reference.size) : '用于时间/响度/声场匹配' }}</small>
        </label>

        <div class="source-actions">
          <el-button :disabled="running" @click="resetFiles">清空素材</el-button>
        </div>
      </aside>

      <main class="repair-panel process-panel">
        <div class="section-title">
          <span>修复链</span>
          <small>source replacement master</small>
        </div>

        <div class="chain-strip">
          <span>对齐</span>
          <i></i>
          <span>RMS</span>
          <i></i>
          <span>M/S</span>
          <i></i>
          <span>合成</span>
          <i></i>
          <span>峰值保护</span>
        </div>

        <div class="param-grid">
          <label>
            <span>chunk 秒数</span>
            <input v-model.number="params.chunkSeconds" type="number" min="3" max="20" step="1" />
          </label>
          <label>
            <span>最大偏移 ms</span>
            <input v-model.number="params.maxShiftMs" type="number" min="20" max="1000" step="10" />
          </label>
          <label>
            <span>目标 RMS dB</span>
            <input v-model.number="params.targetRmsDb" type="number" min="-30" max="-8" step="1" />
          </label>
          <label>
            <span>峰值上限 dB</span>
            <input v-model.number="params.targetPeakDb" type="number" min="-6" max="-0.3" step="0.1" />
          </label>
          <label>
            <span>声场宽度</span>
            <input v-model.number="params.widthScale" type="number" min="0.6" max="1.5" step="0.05" />
          </label>
          <label>
            <span>人声增益 dB</span>
            <input v-model.number="params.vocalGainDb" type="number" min="-12" max="12" step="0.5" />
          </label>
          <label>
            <span>伴奏增益 dB</span>
            <input v-model.number="params.accompanimentGainDb" type="number" min="-12" max="12" step="0.5" />
          </label>
        </div>

        <div class="run-row">
          <el-button type="primary" :loading="running" :disabled="!canRun" @click="runRepair">
            生成修复母版
          </el-button>
          <el-button :disabled="running" @click="resetParams">参数复位</el-button>
        </div>

        <div class="progress-box">
          <div>
            <strong>{{ progress.label }}</strong>
            <span>{{ progress.value }}%</span>
          </div>
          <el-progress :percentage="progress.value" :stroke-width="8" :show-text="false" />
        </div>

        <section class="result-panel" v-if="result">
          <div class="result-main">
            <div>
              <span>输出</span>
              <strong>{{ outputName }}</strong>
              <small>{{ formatDuration(result.duration) }} · {{ result.sampleRate }} Hz · WAV 24-bit</small>
            </div>
            <a class="download-button" :href="outputUrl" :download="outputName">下载母版</a>
          </div>

          <audio class="master-preview" :src="outputUrl" controls />

          <div class="metric-grid">
            <div v-for="metric in metricList" :key="metric.label">
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
            </div>
          </div>
        </section>
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { runOfflineMasterRepair } from '../utils/offlineMasterRepair'

const defaultParams = {
  chunkSeconds: 8,
  maxShiftMs: 250,
  targetRmsDb: -18,
  targetPeakDb: -1,
  widthScale: 1,
  vocalGainDb: 0,
  accompanimentGainDb: 0
}

const files = reactive({
  vocal: null,
  accompaniment: null,
  reference: null
})
const params = reactive({ ...defaultParams })
const running = ref(false)
const progress = reactive({ label: '等待素材', value: 0 })
const result = ref(null)
const outputUrl = ref('')

const canRun = computed(() => Boolean(files.vocal && files.accompaniment && !running.value))
const outputName = computed(() => {
  const base = (files.vocal?.name || 'repaired-master').replace(/\.[^.]+$/, '')
  return `${base}-source-repair-24bit.wav`
})
const metricList = computed(() => {
  if (!result.value) return []
  const { alignment, rms, width, metrics, peakTrimDb } = result.value
  return [
    { label: '平均偏移', value: `${alignment.averageOffsetMs.toFixed(1)} ms` },
    { label: '最大偏移', value: `${alignment.maxOffsetMs.toFixed(1)} ms` },
    { label: '对齐评分', value: alignment.averageScore.toFixed(3) },
    { label: 'L RMS 增益', value: formatDb(rms.leftGainDb) },
    { label: 'R RMS 增益', value: formatDb(rms.rightGainDb) },
    { label: 'M/S 宽度', value: `${width.scale.toFixed(2)}x` },
    { label: '峰值修剪', value: formatDb(peakTrimDb) },
    { label: 'L Crest', value: formatDb(metrics.leftCrestDb) },
    { label: 'R Crest', value: formatDb(metrics.rightCrestDb) },
    { label: 'L Peak', value: formatDb(metrics.leftPeakDb) },
    { label: 'R Peak', value: formatDb(metrics.rightPeakDb) },
    { label: 'L/R RMS', value: `${formatDb(metrics.leftRmsDb)} / ${formatDb(metrics.rightRmsDb)}` }
  ]
})

function pickFile(key, event) {
  files[key] = event.target.files?.[0] || null
  result.value = null
  revokeOutput()
}

function resetFiles() {
  files.vocal = null
  files.accompaniment = null
  files.reference = null
  result.value = null
  progress.label = '等待素材'
  progress.value = 0
  revokeOutput()
}

function resetParams() {
  Object.assign(params, defaultParams)
}

async function runRepair() {
  if (!canRun.value) return
  running.value = true
  result.value = null
  revokeOutput()
  try {
    const next = await runOfflineMasterRepair({
      ...params,
      vocalFile: files.vocal,
      accompanimentFile: files.accompaniment,
      referenceFile: files.reference,
      onProgress(label, value) {
        progress.label = label
        progress.value = value
      }
    })
    result.value = next
    outputUrl.value = URL.createObjectURL(next.blob)
    ElMessage.success('修复母版已生成')
  } catch (error) {
    progress.label = '处理失败'
    ElMessage.error(error.message || '母版修复失败')
  } finally {
    running.value = false
  }
}

function revokeOutput() {
  if (outputUrl.value) URL.revokeObjectURL(outputUrl.value)
  outputUrl.value = ''
}

function formatSize(size) {
  const value = Number(size) || 0
  if (!value) return '未选择'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDuration(seconds) {
  const total = Math.max(0, Math.round(seconds || 0))
  const min = Math.floor(total / 60)
  const sec = total % 60
  return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

function formatDb(value) {
  return `${Number(value || 0).toFixed(2)} dB`
}

onBeforeUnmount(revokeOutput)
</script>

<style scoped>
.master-repair-page {
  display: grid;
  gap: 18px;
}

.repair-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.repair-head h1 {
  margin: 6px 0;
  font-size: 32px;
  letter-spacing: 0;
}

.repair-head p {
  margin: 0;
  color: var(--muted);
}

.repair-badge {
  display: grid;
  gap: 4px;
  min-width: 150px;
  padding: 14px 16px;
  border: 1px solid rgba(196, 213, 228, 0.8);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
  text-align: right;
}

.repair-badge strong {
  color: var(--primary-strong);
  font-size: 22px;
}

.repair-badge span {
  color: var(--muted);
  font-size: 12px;
}

.repair-layout {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 18px;
}

.repair-panel {
  padding: 18px;
  border: 1px solid rgba(196, 213, 228, 0.8);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow-soft);
}

.source-panel,
.process-panel {
  display: grid;
  align-content: start;
  gap: 14px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--muted);
  font-size: 12px;
  text-transform: uppercase;
}

.section-title span {
  color: var(--ink);
  font-size: 16px;
  font-weight: 800;
  text-transform: none;
}

.file-slot {
  position: relative;
  display: grid;
  gap: 6px;
  min-height: 108px;
  padding: 14px;
  border: 1px dashed rgba(28, 124, 125, 0.42);
  border-radius: 8px;
  background: #f7fbff;
  cursor: pointer;
}

.file-slot input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.file-slot span {
  color: var(--primary-strong);
  font-weight: 800;
}

.file-slot strong {
  min-width: 0;
  overflow: hidden;
  color: #172033;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-slot small {
  color: var(--muted);
}

.file-slot.selected {
  border-style: solid;
  background: #eef6f6;
}

.file-slot.optional {
  background: #fbfcff;
}

.source-actions,
.run-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.chain-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: #f4f8fb;
  color: #344054;
  font-weight: 800;
  overflow-x: auto;
}

.chain-strip i {
  flex: 0 0 28px;
  height: 2px;
  border-radius: 999px;
  background: rgba(28, 124, 125, 0.34);
}

.param-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  gap: 12px;
}

.param-grid label {
  display: grid;
  gap: 6px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.param-grid input {
  width: 100%;
  height: 36px;
  padding: 0 10px;
  border: 1px solid rgba(196, 213, 228, 0.9);
  border-radius: 6px;
  background: #ffffff;
  color: var(--ink);
}

.progress-box {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fbfd;
}

.progress-box > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.result-panel {
  display: grid;
  gap: 14px;
  padding-top: 2px;
}

.result-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border-radius: 8px;
  background: #eef6f6;
}

.result-main div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.result-main span,
.result-main small {
  color: var(--muted);
}

.result-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.download-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border-radius: 6px;
  background: var(--primary);
  color: #ffffff;
  font-weight: 800;
}

.master-preview {
  width: 100%;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(130px, 1fr));
  gap: 10px;
}

.metric-grid div {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid rgba(196, 213, 228, 0.72);
  border-radius: 8px;
  background: #ffffff;
}

.metric-grid span {
  color: var(--muted);
  font-size: 12px;
}

.metric-grid strong {
  font-family: ui-monospace, "Courier New", monospace;
}

@media (max-width: 980px) {
  .repair-head,
  .result-main {
    align-items: stretch;
    flex-direction: column;
  }

  .repair-badge {
    text-align: left;
  }

  .repair-layout,
  .param-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
