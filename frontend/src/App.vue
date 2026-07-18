<script setup>
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import CompareTray from './components/CompareTray.vue'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const route = useRoute()
const year = new Date().getFullYear()
const isAdminRoute = computed(() => route.path === '/admin' || route.path === '/admin/login')
onMounted(() => { if (!isAdminRoute.value) auth.refresh() })
</script>

<template>
  <AppHeader v-if="!isAdminRoute" />
  <RouterView />
  <footer v-if="!isAdminRoute" class="site-footer">
    <div class="footer-shell">
      <RouterLink class="footer-brand" to="/">Clicker Index</RouterLink>
      <p>© {{ year }} Clicker Index · 技术鼠标数据库</p>
      <nav aria-label="页脚导航">
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
        <RouterLink to="/login">账户</RouterLink>
      </nav>
    </div>
  </footer>
  <CompareTray v-if="!isAdminRoute && route.path !== '/compare'" />
</template>
