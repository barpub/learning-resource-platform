import { createRouter, createWebHistory } from 'vue-router'
import store from '../store'

const routes = [
  { path: '/', component: () => import('../views/Home.vue') },
  { path: '/login', component: () => import('../views/Login.vue') },
  { path: '/register', component: () => import('../views/Register.vue') },
  { path: '/search', component: () => import('../views/GlobalSearch.vue') },
  { path: '/forum', component: () => import('../views/Forum.vue') },
  { path: '/agent/search', redirect: (to) => ({ path: '/agent/understand', query: to.query }) },
  { path: '/agent/analyze', redirect: (to) => ({ path: '/agent/understand', query: to.query }) },
  { path: '/agent/understand', component: () => import('../views/AgentUnderstand.vue') },
  { path: '/resources', component: () => import('../views/ResourceList.vue') },
  { path: '/resources/:id', component: () => import('../views/ResourceDetail.vue') },
  { path: '/upload', component: () => import('../views/Upload.vue'), meta: { requiresAuth: true } },
  { path: '/mine', component: () => import('../views/Mine.vue'), meta: { requiresAuth: true } },
  { path: '/profile', component: () => import('../views/Profile.vue'), meta: { requiresAuth: true } },
  { path: '/favorites', component: () => import('../views/Favorites.vue'), meta: { requiresAuth: true } },
  { path: '/notes/share/:token', component: () => import('../views/NoteShareImport.vue') },
  { path: '/notes', component: () => import('../views/Notes.vue'), meta: { requiresAuth: true } },
  { path: '/ftp', redirect: '/resources?source=ftp' },
  { path: '/admin', component: () => import('../views/Admin.vue'), meta: { requiresAuth: true, admin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !store.state.token) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  if (to.meta.admin && store.state.user?.role !== 'ADMIN') {
    return '/'
  }
})

export default router
