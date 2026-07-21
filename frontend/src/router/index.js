import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import MiceView from '../views/MiceView.vue'
import MouseDetailView from '../views/MouseDetailView.vue'
import CompareView from '../views/CompareView.vue'
import AuthView from '../views/AuthView.vue'
import AdminView from '../views/AdminView.vue'
import ProfileView from '../views/ProfileView.vue'
import LeaderboardView from '../views/LeaderboardView.vue'
import RecommendationView from '../views/RecommendationView.vue'
import LegalView from '../views/LegalView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/mice', component: MiceView },
    { path: '/mice/:id', component: MouseDetailView },
    { path: '/compare', component: CompareView },
    { path: '/ranking', component: LeaderboardView },
    { path: '/recommend', component: RecommendationView },
    { path: '/privacy', component: LegalView, props: { document: 'privacy' } },
    { path: '/terms', component: LegalView, props: { document: 'terms' } },
    { path: '/review-rules', component: LegalView, props: { document: 'rules' } },
    { path: '/login', component: AuthView, props: { mode: 'login' } },
    { path: '/register', component: AuthView, props: { mode: 'register' } },
    { path: '/profile', component: ProfileView, meta: { requiresAuth: true } },
    { path: '/admin/login', component: AuthView, props: { mode: 'login', admin: true } },
    { path: '/admin', component: AdminView, meta: { requiresAdmin: true } }
  ],
  // Route transitions should not compete with a smooth scroll animation.
  scrollBehavior: () => ({ top: 0, left: 0, behavior: 'instant' })
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !localStorage.getItem('clicker.token')) return '/login'
  if (to.meta.requiresAdmin) {
    const user = JSON.parse(localStorage.getItem('clicker.admin.user') || 'null')
    if (user?.role !== 'ADMIN') return '/admin/login'
  }
})

export default router
