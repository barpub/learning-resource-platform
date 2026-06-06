import request from '../utils/request'

export const authApi = {
  login(data) {
    return request.post('/api/auth/login', data)
  },
  register(data) {
    return request.post('/api/auth/register', data)
  }
}

export const userApi = {
  profile() {
    return request.get('/api/users/profile')
  },
  updateProfile(data) {
    return request.put('/api/users/profile', data)
  },
  updatePassword(data) {
    return request.put('/api/users/password', data)
  },
  dashboard() {
    return request.get('/api/users/me/dashboard')
  },
  list() {
    return request.get('/api/users')
  },
  updateStatus(id, status) {
    return request.put(`/api/users/${id}/status`, null, { params: { status } })
  },
  remove(id) {
    return request.delete(`/api/users/${id}`)
  }
}

export const categoryApi = {
  list() {
    return request.get('/api/categories')
  },
  create(data) {
    return request.post('/api/categories', data)
  },
  update(id, data) {
    return request.put(`/api/categories/${id}`, data)
  },
  remove(id) {
    return request.delete(`/api/categories/${id}`)
  }
}

export const resourceApi = {
  page(params) {
    return request.get('/api/resources', { params })
  },
  get(id) {
    return request.get(`/api/resources/${id}`)
  },
  upload(data, onUploadProgress) {
    return request.post('/api/resources', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    })
  },
  uploadBatch(data, onUploadProgress) {
    return request.post('/api/resources/batch', data, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    })
  },
  children(id) {
    return request.get(`/api/resources/${id}/children`)
  },
  appendChildren(id, data, onUploadProgress) {
    return request.post(`/api/resources/${id}/children`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress
    })
  },
  update(id, data) {
    return request.put(`/api/resources/${id}`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  updateFilePath(id, params) {
    return request.put(`/api/resources/${id}/file-path`, null, { params })
  },
  updateFolderPath(id, params) {
    return request.put(`/api/resources/${id}/folder-path`, null, { params })
  },
  remove(id) {
    return request.delete(`/api/resources/${id}`)
  },
  downloadUrl(id) {
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/resources/${id}/download`
  },
  previewUrl(id) {
    return `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}/api/resources/${id}/preview`
  },
  previewText(id) {
    return request.get(`/api/resources/${id}/preview`, { responseType: 'text' })
  }
}

export const betaRecommendationApi = {
  recommend(data) {
    return request.post('/api/beta/recommendations', data)
  }
}

export const globalSearchApi = {
  search(params) {
    return request.get('/api/search/global', { params, timeout: 70000 })
  }
}

export const resourceAgentApi = {
  search(params) {
    return request.get('/api/agent/search', { params, timeout: 70000 })
  },
  summarizeLocal(id) {
    return request.get(`/api/agent/resources/${id}/summary`)
  },
  summarizeFolder(id, params) {
    return request.get(`/api/agent/folders/${id}/summary`, { params })
  },
  summarizeRemote(data) {
    return request.post('/api/agent/remote/summary', data)
  }
}

export const danmakuApi = {
  list(resourceId) {
    return request.get(`/api/resources/${resourceId}/danmakus`)
  },
  send(resourceId, data) {
    return request.post(`/api/resources/${resourceId}/danmakus`, data)
  },
  remove(id) {
    return request.delete(`/api/danmakus/${id}`)
  },
  updateConfig(resourceId, data) {
    return request.put(`/api/resources/${resourceId}/danmaku-config`, data)
  }
}

export const commentApi = {
  list(resourceId) {
    return request.get(`/api/resources/${resourceId}/comments`)
  },
  all() {
    return request.get('/api/comments')
  },
  create(data) {
    return request.post('/api/comments', data)
  },
  remove(id) {
    return request.delete(`/api/comments/${id}`)
  }
}

export const favoriteApi = {
  list() {
    return request.get('/api/favorites')
  },
  create(resourceId) {
    return request.post('/api/favorites', { resourceId })
  },
  remove(id) {
    return request.delete(`/api/favorites/${id}`)
  },
  removeByResource(resourceId) {
    return request.delete('/api/favorites', { params: { resourceId } })
  }
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

export const ftpApi = {
  listConnections() {
    return request.get('/api/ftp/connections')
  },
  listDirectory(id, path) {
    return request.get(`/api/ftp/connections/${id}/list`, { params: path ? { path } : {} })
  },
  searchDirectory(id, params) {
    return request.get(`/api/ftp/connections/${id}/search`, { params, timeout: 60000 })
  },
  previewUrl(id, path) {
    const query = new URLSearchParams({ path: path || '' }).toString()
    return `${API_BASE}/api/ftp/connections/${id}/preview?${query}`
  },
  downloadUrl(id, path) {
    const query = new URLSearchParams({ path: path || '' }).toString()
    return `${API_BASE}/api/ftp/connections/${id}/download?${query}`
  },
  previewText(id, path) {
    return request.get(`/api/ftp/connections/${id}/preview`, { params: { path }, responseType: 'text' })
  },
  previewBinary(id, path) {
    return request.get(`/api/ftp/connections/${id}/preview`, { params: { path }, responseType: 'arraybuffer' })
  },
  admin: {
    list() {
      return request.get('/api/ftp/admin/connections')
    },
    create(data) {
      return request.post('/api/ftp/admin/connections', data)
    },
    update(id, data) {
      return request.put(`/api/ftp/admin/connections/${id}`, data)
    },
    remove(id) {
      return request.delete(`/api/ftp/admin/connections/${id}`)
    },
    testNew(data) {
      return request.post('/api/ftp/admin/connections/test', data)
    },
    testExisting(id, data) {
      return request.post(`/api/ftp/admin/connections/${id}/test`, data || {})
    }
  }
}

export const forumApi = {
  page(params) {
    return request.get('/api/forum/posts', { params })
  },
  get(id) {
    return request.get(`/api/forum/posts/${id}`)
  },
  createPost(data) {
    return request.post('/api/forum/posts', data, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  createComment(postId, data) {
    return request.post(`/api/forum/posts/${postId}/comments`, data, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  removePost(id) {
    return request.delete(`/api/forum/posts/${id}`)
  },
  removeComment(id) {
    return request.delete(`/api/forum/comments/${id}`)
  }
}
