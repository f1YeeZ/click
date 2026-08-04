<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import logoMark from '../assets/clicker-index-mark.svg'
const auth = useAuthStore()
const router = useRouter()
const logout = () => { auth.logout(); router.push('/') }
</script>

<template>
  <header class="site-header">
    <div class="header-shell">
      <RouterLink class="brand-mark" to="/"><img :src="logoMark" alt=""><span>Clicker <b>Index</b></span></RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink exact-active-class="router-link-active" to="/">首页</RouterLink>
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/ranking">排行榜</RouterLink>
        <RouterLink to="/recommend">鼠标推荐</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
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
    <RouterLink exact-active-class="router-link-active" to="/">首页</RouterLink>
    <RouterLink to="/mice">鼠标库</RouterLink>
    <RouterLink to="/ranking">排行榜</RouterLink>
    <RouterLink to="/recommend">鼠标推荐</RouterLink>
    <RouterLink to="/compare">参数对比</RouterLink>
  </nav>
</template>
