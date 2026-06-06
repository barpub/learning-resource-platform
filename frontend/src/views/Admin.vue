<template>
  <div class="page-card">
    <h1 class="section-title">管理员后台</h1>
    <el-tabs v-model="tab" @tab-change="loadCurrent">
      <el-tab-pane label="用户管理" name="users">
        <el-table :data="users" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column prop="role" label="角色" />
          <el-table-column prop="status" label="状态" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button size="small" @click="toggleUser(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
              <el-button size="small" type="danger" @click="removeUser(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="资源管理" name="resources">
        <el-table :data="resources" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="categoryName" label="分类" />
          <el-table-column prop="username" label="上传者" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeResource(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="分类管理" name="categories">
        <div class="toolbar">
          <el-input v-model="categoryForm.name" placeholder="分类名称" style="width: 180px" />
          <el-input v-model="categoryForm.description" placeholder="描述" style="width: 260px" />
          <el-button type="primary" @click="saveCategory">新增分类</el-button>
        </div>
        <el-table :data="categories" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="description" label="描述" />
          <el-table-column prop="resourceCount" label="资源数" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeCategory(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="评论管理" name="comments">
        <el-table :data="comments" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户" />
          <el-table-column prop="content" label="内容" />
          <el-table-column prop="rating" label="评分" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeComment(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="FTP 连接" name="ftp">
        <div class="toolbar">
          <el-button type="primary" @click="openFtpDialog()">新增连接</el-button>
          <el-button @click="loadFtp">刷新</el-button>
        </div>
        <el-table :data="ftpConnections" border>
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="name" label="名称" />
          <el-table-column label="地址" width="220">
            <template #default="{ row }">{{ row.host }}:{{ row.port }}</template>
          </el-table-column>
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column label="模式" width="90">
            <template #default="{ row }">{{ row.passiveMode === 0 ? '主动' : '被动' }}</template>
          </el-table-column>
          <el-table-column prop="homePath" label="起始目录" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" @click="testFtp(row)">测试</el-button>
              <el-button size="small" @click="openFtpDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeFtp(row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="ftpDialog.visible"
      :title="ftpDialog.id ? '编辑 FTP 连接' : '新增 FTP 连接'"
      width="560px"
      append-to-body
      :modal-append-to-body="true"
    >
      <el-form :model="ftpForm" label-width="110px" label-position="right">
        <el-form-item label="名称" required>
          <el-input v-model="ftpForm.name" placeholder="给这个连接起个名字" maxlength="100" />
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="ftpForm.host" placeholder="例如 ftp.example.com" maxlength="255" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="ftpForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="ftpForm.username" placeholder="默认 anonymous" maxlength="100" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="ftpForm.password"
            type="password"
            show-password
            :placeholder="ftpDialog.id ? '留空表示保持原密码' : '可留空'"
          />
        </el-form-item>
        <el-form-item v-if="ftpDialog.id" label="重置密码">
          <el-switch v-model="ftpForm.clearPassword" />
          <span class="muted" style="margin-left: 8px; font-size: 12px">开启后保存将清空已存储的密码</span>
        </el-form-item>
        <el-form-item label="被动模式">
          <el-switch v-model="ftpForm.passiveMode" />
        </el-form-item>
        <el-form-item label="编码">
          <el-select v-model="ftpForm.encoding" style="width: 100%">
            <el-option label="UTF-8（推荐，Linux/现代服务器）" value="UTF-8" />
            <el-option label="GBK（常见中文 Windows FTP）" value="GBK" />
            <el-option label="GB18030" value="GB18030" />
            <el-option label="Big5（繁体中文）" value="Big5" />
            <el-option label="ISO-8859-1" value="ISO-8859-1" />
          </el-select>
        </el-form-item>
        <el-form-item label="起始目录">
          <el-input v-model="ftpForm.homePath" placeholder="默认 /" maxlength="500" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="ftpForm.status" style="width: 160px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="ftpForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testFtpForm" :loading="ftpTesting">测试连接</el-button>
        <el-button @click="ftpDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveFtp" :loading="ftpSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, commentApi, ftpApi, resourceApi, userApi } from '../api'

const tab = ref('users')
const users = ref([])
const resources = ref([])
const categories = ref([])
const comments = ref([])
const ftpConnections = ref([])
const categoryForm = reactive({ name: '', description: '', parentId: 0, sortOrder: 0 })
const ftpDialog = reactive({ visible: false, id: null })
const ftpForm = reactive(buildDefaultFtpForm())
const ftpSaving = ref(false)
const ftpTesting = ref(false)

function buildDefaultFtpForm() {
  return {
    name: '',
    host: '',
    port: 21,
    username: 'anonymous',
    password: '',
    clearPassword: false,
    passiveMode: true,
    encoding: 'UTF-8',
    homePath: '/',
    description: '',
    status: 1
  }
}

async function loadUsers() {
  users.value = await userApi.list()
}

async function loadResources() {
  const page = await resourceApi.page({ page: 1, size: 50 })
  resources.value = page.records || []
}

async function loadCategories() {
  categories.value = await categoryApi.list()
}

async function loadComments() {
  comments.value = await commentApi.all()
}

function loadCurrent() {
  if (tab.value === 'users') loadUsers()
  if (tab.value === 'resources') loadResources()
  if (tab.value === 'categories') loadCategories()
  if (tab.value === 'comments') loadComments()
  if (tab.value === 'ftp') loadFtp()
}

async function toggleUser(row) {
  await userApi.updateStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('用户状态已更新')
  await loadUsers()
}

async function removeUser(id) {
  await confirmDanger()
  await userApi.remove(id)
  await loadUsers()
}

async function removeResource(id) {
  await confirmDanger()
  await resourceApi.remove(id)
  await loadResources()
}

async function saveCategory() {
  await categoryApi.create(categoryForm)
  categoryForm.name = ''
  categoryForm.description = ''
  ElMessage.success('分类已创建')
  await loadCategories()
}

async function removeCategory(id) {
  await confirmDanger()
  await categoryApi.remove(id)
  await loadCategories()
}

async function removeComment(id) {
  await confirmDanger()
  await commentApi.remove(id)
  await loadComments()
}

function confirmDanger() {
  return ElMessageBox.confirm('确认执行该操作？', '提示', { type: 'warning' })
}

async function loadFtp() {
  ftpConnections.value = await ftpApi.admin.list()
}

function openFtpDialog(row) {
  Object.assign(ftpForm, buildDefaultFtpForm())
  if (row) {
    ftpDialog.id = row.id
    ftpForm.name = row.name
    ftpForm.host = row.host
    ftpForm.port = row.port || 21
    ftpForm.username = row.username || 'anonymous'
    ftpForm.password = ''
    ftpForm.clearPassword = false
    ftpForm.passiveMode = row.passiveMode !== 0
    ftpForm.encoding = row.encoding || 'UTF-8'
    ftpForm.homePath = row.homePath || '/'
    ftpForm.description = row.description || ''
    ftpForm.status = row.status === 0 ? 0 : 1
  } else {
    ftpDialog.id = null
  }
  ftpDialog.visible = true
}

function ftpPayload() {
  const payload = {
    name: ftpForm.name.trim(),
    host: ftpForm.host.trim(),
    port: Number(ftpForm.port) || 21,
    username: (ftpForm.username || '').trim() || 'anonymous',
    passiveMode: Boolean(ftpForm.passiveMode),
    encoding: ftpForm.encoding || 'UTF-8',
    homePath: ftpForm.homePath || '/',
    description: ftpForm.description || '',
    status: ftpForm.status === 0 ? 0 : 1,
    clearPassword: Boolean(ftpForm.clearPassword)
  }
  if (ftpForm.password) payload.password = ftpForm.password
  return payload
}

async function saveFtp() {
  if (!ftpForm.name.trim() || !ftpForm.host.trim()) {
    ElMessage.warning('名称和主机地址必填')
    return
  }
  ftpSaving.value = true
  try {
    if (ftpDialog.id) {
      await ftpApi.admin.update(ftpDialog.id, ftpPayload())
      ElMessage.success('已更新连接')
    } else {
      await ftpApi.admin.create(ftpPayload())
      ElMessage.success('已创建连接')
    }
    ftpDialog.visible = false
    await loadFtp()
  } finally {
    ftpSaving.value = false
  }
}

async function testFtpForm() {
  if (!ftpForm.host.trim()) {
    ElMessage.warning('请先填写主机地址')
    return
  }
  ftpTesting.value = true
  try {
    const info = ftpDialog.id
      ? await ftpApi.admin.testExisting(ftpDialog.id, ftpPayload())
      : await ftpApi.admin.testNew(ftpPayload())
    showFtpTestResult(info)
  } finally {
    ftpTesting.value = false
  }
}

async function testFtp(row) {
  const info = await ftpApi.admin.testExisting(row.id, {})
  showFtpTestResult(info)
}

function showFtpTestResult(info) {
  if (!info) return
  if (info.success) {
    ElMessage.success(`连接成功，耗时 ${info.elapsedMs} ms，当前目录 ${info.workingDir || '-'}`)
  } else {
    ElMessage.error(info.message || '连接失败')
  }
}

async function removeFtp(id) {
  await confirmDanger()
  await ftpApi.admin.remove(id)
  ElMessage.success('已删除连接')
  await loadFtp()
}

onMounted(loadCurrent)
</script>
