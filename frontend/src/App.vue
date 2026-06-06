<template>
  <el-container class="app-shell">
    <FlowBackground />

    <div ref="headerStackRef" class="header-stack">
      <div class="section-strip">
        <router-link to="/">头部作品</router-link>
        <router-link to="/resources?sort=rating">高评分</router-link>
        <router-link to="/resources?sort=downloadCount">下载榜</router-link>
        <router-link to="/resources?source=ftp">远程库</router-link>
        <router-link to="/forum">资源论坛</router-link>
        <router-link to="/agent/analyze">分析Agent</router-link>
        <router-link to="/beta/recommendations">Beta 推荐</router-link>
      </div>

      <el-header class="app-header">
        <router-link to="/" class="brand">
          <span class="brand-mark">LR</span>
          <span>
            <strong>学习资源共享平台</strong>
            <small>Learning Resource Hub</small>
          </span>
        </router-link>

        <nav class="nav">
          <router-link to="/">发现</router-link>
          <router-link to="/resources">资源库</router-link>
          <router-link to="/forum">资源论坛</router-link>
          <router-link to="/search">资源搜索</router-link>
          <router-link to="/agent/analyze">分析Agent</router-link>
          <router-link to="/beta/recommendations">Beta推荐</router-link>
          <router-link to="/beta/master-repair">母版修复</router-link>
          <router-link v-if="isLoggedIn" to="/mine">我的</router-link>
          <router-link v-if="isLoggedIn" to="/upload">上传</router-link>
          <router-link v-if="isLoggedIn" to="/favorites">收藏</router-link>
          <router-link v-if="isAdmin" to="/admin">管理</router-link>
        </nav>

        <div class="account">
          <router-link v-if="!isLoggedIn" to="/login" class="ghost-link">登录</router-link>
          <router-link v-if="!isLoggedIn" to="/register" class="primary-link">注册</router-link>
          <router-link v-if="isLoggedIn" to="/profile" class="user-chip">
            {{ user?.nickname || user?.username }}
          </router-link>
          <el-button v-if="isLoggedIn" link @click="logout">退出</el-button>
        </div>
      </el-header>
    </div>

    <div
      v-if="routeOverlayVisible"
      :key="routeOverlayKey"
      class="route-transition-overlay"
      :class="`route-transition-overlay--${routeOverlayMode}`"
      aria-hidden="true"
    ></div>

    <el-main class="app-main">
      <div ref="routeStageRef" class="route-stage">
        <router-view v-slot="{ Component, route }">
          <transition
            :name="transitionName"
            @before-enter="prepareRouteTransition"
            @before-leave="prepareRouteTransition"
            @after-enter="finishRouteTransition"
          >
            <component :is="Component" :key="route.fullPath" class="route-page" />
          </transition>
        </router-view>
      </div>
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import FlowBackground from './components/FlowBackground.vue'

const store = useStore()
const router = useRouter()
const headerStackRef = ref(null)
const routeStageRef = ref(null)
const routeOverlayVisible = ref(false)
const routeOverlayKey = ref(0)
const routeOverlayMode = ref('open')
const transitionDirection = ref('open')
const user = computed(() => store.state.user)
const isLoggedIn = computed(() => Boolean(store.state.token))
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const transitionName = computed(() => 'route-fade')

const routeOrigins = new Map()
let pendingOrigin = null
let activeOrigin = null
let transitionOrigin = null
let popNavigation = false
let removeBeforeHook = null
let removeAfterHook = null
let headerResizeObserver = null
let overlayTimer = 0
let overlayPlayedForNavigation = false

function logout() {
  store.commit('logout')
  router.push('/login')
}

function rememberTransitionOrigin(event) {
  if (event.button !== undefined && event.button !== 0) return
  const source = event.target?.closest?.([
    'a',
    'button',
    '.resource-card',
    '.category-tab',
    '.quick-action',
    '.latest-item',
    '.hot-item',
    '.mini-rank-item',
    '.featured-slide',
    '.el-button'
  ].join(','))
  if (!source) return
  const rect = source.getBoundingClientRect()
  if (rect.width < 2 || rect.height < 2) return
  pendingOrigin = {
    left: rect.left,
    top: rect.top,
    right: rect.right,
    bottom: rect.bottom,
    width: rect.width,
    height: rect.height
  }
}

function markPopNavigation() {
  popNavigation = true
}

function defaultOrigin() {
  const stageRect = routeStageRef.value?.getBoundingClientRect()
  const width = stageRect?.width || window.innerWidth
  const top = stageRect?.top || 0
  const left = (stageRect?.left || 0) + width / 2 - 90
  return {
    left,
    top: top + 80,
    right: left + 180,
    bottom: top + 170,
    width: 180,
    height: 90
  }
}

function prepareRouteTransition() {
  const stage = routeStageRef.value
  if (!stage) return
  updateRouteViewportSize()
  const rect = stage.getBoundingClientRect()
  const visibleHeight = Math.max(window.innerHeight - Math.max(rect.top, 0) - 64, 360)
  stage.style.minHeight = `${Math.max(rect.height, visibleHeight, 360)}px`
  if (!overlayPlayedForNavigation) {
    if (!routeOverlayVisible.value) {
      playRouteOverlay(transitionOrigin || activeOrigin || defaultOrigin())
    }
    overlayPlayedForNavigation = true
  }
}

function finishRouteTransition() {
  const stage = routeStageRef.value
  if (!stage) return
  window.setTimeout(() => {
    stage.style.minHeight = ''
  }, 80)
}

function applyTransitionOrigin(origin) {
  const stage = routeStageRef.value
  if (!stage) return
  const stageRect = stage.getBoundingClientRect()
  const width = Math.max(stageRect.width, 1)
  const height = Math.max(stageRect.height, 1)
  const source = origin || defaultOrigin()
  const rawWidth = Math.max(
    source?.width || ((source?.right ?? 0) - (source?.left ?? 0)),
    Math.min(width, 96)
  )
  const rawHeight = Math.max(
    source?.height || ((source?.bottom ?? 0) - (source?.top ?? 0)),
    Math.min(height, 64)
  )
  const originWidth = Math.min(width, rawWidth)
  const originHeight = Math.min(height, rawHeight)
  const centerX = (
    (source?.left !== undefined && source?.right !== undefined)
      ? (source.left + source.right) / 2
      : (source?.left ?? stageRect.left + width / 2)
  ) - stageRect.left
  const centerY = (
    (source?.top !== undefined && source?.bottom !== undefined)
      ? (source.top + source.bottom) / 2
      : (source?.top ?? stageRect.top + 110)
  ) - stageRect.top
  const left = clamp(centerX - originWidth / 2, 0, Math.max(width - originWidth, 0))
  const top = clamp(centerY - originHeight / 2, 0, Math.max(height - originHeight, 0))
  const right = Math.max(0, width - left - originWidth)
  const bottom = Math.max(0, height - top - originHeight)
  const radius = clamp(Math.min(originWidth, originHeight) / 5, 8, 18)
  stage.style.setProperty('--flow-left', `${left}px`)
  stage.style.setProperty('--flow-top', `${top}px`)
  stage.style.setProperty('--flow-right', `${right}px`)
  stage.style.setProperty('--flow-bottom', `${bottom}px`)
  stage.style.setProperty('--flow-radius', `${radius}px`)
}

function playRouteOverlay(origin) {
  applyOverlayOrigin(origin)
  routeOverlayMode.value = transitionDirection.value
  routeOverlayKey.value += 1
  routeOverlayVisible.value = true
  window.clearTimeout(overlayTimer)
  overlayTimer = window.setTimeout(() => {
    routeOverlayVisible.value = false
  }, 760)
}

function applyOverlayOrigin(origin) {
  const width = Math.max(window.innerWidth, 1)
  const height = Math.max(window.innerHeight, 1)
  const source = origin || defaultOrigin()
  const rawWidth = Math.max(
    source?.width || ((source?.right ?? 0) - (source?.left ?? 0)),
    Math.min(width, 96)
  )
  const rawHeight = Math.max(
    source?.height || ((source?.bottom ?? 0) - (source?.top ?? 0)),
    Math.min(height, 64)
  )
  const originWidth = Math.min(width, rawWidth)
  const originHeight = Math.min(height, rawHeight)
  const centerX = (
    (source?.left !== undefined && source?.right !== undefined)
      ? (source.left + source.right) / 2
      : (source?.left ?? width / 2)
  )
  const centerY = (
    (source?.top !== undefined && source?.bottom !== undefined)
      ? (source.top + source.bottom) / 2
      : (source?.top ?? 110)
  )
  const left = clamp(centerX - originWidth / 2, 0, Math.max(width - originWidth, 0))
  const top = clamp(centerY - originHeight / 2, 0, Math.max(height - originHeight, 0))
  const right = Math.max(0, width - left - originWidth)
  const bottom = Math.max(0, height - top - originHeight)
  const radius = clamp(Math.min(originWidth, originHeight) / 5, 8, 18)
  document.documentElement.style.setProperty('--route-overlay-left', `${left}px`)
  document.documentElement.style.setProperty('--route-overlay-top', `${top}px`)
  document.documentElement.style.setProperty('--route-overlay-right', `${right}px`)
  document.documentElement.style.setProperty('--route-overlay-bottom', `${bottom}px`)
  document.documentElement.style.setProperty('--route-overlay-radius', `${radius}px`)
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function updateRouteViewportSize() {
  const headerHeight = headerStackRef.value?.getBoundingClientRect().height || 106
  document.documentElement.style.setProperty('--app-header-height', `${headerHeight}px`)
}

onMounted(() => {
  updateRouteViewportSize()
  activeOrigin = defaultOrigin()
  routeOrigins.set(router.currentRoute.value.fullPath, activeOrigin)
  document.addEventListener('pointerdown', rememberTransitionOrigin, true)
  window.addEventListener('popstate', markPopNavigation)
  window.addEventListener('resize', updateRouteViewportSize)
  if (typeof ResizeObserver !== 'undefined' && headerStackRef.value) {
    headerResizeObserver = new ResizeObserver(updateRouteViewportSize)
    headerResizeObserver.observe(headerStackRef.value)
  }
  removeBeforeHook = router.beforeEach((to, from) => {
    overlayPlayedForNavigation = false
    transitionDirection.value = popNavigation ? 'close' : 'open'
    if (popNavigation) {
      transitionOrigin = routeOrigins.get(from.fullPath) || activeOrigin || defaultOrigin()
    } else {
      transitionOrigin = pendingOrigin || defaultOrigin()
      routeOrigins.set(to.fullPath, transitionOrigin)
    }
  })
  removeAfterHook = router.afterEach((to) => {
    activeOrigin = routeOrigins.get(to.fullPath) || transitionOrigin || defaultOrigin()
    routeOrigins.set(to.fullPath, activeOrigin)
    pendingOrigin = null
    popNavigation = false
  })
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', rememberTransitionOrigin, true)
  window.removeEventListener('popstate', markPopNavigation)
  window.removeEventListener('resize', updateRouteViewportSize)
  headerResizeObserver?.disconnect()
  window.clearTimeout(overlayTimer)
  removeBeforeHook?.()
  removeAfterHook?.()
})
</script>
