const DB_NAME = 'learning-resource-upload-workspace'
const DB_VERSION = 1
const STORE_NAME = 'drafts'
const DRAFT_KEY = 'default'

export async function loadUploadWorkspace() {
  if (!canUseIndexedDb()) return null
  const db = await openDb()
  return requestToPromise(db.transaction(STORE_NAME, 'readonly').objectStore(STORE_NAME).get(DRAFT_KEY))
}

export async function saveUploadWorkspace(draft) {
  if (!canUseIndexedDb()) return
  const db = await openDb()
  // IndexedDB uses the structured-clone algorithm, which can't serialise Vue
  // reactive proxies, Sets or Maps. Normalise everything to plain JS first.
  const payload = toPlain({
    ...draft,
    id: DRAFT_KEY,
    savedAt: Date.now()
  })
  await requestToPromise(db.transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME).put(payload))
}

export async function clearUploadWorkspace() {
  if (!canUseIndexedDb()) return
  const db = await openDb()
  await requestToPromise(db.transaction(STORE_NAME, 'readwrite').objectStore(STORE_NAME).delete(DRAFT_KEY))
}

function canUseIndexedDb() {
  return typeof indexedDB !== 'undefined'
}

function openDb() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

function requestToPromise(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

/**
 * Recursively strip reactive proxies, Sets, Maps and other non-clonable wrappers.
 * Preserves File/Blob instances so the workspace can still hold pending uploads.
 */
function toPlain(value) {
  if (value == null) return value
  if (value instanceof Blob) return value // File extends Blob and is clonable
  if (value instanceof Date) return value
  if (value instanceof ArrayBuffer) return value
  if (value instanceof Set) return Array.from(value).map(toPlain)
  if (value instanceof Map) {
    const obj = {}
    value.forEach((v, k) => { obj[String(k)] = toPlain(v) })
    return obj
  }
  if (Array.isArray(value)) return value.map(toPlain)
  if (typeof value === 'object') {
    const out = {}
    for (const key of Object.keys(value)) {
      const v = value[key]
      if (typeof v === 'function') continue
      out[key] = toPlain(v)
    }
    return out
  }
  return value
}
