// Pure client-side decoders for legacy Office binary formats (.doc / .ppt / .xls)
// plus the modern .xlsx spreadsheet. We rely on SheetJS's CFB utilities to read
// the OLE2 compound document streams; we do NOT run any backend conversion.
//
// Supported today:
//   .xlsx / .xls / .csv   -> full table rendering via SheetJS
//   .doc                  -> plain text via FIB + piece table walk (Word 97+).
//                            When parsing fails we show a friendly "请下载" hint
//                            instead of dumping garbage bytes to the user.
//   .ppt                  -> per-slide text atoms (UTF-16 / byte atoms)
//
// The goal is "either clean text or nothing" — never garbled output.

import * as XLSX from 'xlsx'

const CFB = XLSX.CFB

export function legacyKind(name = '') {
  const lower = String(name).toLowerCase()
  if (/\.(xlsx|xlsm|xlsb|xls|csv|ods)$/.test(lower)) return 'spreadsheet'
  if (/\.doc$/.test(lower)) return 'doc'
  if (/\.ppt$/.test(lower)) return 'ppt'
  return null
}

// ---------- spreadsheet -----------------------------------------------------

export function renderSpreadsheet(arrayBuffer, fileName = '') {
  const workbook = XLSX.read(arrayBuffer, { type: 'array', cellStyles: true, cellDates: true })
  const sheets = workbook.SheetNames.map((name) => {
    const sheet = workbook.Sheets[name]
    const html = XLSX.utils.sheet_to_html(sheet, { id: slug(name) })
    return { name, html }
  })
  const tabs = sheets.map((sheet) =>
    `<a href="#${slug(sheet.name)}">${escapeHtml(sheet.name)}</a>`
  ).join('')
  const body = sheets.map((sheet) =>
    `<section class="sheet"><h3 id="${slug(sheet.name)}">${escapeHtml(sheet.name)}</h3>${sheet.html}</section>`
  ).join('')
  return wrapHtml(escapeHtml(fileName || '工作簿'), `
    <style>
      ${sharedStyle()}
      .tabs{position:sticky;top:0;background:#f5f7fb;padding:10px 0;border-bottom:1px solid #dde5ef;margin-bottom:12px;z-index:1;}
      .tabs a{display:inline-block;margin-right:6px;padding:5px 12px;border-radius:6px;background:#eef4f4;color:#123f4a;font-weight:600;text-decoration:none;font-size:13px;}
      .tabs a:hover{background:#1c7c7d;color:#fff;}
      .sheet{margin-bottom:28px;}
      .sheet h3{margin:0 0 10px;color:#1c7c7d;}
      table{border-collapse:collapse;width:100%;}
      td,th{border:1px solid #dde5ef;padding:6px 10px;font-size:13px;vertical-align:top;}
      thead td,thead th{background:#eef6f6;font-weight:700;}
      tr:nth-child(even) td{background:#fafbfe;}
    </style>
    <nav class="tabs">${tabs}</nav>
    ${body}
  `)
}

// ---------- .doc ------------------------------------------------------------

/**
 * Walk the FIB (File Information Block) in the WordDocument stream to locate
 * the CLX piece table, then collect every text piece. Each piece is stored
 * either as UTF-16LE or as a compressed 8-bit representation (Windows-1252 /
 * GBK depending on locale). We decode with the platform's TextDecoder so
 * Chinese content survives intact.
 *
 * If any step fails we return {ok:false} so the caller can render a clear
 * "please download" card instead of printing binary junk.
 */
export function renderDoc(arrayBuffer, fileName = '') {
  const parsed = parseDocText(arrayBuffer)
  if (!parsed.ok) {
    return unsupportedDoc(fileName, parsed.reason)
  }
  const paragraphs = splitParagraphs(parsed.text)
  const filtered = paragraphs.filter((line) => isLikelyReadable(line))
  if (!filtered.length) {
    return unsupportedDoc(fileName, '未提取到可读文本')
  }
  const body = filtered.map((line) => `<p>${escapeHtml(line)}</p>`).join('')
  return wrapHtml(escapeHtml(fileName || 'DOC 预览'), `
    <style>
      ${sharedStyle()}
      article{max-width:820px;margin:0 auto;padding:32px 40px;background:#fff;border-radius:8px;box-shadow:0 8px 22px rgba(26,39,68,0.08);}
      article h1{margin:0 0 18px;font-size:22px;color:#123f4a;}
      article p{line-height:1.9;margin:0 0 10px;text-indent:2em;color:#1f2a3a;font-size:15px;}
      .notice{max-width:820px;margin:0 auto 16px;padding:12px 16px;border-left:4px solid #f0a847;background:#fff8ea;color:#8a5d25;border-radius:6px;font-size:13px;}
    </style>
    <div class="notice">老版 .doc 在浏览器端仅提取了文字内容，表格 / 图片 / 样式已丢失；需要完整排版请下载后用 Office/WPS 打开。</div>
    <article>
      <h1>${escapeHtml(fileName || 'DOC 预览')}</h1>
      ${body}
    </article>
  `)
}

function parseDocText(arrayBuffer) {
  try {
    const container = CFB.read(new Uint8Array(arrayBuffer), { type: 'array' })
    const wd = findStream(container, ['WordDocument'])
    if (!wd) return { ok: false, reason: '找不到 WordDocument 流' }
    const wdBytes = toUint8(wd.content)
    if (wdBytes.length < 0x0200) return { ok: false, reason: 'WordDocument 流过短' }
    // FIB magic — any of these signal a Word 97+/95 file we can attempt.
    const wIdent = readU16(wdBytes, 0)
    if (wIdent !== 0xA5EC && wIdent !== 0xA5DC && wIdent !== 0xA5DB && wIdent !== 0xA699) {
      return { ok: false, reason: '不是有效的 Word 文档签名' }
    }
    const flags = readU16(wdBytes, 0x000A)
    const fWhichTblStm = (flags & 0x0200) !== 0
    const tableName = fWhichTblStm ? '1Table' : '0Table'
    const tableEntry = findStream(container, [tableName])
      || findStream(container, ['1Table', '0Table'])
    if (!tableEntry) return { ok: false, reason: '找不到 Table 流' }
    const tableBytes = toUint8(tableEntry.content)
    const fcClx = readU32(wdBytes, 0x01A2)
    const lcbClx = readU32(wdBytes, 0x01A6)
    if (!lcbClx || fcClx + lcbClx > tableBytes.length) {
      return { ok: false, reason: '分片表越界' }
    }
    const clx = tableBytes.subarray(fcClx, fcClx + lcbClx)
    const pcdt = locatePcdt(clx)
    if (!pcdt) return { ok: false, reason: '未定位到 Pcdt' }

    // PlcPcd: (n+1) CPs (4 bytes each) followed by n PCDs (8 bytes each)
    // Total length = 4(n+1) + 8n = 12n + 4  =>  n = (len - 4) / 12
    const n = Math.floor((pcdt.length - 4) / 12)
    if (n <= 0) return { ok: false, reason: '没有文本分片' }
    const cps = []
    for (let i = 0; i <= n; i++) cps.push(readU32(pcdt, i * 4))
    const pcdOffset = (n + 1) * 4

    const utf16Decoder = new TextDecoder('utf-16le', { fatal: false })
    const ansiDecoder = pickAnsiDecoder()

    let text = ''
    for (let i = 0; i < n; i++) {
      const cpStart = cps[i]
      const cpEnd = cps[i + 1]
      const charCount = cpEnd - cpStart
      if (charCount <= 0) continue
      const base = pcdOffset + i * 8
      const fc = readU32(pcdt, base + 2)
      const fCompressed = (fc & 0x40000000) !== 0
      const realFc = fc & 0xBFFFFFFF
      if (fCompressed) {
        const offset = realFc / 2
        const end = offset + charCount
        if (end > wdBytes.length) continue
        text += ansiDecoder.decode(wdBytes.subarray(offset, end))
      } else {
        const end = realFc + charCount * 2
        if (end > wdBytes.length) continue
        text += utf16Decoder.decode(wdBytes.subarray(realFc, end))
      }
    }
    return { ok: true, text }
  } catch (error) {
    return { ok: false, reason: error.message || '解析异常' }
  }
}

function locatePcdt(clx) {
  let p = 0
  while (p < clx.length) {
    const clxt = clx[p]
    if (clxt === 0x01) {
      // Prc (property run): grf_prl size is a signed 16-bit at +1
      if (p + 3 > clx.length) break
      const size = readI16(clx, p + 1)
      p += 3 + Math.max(0, size)
    } else if (clxt === 0x02) {
      if (p + 5 > clx.length) break
      const lcb = readU32(clx, p + 1)
      const start = p + 5
      const end = Math.min(start + lcb, clx.length)
      return clx.subarray(start, end)
    } else {
      break
    }
  }
  return null
}

function pickAnsiDecoder() {
  // TextDecoder('gbk') is widely available in modern browsers and works for
  // Chinese legacy .doc files. Fall back to Latin-1 if the runtime lacks it.
  try {
    return new TextDecoder('gbk', { fatal: false })
  } catch (_) {
    return new TextDecoder('windows-1252', { fatal: false })
  }
}

function unsupportedDoc(fileName, reason) {
  return wrapHtml(escapeHtml(fileName || 'DOC 预览'), `
    <style>
      ${sharedStyle()}
      .box{max-width:640px;margin:64px auto;padding:32px;background:#fff;border-radius:10px;box-shadow:0 8px 22px rgba(26,39,68,0.08);text-align:center;color:#1f2a3a;}
      .box h1{margin:0 0 12px;color:#123f4a;}
      .box p{color:#667085;line-height:1.75;margin:6px 0;}
      .box small{display:block;margin-top:12px;color:#8a8f99;font-size:12px;}
    </style>
    <div class="box">
      <h1>无法在线预览此 .doc</h1>
      <p>浏览器端解析失败，排版或内部结构过于复杂。</p>
      <p>建议下载后用 Word / WPS 打开。</p>
      <small>原因：${escapeHtml(reason || '未知')}</small>
    </div>
  `)
}

// ---------- .ppt ------------------------------------------------------------

export function renderPpt(arrayBuffer, fileName = '') {
  const slides = parsePptSlides(arrayBuffer)
  if (!slides.length) {
    return wrapHtml(escapeHtml(fileName || 'PPT 预览'), `
      <style>
        ${sharedStyle()}
        .box{max-width:640px;margin:64px auto;padding:32px;background:#fff;border-radius:10px;box-shadow:0 8px 22px rgba(26,39,68,0.08);text-align:center;color:#1f2a3a;}
        .box h1{margin:0 0 12px;color:#123f4a;}
        .box p{color:#667085;line-height:1.75;margin:6px 0;}
      </style>
      <div class="box">
        <h1>无法在线预览此 .ppt</h1>
        <p>浏览器端未能从中提取到可读幻灯片文本。</p>
        <p>建议下载后用 PowerPoint / WPS 打开。</p>
      </div>
    `)
  }
  const body = slides.map((slide, index) => {
    const title = slide.title || `第 ${index + 1} 页`
    const lines = slide.lines.map((line) => `<p>${escapeHtml(line)}</p>`).join('')
    return `<section class="slide"><h2>${escapeHtml(title)}</h2>${lines}</section>`
  }).join('')
  return wrapHtml(escapeHtml(fileName || 'PPT 预览'), `
    <style>
      ${sharedStyle()}
      .notice{max-width:820px;margin:0 auto 16px;padding:12px 16px;border-left:4px solid #f0a847;background:#fff8ea;color:#8a5d25;border-radius:6px;font-size:13px;}
      .slide{max-width:820px;margin:0 auto 18px;padding:22px 28px;background:#fff;border-radius:8px;box-shadow:0 8px 22px rgba(26,39,68,0.06);}
      .slide h2{margin:0 0 12px;color:#1c7c7d;border-bottom:1px dashed #cad6e2;padding-bottom:8px;}
      .slide p{margin:0 0 8px;line-height:1.75;color:#1f2a3a;}
    </style>
    <div class="notice">老版 .ppt 仅提取了文字内容，图片 / 排版 / 动画已丢失；建议下载后用 Office / WPS 打开。</div>
    ${body}
  `)
}

function parsePptSlides(arrayBuffer) {
  try {
    const container = CFB.read(new Uint8Array(arrayBuffer), { type: 'array' })
    const stream = findStream(container, ['PowerPoint Document'])
    if (!stream) return []
    return decodePptSlides(toUint8(stream.content))
  } catch (_) {
    return []
  }
}

/**
 * Walk the PowerPoint Document stream as a chain of records. Records of
 * recType 0x0FA0 (TextCharsAtom) carry UTF-16 text; 0x0FA8 (TextBytesAtom)
 * carry 8-bit text. Slide boundaries are announced by 0x03EE records.
 * Notes are skipped so speaker notes do not mix into slide bodies.
 */
function decodePptSlides(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  const total = view.byteLength
  const slides = []
  let current = null
  let inNotes = false
  const utf16Decoder = new TextDecoder('utf-16le', { fatal: false })
  const ansiDecoder = pickAnsiDecoder()

  const pushSlide = () => {
    if (current && current.lines.length) slides.push(current)
  }

  let offset = 0
  while (offset + 8 <= total) {
    const verInst = view.getUint16(offset, true)
    const recType = view.getUint16(offset + 2, true)
    const recLen = view.getUint32(offset + 4, true)
    const bodyStart = offset + 8
    const bodyEnd = bodyStart + recLen
    if (bodyEnd > total) break
    const isContainer = (verInst & 0x000f) === 0x000f

    if (recType === 0x03F0) inNotes = true
    if (recType === 0x03EE) {
      pushSlide()
      current = { title: '', lines: [] }
      inNotes = false
    }

    if (!inNotes) {
      if (recType === 0x0FA0) {
        const text = utf16Decoder.decode(bytes.subarray(bodyStart, bodyEnd))
        appendSlideLines(current, splitParagraphs(text))
      } else if (recType === 0x0FA8) {
        const text = ansiDecoder.decode(bytes.subarray(bodyStart, bodyEnd))
        appendSlideLines(current, splitParagraphs(text))
      }
    }

    offset = isContainer ? bodyStart : bodyEnd
  }
  pushSlide()
  return slides.filter((slide) => slide.lines.some(isLikelyReadable))
}

function appendSlideLines(slide, lines) {
  if (!slide) return
  for (const line of lines) {
    if (!isLikelyReadable(line)) continue
    if (!slide.title) slide.title = line
    slide.lines.push(line)
  }
}

// ---------- shared helpers --------------------------------------------------

function findStream(container, candidates) {
  if (!container || !Array.isArray(container.FullPaths)) return null
  for (const candidate of candidates) {
    const index = container.FullPaths.findIndex((path) => path.endsWith('/' + candidate))
    if (index >= 0) {
      const entry = container.FileIndex[index]
      if (entry && entry.content && entry.content.length) return entry
    }
  }
  return null
}

function toUint8(content) {
  if (content instanceof Uint8Array) return content
  if (ArrayBuffer.isView(content)) return new Uint8Array(content.buffer, content.byteOffset, content.byteLength)
  if (content && content.buffer) return new Uint8Array(content.buffer)
  return new Uint8Array(content)
}

function readU16(bytes, offset) {
  return bytes[offset] | (bytes[offset + 1] << 8)
}

function readI16(bytes, offset) {
  const value = readU16(bytes, offset)
  return value > 0x7fff ? value - 0x10000 : value
}

function readU32(bytes, offset) {
  return (bytes[offset] |
    (bytes[offset + 1] << 8) |
    (bytes[offset + 2] << 16) |
    (bytes[offset + 3] << 24)) >>> 0
}

function splitParagraphs(text) {
  return String(text || '')
    // Word / PowerPoint paragraph markers
    .replace(/\u0007/g, '') // cell marker
    .split(/[\r\n\v\u000b\u2028\u2029]+/)
    .map((line) => stripControl(line).trim())
    .filter(Boolean)
}

function stripControl(line) {
  // Remove anything below 0x20 except tab, plus private-use / specials that
  // typically appear as placeholder fields in Word.
  let out = ''
  for (let i = 0; i < line.length; i++) {
    const code = line.charCodeAt(i)
    if (code === 0x09) { out += '\t'; continue }
    if (code < 0x20) continue
    if (code === 0xFFFC) continue // object replacement
    if (code >= 0xE000 && code <= 0xF8FF) continue // private use area
    if (code === 0xFEFF) continue // BOM / zero-width
    out += String.fromCharCode(code)
  }
  return out
}

/**
 * Reject lines that look like binary noise rather than real prose. The heuristic:
 *   - must have >= 3 recognisable characters
 *   - recognisable == ASCII letters/digits/punctuation or CJK ideographs or
 *     full-width punctuation. Replacement glyphs and random PUA code points
 *     count as unreadable.
 *   - readable ratio must be >= 50%.
 */
function isLikelyReadable(line) {
  if (!line) return false
  const trimmed = line.trim()
  if (trimmed.length < 2) return false
  let readable = 0
  for (let i = 0; i < trimmed.length; i++) {
    const code = trimmed.charCodeAt(i)
    if (code === 0xFFFD) continue
    const isAsciiPrintable = code >= 0x20 && code <= 0x7E
    const isLatinSupp = code >= 0xA0 && code <= 0x024F
    const isCjk = code >= 0x3000 && code <= 0x9FFF
    const isFullwidth = code >= 0xFF00 && code <= 0xFFEF
    if (isAsciiPrintable || isLatinSupp || isCjk || isFullwidth) readable++
  }
  return readable >= 3 && readable / trimmed.length >= 0.5
}

function wrapHtml(title, inner) {
  return `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title></head><body>${inner}</body></html>`
}

function sharedStyle() {
  return `html,body{margin:0;padding:0;background:#f5f7fb;color:#1f2a3a;font-family:"Microsoft YaHei","PingFang SC",Arial,sans-serif;}
body{padding:20px 24px;}
*{box-sizing:border-box;}`
}

function escapeHtml(raw) {
  return String(raw == null ? '' : raw)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function slug(name) {
  return 'sheet-' + String(name).replace(/[^a-z0-9]+/gi, '-').toLowerCase()
}
