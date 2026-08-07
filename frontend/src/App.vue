<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import CompareTray from './components/CompareTray.vue'
import { useAuthStore } from './stores/auth'
import { startRealtime, stopRealtime } from './services/realtime'
import api from './api/client'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const year = new Date().getFullYear()
const isAdminRoute = computed(() => route.path === '/admin' || route.path === '/admin/login')
const isCodeMapRoute = computed(() => route.path === '/dev/code-map')
const routeMotion = ref('idle')
const publicConfig = ref({ maintenanceNotice: '', registrationEnabled: true, reviewSubmissionEnabled: true })
let routeMotionTimer

const shouldAnimateRoute = (to, from) => Boolean(from?.matched?.length) && to.path !== from.path
const removeMotionGuard = router.beforeEach((to, from) => {
  if (!shouldAnimateRoute(to, from)) return true
  clearTimeout(routeMotionTimer)
  routeMotion.value = 'loading'
  return true
})
const removeMotionAfterHook = router.afterEach((to, from) => {
  if (!shouldAnimateRoute(to, from) || routeMotion.value === 'idle') return
  requestAnimationFrame(() => {
    routeMotion.value = 'done'
    routeMotionTimer = window.setTimeout(() => { routeMotion.value = 'idle' }, 220)
  })
})
onMounted(() => {
  startRealtime()
  api.get('/config').then(({ data }) => { publicConfig.value = data }).catch(() => {})
  if (!isAdminRoute.value) auth.refresh()
})
onBeforeUnmount(() => {
  stopRealtime()
  clearTimeout(routeMotionTimer)
  removeMotionGuard()
  removeMotionAfterHook()
})
</script>

<template>
  <AppHeader v-if="!isAdminRoute" />
  <div v-if="!isAdminRoute && publicConfig.maintenanceNotice" class="maintenance-banner" role="status">
    <strong>运营公告</strong><span>{{ publicConfig.maintenanceNotice }}</span>
  </div>
  <RouterView v-slot="{ Component, route: viewRoute }">
    <KeepAlive :max="8">
      <component :is="Component" :key="viewRoute.path" />
    </KeepAlive>
  </RouterView>
  <footer v-if="!isAdminRoute" class="site-footer">
    <div class="footer-shell">
      <RouterLink class="footer-brand" to="/">Clicker Index</RouterLink>
      <p>© {{ year }} Clicker Index · 技术鼠标数据库</p>
      <nav aria-label="页脚导航">
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/ranking">排行榜</RouterLink>
        <RouterLink to="/recommend">鼠标推荐</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
        <RouterLink to="/privacy">隐私政策</RouterLink>
        <RouterLink to="/terms">用户协议</RouterLink>
        <RouterLink to="/review-rules">评价规则</RouterLink>
        <RouterLink to="/login">账户</RouterLink>
      </nav>
    </div>
  </footer>
  <CompareTray v-if="!isAdminRoute && !isCodeMapRoute && route.path !== '/compare'" />
  <div class="route-motion-line" :class="`is-${routeMotion}`" aria-hidden="true"></div>
</template>

<style scoped>
.maintenance-banner{display:flex;justify-content:center;gap:12px;padding:9px 20px;background:#173f42;color:#eefafa;font-size:13px}.maintenance-banner strong{color:#8ed9d1;letter-spacing:.08em}
</style>
