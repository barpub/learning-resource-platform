<template>
  <div class="detail-page" v-if="resource">
    <section class="panel detail-hero">
      <div class="detail-heading">
        <el-button class="detail-back-button" plain size="small" aria-label="返回上一页" @click="goBack">
          返回上一页
        </el-button>
        <span class="eyebrow">Resource Detail</span>
        <h1>{{ resource.title }}</h1>
        <p class="muted">{{ resource.description || '暂无描述' }}</p>
        <div class="detail-meta">
          <span>上传者：{{ resource.username }}</span>
          <span v-if="isFolder">文件 {{ resource.fileCount || children.length }}</span>
          <span>浏览 {{ resource.viewCount || 0 }}</span>
          <span>下载 {{ resource.downloadCount || 0 }}</span>
          <span>评分 {{ resource.rating || '0.00' }}</span>
        </div>
        <div v-if="resourceTags.length" class="detail-tags">
          <span v-for="tag in resourceTags" :key="tag">#{{ tag }}</span>
        </div>
      </div>
      <div class="detail-actions">
        <el-button @click="shareResource(resource)">分享</el-button>
        <el-button type="primary" @click="downloadResource">{{ isFolder ? '下载整个文件夹' : '下载资源' }}</el-button>
        <el-button @click="openPreview">新窗口打开</el-button>
        <el-button @click="understandResource">Agent 理解</el-button>
        <el-button @click="toggleFavorite">{{ resource.favorite ? '取消收藏' : '收藏资源' }}</el-button>
        <el-button v-if="isFolder && canDeleteResource" @click="editingFolder = !editingFolder">
          {{ editingFolder ? '退出编辑' : '编辑文件夹' }}
        </el-button>
        <el-button v-if="canDeleteResource" type="danger" plain @click="deleteResource(resource)">
          {{ isFolder ? '删除文件夹' : '删除资源' }}
        </el-button>
      </div>
    </section>

    <section class="panel preview-section">
      <div class="preview-header">
        <h2>{{ isFolder ? '文件夹浏览' : '在线预览' }}</h2>
        <el-tag type="info">{{ previewLabel }}</el-tag>
      </div>

      <div v-if="isFolder" class="local-folder-browser">
        <div class="ftp-breadcrumb">
          <button
            v-for="crumb in breadcrumbs"
            :key="crumb.path || 'root'"
            type="button"
            :class="{ active: currentPath === crumb.path }"
            @click="openFolder(crumb.path)"
          >
            {{ crumb.label === '/' ? '根目录' : crumb.label }}
          </button>
          <span class="toolbar-push"></span>
          <el-button v-if="currentPath" size="small" @click="goUpFolder">返回上级</el-button>
          <label v-if="editingFolder" class="inline-upload-button">
            <input type="file" multiple @change="appendFilesAtCurrentPath" />
            <span>追加文件</span>
          </label>
          <label v-if="editingFolder" class="inline-upload-button">
            <input type="file" multiple webkitdirectory directory @change="appendFolderAtCurrentPath" />
            <span>追加文件夹</span>
          </label>
        </div>

        <div class="directory-toolbar">
          <span>{{ currentDirectory.folder.fileCount }} 个文件 · {{ currentDirectory.folders.length }} 个目录</span>
          <el-button size="small" type="primary" plain :disabled="!currentAnalysisFileCount" @click="understandCurrentFolder">理解当前文件夹</el-button>
          <el-button size="small" @click="selectFirstInCurrentDirectory" :disabled="!currentDirectory.files.length">选择预览</el-button>
        </div>

        <div class="grid">
          <FileCard
            v-for="folder in currentDirectory.folders"
            :key="folder.path"
            :title="folder.name"
            :description="`${folder.fileCount} 个文件 · ${formatSize(folder.totalSize)}`"
            :is-folder="true"
            footer-label="本地目录"
            @click="openFolder(folder.path)"
          >
            <template v-if="editingFolder" #actions>
              <el-button size="small" text @click.stop="renameLogicalFolder(folder.path)">重命名</el-button>
              <el-button size="small" text @click.stop="moveLogicalFolder(folder.path)">移动</el-button>
              <el-button size="small" text type="danger" @click.stop="deleteLogicalFolder(folder.path)">删除</el-button>
            </template>
          </FileCard>

          <FileCard
            v-for="item in currentDirectory.files"
            :key="item.key"
            :title="item.fileName"
            :description="item.relativePath || item.fileName"
            :file-name="item.fileName"
            :extras="[formatSize(item.fileSize)]"
            :footer-label="`${resource.title} · 本地文件`"
            :active="selectedChildId === item.id"
            @click="selectChild(item.id)"
          >
            <template v-if="editingFolder || canDelete(item)" #actions>
              <el-button v-if="editingFolder" size="small" text @click.stop="renameChildFile(item)">重命名</el-button>
              <el-button v-if="editingFolder" size="small" text @click.stop="moveChildFile(item)">移动</el-button>
              <el-button v-if="canDelete(item)" size="small" text type="danger" @click.stop="deleteResource(item)">删除</el-button>
            </template>
          </FileCard>

          <el-empty
            v-if="!currentDirectory.folders.length && !currentDirectory.files.length"
            description="当前目录为空"
          />
        </div>
      </div>

      <main class="folder-preview-pane full-preview-pane">
        <div v-if="previewTarget" class="selected-file-bar">
          <div>
            <strong>{{ previewTarget.fileName || previewTarget.title }}</strong>
            <span>{{ previewTarget.relativePath || previewTarget.fileName || resource.title }}</span>
          </div>
          <div class="selected-actions">
            <el-button size="small" type="primary" plain @click="understandPreviewTarget">Agent 理解</el-button>
            <el-button v-if="store.state.token" size="small" type="primary" @click="openCurrentSnippetNote">记当前片段</el-button>
            <el-button size="small" @click="downloadPreviewTarget">下载</el-button>
            <el-button
              v-if="canDeletePreviewTarget"
              size="small"
              type="danger"
              plain
              @click="deleteResource(previewTarget)"
            >
              删除
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="previewKind === 'docx' || previewKind === 'pptx'"
          class="preview-tip"
          title="Office 文档由浏览器端 JS 渲染，复杂排版、动画和母版效果可能与 Office/WPS 不完全一致"
          type="info"
          :closable="false"
          show-icon
        />

        <div v-if="previewKind === 'image'" class="preview-box image-preview">
          <img :src="previewUrl" :alt="previewTarget?.title || resource.title" />
        </div>

        <iframe
          v-else-if="previewKind === 'pdf'"
          class="preview-box document-preview"
          :src="previewUrl"
          title="资源预览"
        />

        <div v-else-if="previewKind === 'docx'" class="preview-box office-preview">
          <div ref="docxContainer" class="docx-preview-container"></div>
        </div>

        <div v-else-if="previewKind === 'pptx'" class="preview-box office-preview pptx-preview">
          <div v-if="pptxSlides.length" class="pptx-slide-list">
            <section v-for="slide in pptxSlides" :key="slide.name" class="pptx-slide">
              <div class="pptx-slide-title">{{ slide.title }}</div>
              <div v-if="slide.images.length" class="pptx-images">
                <img v-for="image in slide.images" :key="image.src" :src="image.src" :alt="image.name" />
              </div>
              <p v-for="(text, index) in slide.texts" :key="index">{{ text }}</p>
              <el-empty v-if="!slide.texts.length && !slide.images.length" description="该页没有可提取的文本或图片" />
            </section>
          </div>
          <el-empty v-else description="正在解析 PPTX，或该文件没有可预览内容" />
        </div>

        <div v-else-if="previewKind === 'video'" class="preview-box media-preview">
          <DanmakuPlayer
            v-if="previewTarget"
            ref="videoPlayerRef"
            :resource-id="previewTarget.id"
            :src="previewUrl"
            :enabled="danmakuEnabled"
            :permission="danmakuPermission"
            :share-token="noteShareToken"
            :current-user="currentUser"
            :is-owner-or-admin="danmakuOwnerOrAdmin"
            @open-config="danmakuConfigDialog = true"
          />
        </div>

        <div v-else-if="previewKind === 'audio'" class="preview-box audio-preview">
          <AudioPlayer
            v-if="previewTarget"
            :src="previewUrl"
            :title="previewTarget.title || previewTarget.fileName"
            :subtitle="previewTarget.relativePath || previewTarget.description || resource.title"
            :file-name="previewTarget.fileName"
            :file-size="previewTarget.fileSize"
            @download="downloadPreviewTarget"
            @open="openPreview"
          />
        </div>

        <pre v-else-if="previewKind === 'text'" ref="textPreviewRef" class="preview-box text-preview"><template v-for="(segment, index) in textPreviewSegments" :key="index"><mark v-if="segment.highlight" class="note-anchor-highlight">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></pre>

        <div v-else-if="previewKind === 'legacy-office'" class="preview-box document-preview legacy-frame">
          <div v-if="legacyLoading" class="legacy-loading">正在解析老版 Office 文档……</div>
          <div v-else-if="legacyError" class="legacy-error">
            <p>{{ legacyError }}</p>
            <el-button type="primary" @click="reloadLegacy">重试</el-button>
          </div>
          <iframe
            v-else-if="legacyHtml"
            class="legacy-iframe"
            :srcdoc="legacyHtml"
            title="老版 Office 预览"
            sandbox="allow-same-origin"
          />
        </div>

        <el-empty
          v-else
          class="preview-empty"
          description="该文件类型设置为仅下载，不进行在线预览"
        >
          <div class="empty-actions">
            <el-button v-if="previewTarget" @click="downloadPreviewTarget">下载文件</el-button>
            <el-button
              v-if="canDeletePreviewTarget"
              type="danger"
              plain
              @click="deleteResource(previewTarget)"
            >
              删除文件
            </el-button>
          </div>
          </el-empty>
        </main>

      <el-dialog
        v-model="targetDialog.visible"
        :title="targetDialog.title"
        width="420px"
        class="move-target-dialog"
        @close="cancelTargetDialog"
      >
        <div class="target-folder-list">
          <button
            v-for="folder in targetFolderOptions"
            :key="folder.path || 'root'"
            type="button"
            class="file-tree-item"
            :class="{ active: targetDialog.path === folder.path }"
            :style="{ paddingLeft: `${12 + folder.depth * 14}px` }"
            @click="targetDialog.path = folder.path"
          >
            <span>{{ folder.path ? '▸' : '⌂' }}</span>
            <strong>{{ folder.name }}</strong>
            <em>{{ folder.fileCount }}</em>
          </button>
        </div>
        <template #footer>
          <el-button @click="cancelTargetDialog">取消</el-button>
          <el-button type="primary" @click="confirmTargetDialog">移动到这里</el-button>
        </template>
      </el-dialog>
    </section>

    <section v-if="store.state.token && previewTarget && mediaEnhanceSupported" class="panel media-enhance-panel">
      <div class="media-enhance-header">
        <div>
          <span class="eyebrow">Media Enhance</span>
          <h2>媒体增强</h2>
        </div>
        <el-tag :type="ffmpegReady ? 'success' : 'warning'">
          {{ ffmpegReady ? 'FFmpeg 可用' : 'FFmpeg 未就绪' }}
        </el-tag>
      </div>

      <el-alert
        v-if="!canEnhancePreviewTarget"
        title="只有资源发布者或管理员可以提交增强任务"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="mediaCapability && !ffmpegReady"
        :title="mediaCapability.message || 'FFmpeg 不可用，暂时无法执行本地增强'"
        type="warning"
        :closable="false"
        show-icon
      />

      <div class="media-enhance-controls">
        <el-form :model="enhanceForm" label-width="96px" class="media-enhance-form">
          <template v-if="enhanceMediaKind === 'video'">
            <el-form-item label="目标分辨率">
              <el-select v-model="enhanceForm.targetResolution">
                <el-option label="4K UHD" value="4K" />
                <el-option label="2K QHD" value="2K" />
                <el-option label="1080P" value="1080P" />
                <el-option label="720P" value="720P" />
                <el-option label="原分辨率" value="ORIGINAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标帧率">
              <el-select v-model="enhanceForm.targetFps">
                <el-option label="120 FPS" :value="120" />
                <el-option label="60 FPS" :value="60" />
                <el-option label="30 FPS" :value="30" />
                <el-option label="保持原帧率" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="编码质量">
              <el-select v-model="enhanceForm.videoPreset">
                <el-option label="质量优先" value="QUALITY" />
                <el-option label="均衡" value="BALANCED" />
                <el-option label="快速" value="FAST" />
              </el-select>
            </el-form-item>
            <el-form-item label="运动插帧">
              <el-switch v-model="enhanceForm.frameInterpolation" />
            </el-form-item>
          </template>
          <el-form-item label="音频预设">
            <el-select v-model="enhanceForm.audioPreset">
              <el-option label="HiFi 响度/限幅" value="HIFI" />
              <el-option label="人声平滑" value="VOCAL" />
              <el-option label="鼓点动态" value="BEAT" />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="media-enhance-summary">
          <strong>{{ previewTarget.fileName || previewTarget.title }}</strong>
          <span>{{ enhancementSummary }}</span>
          <div class="media-enhance-actions">
            <el-button
              type="primary"
              :loading="submittingEnhancement"
              :disabled="!canSubmitEnhancement"
              @click="submitMediaEnhancement"
            >
              开始增强
            </el-button>
            <el-button :loading="loadingEnhancements" @click="refreshEnhancementPanel">刷新状态</el-button>
          </div>
        </div>
      </div>

      <div v-if="mediaJobs.length" class="media-job-list">
        <article v-for="job in mediaJobs" :key="job.id" class="media-job-card">
          <div class="media-job-main">
            <div class="media-job-title">
              <strong>#{{ job.id }} {{ job.mediaType === 'VIDEO' ? '视频增强' : '音频增强' }}</strong>
              <el-tag :type="enhancementStatusType(job)">{{ enhancementStatusLabel(job.status) }}</el-tag>
            </div>
            <el-progress
              :percentage="Number(job.progress || 0)"
              :status="enhancementProgressStatus(job)"
            />
            <p>{{ job.message || '等待处理' }}</p>
            <span>{{ formatJobTime(job.createTime) }}</span>
          </div>
          <div class="media-job-actions">
            <el-button
              v-if="job.outputResourceId"
              size="small"
              type="primary"
              plain
              @click="openEnhancedResource(job)"
            >
              查看结果
            </el-button>
            <el-button
              v-if="isEnhancementActive(job)"
              size="small"
              type="danger"
              plain
              @click="cancelMediaEnhancement(job)"
            >
              取消
            </el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="当前文件还没有增强任务" />
    </section>

    <el-dialog
      v-model="danmakuConfigDialog"
      title="弹幕设置"
      width="420px"
      append-to-body
      :modal-append-to-body="true"
    >
      <el-form label-width="100px">
        <el-form-item label="启用弹幕">
          <el-switch v-model="danmakuForm.enabled" />
        </el-form-item>
        <el-form-item label="发送权限">
          <el-radio-group v-model="danmakuForm.permission" :disabled="!danmakuForm.enabled">
            <el-radio value="EVERYONE">所有人</el-radio>
            <el-radio value="LOGGED">登录用户</el-radio>
            <el-radio value="OWNER">仅发布者</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="danmakuConfigDialog = false">取消</el-button>
        <el-button type="primary" @click="saveDanmakuConfig" :loading="savingDanmaku">保存</el-button>
      </template>
    </el-dialog>

    <section v-if="store.state.token && previewTarget" class="panel note-panel">
      <div class="note-panel-header">
        <div>
          <span class="eyebrow">Notes</span>
          <h2>当前文件笔记</h2>
        </div>
        <el-button type="primary" @click="openCurrentSnippetNote">记当前片段</el-button>
      </div>

      <div v-if="resourceNotes.length" class="resource-note-list">
        <article v-for="note in resourceNotes" :key="note.id" class="resource-note-card">
          <div class="resource-note-main">
            <h3>{{ note.title }}</h3>
            <div class="resource-note-meta">
              <span v-if="note.category">{{ note.category }}</span>
              <span v-if="note.anchorSeconds !== null && note.anchorSeconds !== undefined">{{ formatAnchorSeconds(note.anchorSeconds) }}</span>
              <span>{{ note.createTime }}</span>
            </div>
            <button v-if="hasAnchorPreview(note)" type="button" class="note-snippet-card" @click="confirmJumpToNote(note)">
              <img v-if="note.anchorImage" :src="note.anchorImage" :alt="note.title" />
              <span class="snippet-text">{{ note.anchorText || anchorFallback(note) }}</span>
            </button>
            <p>{{ previewNoteContent(note.content) }}</p>
          </div>
          <div class="resource-note-actions">
            <el-button size="small" @click="editResourceNote(note)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="deleteResourceNote(note)">删除</el-button>
          </div>
        </article>
      </div>
      <el-empty v-else description="当前文件暂无笔记" />
    </section>

    <el-dialog v-model="noteDialogVisible" :title="noteForm.id ? '编辑片段笔记' : '记录当前片段'" width="720px">
      <div v-if="hasAnchorPreview(noteForm)" class="note-dialog-snippet">
        <img v-if="noteForm.anchorImage" :src="noteForm.anchorImage" alt="片段截图" />
        <div>
          <strong>{{ anchorTypeLabel(noteForm.anchorType) }}</strong>
          <p>{{ noteForm.anchorText || anchorFallback(noteForm) }}</p>
        </div>
      </div>
      <el-form :model="noteForm" label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="noteForm.title" placeholder="请输入笔记标题" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="noteForm.category" placeholder="如：前端、后端、数据库" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="noteForm.content" type="textarea" :rows="8" placeholder="写下这段内容的理解、问题或总结" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="noteDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingNote" @click="saveResourceNote">保存</el-button>
      </template>
    </el-dialog>

    <section class="panel comments-panel">
      <h2>评论与评分</h2>
      <el-form v-if="store.state.token" :model="commentForm">
        <el-form-item>
          <el-rate v-model="commentForm.rating" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="commentForm.content" type="textarea" placeholder="写下评论" />
        </el-form-item>
        <el-button type="primary" @click="submitComment">发表评论</el-button>
      </el-form>
      <el-empty v-else description="登录后可评论和评分" />
      <el-timeline style="margin-top: 18px">
        <el-timeline-item v-for="item in comments" :key="item.id" :timestamp="item.createTime">
          <strong>{{ item.username }}</strong>
          <el-rate :model-value="item.rating || 0" disabled size="small" />
          <div>{{ item.content }}</div>
        </el-timeline-item>
      </el-timeline>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderAsync } from 'docx-preview'
import JSZip from 'jszip'
import { commentApi, danmakuApi, favoriteApi, mediaEnhancementApi, noteApi, resourceApi } from '../api'
import { buildFileTree, collectFolderPaths, listDirectory, pathBreadcrumb } from '../utils/fileTree.mjs'
import { legacyKind, renderDoc, renderPpt, renderSpreadsheet } from '../utils/legacyOffice'
import { shareResource } from '../utils/shareResource'
import AudioPlayer from '../components/AudioPlayer.vue'
import FileCard from '../components/FileCard.vue'
import DanmakuPlayer from '../components/DanmakuPlayer.vue'

const route = useRoute()
const router = useRouter()
const store = useStore()
const resource = ref(null)
const children = ref([])
const selectedChildId = ref(null)
const currentPath = ref('')
const comments = ref([])
const previewText = ref('')
const legacyHtml = ref('')
const legacyLoading = ref(false)
const legacyError = ref('')
const pptxSlides = ref([])
const docxContainer = ref(null)
const textPreviewRef = ref(null)
const videoPlayerRef = ref(null)
const editingFolder = ref(false)
const objectUrls = []
const commentForm = reactive({ content: '', rating: 5 })
const resourceNotes = ref([])
const noteDialogVisible = ref(false)
const savingNote = ref(false)
const highlightAnchorText = ref('')
const noteForm = reactive({
  id: null,
  title: '',
  content: '',
  category: '',
  resourceId: null,
  anchorType: 'RESOURCE',
  anchorText: '',
  anchorImage: '',
  anchorSeconds: null
})
const mediaCapability = ref(null)
const mediaJobs = ref([])
const loadingEnhancements = ref(false)
const submittingEnhancement = ref(false)
const enhanceForm = reactive({
  targetResolution: '4K',
  targetFps: 60,
  videoPreset: 'BALANCED',
  audioPreset: 'HIFI',
  frameInterpolation: true,
  aiUpscale: false
})
let enhancementTimer = 0
const targetDialog = reactive({
  visible: false,
  title: '',
  excludePath: '',
  path: '',
  resolve: null
})

const isFolder = computed(() => resource.value?.resourceType === 'FOLDER')
const currentUser = computed(() => store.state.user)
const resourceTags = computed(() => splitTags(resource.value?.tags))

const danmakuConfigDialog = ref(false)
const savingDanmaku = ref(false)
const danmakuForm = reactive({ enabled: true, permission: 'LOGGED' })
const danmakuEnabled = computed(() => resource.value?.danmakuEnabled !== 0)
const danmakuPermission = computed(() => resource.value?.danmakuPermission || 'LOGGED')
const noteShareToken = computed(() => typeof route.query.shareToken === 'string' ? route.query.shareToken : '')
const danmakuOwnerOrAdmin = computed(() => {
  const user = currentUser.value
  if (!user || !resource.value) return false
  return user.role === 'ADMIN' || Number(resource.value.userId) === Number(user.id)
})

watch(resource, (value) => {
  if (!value) return
  danmakuForm.enabled = value.danmakuEnabled !== 0
  danmakuForm.permission = value.danmakuPermission || 'LOGGED'
}, { immediate: true })

async function saveDanmakuConfig() {
  if (!resource.value) return
  savingDanmaku.value = true
  try {
    const updated = await danmakuApi.updateConfig(resource.value.id, {
      enabled: danmakuForm.enabled,
      permission: danmakuForm.permission
    })
    resource.value = { ...resource.value, ...updated }
    ElMessage.success('弹幕设置已更新')
    danmakuConfigDialog.value = false
  } finally {
    savingDanmaku.value = false
  }
}
const treeFiles = computed(() => children.value.map((item) => ({
  ...item,
  key: String(item.id),
  name: item.fileName,
  size: item.fileSize,
  relativePath: item.relativePath || item.fileName
})))
const tree = computed(() => buildFileTree(treeFiles.value))
const folderOptions = computed(() => collectFolderPaths(tree.value))
const targetFolderOptions = computed(() => folderOptions.value.filter((folder) => {
  const excluded = normalizePath(targetDialog.excludePath)
  return !excluded || (folder.path !== excluded && !folder.path.startsWith(`${excluded}/`))
}))
const currentDirectory = computed(() => listDirectory(tree.value, currentPath.value))
const currentAnalysisFileCount = computed(() => treeFiles.value.filter((item) => isPathInFolder(item.relativePath || item.fileName, currentPath.value)).length)
const breadcrumbs = computed(() => pathBreadcrumb(currentPath.value))
const selectedChild = computed(() => children.value.find((item) => item.id === selectedChildId.value) || null)
const previewTarget = computed(() => isFolder.value ? selectedChild.value : resource.value)
const previewUrl = computed(() => previewTarget.value ? resourceApi.previewUrl(previewTarget.value.id) : '')
const previewKind = computed(() => detectPreviewKind(previewTarget.value))
const enhanceMediaKind = computed(() => detectEnhanceMediaKind(previewTarget.value))
const mediaEnhanceSupported = computed(() => Boolean(enhanceMediaKind.value))
const canDeleteResource = computed(() => canDelete(resource.value))
const canDeletePreviewTarget = computed(() => canDelete(previewTarget.value))
const canEnhancePreviewTarget = computed(() => canDelete(previewTarget.value))
const ffmpegReady = computed(() => Boolean(mediaCapability.value?.ffmpegAvailable))
const hasActiveEnhancementJob = computed(() => mediaJobs.value.some(isEnhancementActive))
const canSubmitEnhancement = computed(() => {
  return Boolean(store.state.token && previewTarget.value && mediaEnhanceSupported.value
    && canEnhancePreviewTarget.value && ffmpegReady.value && !hasActiveEnhancementJob.value)
})
const enhancementSummary = computed(() => {
  if (enhanceMediaKind.value === 'video') {
    const fps = Number(enhanceForm.targetFps || 0)
    const fpsText = fps > 0 ? `${fps} FPS` : '原帧率'
    const motion = enhanceForm.frameInterpolation && fps > 0 ? '运动插帧' : '帧率转换'
    return `${enhanceForm.targetResolution} / ${fpsText} / ${motion} / ${enhanceForm.audioPreset}`
  }
  return `FLAC 输出 / ${enhanceForm.audioPreset} / 响度标准化 / 波峰限幅`
})
const previewLabel = computed(() => {
  const labels = {
    image: '图片',
    pdf: 'PDF',
    docx: 'DOCX',
    pptx: 'PPTX',
    'legacy-office': '老版 Office',
    video: '视频',
    audio: '音频',
    text: '文本',
    unsupported: '仅下载'
  }
  return labels[previewKind.value] || labels.unsupported
})
const textPreviewSegments = computed(() => {
  const text = previewText.value || ''
  const anchor = highlightAnchorText.value || ''
  if (!anchor) return [{ text, highlight: false }]
  const index = text.indexOf(anchor)
  if (index < 0) return [{ text, highlight: false }]
  return [
    { text: text.slice(0, index), highlight: false },
    { text: text.slice(index, index + anchor.length), highlight: true },
    { text: text.slice(index + anchor.length), highlight: false }
  ].filter((segment) => segment.text)
})

async function load() {
  resource.value = await resourceApi.get(route.params.id)
  if (isFolder.value) {
    children.value = await resourceApi.children(route.params.id)
    if (route.query.path !== undefined) {
      currentPath.value = normalizePath(route.query.path)
    }
    if (!children.value.some((item) => item.id === selectedChildId.value)) {
      selectedChildId.value = children.value[0]?.id || null
    }
  } else {
    children.value = []
    selectedChildId.value = null
  }
  comments.value = await commentApi.list(route.params.id)
  await loadResourceNotes()
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
    return
  }
  router.push('/resources')
}

function canDelete(item) {
  if (!item || !currentUser.value) return false
  return currentUser.value.role === 'ADMIN' || Number(item.userId) === Number(currentUser.value.id)
}

function detectPreviewKind(item) {
  if (!item) return 'unsupported'
  if (item.resourceType === 'FOLDER') return 'unsupported'
  const type = (item.fileType || '').toLowerCase()
  const name = (item.fileName || '').toLowerCase()
  if (type.startsWith('image/') || /\.(png|jpe?g|gif|webp)$/i.test(name)) return 'image'
  if (type === 'application/pdf' || name.endsWith('.pdf')) return 'pdf'
  if (name.endsWith('.docx')) return 'docx'
  if (name.endsWith('.pptx')) return 'pptx'
  if (/\.(doc|ppt|xls|xlsx|xlsm|xlsb|csv|ods)$/i.test(name)) return 'legacy-office'
  if (type.startsWith('video/') || /\.(mp4|mov|webm)$/i.test(name)) return 'video'
  if (type.startsWith('audio/') || /\.(mp3|wav|ogg|flac|m4a|aac)$/i.test(name)) return 'audio'
  if (type.startsWith('text/') || name.endsWith('.txt')) return 'text'
  return 'unsupported'
}

function detectEnhanceMediaKind(item) {
  if (!item || item.resourceType === 'FOLDER') return ''
  const type = (item.fileType || '').toLowerCase()
  const name = (item.fileName || '').toLowerCase()
  if (type.startsWith('video/') || /\.(mp4|mov|webm|mkv|avi|m4v)$/i.test(name)) return 'video'
  if (type.startsWith('audio/') || /\.(mp3|wav|ogg|flac|m4a|aac)$/i.test(name)) return 'audio'
  return ''
}

function fileLabel(name = '') {
  const lower = name.toLowerCase()
  if (lower.endsWith('.pdf')) return 'PDF'
  if (lower.endsWith('.docx')) return 'DOC'
  if (lower.endsWith('.pptx')) return 'PPT'
  if (/\.(png|jpe?g|gif|webp)$/.test(lower)) return 'IMG'
  if (/\.(mp4|mov|webm|mkv|avi|m4v)$/.test(lower)) return 'VID'
  if (/\.(mp3|wav|ogg|flac|m4a|aac)$/.test(lower)) return 'AUD'
  if (lower.endsWith('.txt')) return 'TXT'
  return 'FILE'
}

function formatSize(size = 0) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function splitTags(tags = '') {
  return String(tags || '').split(',').map((tag) => tag.trim()).filter(Boolean).slice(0, 6)
}

function normalizePath(path = '') {
  return String(path).replace(/\\/g, '/').split('/').map((part) => part.trim()).filter(Boolean).join('/')
}

function joinPath(base = '', path = '') {
  return [base, path].map(normalizePath).filter(Boolean).join('/')
}

function parentPath(path = '') {
  const parts = normalizePath(path).split('/').filter(Boolean)
  parts.pop()
  return parts.join('/')
}

function isPathInFolder(relativePath = '', folderPath = '') {
  const relative = normalizePath(relativePath)
  const target = normalizePath(folderPath)
  if (!relative) return false
  if (!target) return true
  return relative.startsWith(`${target}/`)
}

async function promptText(title, message, placeholder, value = '') {
  try {
    const result = await ElMessageBox.prompt(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: value,
      inputPlaceholder: placeholder,
      inputValidator: (input) => {
        if (!input || !input.trim()) return '名称不能为空'
        if (/[<>:"|?*]/.test(input)) return '名称不能包含 <>:"|?*'
        return true
      }
    })
    return result.value
  } catch {
    return ''
  }
}

function folderKeysFromPath(path) {
  const target = normalizePath(path)
  return children.value
    .filter((item) => {
      const relativePath = normalizePath(item.relativePath || item.fileName)
      return relativePath === target || relativePath.startsWith(`${target}/`)
    })
    .map((item) => String(item.id))
}

async function chooseTargetFolder(title, excludePath = '') {
  targetDialog.title = title
  targetDialog.excludePath = excludePath
  const excluded = normalizePath(excludePath)
  const current = normalizePath(currentPath.value)
  targetDialog.path = excluded && (current === excluded || current.startsWith(`${excluded}/`)) ? '' : current
  targetDialog.visible = true
  return new Promise((resolve) => {
    targetDialog.resolve = resolve
  })
}

function confirmTargetDialog() {
  const resolve = targetDialog.resolve
  const path = normalizePath(targetDialog.path)
  resetTargetDialog()
  resolve?.(path)
}

function cancelTargetDialog() {
  const resolve = targetDialog.resolve
  if (!resolve) return
  resetTargetDialog()
  resolve?.(null)
}

function resetTargetDialog() {
  targetDialog.visible = false
  targetDialog.title = ''
  targetDialog.excludePath = ''
  targetDialog.path = ''
  targetDialog.resolve = null
}

function openFolder(path) {
  currentPath.value = path
}

function goUpFolder() {
  const parts = String(currentPath.value || '').split('/').filter(Boolean)
  parts.pop()
  currentPath.value = parts.join('/')
}

function selectChild(id) {
  selectedChildId.value = id
}

function selectFirstInCurrentDirectory() {
  const first = currentDirectory.value.files[0]
  if (!first) {
    ElMessage.info('当前目录没有可预览文件')
    return
  }
  selectedChildId.value = first.id
}

async function loadResourceNotes() {
  if (!store.state.token || !previewTarget.value?.id) {
    resourceNotes.value = []
    return
  }
  try {
    resourceNotes.value = await noteApi.listByResource(previewTarget.value.id)
  } catch {
    resourceNotes.value = []
  }
}

async function loadMediaCapability() {
  try {
    mediaCapability.value = await mediaEnhancementApi.capabilities()
  } catch {
    mediaCapability.value = {
      ffmpegAvailable: false,
      message: '媒体增强能力检测失败'
    }
  }
}

async function loadEnhancementJobs() {
  if (!store.state.token || !previewTarget.value?.id || !mediaEnhanceSupported.value || !canEnhancePreviewTarget.value) {
    mediaJobs.value = []
    stopEnhancementPolling()
    return
  }
  loadingEnhancements.value = true
  try {
    mediaJobs.value = await mediaEnhancementApi.list(previewTarget.value.id)
    syncEnhancementPolling()
  } catch {
    mediaJobs.value = []
    stopEnhancementPolling()
  } finally {
    loadingEnhancements.value = false
  }
}

async function refreshEnhancementPanel() {
  if (!mediaEnhanceSupported.value) {
    mediaJobs.value = []
    stopEnhancementPolling()
    return
  }
  await loadMediaCapability()
  await loadEnhancementJobs()
}

async function submitMediaEnhancement() {
  if (!canSubmitEnhancement.value || !previewTarget.value) return
  submittingEnhancement.value = true
  try {
    const isVideo = enhanceMediaKind.value === 'video'
    const job = await mediaEnhancementApi.submit(previewTarget.value.id, {
      targetResolution: isVideo ? enhanceForm.targetResolution : 'ORIGINAL',
      targetFps: isVideo ? Number(enhanceForm.targetFps || 0) : null,
      videoPreset: enhanceForm.videoPreset,
      audioPreset: enhanceForm.audioPreset,
      frameInterpolation: isVideo && enhanceForm.frameInterpolation && Number(enhanceForm.targetFps || 0) > 0,
      aiUpscale: false
    })
    ElMessage.success(`增强任务 #${job.id} 已提交`)
    await loadEnhancementJobs()
  } catch (error) {
    ElMessage.error(error.message || '提交增强任务失败')
  } finally {
    submittingEnhancement.value = false
  }
}

async function cancelMediaEnhancement(job) {
  if (!job?.id) return
  try {
    await mediaEnhancementApi.cancel(job.id)
    ElMessage.success('增强任务已取消')
    await loadEnhancementJobs()
  } catch (error) {
    ElMessage.error(error.message || '取消增强任务失败')
  }
}

function openEnhancedResource(job) {
  if (!job?.outputResourceId) return
  router.push(`/resources/${job.outputResourceId}`)
}

function isEnhancementActive(job) {
  return ['PENDING', 'RUNNING'].includes(job?.status)
}

function enhancementStatusLabel(status) {
  return ({
    PENDING: '排队中',
    RUNNING: '处理中',
    SUCCESS: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消'
  }[status] || status || '未知')
}

function enhancementStatusType(job) {
  return ({
    PENDING: 'info',
    RUNNING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
    CANCELLED: 'info'
  }[job?.status] || 'info')
}

function enhancementProgressStatus(job) {
  if (job?.status === 'SUCCESS') return 'success'
  if (job?.status === 'FAILED') return 'exception'
  if (job?.status === 'CANCELLED') return 'warning'
  return undefined
}

function syncEnhancementPolling() {
  if (mediaJobs.value.some(isEnhancementActive)) {
    startEnhancementPolling()
  } else {
    stopEnhancementPolling()
  }
}

function startEnhancementPolling() {
  if (enhancementTimer) return
  enhancementTimer = window.setInterval(() => {
    loadEnhancementJobs()
  }, 2500)
}

function stopEnhancementPolling() {
  if (!enhancementTimer) return
  window.clearInterval(enhancementTimer)
  enhancementTimer = 0
}

function formatJobTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

async function openCurrentSnippetNote() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (!previewTarget.value) {
    ElMessage.info('请先选择要记录的文件')
    return
  }

  const anchor = await buildCurrentAnchor()
  resetNoteForm({
    title: `${previewTarget.value.fileName || previewTarget.value.title || resource.value.title} 片段笔记`,
    category: resource.value?.categoryName || '',
    content: '',
    resourceId: previewTarget.value.id,
    ...anchor
  })
  noteDialogVisible.value = true
}

async function buildCurrentAnchor() {
  const selected = selectedTextSnippet()
  if (previewKind.value === 'video') {
    const seconds = videoPlayerRef.value?.getCurrentTime?.() || 0
    return {
      anchorType: 'VIDEO',
      anchorText: selected || `视频 ${formatAnchorSeconds(seconds)} 处`,
      anchorImage: videoPlayerRef.value?.captureSnapshot?.() || '',
      anchorSeconds: seconds
    }
  }
  if (['text', 'docx', 'pptx', 'pdf', 'legacy-office'].includes(previewKind.value)) {
    return {
      anchorType: previewKind.value === 'text' ? 'TEXT' : 'DOCUMENT',
      anchorText: selected || fallbackDocumentSnippet(),
      anchorImage: '',
      anchorSeconds: null
    }
  }
  return {
    anchorType: 'RESOURCE',
    anchorText: selected || `${previewLabel.value}：${previewTarget.value.fileName || previewTarget.value.title || resource.value.title}`,
    anchorImage: '',
    anchorSeconds: null
  }
}

function selectedTextSnippet() {
  const text = window.getSelection?.()?.toString?.().trim() || ''
  return trimSnippet(text, 1000)
}

function fallbackDocumentSnippet() {
  if (previewKind.value === 'text' && previewText.value) {
    return trimSnippet(previewText.value, 360)
  }
  return `${previewLabel.value}：${previewTarget.value?.fileName || previewTarget.value?.title || resource.value?.title || ''}`
}

function resetNoteForm(values = {}) {
  Object.assign(noteForm, {
    id: null,
    title: '',
    content: '',
    category: '',
    resourceId: previewTarget.value?.id || null,
    anchorType: 'RESOURCE',
    anchorText: '',
    anchorImage: '',
    anchorSeconds: null
  }, values)
}

function editResourceNote(note) {
  resetNoteForm({
    id: note.id,
    title: note.title,
    content: note.content,
    category: note.category || '',
    resourceId: note.resourceId,
    anchorType: note.anchorType || 'RESOURCE',
    anchorText: note.anchorText || '',
    anchorImage: note.anchorImage || '',
    anchorSeconds: note.anchorSeconds
  })
  noteDialogVisible.value = true
}

async function saveResourceNote() {
  if (!noteForm.title || !noteForm.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  const payload = {
    title: noteForm.title,
    content: noteForm.content,
    category: noteForm.category || null,
    resourceId: noteForm.resourceId || previewTarget.value?.id,
    anchorType: noteForm.anchorType,
    anchorText: noteForm.anchorText || null,
    anchorImage: noteForm.anchorImage || null,
    anchorSeconds: noteForm.anchorSeconds,
    isFavorite: 0
  }
  savingNote.value = true
  try {
    if (noteForm.id) {
      await noteApi.update(noteForm.id, payload)
      ElMessage.success('笔记已更新')
    } else {
      await noteApi.create(payload)
      ElMessage.success('片段笔记已保存')
    }
    noteDialogVisible.value = false
    await loadResourceNotes()
  } finally {
    savingNote.value = false
  }
}

async function deleteResourceNote(note) {
  await ElMessageBox.confirm(`确定删除「${note.title}」吗？`, '删除笔记', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
  await noteApi.remove(note.id)
  ElMessage.success('笔记已删除')
  await loadResourceNotes()
}

async function confirmJumpToNote(note) {
  await ElMessageBox.confirm('是否跳转到这条笔记记录的资源片段？', '跳转确认', {
    confirmButtonText: '跳转',
    cancelButtonText: '取消'
  })
  if (Number(note.resourceId) === Number(previewTarget.value?.id)) {
    await applyNoteAnchor(note)
    return
  }
  router.push({
    path: `/resources/${note.resourceId}`,
    query: {
      noteId: note.id,
      t: note.anchorSeconds !== null && note.anchorSeconds !== undefined ? note.anchorSeconds : undefined
    }
  })
}

async function applyRouteAnchor() {
  if (!store.state.token && !route.query.t) return
  if (route.query.noteId && store.state.token) {
    try {
      const note = await noteApi.get(route.query.noteId)
      await applyNoteAnchor(note)
      return
    } catch {
      return
    }
  }
  const seconds = Number(route.query.t)
  if (Number.isFinite(seconds) && previewKind.value === 'video') {
    await nextTick()
    setTimeout(() => videoPlayerRef.value?.seekToTime?.(seconds), 250)
  }
}

async function applyNoteAnchor(note) {
  highlightAnchorText.value = ''
  document.querySelector('.preview-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  if (note.anchorType === 'VIDEO' && note.anchorSeconds !== null && note.anchorSeconds !== undefined && previewKind.value === 'video') {
    await nextTick()
    setTimeout(() => videoPlayerRef.value?.seekToTime?.(note.anchorSeconds), 250)
    ElMessage.success(`已定位到 ${formatAnchorSeconds(note.anchorSeconds)}`)
    return
  }
  if (note.anchorText && previewKind.value === 'text') {
    highlightAnchorText.value = note.anchorText
    await nextTick()
    const mark = textPreviewRef.value?.querySelector?.('.note-anchor-highlight')
    if (mark) {
      mark.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
  }
  ElMessage.info('已打开关联资源，请根据片段预览查看上下文')
}

function hasAnchorPreview(note) {
  return Boolean(note?.anchorImage || note?.anchorText || note?.anchorSeconds !== null && note?.anchorSeconds !== undefined)
}

function anchorFallback(note) {
  if (note?.anchorSeconds !== null && note?.anchorSeconds !== undefined) {
    return `视频 ${formatAnchorSeconds(note.anchorSeconds)} 处`
  }
  return note?.resourceTitle || note?.title || '资源片段'
}

function anchorTypeLabel(type) {
  return ({
    VIDEO: '视频画面',
    TEXT: '文本片段',
    DOCUMENT: '文档片段',
    RESOURCE: '资源片段'
  }[type] || '资源片段')
}

function formatAnchorSeconds(seconds) {
  const value = Number(seconds || 0)
  const total = Math.max(0, Math.floor(value))
  const hour = Math.floor(total / 3600)
  const minute = Math.floor((total % 3600) / 60)
  const second = total % 60
  if (hour > 0) {
    return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
  }
  return `${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
}

function previewNoteContent(content = '') {
  return trimSnippet(content, 160)
}

function trimSnippet(value = '', maxLength = 160) {
  const text = String(value || '').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function understandResource() {
  if (!resource.value) return
  router.push({
    path: '/agent/understand',
    query: {
      resourceId: resource.value.id,
      scope: isFolder.value ? 'folder' : undefined
    }
  })
}

function understandPreviewTarget() {
  if (!previewTarget.value) return
  router.push({
    path: '/agent/understand',
    query: { resourceId: previewTarget.value.id }
  })
}

function understandCurrentFolder() {
  if (!resource.value || !isFolder.value) {
    ElMessage.warning('Agent 理解当前目录只支持用户上传的虚拟文件夹')
    return
  }
  if (!currentAnalysisFileCount.value) {
    ElMessage.info('当前虚拟文件夹下没有可理解文件')
    return
  }
  router.push({
    path: '/agent/understand',
    query: {
      resourceId: resource.value.id,
      path: currentPath.value || undefined,
      scope: 'folder'
    }
  })
}

async function appendFilesAtCurrentPath(event) {
  const input = event.target
  try {
    await appendChildren(Array.from(input.files || []), false)
  } catch (error) {
    ElMessage.error(error.message || '追加文件失败')
  } finally {
    input.value = ''
  }
}

async function appendFolderAtCurrentPath(event) {
  const input = event.target
  try {
    await appendChildren(Array.from(input.files || []), true)
  } catch (error) {
    ElMessage.error(error.message || '追加文件夹失败')
  } finally {
    input.value = ''
  }
}

async function appendChildren(files, preserveFolderPath) {
  if (!resource.value || !files.length) return
  const data = new FormData()
  files.forEach((file) => {
    const sourcePath = preserveFolderPath ? (file.webkitRelativePath || file.name) : file.name
    data.append('files', file)
    data.append('relativePaths', joinPath(currentPath.value, sourcePath))
  })
  const created = await resourceApi.appendChildren(resource.value.id, data)
  selectedChildId.value = created[0]?.id || selectedChildId.value
  ElMessage.success(`已追加 ${created.length} 个文件`)
  await load()
}

async function renameChildFile(item) {
  const name = await promptText('重命名文件', '请输入新的文件名', item.fileName, item.fileName)
  if (!name) return
  const relativePath = joinPath(parentPath(item.relativePath || item.fileName), name)
  const updated = await resourceApi.updateFilePath(item.id, { fileName: name, relativePath })
  selectedChildId.value = updated.id
  ElMessage.success('文件已重命名')
  await load()
}

async function moveChildFile(item) {
  const target = await chooseTargetFolder('移动文件')
  if (target === null) return
  const relativePath = joinPath(target, item.fileName)
  const updated = await resourceApi.updateFilePath(item.id, { fileName: item.fileName, relativePath })
  selectedChildId.value = updated.id
  ElMessage.success(`已移动到 ${target || '全部文件'}`)
  await load()
}

async function renameLogicalFolder(path) {
  const currentName = path.split('/').pop()
  const name = await promptText('重命名文件夹', '请输入新的文件夹名', currentName, currentName)
  if (!name || !resource.value) return
  const targetPath = joinPath(parentPath(path), name)
  await resourceApi.updateFolderPath(resource.value.id, { sourcePath: path, targetPath })
  currentPath.value = targetPath
  ElMessage.success('文件夹已重命名')
  await load()
}

async function moveLogicalFolder(path) {
  if (!resource.value) return
  const target = await chooseTargetFolder('移动文件夹', path)
  if (target === null) return
  const targetPath = joinPath(target, path.split('/').pop())
  await resourceApi.updateFolderPath(resource.value.id, { sourcePath: path, targetPath })
  currentPath.value = targetPath
  ElMessage.success('文件夹已移动')
  await load()
}

async function deleteLogicalFolder(path) {
  if (!resource.value) return
  try {
    await ElMessageBox.confirm('删除该文件夹会同时删除其中所有文件，确定继续吗？', '删除文件夹', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  const keys = new Set(folderKeysFromPath(path))
  const targets = children.value.filter((item) => keys.has(String(item.id)))
  await Promise.all(targets.map((item) => resourceApi.remove(item.id)))
  selectedChildId.value = null
  currentPath.value = ''
  ElMessage.success('文件夹已删除')
  await load()
}

async function downloadResource() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (!resource.value) return
  await downloadTarget(resource.value)
}

async function downloadPreviewTarget() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (!previewTarget.value || previewTarget.value.resourceType === 'FOLDER') {
    ElMessage.warning('请先选择文件')
    return
  }
  await downloadTarget(previewTarget.value)
}

async function downloadTarget(item) {
  try {
    const response = await fetch(resourceApi.downloadUrl(item.id), {
      headers: {
        Authorization: `Bearer ${store.state.token}`
      }
    })
    const contentType = response.headers.get('content-type') || ''
    if (!response.ok) {
      throw new Error(`下载失败：${response.status}`)
    }
    if (contentType.includes('application/json')) {
      const data = await response.json()
      throw new Error(data.message || '下载失败')
    }
    const blob = await response.blob()
    saveBlob(blob, parseDownloadFilename(response.headers.get('content-disposition')) || fallbackDownloadName(item))
  } catch (error) {
    ElMessage.error(error.message || '下载失败')
  }
}

function parseDownloadFilename(disposition = '') {
  const utf8Name = disposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Name) {
    try {
      return decodeURIComponent(utf8Name[1])
    } catch {
      return utf8Name[1]
    }
  }
  return disposition.match(/filename="?([^"]+)"?/i)?.[1] || ''
}

function fallbackDownloadName(item) {
  const name = item.fileName || item.title || 'download'
  if (item.resourceType === 'FOLDER' && !name.toLowerCase().endsWith('.zip')) {
    return `${safeDownloadName(name)}.zip`
  }
  return safeDownloadName(name)
}

function safeDownloadName(name) {
  return String(name || 'download').replace(/[\\/:*?"<>|]/g, '_')
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename || 'download'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function openPreview() {
  if (isFolder.value && !selectedChild.value) {
    ElMessage.info('请先选择文件')
    return
  }
  if (previewKind.value === 'unsupported') {
    ElMessage.info('该文件类型仅支持下载')
    return
  }
  window.open(previewUrl.value, '_blank')
}

async function deleteResource(item) {
  if (!item || !canDelete(item)) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.fileName || item.title}」吗？删除后列表中将不再显示。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await resourceApi.remove(item.id)
  ElMessage.success('删除成功')
  if (Number(item.id) === Number(resource.value.id)) {
    router.push('/resources')
    return
  }
  selectedChildId.value = null
  await load()
}

async function toggleFavorite() {
  if (!store.state.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (resource.value.favorite) {
    await favoriteApi.removeByResource(resource.value.id)
    ElMessage.success('已取消收藏')
  } else {
    await favoriteApi.create(resource.value.id)
    ElMessage.success('收藏成功')
  }
  await load()
}

async function submitComment() {
  await commentApi.create({ resourceId: Number(route.params.id), content: commentForm.content, rating: commentForm.rating })
  commentForm.content = ''
  ElMessage.success('评论成功')
  await load()
}

async function loadDocxPreview() {
  await nextTick()
  if (!docxContainer.value || !previewTarget.value) return
  docxContainer.value.innerHTML = ''
  try {
    const buffer = await fetchPreviewArrayBuffer()
    await renderAsync(buffer, docxContainer.value, null, {
      className: 'docx',
      inWrapper: true,
      ignoreWidth: false,
      ignoreHeight: false,
      breakPages: true
    })
  } catch (error) {
    ElMessage.error(error.message || 'DOCX 预览失败')
  }
}

async function loadPptxPreview() {
  clearObjectUrls()
  pptxSlides.value = []
  try {
    const buffer = await fetchPreviewArrayBuffer()
    const zip = await JSZip.loadAsync(buffer)
    const slideNames = Object.keys(zip.files)
      .filter((name) => /^ppt\/slides\/slide\d+\.xml$/.test(name))
      .sort((a, b) => slideNumber(a) - slideNumber(b))
    const mediaMap = await buildPptxMediaMap(zip)
    pptxSlides.value = await Promise.all(slideNames.map(async (name, index) => {
      const xml = await zip.file(name).async('string')
      const rels = await readSlideRelationships(zip, slideNumber(name))
      const texts = extractPptxText(xml)
      const images = extractPptxImages(xml, rels, mediaMap)
      return { name, title: `第 ${index + 1} 页`, texts, images }
    }))
  } catch (error) {
    ElMessage.error(error.message || 'PPTX 预览失败')
  }
}

async function fetchPreviewArrayBuffer() {
  const response = await fetch(previewUrl.value)
  if (!response.ok) {
    throw new Error(`预览文件加载失败：${response.status}`)
  }
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const data = await response.json()
    throw new Error(data.message || '预览文件加载失败')
  }
  return response.arrayBuffer()
}

function extractPptxText(xml) {
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  return Array.from(document.getElementsByTagName('a:t'))
    .map((node) => node.textContent.trim())
    .filter(Boolean)
}

async function readSlideRelationships(zip, slideNo) {
  const file = zip.file(`ppt/slides/_rels/slide${slideNo}.xml.rels`)
  if (!file) return {}
  const xml = await file.async('string')
  const document = new DOMParser().parseFromString(xml, 'application/xml')
  const rels = {}
  Array.from(document.getElementsByTagName('Relationship')).forEach((node) => {
    rels[node.getAttribute('Id')] = node.getAttribute('Target')
  })
  return rels
}

async function buildPptxMediaMap(zip) {
  const entries = Object.keys(zip.files).filter((name) => name.startsWith('ppt/media/'))
  const result = {}
  await Promise.all(entries.map(async (name) => {
    const blob = await zip.file(name).async('blob')
    const src = URL.createObjectURL(blob)
    objectUrls.push(src)
    result[name] = { name, src }
  }))
  return result
}

function extractPptxImages(xml, rels, mediaMap) {
  const ids = Array.from(xml.matchAll(/r:embed="([^"]+)"/g)).map((match) => match[1])
  return ids
    .map((id) => rels[id])
    .filter(Boolean)
    .map((target) => target.replace(/^\.\.\//, 'ppt/'))
    .map((name) => mediaMap[name])
    .filter(Boolean)
}

function slideNumber(path) {
  return Number(path.match(/slide(\d+)\.xml/)?.[1] || 0)
}

function clearObjectUrls() {
  while (objectUrls.length) {
    URL.revokeObjectURL(objectUrls.pop())
  }
}

watch([previewKind, previewTarget], async ([kind]) => {
  previewText.value = ''
  pptxSlides.value = []
  legacyHtml.value = ''
  legacyError.value = ''
  highlightAnchorText.value = ''
  clearObjectUrls()
  if (kind === 'text' && previewTarget.value) {
    previewText.value = await resourceApi.previewText(previewTarget.value.id)
  }
  if (kind === 'docx') {
    await loadDocxPreview()
  }
  if (kind === 'pptx') {
    await loadPptxPreview()
  }
  if (kind === 'legacy-office') {
    await loadLegacyPreview()
  }
  await loadResourceNotes()
  await refreshEnhancementPanel()
  await applyRouteAnchor()
})

watch(() => route.params.id, async () => {
  await load()
})

watch(() => [route.query.noteId, route.query.t], async () => {
  await applyRouteAnchor()
})

async function loadLegacyPreview() {
  legacyHtml.value = ''
  legacyError.value = ''
  if (!previewTarget.value) return
  const name = previewTarget.value.fileName || previewTarget.value.title || ''
  const kind = legacyKind(name)
  if (!kind) {
    legacyError.value = '不支持的老版 Office 格式'
    return
  }
  legacyLoading.value = true
  try {
    const response = await fetch(previewUrl.value)
    if (!response.ok) throw new Error(`加载失败：${response.status}`)
    const buffer = await response.arrayBuffer()
    if (kind === 'spreadsheet') {
      legacyHtml.value = renderSpreadsheet(buffer, name)
    } else if (kind === 'doc') {
      legacyHtml.value = renderDoc(buffer, name)
    } else if (kind === 'ppt') {
      legacyHtml.value = renderPpt(buffer, name)
    }
  } catch (error) {
    legacyError.value = error.message || '解析失败'
  } finally {
    legacyLoading.value = false
  }
}

function reloadLegacy() {
  loadLegacyPreview()
}

onMounted(load)
onBeforeUnmount(() => {
  clearObjectUrls()
  stopEnhancementPolling()
})
</script>

<style scoped>
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.detail-tags span {
  max-width: 160px;
  overflow: hidden;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(238, 246, 246, 0.96);
  color: var(--primary-strong);
  font-size: 12px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-enhance-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.media-enhance-header,
.media-job-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.media-enhance-header h2 {
  margin: 4px 0 0;
}

.media-enhance-controls {
  display: grid;
  grid-template-columns: minmax(280px, 420px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.media-enhance-form {
  max-width: 420px;
}

.media-enhance-summary {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.86);
  border-radius: 6px;
  background: #f8fafc;
}

.media-enhance-summary strong,
.media-job-title strong {
  color: #1f2937;
  line-height: 1.35;
  word-break: break-word;
}

.media-enhance-summary span,
.media-job-main p,
.media-job-main > span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.media-enhance-actions,
.media-job-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.media-job-list {
  display: grid;
  gap: 12px;
}

.media-job-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.86);
  border-radius: 6px;
  background: #ffffff;
}

.media-job-main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.note-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.note-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.note-panel-header h2 {
  margin: 4px 0 0;
}

.resource-note-list {
  display: grid;
  gap: 12px;
}

.resource-note-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  padding: 14px;
  border: 1px solid rgba(196, 213, 228, 0.8);
  border-radius: 6px;
  background: #ffffff;
}

.resource-note-main {
  min-width: 0;
}

.resource-note-card h3 {
  margin: 0 0 6px;
  color: #1f2937;
  font-size: 17px;
  line-height: 1.3;
  word-break: break-word;
}

.resource-note-card p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-word;
}

.resource-note-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #64748b;
  font-size: 12px;
}

.resource-note-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.note-snippet-card,
.note-dialog-snippet {
  display: grid;
  grid-template-columns: minmax(96px, 160px) minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid rgba(196, 213, 228, 0.88);
  border-radius: 6px;
  background: #f8fafc;
  color: #1f2937;
  text-align: left;
}

.note-snippet-card {
  cursor: pointer;
  transition: border-color 150ms ease, background 150ms ease;
}

.note-snippet-card:hover {
  border-color: var(--primary);
  background: #eef7f7;
}

.note-snippet-card img,
.note-dialog-snippet img {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 4px;
  object-fit: cover;
  background: #111827;
}

.note-snippet-card:not(:has(img)),
.note-dialog-snippet:not(:has(img)) {
  grid-template-columns: minmax(0, 1fr);
}

.snippet-text,
.note-dialog-snippet p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  word-break: break-word;
}

.note-dialog-snippet {
  margin: 0 0 16px;
}

.note-dialog-snippet strong {
  display: block;
  margin-bottom: 4px;
  color: #0f766e;
  font-size: 13px;
}

.note-anchor-highlight {
  padding: 2px 0;
  background: #fff2a8;
  color: #111827;
}

@media (max-width: 720px) {
  .media-enhance-header,
  .media-enhance-controls,
  .media-job-card,
  .media-job-actions,
  .note-panel-header,
  .resource-note-card,
  .resource-note-actions {
    align-items: stretch;
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .media-enhance-form {
    max-width: none;
  }

  .note-snippet-card,
  .note-dialog-snippet {
    grid-template-columns: 1fr;
  }
}
</style>
