<template>
  <div class="flow-background" aria-hidden="true">
    <span class="pastel-field"></span>
    <span class="ambient-sheen"></span>
    <canvas ref="canvasRef" class="flow-canvas"></canvas>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref(null)

let ctx
let width = 0
let height = 0
let rafId = 0
let lastTime = 0
let nodes = []
let lanes = []
let documents = []
let pulses = []
let reducedMotion = false
let mediaQuery

const pointer = {
  x: 0,
  y: 0,
  active: false,
  lastMove: 0
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function random(min, max) {
  return min + Math.random() * (max - min)
}

function createNodes() {
  const count = clamp(Math.floor((width * height) / 18000), width < 700 ? 34 : 48, width < 900 ? 58 : 96)
  nodes = Array.from({ length: count }, () => {
    const angle = random(0, Math.PI * 2)
    const speed = random(0.1, 0.32)

    return {
      x: random(0, width),
      y: random(0, height),
      vx: Math.cos(angle) * speed,
      vy: Math.sin(angle) * speed,
      angle,
      drift: random(0.0004, 0.0011),
      phase: random(0, Math.PI * 2)
    }
  })
}

function createLanes() {
  const count = width < 700 ? 5 : 8
  lanes = Array.from({ length: count }, (_, index) => ({
    y: ((index + 0.7) / (count + 0.4)) * height,
    amplitude: random(22, width < 700 ? 42 : 72),
    phase: random(0, Math.PI * 2),
    speed: random(0.18, 0.42),
    density: random(0.0036, 0.0064),
    offset: random(-80, 80)
  }))
}

function createDocuments() {
  const count = clamp(Math.floor(width / 145), width < 700 ? 5 : 8, width < 900 ? 8 : 13)
  const labels = ['DOC', 'PPT', 'PDF', 'IMG', 'TXT']

  documents = Array.from({ length: count }, (_, index) => ({
    laneIndex: index % Math.max(lanes.length, 1),
    progress: random(0, 1),
    speed: random(0.000035, 0.000085),
    size: random(18, 28),
    label: labels[index % labels.length],
    alpha: random(0.46, 0.78)
  }))
}

function resizeCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return

  width = window.innerWidth
  height = window.innerHeight

  const dpr = clamp(window.devicePixelRatio || 1, 1, 2)
  canvas.width = Math.floor(width * dpr)
  canvas.height = Math.floor(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  createNodes()
  createLanes()
  createDocuments()
  drawFrame(performance.now(), 0, true)
}

function lanePoint(lane, x, time) {
  const animatedPhase = lane.phase + time * lane.speed
  const baseWave = Math.sin(x * lane.density + animatedPhase) * lane.amplitude
  const fineWave = Math.sin(x * lane.density * 2.4 + animatedPhase * 0.68) * lane.amplitude * 0.32
  let y = lane.y + lane.offset + baseWave + fineWave

  const pointerAge = performance.now() - pointer.lastMove
  const pointerLive = pointer.active && pointerAge < 1500

  if (pointerLive) {
    const dx = x - pointer.x
    const dy = y - pointer.y
    const distance = Math.hypot(dx, dy)
    const radius = width < 700 ? 170 : 260

    if (distance < radius) {
      y += Math.sin((1 - distance / radius) * Math.PI) * 24
    }
  }

  return { x, y }
}

function updateNodes(delta) {
  const pointerAge = performance.now() - pointer.lastMove
  const pointerLive = pointer.active && pointerAge < 1800

  nodes.forEach((node) => {
    node.phase += node.drift * delta
    node.angle += Math.sin(node.phase) * 0.0008 * delta
    node.x += (node.vx + Math.cos(node.angle) * 0.018) * delta
    node.y += (node.vy + Math.sin(node.angle) * 0.018) * delta

    if (pointerLive) {
      const dx = node.x - pointer.x
      const dy = node.y - pointer.y
      const distance = Math.hypot(dx, dy) || 1
      const radius = width < 700 ? 130 : 180

      if (distance < radius) {
        const force = (1 - distance / radius) * 0.035 * delta
        node.x += (dx / distance) * force
        node.y += (dy / distance) * force
      }
    }

    if (node.x < -24) node.x = width + 24
    if (node.x > width + 24) node.x = -24
    if (node.y < -24) node.y = height + 24
    if (node.y > height + 24) node.y = -24
  })
}

function updatePulses(delta) {
  pulses = pulses
    .map((pulse) => ({ ...pulse, age: pulse.age + delta }))
    .filter((pulse) => pulse.age < pulse.life)
}

function updateDocuments(delta) {
  documents.forEach((document) => {
    document.progress += document.speed * delta
    if (document.progress > 1.08) {
      document.progress = -0.08
      document.laneIndex = Math.floor(random(0, Math.max(lanes.length, 1)))
    }
  })
}

function drawBackdropWash(time) {
  const gradient = ctx.createLinearGradient(0, 0, width, height)
  gradient.addColorStop(0, 'rgba(53, 118, 255, 0.08)')
  gradient.addColorStop(0.45 + Math.sin(time * 0.22) * 0.08, 'rgba(190, 94, 255, 0.055)')
  gradient.addColorStop(1, 'rgba(33, 213, 185, 0.045)')

  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, width, height)
}

function drawLanes(time) {
  lanes.forEach((lane, index) => {
    const path = new Path2D()

    for (let x = -140; x <= width + 140; x += 28) {
      const point = lanePoint(lane, x, time)

      if (x === -140) {
        path.moveTo(point.x, point.y)
      } else {
        path.lineTo(point.x, point.y)
      }
    }

    const primary = index % 4 === 0
      ? 'rgba(196, 72, 255, 0.24)'
      : index % 4 === 1
        ? 'rgba(53, 118, 255, 0.23)'
        : index % 4 === 2
          ? 'rgba(255, 92, 180, 0.18)'
          : 'rgba(33, 213, 185, 0.2)'
    const secondary = index % 2 === 0 ? 'rgba(53, 118, 255, 0.08)' : 'rgba(196, 72, 255, 0.075)'

    ctx.save()
    ctx.setLineDash(index % 2 === 0 ? [44, 18] : [18, 16])
    ctx.lineDashOffset = reducedMotion ? 0 : -time * (44 + index * 5)
    ctx.lineWidth = index % 2 === 0 ? 1.8 : 1.2
    ctx.strokeStyle = primary
    ctx.stroke(path)

    ctx.setLineDash([])
    ctx.lineWidth = 0.7
    ctx.strokeStyle = secondary
    ctx.translate(0, 10)
    ctx.stroke(path)
    ctx.restore()
  })
}

function drawNetwork(time) {
  const threshold = width < 700 ? 105 : 145
  const pointerAge = performance.now() - pointer.lastMove
  const pointerLive = pointer.active && pointerAge < 1600

  for (let i = 0; i < nodes.length; i += 1) {
    const current = nodes[i]

    for (let j = i + 1; j < nodes.length; j += 1) {
      const next = nodes[j]
      const dx = current.x - next.x
      const dy = current.y - next.y
      const distance = Math.hypot(dx, dy)

      if (distance > threshold) continue

      const midX = (current.x + next.x) / 2
      const midY = (current.y + next.y) / 2
      let alpha = (1 - distance / threshold) * 0.19

      if (pointerLive) {
        const pointerDistance = Math.hypot(midX - pointer.x, midY - pointer.y)
        const radius = width < 700 ? 150 : 220
        if (pointerDistance < radius) {
          alpha += (1 - pointerDistance / radius) * 0.18
        }
      }

      ctx.beginPath()
      ctx.moveTo(current.x, current.y)
      ctx.lineTo(next.x, next.y)
      ctx.lineWidth = 1
      ctx.strokeStyle = i % 2 === 0 ? `rgba(53, 118, 255, ${alpha})` : `rgba(196, 72, 255, ${alpha})`
      ctx.stroke()
    }

    const tick = 5 + Math.sin(time * 1.8 + current.phase) * 1.2
    ctx.beginPath()
    ctx.moveTo(current.x - Math.cos(current.angle) * tick, current.y - Math.sin(current.angle) * tick)
    ctx.lineTo(current.x + Math.cos(current.angle) * tick, current.y + Math.sin(current.angle) * tick)
    ctx.lineWidth = 1.2
    ctx.strokeStyle = 'rgba(118, 91, 255, 0.22)'
    ctx.stroke()
  }
}

function drawDocuments(time) {
  documents.forEach((document) => {
    const lane = lanes[document.laneIndex % Math.max(lanes.length, 1)]
    if (!lane) return

    const x = -40 + document.progress * (width + 80)
    const point = lanePoint(lane, x, time)
    const size = document.size

    ctx.save()
    ctx.translate(point.x, point.y)
    ctx.rotate(Math.sin(time * 0.8 + document.progress * 8) * 0.08)
    ctx.globalAlpha = document.alpha
    ctx.fillStyle = 'rgba(255, 255, 255, 0.78)'
    ctx.strokeStyle = 'rgba(118, 91, 255, 0.44)'
    ctx.lineWidth = 1

    ctx.beginPath()
    ctx.roundRect(-size * 0.46, -size * 0.6, size * 0.92, size * 1.12, 4)
    ctx.fill()
    ctx.stroke()

    ctx.beginPath()
    ctx.moveTo(size * 0.18, -size * 0.6)
    ctx.lineTo(size * 0.46, -size * 0.32)
    ctx.lineTo(size * 0.18, -size * 0.32)
    ctx.closePath()
    ctx.fillStyle = 'rgba(196, 72, 255, 0.13)'
    ctx.fill()

    ctx.fillStyle = 'rgba(23, 32, 51, 0.72)'
    ctx.font = '700 8px Inter, Arial, sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(document.label, 0, size * 0.16)
    ctx.restore()
  })
}

function drawPulses() {
  pulses.forEach((pulse) => {
    const progress = pulse.age / pulse.life
    const alpha = (1 - progress) * 0.32
    const spread = 20 + progress * 125
    const length = 16 + progress * 30
    const segments = 10

    ctx.save()
    ctx.lineWidth = 1.2
    ctx.strokeStyle = `rgba(196, 72, 255, ${alpha})`

    for (let index = 0; index < segments; index += 1) {
      const angle = (Math.PI * 2 * index) / segments + progress * 0.45
      const centerX = pulse.x + Math.cos(angle) * spread
      const centerY = pulse.y + Math.sin(angle) * spread
      const tangent = angle + Math.PI / 2

      ctx.beginPath()
      ctx.moveTo(centerX - Math.cos(tangent) * length * 0.5, centerY - Math.sin(tangent) * length * 0.5)
      ctx.lineTo(centerX + Math.cos(tangent) * length * 0.5, centerY + Math.sin(tangent) * length * 0.5)
      ctx.stroke()
    }

    ctx.restore()
  })
}

function drawFrame(now, delta = 16, staticFrame = false) {
  if (!ctx) return

  const time = now * 0.001
  ctx.clearRect(0, 0, width, height)

  if (!staticFrame) {
    updateNodes(delta)
    updateDocuments(delta)
    updatePulses(delta)
  }

  drawBackdropWash(time)
  drawLanes(time)
  drawNetwork(time)
  drawDocuments(time)
  drawPulses()
}

function animate(now) {
  const delta = lastTime ? clamp(now - lastTime, 8, 32) : 16
  lastTime = now

  drawFrame(now, delta)

  if (!reducedMotion) {
    rafId = window.requestAnimationFrame(animate)
  }
}

function startAnimation() {
  window.cancelAnimationFrame(rafId)
  lastTime = 0

  if (reducedMotion) {
    drawFrame(performance.now(), 0, true)
    return
  }

  rafId = window.requestAnimationFrame(animate)
}

function handlePointerMove(event) {
  pointer.x = event.clientX
  pointer.y = event.clientY
  pointer.active = true
  pointer.lastMove = performance.now()
}

function handlePointerLeave() {
  pointer.active = false
}

function handlePointerDown(event) {
  pulses.push({
    x: event.clientX,
    y: event.clientY,
    age: 0,
    life: 760
  })

  if (pulses.length > 5) {
    pulses.shift()
  }

  if (reducedMotion) {
    drawFrame(performance.now(), 0, true)
  }
}

function handleReducedMotionChange(event) {
  reducedMotion = event.matches
  startAnimation()
}

onMounted(() => {
  mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion = mediaQuery.matches

  resizeCanvas()
  startAnimation()

  window.addEventListener('resize', resizeCanvas)
  window.addEventListener('pointermove', handlePointerMove, { passive: true })
  window.addEventListener('pointerleave', handlePointerLeave)
  window.addEventListener('pointerdown', handlePointerDown, { passive: true })
  mediaQuery.addEventListener('change', handleReducedMotionChange)
})

onBeforeUnmount(() => {
  window.cancelAnimationFrame(rafId)
  window.removeEventListener('resize', resizeCanvas)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerleave', handlePointerLeave)
  window.removeEventListener('pointerdown', handlePointerDown)

  if (mediaQuery) {
    mediaQuery.removeEventListener('change', handleReducedMotionChange)
  }
})
</script>

<style scoped>
.flow-background {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background:
    linear-gradient(135deg, #f7f8ff 0%, #f6fbfb 48%, #fbf7ff 100%);
  pointer-events: none;
}

.flow-background::after {
  position: absolute;
  inset: 0;
  z-index: 2;
  content: "";
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0.08) 48%, rgba(255, 255, 255, 0.5)),
    linear-gradient(112deg, rgba(255, 255, 255, 0.52), transparent 42%, rgba(255, 255, 255, 0.26) 76%);
}

.pastel-field {
  position: absolute;
  inset: -8%;
  z-index: 0;
  background:
    linear-gradient(115deg, rgba(255, 209, 220, 0.66) 0%, transparent 38%),
    linear-gradient(235deg, rgba(229, 212, 255, 0.7) 0%, transparent 42%),
    linear-gradient(35deg, rgba(209, 255, 246, 0.62) 0%, transparent 44%),
    linear-gradient(315deg, rgba(255, 229, 217, 0.54) 0%, transparent 46%),
    conic-gradient(from 170deg at 52% 46%, rgba(99, 102, 241, 0.14), rgba(20, 184, 166, 0.12), rgba(255, 255, 255, 0.36), rgba(229, 212, 255, 0.18), rgba(99, 102, 241, 0.14));
  filter: blur(52px);
  opacity: 0.8;
  transform: translateZ(0);
}

.ambient-sheen {
  position: absolute;
  z-index: 1;
  inset: -18% -10%;
  background:
    linear-gradient(100deg, transparent 8%, rgba(99, 102, 241, 0.14) 34%, transparent 62%),
    linear-gradient(18deg, transparent 20%, rgba(20, 184, 166, 0.12) 48%, transparent 76%);
  filter: blur(70px);
  opacity: 0.7;
  transform: translateZ(0);
  animation: backgroundSheenFloat 16s ease-in-out infinite;
}

.flow-canvas {
  position: absolute;
  inset: 0;
  z-index: 3;
  width: 100vw;
  height: 100vh;
  opacity: 0.34;
  mix-blend-mode: multiply;
}

@keyframes backgroundSheenFloat {
  0%,
  100% {
    transform: translate3d(-1.5%, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(1.5%, -1%, 0) scale(1.04);
  }
}

@media (max-width: 640px) {
  .pastel-field {
    inset: -18%;
    filter: blur(44px);
    opacity: 0.82;
  }

  .ambient-sheen {
    inset: -24% -22%;
    filter: blur(56px);
  }

  .flow-canvas {
    opacity: 0.24;
  }
}
</style>
