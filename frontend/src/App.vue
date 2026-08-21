<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import AdRail from './components/AdRail.vue'
import CompareTray from './components/CompareTray.vue'
import FeedbackWidget from './components/FeedbackWidget.vue'
import SocialMediaWidget from './components/SocialMediaWidget.vue'
import ToastViewport from './components/ToastViewport.vue'
import SelectEnhancer from './components/SelectEnhancer.vue'
import brandLogo from './assets/geardb-logo.svg'
import { useAuthStore } from './stores/auth'
import { useCompareStore } from './stores/compare'
import { usePublicConfigStore } from './stores/publicConfig'
import { onRealtime, startRealtime, stopRealtime } from './services/realtime'

const auth = useAuthStore()
const compare = useCompareStore()
const publicConfig = usePublicConfigStore()
const route = useRoute()
const router = useRouter()
const year = new Date().getFullYear()
const isAdminRoute = computed(() => route.path === '/admin' || route.path === '/admin/login')
const isCodeMapRoute = computed(() => route.path === '/dev/code-map')
const isMouseTestRoute = computed(() => route.path === '/mouse-test')
const routeMotion = ref('idle')
let routeMotionTimer
let stopConfigRealtime = () => {}

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
  stopConfigRealtime = onRealtime(event => {
    if (event.type === 'settings.changed' || event.type === 'sync.required') publicConfig.load().catch(() => {})
  })
  publicConfig.load().catch(() => {})
  if (!isAdminRoute.value) auth.refresh()
})
onBeforeUnmount(() => {
  stopConfigRealtime()
  stopRealtime()
  clearTimeout(routeMotionTimer)
  removeMotionGuard()
  removeMotionAfterHook()
})
</script>

<template>
  <AppHeader v-if="!isAdminRoute" />
  <ToastViewport />
  <SelectEnhancer />
  <div v-if="!isAdminRoute && publicConfig.maintenanceNotice" class="maintenance-banner" role="status">
    <strong>运营公告</strong><span>{{ publicConfig.maintenanceNotice }}</span>
  </div>
  <template v-if="!isAdminRoute && !isCodeMapRoute && publicConfig.advertisingEnabled">
    <AdRail v-if="publicConfig.leftAd.enabled" side="left" :ad="publicConfig.leftAd" />
    <AdRail v-if="publicConfig.rightAd.enabled" side="right" :ad="publicConfig.rightAd" />
  </template>
  <RouterView v-slot="{ Component, route: viewRoute }">
    <KeepAlive :max="8">
      <component :is="Component" :key="viewRoute.path" />
    </KeepAlive>
  </RouterView>
  <footer v-if="!isAdminRoute" class="site-footer">
    <div class="footer-shell">
      <RouterLink class="footer-brand" to="/" aria-label="GearDB 首页"><img :src="brandLogo" alt="GearDB"></RouterLink>
      <p>© {{ year }} GearDB / 技术鼠标数据库</p>
      <nav aria-label="页脚导航">
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/recommend">鼠标推荐</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
        <RouterLink to="/mouse-test">鼠标测试</RouterLink>
        <RouterLink to="/sensitivity">灵敏度换算</RouterLink>
        <RouterLink to="/privacy">隐私政策</RouterLink>
        <RouterLink to="/terms">用户协议</RouterLink>
        <RouterLink to="/review-rules">提交规则</RouterLink>
        <RouterLink to="/login">账户</RouterLink>
      </nav>
    </div>
  </footer>
  <CompareTray v-if="!isAdminRoute && !isCodeMapRoute && !isMouseTestRoute && route.path !== '/compare'" />
  <div
    v-if="!isAdminRoute && !isCodeMapRoute && !isMouseTestRoute"
    class="public-floating-actions"
    :class="{ 'has-compare-tray': compare.items.length && route.path !== '/compare' }"
  >
    <SocialMediaWidget />
    <FeedbackWidget />
  </div>
  <div class="route-motion-line" :class="`is-${routeMotion}`" aria-hidden="true"></div>
</template>

<style scoped>
.maintenance-banner{display:flex;justify-content:center;gap:.75rem;padding:.5625rem 1.25rem;border-bottom:1px solid #3d3d3d;background:#f2f2f2;color:#0b0b0b;font-size:.8125rem}.maintenance-banner strong{color:#0b0b0b;letter-spacing:.08em}
.public-floating-actions{position:fixed;z-index:70;right:max(22px,env(safe-area-inset-right));bottom:max(22px,env(safe-area-inset-bottom));display:flex;align-items:flex-end;gap:18px;transition:bottom .2s ease}.public-floating-actions.has-compare-tray{bottom:96px}@media(max-width:900px){.public-floating-actions{right:max(14px,env(safe-area-inset-right));bottom:calc(72px + env(safe-area-inset-bottom))}.public-floating-actions.has-compare-tray{bottom:calc(148px + env(safe-area-inset-bottom))}}@media(max-width:820px){.public-floating-actions.has-compare-tray{bottom:calc(72px + env(safe-area-inset-bottom))}}@media(prefers-reduced-motion:reduce){.public-floating-actions{transition:none}}
</style>
