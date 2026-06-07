import { computed, onBeforeUnmount, ref, watch } from 'vue'

export function useMediaAudioEnhancer() {
  const vocalEnhance = ref(false)
  const hiResEnhance = ref(false)
  const limiterEnabled = ref(false)
  const peakLevel = ref(0)
  const rmsLevel = ref(0)
  const audioRateLabel = ref('Web Audio ready')

  let audioContext = null
  let mediaElement = null
  let mediaSource = null
  let highPassFilter = null
  let lowShelf = null
  let mudFilter = null
  let vocalBodyFilter = null
  let presenceFilter = null
  let highShelf = null
  let compressor = null
  let limiter = null
  let analyser = null
  let gainNode = null
  let frequencyData = null
  let timeData = null

  const processingLabel = computed(() => {
    const active = []
    if (vocalEnhance.value) active.push('Vocal+')
    if (hiResEnhance.value) active.push('Hi-Res')
    if (limiterEnabled.value) active.push('Peak Smooth')
    return active.length ? active.join(' / ') : 'Original'
  })

  async function ensureAudioGraph(element) {
    if (!element || mediaSource) return Boolean(mediaSource)
    if (typeof window === 'undefined') return false
    const AudioContextClass = window.AudioContext || window.webkitAudioContext
    if (!AudioContextClass) return false

    mediaElement = element
    audioContext = new AudioContextClass()
    mediaSource = audioContext.createMediaElementSource(element)
    highPassFilter = audioContext.createBiquadFilter()
    lowShelf = audioContext.createBiquadFilter()
    mudFilter = audioContext.createBiquadFilter()
    vocalBodyFilter = audioContext.createBiquadFilter()
    presenceFilter = audioContext.createBiquadFilter()
    highShelf = audioContext.createBiquadFilter()
    compressor = audioContext.createDynamicsCompressor()
    limiter = audioContext.createDynamicsCompressor()
    analyser = audioContext.createAnalyser()
    gainNode = audioContext.createGain()

    highPassFilter.type = 'highpass'
    highPassFilter.frequency.value = 70
    highPassFilter.Q.value = 0.72
    lowShelf.type = 'lowshelf'
    lowShelf.frequency.value = 180
    mudFilter.type = 'peaking'
    mudFilter.frequency.value = 320
    mudFilter.Q.value = 1.1
    vocalBodyFilter.type = 'peaking'
    vocalBodyFilter.frequency.value = 1050
    vocalBodyFilter.Q.value = 0.9
    presenceFilter.type = 'peaking'
    presenceFilter.frequency.value = 3100
    presenceFilter.Q.value = 1
    highShelf.type = 'highshelf'
    highShelf.frequency.value = 8200
    analyser.fftSize = 2048
    analyser.smoothingTimeConstant = 0.82
    frequencyData = new Uint8Array(analyser.frequencyBinCount)
    timeData = new Uint8Array(analyser.fftSize)

    mediaSource
      .connect(highPassFilter)
      .connect(lowShelf)
      .connect(mudFilter)
      .connect(vocalBodyFilter)
      .connect(presenceFilter)
      .connect(highShelf)
      .connect(compressor)
      .connect(limiter)
      .connect(analyser)
      .connect(gainNode)
      .connect(audioContext.destination)

    audioRateLabel.value = `${Math.round(audioContext.sampleRate / 100) / 10} kHz engine`
    applyProcessing()
    return true
  }

  async function resumeAudio() {
    if (audioContext?.state === 'suspended') {
      await audioContext.resume()
    }
  }

  function applyProcessing() {
    if (!audioContext || !highPassFilter || !lowShelf || !mudFilter || !vocalBodyFilter || !presenceFilter || !highShelf || !compressor || !limiter || !gainNode) return
    const now = audioContext.currentTime
    const vocalBoost = vocalEnhance.value
    const clarityBoost = hiResEnhance.value

    highPassFilter.frequency.setTargetAtTime(vocalBoost ? 95 : 45, now, 0.02)
    lowShelf.gain.setTargetAtTime(vocalBoost ? -4.2 : clarityBoost ? 1.2 : 0, now, 0.02)
    mudFilter.gain.setTargetAtTime(vocalBoost ? -4.8 : 0, now, 0.02)
    vocalBodyFilter.gain.setTargetAtTime(vocalBoost ? 2.8 : 0, now, 0.02)
    presenceFilter.gain.setTargetAtTime((vocalBoost ? 4.6 : 0) + (clarityBoost ? 1.4 : 0), now, 0.02)
    highShelf.gain.setTargetAtTime(clarityBoost ? (vocalBoost ? 1.4 : 3.2) : vocalBoost ? -1.2 : 0, now, 0.02)

    compressor.threshold.setTargetAtTime(vocalBoost ? -30 : -4, now, 0.02)
    compressor.knee.setTargetAtTime(vocalBoost ? 18 : 4, now, 0.02)
    compressor.ratio.setTargetAtTime(vocalBoost ? 3.2 : 1, now, 0.02)
    compressor.attack.setTargetAtTime(vocalBoost ? 0.004 : 0.003, now, 0.02)
    compressor.release.setTargetAtTime(vocalBoost ? 0.16 : 0.08, now, 0.02)

    limiter.threshold.setTargetAtTime(limiterEnabled.value ? -1 : 0, now, 0.02)
    limiter.knee.setTargetAtTime(limiterEnabled.value ? 1 : 0, now, 0.02)
    limiter.ratio.setTargetAtTime(limiterEnabled.value ? 18 : 1, now, 0.02)
    limiter.attack.setTargetAtTime(0.002, now, 0.02)
    limiter.release.setTargetAtTime(0.08, now, 0.02)

    gainNode.gain.setTargetAtTime(vocalBoost ? (clarityBoost ? 0.9 : 0.94) : clarityBoost ? 0.94 : 1, now, 0.02)
  }

  function updateLevels(data = timeData) {
    if (!data?.length) {
      peakLevel.value = 0
      rmsLevel.value = 0
      return
    }
    let peak = 0
    let sum = 0
    for (let i = 0; i < data.length; i++) {
      const value = Math.abs((data[i] - 128) / 128)
      peak = Math.max(peak, value)
      sum += value * value
    }
    peakLevel.value = Math.round(peak * 100)
    rmsLevel.value = Math.round(Math.sqrt(sum / data.length) * 100)
  }

  function sampleAnalyser() {
    if (!analyser || !frequencyData || !timeData) return null
    analyser.getByteFrequencyData(frequencyData)
    analyser.getByteTimeDomainData(timeData)
    updateLevels(timeData)
    return { frequencyData, timeData }
  }

  function readFrequencyData() {
    return frequencyData
  }

  function readTimeData() {
    return timeData
  }

  function dispose() {
    if (mediaSource) {
      try { mediaSource.disconnect() } catch (error) {}
    }
    if (audioContext) {
      audioContext.close().catch(() => {})
    }
    mediaElement = null
    audioContext = null
    mediaSource = null
    highPassFilter = null
    lowShelf = null
    mudFilter = null
    vocalBodyFilter = null
    presenceFilter = null
    highShelf = null
    compressor = null
    limiter = null
    analyser = null
    gainNode = null
    frequencyData = null
    timeData = null
  }

  watch([vocalEnhance, hiResEnhance, limiterEnabled], applyProcessing)
  onBeforeUnmount(dispose)

  return {
    vocalEnhance,
    hiResEnhance,
    limiterEnabled,
    peakLevel,
    rmsLevel,
    audioRateLabel,
    processingLabel,
    ensureAudioGraph,
    resumeAudio,
    applyProcessing,
    sampleAnalyser,
    readFrequencyData,
    readTimeData,
    updateLevels,
    dispose
  }
}
