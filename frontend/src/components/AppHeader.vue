<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'
import brandLogo from '../assets/geardb-logo.svg'
const auth = useAuthStore()
const compare = useCompareStore()
const route = useRoute()
const router = useRouter()
const toolsOpen = ref(false)
const toolsActive = computed(() => ['/mouse-test', '/sensitivity'].includes(route.path))
const logout = () => { auth.logout(); router.push('/') }
const openTools = () => { toolsOpen.value = true }
const closeTools = () => { toolsOpen.value = false }
const toggleTools = () => { toolsOpen.value ? closeTools() : openTools() }
const handleToolsFocusOut = event => {
  if (!event.currentTarget.contains(event.relatedTarget)) closeTools()
}
const handleDocumentPointerDown = event => {
  if (event.target?.closest?.('.nav-tools, .mobile-tools')) return
  closeTools()
}
onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerDown, true))
onBeforeUnmount(() => document.removeEventListener('pointerdown', handleDocumentPointerDown, true))
</script>

<template>
  <header class="site-header">
    <div class="header-shell">
      <RouterLink class="brand-mark" to="/" aria-label="GearDB 首页">
        <img class="brand-logo" :src="brandLogo" alt="GearDB">
      </RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/recommend">鼠标推荐</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
        <div
          class="nav-tools"
          :class="{ 'is-open': toolsOpen, 'is-active': toolsActive }"
          @mouseenter="openTools"
          @focusin="openTools"
          @focusout="handleToolsFocusOut"
          @keydown.esc.stop="closeTools"
        >
          <button
            class="nav-tools-trigger"
            type="button"
            aria-haspopup="true"
            aria-controls="desktop-tools-menu"
            :aria-expanded="toolsOpen"
            @click="toggleTools"
          >
            工具
          </button>
          <div
            id="desktop-tools-menu"
            class="nav-tools-menu"
            aria-label="工具页面"
            :aria-hidden="!toolsOpen"
            :inert="!toolsOpen"
            @mouseleave="closeTools"
          >
            <RouterLink to="/mouse-test" @click="closeTools">
              <span><strong>鼠标测试</strong><small>检测五个基础按键</small></span>
            </RouterLink>
            <RouterLink to="/sensitivity" @click="closeTools">
              <span><strong>灵敏度换算</strong><small>保持相同转身距离</small></span>
            </RouterLink>
          </div>
        </div>
      </nav>
      <div class="account-nav">
        <template v-if="auth.authenticated">
          <RouterLink class="account-id" to="/profile">{{ auth.user.email }}</RouterLink>
          <button class="sign-in-button" @click="logout">退出</button>
        </template>
        <RouterLink v-else class="sign-in-button" to="/login">登录</RouterLink>
      </div>
    </div>
  </header>
  <nav class="mobile-nav" aria-label="移动主导航">
    <RouterLink to="/mice">鼠标库</RouterLink>
    <RouterLink to="/recommend">鼠标推荐</RouterLink>
    <RouterLink class="mobile-compare-link" :to="{ path: '/compare', query: compare.ids.length ? { ids: compare.ids.join(',') } : {} }">参数对比<span v-if="compare.items.length" class="mobile-compare-count" :aria-label="`已选 ${compare.items.length} 款`">{{ compare.items.length }}</span></RouterLink>
    <div
      class="mobile-tools"
      :class="{ 'is-open': toolsOpen, 'is-active': toolsActive }"
      @focusout="handleToolsFocusOut"
      @keydown.esc.stop="closeTools"
    >
      <button
        class="mobile-tools-trigger"
        type="button"
        aria-haspopup="true"
        aria-controls="mobile-tools-menu"
        :aria-expanded="toolsOpen"
        @click="toggleTools"
      >工具</button>
      <div
        id="mobile-tools-menu"
        class="mobile-tools-menu"
        aria-label="移动端工具页面"
        :aria-hidden="!toolsOpen"
        :inert="!toolsOpen"
      >
        <RouterLink to="/mouse-test" @click="closeTools">鼠标测试</RouterLink>
        <RouterLink to="/sensitivity" @click="closeTools">灵敏度换算</RouterLink>
      </div>
    </div>
  </nav>
</template>
