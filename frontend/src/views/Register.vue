<template>
  <div class="auth-page">
    <section class="auth-visual register-visual">
      <span class="eyebrow">Join The Hub</span>
      <h1>建立自己的学习资料档案</h1>
      <p>注册后可以上传、收藏、评分和管理学习资源，让资料流转更清晰。</p>
      <div class="auth-feature-list">
        <span>快速上传</span>
        <span>资料沉淀</span>
        <span>协作评价</span>
      </div>
    </section>

    <section class="auth-card">
      <h2>创建账号</h2>
      <p class="muted">填写基础信息后即可登录</p>
      <el-form :model="form">
        <el-form-item><el-input v-model="form.username" size="large" placeholder="用户名" /></el-form-item>
        <el-form-item><el-input v-model="form.email" size="large" placeholder="邮箱" /></el-form-item>
        <el-form-item><el-input v-model="form.nickname" size="large" placeholder="昵称" /></el-form-item>
        <el-form-item><el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" /></el-form-item>
        <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">
          注册
        </el-button>
      </el-form>
      <div class="auth-switch">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: '', email: '', nickname: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await authApi.register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>
