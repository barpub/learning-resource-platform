export function normalizePath(path = '') {
  return String(path)
    .replace(/\\/g, '/')
    .split('/')
    .map((part) => part.trim())
    .filter(Boolean)
    .join('/')
}

export function buildFileTree(files = [], virtualFolders = []) {
  const root = createFolder('', '')
  virtualFolders.forEach((path) => ensureFolder(root, path))
  files.forEach((file) => {
    const relativePath = normalizePath(file.relativePath || file.name || file.fileName || '')
    const parts = relativePath.split('/').filter(Boolean)
    const name = parts.pop() || file.name || file.fileName || 'file'
    const cursor = ensureFolder(root, parts.join('/'))
    cursor.files.push({
      ...file,
      name,
      relativePath: relativePath || name
    })
    root.fileCount += 1
    root.totalSize += Number(file.size ?? file.fileSize ?? 0)
  })
  updateFolderStats(root)
  return root
}

export function collectFolderPaths(tree) {
  const result = []
  walkFolders(tree, (folder) => {
    result.push({
      name: folder.name || '全部文件',
      path: folder.path,
      fileCount: folder.fileCount,
      totalSize: folder.totalSize,
      depth: folder.path ? folder.path.split('/').length : 0
    })
  })
  return result
}

export function listDirectory(tree, path = '') {
  const folder = findFolder(tree, path) || tree
  return {
    folder,
    folders: Array.from(folder.children.values())
      .map((item) => ({
        name: item.name,
        path: item.path,
        fileCount: item.fileCount,
        totalSize: item.totalSize
      }))
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN')),
    files: [...folder.files].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  }
}

export function selectedStats(files = [], selectedKeys = new Set()) {
  return files.reduce((stats, file) => {
    if (selectedKeys.has(file.key)) {
      stats.count += 1
      stats.size += Number(file.size ?? file.fileSize ?? 0)
    }
    return stats
  }, { count: 0, size: 0 })
}

export function folderKeys(tree, path = '') {
  const folder = findFolder(tree, path)
  if (!folder) return []
  const keys = []
  walkFolders(folder, (item) => {
    item.files.forEach((file) => keys.push(file.key))
  })
  return keys
}

export function pathBreadcrumb(path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  const crumbs = [{ label: '全部文件', path: '' }]
  parts.forEach((part, index) => {
    crumbs.push({
      label: part,
      path: parts.slice(0, index + 1).join('/')
    })
  })
  return crumbs
}

export function childFolderPath(parentPath = '', folderName = '') {
  const sanitized = sanitizeFolderName(folderName)
  if (!sanitized) {
    throw new Error('文件夹名称不能为空')
  }
  const parent = normalizePath(parentPath)
  return parent ? `${parent}/${sanitized}` : sanitized
}

export function sanitizeName(name = '') {
  return sanitizeFolderName(name)
}

export function moveFileToFolder(files = [], key, folderPath = '') {
  const target = normalizePath(folderPath)
  return files.map((file) => {
    if (file.key !== key) return file
    const name = file.name || file.fileName || normalizePath(file.relativePath).split('/').pop() || 'file'
    const relativePath = uniqueFilePath(files, target ? `${target}/${name}` : name, key)
    return {
      ...file,
      name: baseName(relativePath),
      relativePath
    }
  })
}

export function renameFilePath(files = [], key, nextName = '') {
  const sanitized = sanitizeFileName(nextName)
  if (!sanitized) {
    throw new Error('文件名不能为空')
  }
  return files.map((file) => {
    if (file.key !== key) return file
    const parts = normalizePath(file.relativePath || file.name || file.fileName).split('/').filter(Boolean)
    parts.pop()
    const relativePath = [...parts, sanitized].join('/')
    if (hasFilePath(files, relativePath, key)) {
      throw new Error('同目录已存在同名文件')
    }
    return {
      ...file,
      name: sanitized,
      relativePath
    }
  })
}

export function renameFolderPath(files = [], virtualFolders = [], folderPath = '', nextName = '') {
  const oldPath = normalizePath(folderPath)
  const sanitized = sanitizeFolderName(nextName)
  if (!oldPath) {
    throw new Error('不能重命名根目录')
  }
  if (!sanitized) {
    throw new Error('文件夹名称不能为空')
  }
  const parent = parentPath(oldPath)
  const nextPath = parent ? `${parent}/${sanitized}` : sanitized
  ensureFolderPathAvailable(files, virtualFolders, oldPath, nextPath)
  return rewriteFolderPath(files, virtualFolders, oldPath, nextPath)
}

export function removeFolderEntries(files = [], virtualFolders = [], folderPath = '') {
  const target = normalizePath(folderPath)
  if (!target) {
    return { files: [], virtualFolders: [] }
  }
  return {
    files: files.filter((file) => !isPathInside(normalizePath(file.relativePath), target)),
    virtualFolders: virtualFolders
      .map(normalizePath)
      .filter((path) => path && !isPathInside(path, target))
  }
}

export function moveFolderToFolder(files = [], virtualFolders = [], folderPath = '', targetFolderPath = '') {
  const oldPath = normalizePath(folderPath)
  const target = normalizePath(targetFolderPath)
  if (!oldPath) {
    throw new Error('不能移动根目录')
  }
  if (target === oldPath || isPathInside(target, oldPath)) {
    throw new Error('不能移动到自身或子目录')
  }
  const nextPath = target ? `${target}/${baseName(oldPath)}` : baseName(oldPath)
  ensureFolderPathAvailable(files, virtualFolders, oldPath, nextPath)
  return rewriteFolderPath(files, virtualFolders, oldPath, nextPath)
}

export function uniqueFilePath(files = [], candidatePath = '', ignoreKey = '') {
  const normalized = normalizePath(candidatePath)
  if (!hasFilePath(files, normalized, ignoreKey)) return normalized

  const folder = parentPath(normalized)
  const originalName = baseName(normalized)
  const dotIndex = originalName.lastIndexOf('.')
  const hasExtension = dotIndex > 0
  const stem = hasExtension ? originalName.slice(0, dotIndex) : originalName
  const extension = hasExtension ? originalName.slice(dotIndex) : ''

  let index = 1
  let nextPath = ''
  do {
    const nextName = `${stem} (${index})${extension}`
    nextPath = folder ? `${folder}/${nextName}` : nextName
    index += 1
  } while (hasFilePath(files, nextPath, ignoreKey))

  return nextPath
}

function createFolder(name, path) {
  return {
    name,
    path,
    children: new Map(),
    files: [],
    fileCount: 0,
    totalSize: 0
  }
}

function ensureFolder(root, path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  let cursor = root
  parts.forEach((part) => {
    const nextPath = cursor.path ? `${cursor.path}/${part}` : part
    if (!cursor.children.has(part)) {
      cursor.children.set(part, createFolder(part, nextPath))
    }
    cursor = cursor.children.get(part)
  })
  return cursor
}

function sanitizeFolderName(name = '') {
  return String(name)
    .replace(/[\\/]/g, '')
    .replace(/[<>:"|?*]/g, '')
    .trim()
}

function sanitizeFileName(name = '') {
  return String(name)
    .replace(/[\\/]/g, '')
    .replace(/[<>:"|?*]/g, '')
    .trim()
}

function parentPath(path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  parts.pop()
  return parts.join('/')
}

function baseName(path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  return parts[parts.length - 1] || ''
}

function hasFilePath(files = [], path = '', ignoreKey = '') {
  const normalized = normalizePath(path).toLowerCase()
  return files.some((file) => {
    if (ignoreKey && file.key === ignoreKey) return false
    return normalizePath(file.relativePath || file.name || file.fileName).toLowerCase() === normalized
  })
}

function ensureFolderPathAvailable(files = [], virtualFolders = [], oldPath = '', nextPath = '') {
  const oldFolder = normalizePath(oldPath)
  const nextFolder = normalizePath(nextPath)
  if (oldFolder === nextFolder) return

  const occupied = collectFolderPaths(buildFileTree(files, virtualFolders))
    .map((folder) => normalizePath(folder.path))
    .some((path) => path && path === nextFolder && !isPathInside(path, oldFolder))

  if (occupied) {
    throw new Error('目标位置已存在同名文件夹')
  }
}

function isPathInside(path = '', folderPath = '') {
  const normalized = normalizePath(path)
  const folder = normalizePath(folderPath)
  return normalized === folder || normalized.startsWith(`${folder}/`)
}

function rewriteFolderPath(files = [], virtualFolders = [], oldPath = '', nextPath = '') {
  const oldFolder = normalizePath(oldPath)
  const nextFolder = normalizePath(nextPath)
  const rewrite = (path) => {
    const normalized = normalizePath(path)
    if (normalized === oldFolder) return nextFolder
    if (normalized.startsWith(`${oldFolder}/`)) {
      return `${nextFolder}/${normalized.slice(oldFolder.length + 1)}`
    }
    return normalized
  }
  return {
    files: files.map((file) => ({
      ...file,
      relativePath: rewrite(file.relativePath)
    })),
    virtualFolders: virtualFolders.map(rewrite).filter(Boolean),
    path: nextFolder
  }
}

function findFolder(tree, path = '') {
  const normalized = normalizePath(path)
  if (!normalized) return tree
  return normalized.split('/').filter(Boolean).reduce((folder, part) => {
    if (!folder) return null
    return folder.children.get(part) || null
  }, tree)
}

function walkFolders(folder, callback) {
  callback(folder)
  Array.from(folder.children.values())
    .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    .forEach((child) => walkFolders(child, callback))
}

function updateFolderStats(folder) {
  let fileCount = folder.files.length
  let totalSize = folder.files.reduce((sum, file) => sum + Number(file.size ?? file.fileSize ?? 0), 0)
  folder.children.forEach((child) => {
    updateFolderStats(child)
    fileCount += child.fileCount
    totalSize += child.totalSize
  })
  folder.fileCount = fileCount
  folder.totalSize = totalSize
}
