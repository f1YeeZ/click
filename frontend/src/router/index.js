import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { readStoredJson } from '../utils/storage'
import { refreshAccessToken, clearStoredSession } from '../api/client'
import { trackPageView } from '../services/trafficAnalytics'

const MiceView = () => import('../views/MiceView.vue')
const MouseDetailView = () => import('../views/MouseDetailView.vue')
const CompareView = () => import('../views/CompareView.vue')
const AuthView = () => import('../views/AuthView.vue')
const PasswordResetView = () => import('../views/PasswordResetView.vue')
const AdminView = () => import('../views/AdminView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const RecommendationView = () => import('../views/RecommendationView.vue')
const SensitivityView = () => import('../views/SensitivityView.vue')
const MouseTestView = () => import('../views/MouseTestView.vue')
const LegalView = () => import('../views/LegalView.vue')
const CodeMapView = () => import('../views/CodeMapView.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/mice', component: MiceView },
    { path: '/mice/:id', component: MouseDetailView },
    { path: '/compare', component: CompareView },
    { path: '/recommend', component: RecommendationView },
    { path: '/sensitivity', component: SensitivityView },
    { path: '/mouse-test', component: MouseTestView },
    { path: '/privacy', component: LegalView, props: { document: 'privacy' } },
    { path: '/terms', component: LegalView, props: { document: 'terms' } },
    { path: '/review-rules', component: LegalView, props: { document: 'rules' } },
    { path: '/login', component: AuthView, props: { mode: 'login' } },
    { path: '/register', component: AuthView, props: { mode: 'register' } },
    { path: '/forgot-password', component: PasswordResetView },
    { path: '/profile', component: ProfileView, meta: { requiresAuth: true } },
    { path: '/admin/login', component: AuthView, props: { mode: 'login', admin: true } },
    { path: '/admin', component: AdminView, meta: { requiresAdmin: true } },
    ...(import.meta.env.DEV ? [{ path: '/dev/code-map', component: CodeMapView }] : [])
  ],
  // Route transitions should not compete with a smooth scroll animation.
  scrollBehavior: () => ({ top: 0, left: 0, behavior: 'instant' })
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && (!sessionStorage.getItem('clicker.token') || !sessionStorage.getItem('clicker.user'))) {
    try { await refreshAccessToken('clicker') } catch { return '/login' }
  }
  if (to.meta.requiresAdmin) {
    let token = sessionStorage.getItem('clicker.admin.token')
    let user = readStoredJson(sessionStorage, 'clicker.admin.user')
    if (!token) {
      try { const data = await refreshAccessToken('clicker.admin'); token = data.token; user = data.user } catch { /* handled below */ }
    }
    if (!token || user?.role !== 'ADMIN') {
      clearStoredSession('clicker.admin')
      return '/admin/login'
    }
  }
})

router.afterEach((to, from, failure) => {
  if (!failure && to.fullPath !== from.fullPath) trackPageView(to.path)
})

export default router
