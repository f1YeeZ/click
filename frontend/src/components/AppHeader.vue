<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import brandLogo from '../assets/geardb-logo.svg'
const auth = useAuthStore()
const router = useRouter()
const headerQuery = ref('')
const logout = () => { auth.logout(); router.push('/') }
const searchCatalog = () => {
  const q = headerQuery.value.trim()
  router.push({ path: '/mice', query: q ? { q } : {} })
  headerQuery.value = ''
}
</script>

<template>
  <header class="site-header">
    <div class="header-shell">
      <RouterLink class="brand-mark" to="/" aria-label="GearDB 首页">
        <img class="brand-logo" :src="brandLogo" alt="GearDB">
      </RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink exact-active-class="router-link-active" to="/">首页</RouterLink>
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/recommend">鼠标推荐</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
      </nav>
      <form class="header-search" role="search" @submit.prevent="searchCatalog">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg>
        <input v-model="headerQuery" type="search" aria-label="全站搜索鼠标" placeholder="搜索鼠标数据库…">
        <button class="header-search-submit" type="submit">搜索</button>
        <kbd>/</kbd>
      </form>
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
    <RouterLink exact-active-class="router-link-active" to="/">首页</RouterLink>
    <RouterLink to="/mice">鼠标库</RouterLink>
    <RouterLink to="/recommend">鼠标推荐</RouterLink>
    <RouterLink to="/compare">参数对比</RouterLink>
  </nav>
</template>
