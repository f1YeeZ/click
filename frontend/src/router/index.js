import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import MiceView from '../views/MiceView.vue'
import MouseDetailView from '../views/MouseDetailView.vue'
import CompareView from '../views/CompareView.vue'
import AuthView from '../views/AuthView.vue'
import AdminView from '../views/AdminView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: HomeView },
    { path: '/mice', component: MiceView },
    { path: '/mice/:slug', component: MouseDetailView },
    { path: '/compare', component: CompareView },
    { path: '/login', component: AuthView, props: { mode: 'login' } },
    { path: '/register', component: AuthView, props: { mode: 'register' } },
    { path: '/admin/login', component: AuthView, props: { mode: 'login', admin: true } },
    { path: '/admin', component: AdminView, meta: { requiresAdmin: true } }
  ],
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  if (to.meta.requiresAdmin) {
    const user = JSON.parse(localStorage.getItem('clicker.admin.user') || 'null')
    if (user?.role !== 'ADMIN') return '/admin/login'
  }
})

export default router
