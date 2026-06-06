// Centralised badge + preview-kind mapping so local resource cards and remote
// FTP cards share one vocabulary of file types.

const EXT_LABEL = [
  { test: /\.(pdf)$/i, label: 'PDF' },
  { test: /\.(docx)$/i, label: 'DOC' },
  { test: /\.(doc)$/i, label: 'DOC' },
  { test: /\.(pptx|ppt)$/i, label: 'PPT' },
  { test: /\.(xlsx|xls|csv)$/i, label: 'XLS' },
  { test: /\.(png|jpe?g|gif|webp|bmp|svg)$/i, label: 'IMG' },
  { test: /\.(mp4|mov|webm|mkv|avi)$/i, label: 'VID' },
  { test: /\.(mp3|wav|ogg|flac|m4a)$/i, label: 'AUD' },
  { test: /\.(txt|log|md|rtf)$/i, label: 'TXT' },
  { test: /\.(json|xml|yml|yaml|toml)$/i, label: 'DATA' },
  { test: /\.(html?|css|scss|less)$/i, label: 'WEB' },
  { test: /\.(js|mjs|ts|tsx|jsx|vue|py|java|c|cpp|h|cs|go|rs|rb|php|sh|bat|sql)$/i, label: 'CODE' },
  { test: /\.(zip|rar|7z|tar|gz|bz2|xz)$/i, label: 'ZIP' },
  { test: /\.(exe|msi|dmg|apk|ipa|iso)$/i, label: 'APP' }
]

export function fileLabel(name = '', isFolder = false) {
  if (isFolder) return 'DIR'
  const lower = String(name).toLowerCase()
  const hit = EXT_LABEL.find((item) => item.test.test(lower))
  return hit ? hit.label : 'FILE'
}

const PREVIEW_RULES = [
  { test: /\.(png|jpe?g|gif|webp|bmp)$/i, kind: 'image' },
  { test: /\.(pdf)$/i, kind: 'pdf' },
  { test: /\.(docx)$/i, kind: 'docx' },
  { test: /\.(pptx)$/i, kind: 'pptx' },
  { test: /\.(doc|ppt|xls|xlsx|xlsm|xlsb|csv|ods)$/i, kind: 'legacy-office' },
  { test: /\.(mp4|mov|webm)$/i, kind: 'video' },
  { test: /\.(mp3|wav|ogg)$/i, kind: 'audio' },
  { test: /\.(txt|log|md|json|xml|htm|html|yml|yaml|js|ts|java|py|sh|sql)$/i, kind: 'text' }
]

export function detectPreviewKind(file) {
  if (!file || file.directory) return 'unsupported'
  const name = (file.name || file.fileName || '').toLowerCase()
  const type = (file.type || file.fileType || '').toLowerCase()
  if (type.startsWith('image/')) return 'image'
  if (type === 'application/pdf') return 'pdf'
  if (type.startsWith('video/')) return 'video'
  if (type.startsWith('audio/')) return 'audio'
  const hit = PREVIEW_RULES.find((rule) => rule.test.test(name))
  if (hit) return hit.kind
  if (type.startsWith('text/')) return 'text'
  return 'unsupported'
}

export function previewLabel(kind) {
  return {
    image: '图片',
    pdf: 'PDF',
    docx: 'DOCX',
    pptx: 'PPTX',
    'legacy-office': '老版 Office',
    video: '视频',
    audio: '音频',
    text: '文本',
    unsupported: '仅下载'
  }[kind] || '仅下载'
}

export function formatSize(size = 0) {
  const value = Number(size) || 0
  if (value <= 0) return '-'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}
