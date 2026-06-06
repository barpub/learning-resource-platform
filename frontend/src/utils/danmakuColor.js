// Danmaku color format:
//   "#rrggbb" | "rgba(...)"         -> solid color
//   "grad:c1,c2[,c3...]"            -> static gradient (across-character)
//   "anim:c1,c2[,c3...]"            -> animated color transition (whole text
//                                      glides smoothly through each color and
//                                      loops seamlessly back to the first)

export const SOLID_PRESETS = [
  '#ffffff',
  '#000000',
  '#ff2d4d',
  '#ff9800',
  '#ffd400',
  '#7bd23c',
  '#1ec7ad',
  '#2f80ed',
  '#5b4ac8',
  '#d946ef',
  '#ff66aa',
  '#8a5d25'
]

export const GRADIENT_PRESETS = [
  { label: '火焰', value: 'grad:#ff512f,#f09819' },
  { label: '日落', value: 'grad:#ff8a00,#e52e71' },
  { label: '星空', value: 'grad:#3a1c71,#d76d77,#ffaf7b' },
  { label: '深海', value: 'grad:#00c6ff,#0072ff' },
  { label: '薄荷', value: 'grad:#11998e,#38ef7d' },
  { label: '水晶', value: 'grad:#a8edea,#fed6e3' },
  { label: '紫罗兰', value: 'grad:#c471f5,#fa71cd' },
  { label: '樱桃', value: 'grad:#ff4e50,#f9d423' }
]

export const ANIMATED_PRESETS = [
  { label: '彩虹', value: 'anim:#ff004e,#ff9500,#ffe600,#00d97a,#00b3ff,#9d00ff,#ff004e' },
  { label: '霓虹', value: 'anim:#ff00e5,#00ffe5,#00ff5e,#fff500,#ff00e5' },
  { label: '极光', value: 'anim:#00f5a0,#00d9f5,#7a5cff,#00f5a0' },
  { label: '熔岩', value: 'anim:#ff1a1a,#ffb300,#ff1a1a' }
]

const HEX_REGEX = /^#[0-9a-fA-F]{3,8}$/
const RGB_REGEX = /^rgba?\([0-9.,%\s]+\)$/

export function parseDanmakuColor(spec) {
  if (typeof spec !== 'string' || !spec.trim()) {
    return { kind: 'solid', value: '#ffffff' }
  }
  const raw = spec.trim()
  if (raw.startsWith('grad:') || raw.startsWith('anim:')) {
    const animated = raw.startsWith('anim:')
    const stops = raw.slice(5).split(',').map((s) => s.trim()).filter((s) => HEX_REGEX.test(s))
    if (stops.length < 2) return { kind: 'solid', value: '#ffffff' }
    return { kind: animated ? 'animated' : 'gradient', stops, value: raw }
  }
  if (HEX_REGEX.test(raw) || RGB_REGEX.test(raw)) {
    return { kind: 'solid', value: raw }
  }
  return { kind: 'solid', value: '#ffffff' }
}

// ----- Dynamic keyframes for animated color transitions ---------------------
// The animated danmaku cycles the whole text's color smoothly across the stops
// and back to the first color. Each unique stop list gets its own @keyframes
// definition injected into a singleton <style> tag so every danmaku that
// shares the same spec reuses the same CSS animation (efficient + perfectly
// synchronised).

const installedKeyframes = new Set()
let styleTag = null

function ensureStyleTag() {
  if (styleTag) return styleTag
  if (typeof document === 'undefined') return null
  styleTag = document.createElement('style')
  styleTag.setAttribute('data-danmaku-keyframes', 'true')
  document.head.appendChild(styleTag)
  return styleTag
}

function keyframesName(stops) {
  // Stable short hash so the same spec always yields the same animation name.
  let h = 0
  for (const s of stops) {
    for (let i = 0; i < s.length; i++) {
      h = (h * 31 + s.charCodeAt(i)) | 0
    }
  }
  return `dm${(h >>> 0).toString(36)}`
}

function ensureKeyframes(name, stops) {
  if (installedKeyframes.has(name)) return
  const tag = ensureStyleTag()
  if (!tag) return
  const last = stops.length - 1
  const rules = stops.map((c, i) => {
    const pct = (i / last * 100).toFixed(2)
    return `${pct}% { color: ${c}; }`
  }).join(' ')
  tag.appendChild(document.createTextNode(`@keyframes ${name} { ${rules} } `))
  installedKeyframes.add(name)
}

export function buildColorStyle(spec) {
  const desc = parseDanmakuColor(spec)
  if (desc.kind === 'solid') {
    return { color: desc.value }
  }
  const stops = desc.stops[0] === desc.stops[desc.stops.length - 1]
    ? desc.stops
    : [...desc.stops, desc.stops[0]]

  if (desc.kind === 'gradient') {
    // Static gradient: colors spread across the characters (classic text clip).
    const gradient = `linear-gradient(90deg, ${stops.join(', ')})`
    return {
      color: 'transparent',
      backgroundImage: gradient,
      backgroundSize: '100% 100%',
      backgroundRepeat: 'no-repeat',
      WebkitBackgroundClip: 'text',
      backgroundClip: 'text',
      WebkitTextStroke: '0.4px rgba(0, 0, 0, 0.55)',
      textShadow: 'none'
    }
  }

  // Animated: scroll a self-tiling gradient strip (pattern + pattern) across
  // the text so colors flow left-to-right continuously. The animation duration
  // matches the danmaku's on-screen lifetime so the loop boundary is never
  // visible — the danmaku recycles before the animation would restart.
  const doubled = [...stops, ...stops]
  const gradient = `linear-gradient(90deg, ${doubled.join(', ')})`
  return {
    color: 'transparent',
    backgroundImage: gradient,
    backgroundSize: '200% 100%',
    backgroundRepeat: 'no-repeat',
    WebkitBackgroundClip: 'text',
    backgroundClip: 'text',
    WebkitTextStroke: '0.4px rgba(0, 0, 0, 0.55)',
    textShadow: 'none',
    // SCROLL_DURATION on the player is 8s; PINNED_DURATION is 4s. Use 8s so a
    // single colour cycle covers the whole scroll journey. `both` keeps the
    // last frame displayed after completion so no reset/seam is ever visible.
    animation: 'danmaku-flow 8s linear 1 both'
  }
}
