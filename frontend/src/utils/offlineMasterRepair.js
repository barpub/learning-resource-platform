const SQRT_HALF = Math.SQRT1_2

export async function runOfflineMasterRepair(options) {
  const {
    vocalFile,
    accompanimentFile,
    referenceFile,
    chunkSeconds = 8,
    maxShiftMs = 250,
    targetRmsDb = -18,
    targetPeakDb = -1,
    widthScale = 1,
    vocalGainDb = 0,
    accompanimentGainDb = 0,
    onProgress = () => {}
  } = options

  if (!vocalFile || !accompanimentFile) {
    throw new Error('请选择 FLAC 人声和 MP3 伴奏')
  }

  onProgress('解码人声', 8)
  const vocalBuffer = await decodeFile(vocalFile)
  onProgress('解码伴奏', 18)
  const accompanimentBuffer = await decodeFile(accompanimentFile)
  onProgress(referenceFile ? '解码参考伴奏' : '准备素材', 28)
  const referenceBuffer = referenceFile ? await decodeFile(referenceFile) : null

  const sampleRate = vocalBuffer.sampleRate
  onProgress('统一采样率', 36)
  const vocal = toStereo(await resampleBuffer(vocalBuffer, sampleRate))
  const accompaniment = toStereo(await resampleBuffer(accompanimentBuffer, sampleRate))
  const reference = referenceBuffer ? toStereo(await resampleBuffer(referenceBuffer, sampleRate)) : null

  onProgress(reference ? 'multi-chunk 时间对齐' : '跳过参考对齐', 48)
  const alignment = reference
    ? buildAlignment(reference, accompaniment, sampleRate, chunkSeconds, maxShiftMs)
    : { anchors: [], averageOffsetMs: 0, maxOffsetMs: 0, averageScore: 0 }

  const outputLength = Math.max(
    vocal.length,
    reference?.length || 0,
    accompaniment.length
  )
  const alignedAccompaniment = reference
    ? alignStereo(accompaniment, outputLength, sampleRate, alignment.anchors)
    : padStereo(accompaniment, outputLength)

  onProgress('per-channel RMS 归一化', 60)
  const rmsResult = normalizeRms(
    alignedAccompaniment,
    reference ? padStereo(reference, outputLength) : null,
    targetRmsDb
  )

  onProgress('M/S 声场宽度校正', 70)
  const widthResult = correctWidth(
    rmsResult.stereo,
    reference ? padStereo(reference, outputLength) : null,
    widthScale
  )

  onProgress('合成人声与新伴奏', 80)
  const mixed = mixStereo(
    padStereo(vocal, outputLength),
    widthResult.stereo,
    dbToGain(vocalGainDb),
    dbToGain(accompanimentGainDb)
  )

  onProgress('峰值保护', 88)
  const peakResult = trimToPeak(mixed, dbToGain(targetPeakDb))
  const metrics = measureStereo(peakResult.stereo)

  onProgress('编码 24-bit WAV', 96)
  const wav = encodeWav24(peakResult.stereo, sampleRate)
  onProgress('完成', 100)

  return {
    blob: new Blob([wav], { type: 'audio/wav' }),
    sampleRate,
    duration: outputLength / sampleRate,
    alignment,
    rms: {
      leftGainDb: gainToDb(rmsResult.leftGain),
      rightGainDb: gainToDb(rmsResult.rightGain),
      mode: reference ? 'reference' : 'target'
    },
    width: {
      scale: widthResult.scale,
      sourceRatio: widthResult.sourceRatio,
      targetRatio: widthResult.targetRatio
    },
    peakTrimDb: gainToDb(peakResult.gain),
    metrics
  }
}

async function decodeFile(file) {
  const AudioContextClass = window.AudioContext || window.webkitAudioContext
  if (!AudioContextClass) {
    throw new Error('当前浏览器不支持 Web Audio 解码')
  }
  const context = new AudioContextClass()
  try {
    const buffer = await file.arrayBuffer()
    return await context.decodeAudioData(buffer.slice(0))
  } catch (error) {
    throw new Error(`${file.name} 解码失败，请确认浏览器支持该格式`)
  } finally {
    context.close().catch(() => {})
  }
}

async function resampleBuffer(buffer, sampleRate) {
  if (Math.round(buffer.sampleRate) === Math.round(sampleRate)) return buffer
  const length = Math.max(1, Math.round(buffer.duration * sampleRate))
  const offline = new OfflineAudioContext(buffer.numberOfChannels, length, sampleRate)
  const source = offline.createBufferSource()
  source.buffer = buffer
  source.connect(offline.destination)
  source.start()
  return await offline.startRendering()
}

function toStereo(buffer) {
  const left = new Float32Array(buffer.length)
  const right = new Float32Array(buffer.length)
  left.set(buffer.getChannelData(0))
  if (buffer.numberOfChannels > 1) right.set(buffer.getChannelData(1))
  else right.set(buffer.getChannelData(0))
  return { left, right, length: buffer.length }
}

function padStereo(stereo, length) {
  if (stereo.length === length) return stereo
  const left = new Float32Array(length)
  const right = new Float32Array(length)
  left.set(stereo.left.subarray(0, length))
  right.set(stereo.right.subarray(0, length))
  return { left, right, length }
}

function buildAlignment(reference, source, sampleRate, chunkSeconds, maxShiftMs) {
  const referenceEnvelope = buildEnvelope(reference, sampleRate)
  const sourceEnvelope = buildEnvelope(source, sampleRate)
  const rate = referenceEnvelope.rate
  const chunk = Math.max(120, Math.round(chunkSeconds * rate))
  const hop = chunk
  const maxShift = Math.max(1, Math.round((maxShiftMs / 1000) * rate))
  const limit = Math.min(referenceEnvelope.data.length, sourceEnvelope.data.length)
  const anchors = []

  for (let start = 0; start + chunk < limit; start += hop) {
    const best = bestOffset(referenceEnvelope.data, sourceEnvelope.data, start, chunk, maxShift)
    anchors.push({
      time: (start + chunk / 2) / rate,
      offset: best.offset / rate,
      offsetMs: (best.offset / rate) * 1000,
      score: best.score
    })
  }

  const smoothed = smoothAnchors(anchors)
  const offsetAbs = smoothed.map((item) => Math.abs(item.offsetMs))
  return {
    anchors: smoothed,
    averageOffsetMs: average(offsetAbs),
    maxOffsetMs: offsetAbs.length ? Math.max(...offsetAbs) : 0,
    averageScore: average(smoothed.map((item) => Math.max(0, item.score)))
  }
}

function buildEnvelope(stereo, sampleRate) {
  const block = Math.max(1, Math.round(sampleRate / 250))
  const length = Math.ceil(stereo.length / block)
  const data = new Float32Array(length)
  for (let i = 0; i < length; i++) {
    const start = i * block
    const end = Math.min(stereo.length, start + block)
    let sum = 0
    for (let j = start; j < end; j++) {
      const mono = (stereo.left[j] + stereo.right[j]) * 0.5
      sum += mono * mono
    }
    data[i] = Math.sqrt(sum / Math.max(1, end - start))
  }
  return { data, rate: sampleRate / block }
}

function bestOffset(reference, source, start, chunk, maxShift) {
  let best = { offset: 0, score: -Infinity }
  for (let offset = -maxShift; offset <= maxShift; offset++) {
    const sourceStart = start + offset
    if (sourceStart < 0 || sourceStart + chunk >= source.length) continue
    const score = correlation(reference, source, start, sourceStart, chunk)
    if (score > best.score) best = { offset, score }
  }
  return best
}

function correlation(a, b, aStart, bStart, length) {
  let sumA = 0
  let sumB = 0
  let sumAA = 0
  let sumBB = 0
  let sumAB = 0
  for (let i = 0; i < length; i++) {
    const av = a[aStart + i]
    const bv = b[bStart + i]
    sumA += av
    sumB += bv
    sumAA += av * av
    sumBB += bv * bv
    sumAB += av * bv
  }
  const meanA = sumA / length
  const meanB = sumB / length
  const cov = sumAB - length * meanA * meanB
  const varA = sumAA - length * meanA * meanA
  const varB = sumBB - length * meanB * meanB
  const denom = Math.sqrt(Math.max(1e-12, varA * varB))
  return cov / denom
}

function smoothAnchors(anchors) {
  if (anchors.length < 3) return anchors
  return anchors.map((item, index) => {
    const offsets = anchors
      .slice(Math.max(0, index - 1), Math.min(anchors.length, index + 2))
      .map((anchor) => anchor.offset)
      .sort((a, b) => a - b)
    const offset = offsets[Math.floor(offsets.length / 2)]
    return { ...item, offset, offsetMs: offset * 1000 }
  })
}

function alignStereo(source, length, sampleRate, anchors) {
  const left = new Float32Array(length)
  const right = new Float32Array(length)
  for (let i = 0; i < length; i++) {
    const offset = offsetAt(anchors, i / sampleRate)
    const position = i + offset * sampleRate
    left[i] = readLinear(source.left, position)
    right[i] = readLinear(source.right, position)
  }
  return { left, right, length }
}

function offsetAt(anchors, time) {
  if (!anchors.length) return 0
  if (time <= anchors[0].time) return anchors[0].offset
  const last = anchors[anchors.length - 1]
  if (time >= last.time) return last.offset
  for (let i = 1; i < anchors.length; i++) {
    const prev = anchors[i - 1]
    const next = anchors[i]
    if (time <= next.time) {
      const ratio = (time - prev.time) / Math.max(1e-6, next.time - prev.time)
      return prev.offset + (next.offset - prev.offset) * ratio
    }
  }
  return 0
}

function readLinear(data, position) {
  if (position < 0 || position >= data.length - 1) return 0
  const index = Math.floor(position)
  const frac = position - index
  return data[index] * (1 - frac) + data[index + 1] * frac
}

function normalizeRms(source, reference, targetRmsDb) {
  const targetLeft = reference ? rms(reference.left) : dbToGain(targetRmsDb)
  const targetRight = reference ? rms(reference.right) : dbToGain(targetRmsDb)
  const leftGain = clamp(targetLeft / Math.max(1e-8, rms(source.left)), 0.25, 4)
  const rightGain = clamp(targetRight / Math.max(1e-8, rms(source.right)), 0.25, 4)
  const left = new Float32Array(source.length)
  const right = new Float32Array(source.length)
  for (let i = 0; i < source.length; i++) {
    left[i] = source.left[i] * leftGain
    right[i] = source.right[i] * rightGain
  }
  return { stereo: { left, right, length: source.length }, leftGain, rightGain }
}

function correctWidth(source, reference, fallbackScale) {
  const sourceRatio = sideMidRatio(source)
  const targetRatio = reference ? sideMidRatio(reference) : sourceRatio * fallbackScale
  const scale = clamp(targetRatio / Math.max(1e-8, sourceRatio), 0.55, 1.65)
  const left = new Float32Array(source.length)
  const right = new Float32Array(source.length)
  for (let i = 0; i < source.length; i++) {
    const mid = (source.left[i] + source.right[i]) * 0.5
    const side = (source.left[i] - source.right[i]) * 0.5 * scale
    left[i] = mid + side
    right[i] = mid - side
  }
  return { stereo: { left, right, length: source.length }, scale, sourceRatio, targetRatio }
}

function mixStereo(vocal, accompaniment, vocalGain, accompanimentGain) {
  const length = Math.max(vocal.length, accompaniment.length)
  const left = new Float32Array(length)
  const right = new Float32Array(length)
  for (let i = 0; i < length; i++) {
    left[i] = (vocal.left[i] || 0) * vocalGain + (accompaniment.left[i] || 0) * accompanimentGain
    right[i] = (vocal.right[i] || 0) * vocalGain + (accompaniment.right[i] || 0) * accompanimentGain
  }
  return { left, right, length }
}

function trimToPeak(stereo, targetPeak) {
  let peak = 0
  for (let i = 0; i < stereo.length; i++) {
    peak = Math.max(peak, Math.abs(stereo.left[i]), Math.abs(stereo.right[i]))
  }
  const gain = peak > targetPeak ? targetPeak / peak : 1
  if (gain >= 1) return { stereo, gain }
  const left = new Float32Array(stereo.length)
  const right = new Float32Array(stereo.length)
  for (let i = 0; i < stereo.length; i++) {
    left[i] = stereo.left[i] * gain
    right[i] = stereo.right[i] * gain
  }
  return { stereo: { left, right, length: stereo.length }, gain }
}

function measureStereo(stereo) {
  const leftPeak = peak(stereo.left)
  const rightPeak = peak(stereo.right)
  const leftRms = rms(stereo.left)
  const rightRms = rms(stereo.right)
  return {
    leftPeakDb: gainToDb(leftPeak),
    rightPeakDb: gainToDb(rightPeak),
    leftRmsDb: gainToDb(leftRms),
    rightRmsDb: gainToDb(rightRms),
    leftCrestDb: gainToDb(leftPeak / Math.max(1e-8, leftRms)),
    rightCrestDb: gainToDb(rightPeak / Math.max(1e-8, rightRms))
  }
}

function encodeWav24(stereo, sampleRate) {
  const channels = 2
  const bytesPerSample = 3
  const dataSize = stereo.length * channels * bytesPerSample
  const buffer = new ArrayBuffer(44 + dataSize)
  const view = new DataView(buffer)
  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + dataSize, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, channels, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * channels * bytesPerSample, true)
  view.setUint16(32, channels * bytesPerSample, true)
  view.setUint16(34, 24, true)
  writeString(view, 36, 'data')
  view.setUint32(40, dataSize, true)

  let offset = 44
  for (let i = 0; i < stereo.length; i++) {
    offset = writeSample24(view, offset, stereo.left[i])
    offset = writeSample24(view, offset, stereo.right[i])
  }
  return buffer
}

function writeSample24(view, offset, sample) {
  const clamped = clamp(sample, -1, 1)
  const value = Math.max(-8388608, Math.min(8388607, Math.round(clamped * 8388607)))
  view.setUint8(offset, value & 0xff)
  view.setUint8(offset + 1, (value >> 8) & 0xff)
  view.setUint8(offset + 2, (value >> 16) & 0xff)
  return offset + 3
}

function writeString(view, offset, value) {
  for (let i = 0; i < value.length; i++) {
    view.setUint8(offset + i, value.charCodeAt(i))
  }
}

function rms(data) {
  let sum = 0
  for (let i = 0; i < data.length; i++) sum += data[i] * data[i]
  return Math.sqrt(sum / Math.max(1, data.length))
}

function peak(data) {
  let value = 0
  for (let i = 0; i < data.length; i++) value = Math.max(value, Math.abs(data[i]))
  return value
}

function sideMidRatio(stereo) {
  let mid = 0
  let side = 0
  for (let i = 0; i < stereo.length; i++) {
    const m = (stereo.left[i] + stereo.right[i]) * SQRT_HALF
    const s = (stereo.left[i] - stereo.right[i]) * SQRT_HALF
    mid += m * m
    side += s * s
  }
  return Math.sqrt(side / Math.max(1e-12, mid))
}

function dbToGain(db) {
  return Math.pow(10, db / 20)
}

function gainToDb(gain) {
  return 20 * Math.log10(Math.max(1e-8, gain))
}

function average(values) {
  if (!values.length) return 0
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}
