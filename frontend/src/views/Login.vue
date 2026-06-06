<template>
  <div class="auth-page">
    <section class="auth-visual">
      <span class="eyebrow">Welcome Back</span>
      <h1>继续管理你的学习资源库</h1>
      <p>登录后可以上传资料、收藏资源、评论评分，并在详情页直接预览文档。</p>
      <div class="auth-feature-list">
        <span>在线文档预览</span>
        <span>资源收藏</span>
        <span>分类检索</span>
      </div>
    </section>

    <section class="auth-card">
      <h2>用户登录</h2>
      <p class="muted">使用账号进入资源共享平台</p>
      <el-form :model="form" @submit.prevent>
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-button class="full-button" type="primary" size="large" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
      <div class="auth-switch">
        没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
      <p class="auth-tip">默认账号：admin / admin123456，普通用户：user / user123456</p>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'

const route = useRoute()
const router = useRouter()
const store = useStore()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123456' })

async function submit() {
  loading.value = true
  try {
    const data = await authApi.login(form)
    store.commit('setAuth', data)
    ElMessage.success('登录成功')
    router.push(route.query.redirect ? decodeURIComponent(route.query.redirect) : '/')
  } finally {
    loading.value = false
  }
}
</script>
