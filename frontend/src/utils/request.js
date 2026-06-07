import axios from 'axios'
import { ElMessage } from 'element-plus'
import store from '../store'
import router from '../router'

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 20000
})

const mojibakePattern = /(?:Ã.|Â.|â.|ä.|å.|è.|é.|ç.|æ.|ï.|ð.|Ð.|Ñ.|þ.|ý.|鏂|瀛|璧|鎼|涓|绠|鍒|鐧|娴|棰|勬|€|�)/
const utf8Decoder = typeof TextDecoder !== 'undefined' ? new TextDecoder('utf-8', { fatal: true }) : null

function repairMojibake(value) {
  if (typeof value !== 'string' || !mojibakePattern.test(value)) return value
  if (!utf8Decoder) return value

  const latin1 = repairUtf8AsLatin1(value)
  if (looksBetter(latin1, value)) return latin1

  return value
}

function repairUtf8AsLatin1(value) {
  const bytes = []
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index)
    if (code > 255) return value
    bytes.push(code)
  }
  try {
    return utf8Decoder.decode(new Uint8Array(bytes))
  } catch {
    return value
  }
}

function looksBetter(candidate, original) {
  if (!candidate || candidate === original) return false
  return mojibakeScore(candidate) + 2 < mojibakeScore(original) || chineseScore(candidate) > chineseScore(original)
}

function mojibakeScore(value) {
  return (value.match(mojibakePattern) || []).length + (value.match(/[\u0080-\u009f�]/g) || []).length
}

function chineseScore(value) {
  return (value.match(/[\u4e00-\u9fff]/g) || []).length
}

function normalizeResponseData(value, seen = new WeakSet()) {
  if (typeof value === 'string') return repairMojibake(value)
  if (!value || typeof value !== 'object') return value
  if (seen.has(value)) return value
  seen.add(value)

  if (Array.isArray(value)) {
    return value.map((item) => normalizeResponseData(item, seen))
  }

  Object.keys(value).forEach((key) => {
    value[key] = normalizeResponseData(value[key], seen)
  })
  return value
}

service.interceptors.request.use((config) => {
  if (store.state.token) {
    config.headers.Authorization = `Bearer ${store.state.token}`
  }
  return config
})

function redirectToLogin(message) {
  store.commit('logout')
  const current = router.currentRoute.value.fullPath
  if (!router.currentRoute.value.path.startsWith('/login')) {
    router.push(`/login?redirect=${encodeURIComponent(current)}`)
  }
  ElMessage.error(message || 'Please login again')
}

service.interceptors.response.use(
  (response) => {
    const data = normalizeResponseData(response.data)
    if (data && typeof data.code !== 'undefined') {
      if (data.code === 200) return data.data
      if (data.code === 401 || data.code === 403) {
        redirectToLogin(data.message)
      } else {
        ElMessage.error(data.message || 'Request failed')
      }
      return Promise.reject(new Error(data.message || 'Request failed'))
    }
    return data
  },
  (error) => {
    const status = error.response?.status
    const data = error.response?.data
    if (!error.response) {
      const message = '后端服务连接失败，请确认 8080 端口的 Spring Boot 已启动'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    if (status === 401 || status === 403 || data?.code === 401 || data?.code === 403) {
      redirectToLogin(data?.message)
      return Promise.reject(error)
    }
    ElMessage.error(data?.message || error.message || 'Network error')
    return Promise.reject(error)
  }
)

export default service
