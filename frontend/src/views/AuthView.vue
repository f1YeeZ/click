<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminAuthStore, useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/client'

const props = defineProps({ mode: { type: String, required: true }, admin: { type: Boolean, default: false } })
const router = useRouter()
const auth = props.admin ? useAdminAuthStore() : useAuthStore()
const form = reactive({ email: '', password: '' })
const error = ref('')
const loading = ref(false)
const register = computed(() => props.mode === 'register')
const submit = async () => {
  loading.value = true; error.value = ''
  try { register.value ? await auth.register(form) : await auth.login(form); router.push(props.admin ? '/admin' : '/mice') }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
</script>

<template>
  <main :class="['auth-shell', { 'admin-login-shell': admin }]">
    <section class="auth-aside"><p class="eyebrow">{{ admin ? 'PRIVATE ADMIN CONSOLE' : (register ? 'CREATE PROFILE' : 'MEMBER ACCESS') }}</p><h1 v-if="admin">管理你的<br>数据资产。</h1><h1 v-else-if="register">一份评价，<br>五个明确维度。</h1><h1 v-else>把体验变成<br>可比较的数据。</h1><p class="auth-note">{{ admin ? '管理员登录使用独立会话，不会影响用户前台登录状态。' : '固定选项让不同用户的体验可以直接聚合，同时减少内容治理负担。' }}</p></section>
    <section class="auth-form-card"><div><p class="eyebrow">{{ admin ? 'ADMIN SIGN IN' : (register ? 'REGISTER' : 'SIGN IN') }}</p><h2>{{ admin ? '登录管理后台' : (register ? '创建账号' : '登录 Clicker Index') }}</h2><p>{{ admin ? '进入独立的数据管理控制台。' : (register ? '注册后即可提交、修改和删除评价。' : '继续管理你的评价与对比清单。') }}</p></div><div class="flash error" v-if="error">{{ error }}</div>
      <form @submit.prevent="submit"><label>邮箱<input v-model.trim="form.email" type="email" autocomplete="email" required placeholder="you@example.com"></label><label>密码<input v-model="form.password" type="password" :autocomplete="register ? 'new-password' : 'current-password'" minlength="8" maxlength="72" required placeholder="8～72 位"></label><button class="button full" :disabled="loading">{{ loading ? '处理中…' : (register ? '创建账号 →' : '登录 →') }}</button></form>
      <p class="auth-switch" v-if="!admin">{{ register ? '已经注册？' : '还没有账号？' }} <RouterLink :to="register ? '/login' : '/register'">{{ register ? '返回登录' : '立即创建' }}</RouterLink></p>
      <p class="auth-switch" v-else><RouterLink to="/">返回前台首页</RouterLink></p>
    </section>
  </main>
</template>
