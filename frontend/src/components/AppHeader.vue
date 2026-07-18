<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const auth = useAuthStore()
const router = useRouter()
const logout = () => { auth.logout(); router.push('/') }
</script>

<template>
  <header class="site-header">
    <div class="header-shell">
      <RouterLink class="brand-mark" to="/">Clicker <span>Index</span></RouterLink>
      <nav class="main-nav" aria-label="主导航">
        <RouterLink exact-active-class="router-link-active" to="/">首页</RouterLink>
        <RouterLink to="/mice">鼠标库</RouterLink>
        <RouterLink to="/compare">参数对比</RouterLink>
        <a href="/#core-systems">功能</a>
      </nav>
      <div class="account-nav">
        <template v-if="auth.authenticated">
          <span class="account-id">{{ auth.user.email }}</span>
          <button class="sign-in-button" @click="logout">退出</button>
        </template>
        <RouterLink v-else class="sign-in-button" to="/login">登录</RouterLink>
      </div>
    </div>
  </header>
</template>
