import { ElMessage } from 'element-plus'

export async function shareResource(resource) {
  if (!resource?.id) return
  const url = `${window.location.origin}/resources/${resource.id}`
  const title = resource.title || resource.fileName || '学习资源'
  const text = resource.description || title

  try {
    if (navigator.share) {
      await navigator.share({ title, text, url })
      return
    }
    await navigator.clipboard.writeText(url)
    ElMessage.success('分享链接已复制')
  } catch (error) {
    if (error?.name === 'AbortError') return
    ElMessage.error('分享失败，请稍后重试')
  }
}
