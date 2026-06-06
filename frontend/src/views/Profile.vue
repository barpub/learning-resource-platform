<template>
  <div class="profile-layout">
    <section class="panel profile-card">
      <div class="avatar-ring">{{ initials }}</div>
      <h1>{{ form.nickname || form.username }}</h1>
      <p class="muted">{{ form.email }}</p>
    </section>

    <section class="panel form-panel">
      <span class="eyebrow">Profile</span>
      <h2>个人资料</h2>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名"><el-input v-model="form.username" disabled size="large" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" size="large" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" size="large" /></el-form-item>
        <el-form-item label="头像地址"><el-input v-model="form.avatar" size="large" /></el-form-item>
        <el-button type="primary" @click="save">保存资料</el-button>
      </el-form>

      <el-divider />
      <span class="eyebrow">Security</span>
      <h2>修改密码</h2>
      <el-form :model="passwordForm" label-position="top">
        <el-form-item label="原密码"><el-input v-model="passwordForm.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
        <el-button @click="changePassword">修改密码</el-button>
      </el-form>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { userApi } from '../api'

const store = useStore()
const form = reactive({ username: '', nickname: '', email: '', avatar: '' })
const passwordForm = reactive({ oldPassword: '', newPassword: '' })
const initials = computed(() => (form.nickname || form.username || 'U').slice(0, 2).toUpperCase())

async function load() {
  const user = await userApi.profile()
  Object.assign(form, user)
  store.commit('setUser', user)
}

async function save() {
  const user = await userApi.updateProfile({ nickname: form.nickname, email: form.email, avatar: form.avatar })
  store.commit('setUser', user)
  ElMessage.success('保存成功')
}

async function changePassword() {
  await userApi.updatePassword(passwordForm)
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  ElMessage.success('密码已更新')
}

onMounted(load)
</script>
