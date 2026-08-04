<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminAuthStore, useAuthStore } from '../stores/auth'
import { errorMessage } from '../api/client'

const props = defineProps({ mode: { type: String, required: true }, admin: { type: Boolean, default: false } })
const router = useRouter()
const auth = props.admin ? useAdminAuthStore() : useAuthStore()
const form = reactive({ email: '', password: '', verificationCode: '', acceptedTerms: false })
const error = ref('')
const loading = ref(false)
const codeLoading = ref(false)
const codeSent = ref(false)
const resendSeconds = ref(0)
let countdownTimer
const register = computed(() => props.mode === 'register')
const startCountdown = (seconds) => {
  resendSeconds.value = seconds
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}
const sendCode = async () => {
  error.value = ''
  if (!form.email) { error.value = '请先填写邮箱'; return }
  codeLoading.value = true
  try {
    const { data } = await auth.sendRegistrationCode(form.email)
    codeSent.value = true
    startCountdown(data.resendAfterSeconds)
  } catch (e) { error.value = errorMessage(e) }
  finally { codeLoading.value = false }
}
const submit = async () => {
  loading.value = true; error.value = ''
  try {
    const payload = register.value
      ? { email: form.email, password: form.password, verificationCode: form.verificationCode, acceptedTerms: form.acceptedTerms }
      : { email: form.email, password: form.password }
    register.value ? await auth.register(payload) : await auth.login(payload)
    router.push(props.admin ? '/admin' : '/mice')
  }
  catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<template>
  <main :class="['auth-shell', { 'admin-login-shell': admin }]">
    <section class="auth-aside"><p class="eyebrow">{{ admin ? 'PRIVATE ADMIN CONSOLE' : (register ? 'CREATE PROFILE' : 'MEMBER ACCESS') }}</p><h1 class="visually-hidden">{{ admin ? '管理员登录' : (register ? '创建账号' : '账户登录') }}</h1><p class="auth-note">{{ admin ? '管理员登录使用独立会话，不会影响用户前台登录状态。' : '四项基础评分只提交一次，握持舒适度按不同握姿分别记录。' }}</p></section>
    <section class="auth-form-card"><div><p class="eyebrow">{{ admin ? 'ADMIN SIGN IN' : (register ? 'REGISTER' : 'SIGN IN') }}</p><h2>{{ admin ? '登录管理后台' : (register ? '创建账号' : '登录 Clicker Index') }}</h2><p>{{ admin ? '进入独立的数据管理控制台。' : (register ? '注册后即可提交、修改和删除评价。' : '继续管理你的评价与对比清单。') }}</p></div><div class="flash error" v-if="error">{{ error }}</div>
      <form @submit.prevent="submit">
        <label>邮箱<input v-model.trim="form.email" type="email" autocomplete="email" required placeholder="you@example.com"></label>
        <label v-if="register">邮箱验证码
          <span class="verification-input"><input v-model.trim="form.verificationCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="\d{6}" maxlength="6" required placeholder="6 位验证码"><button type="button" :disabled="codeLoading || resendSeconds > 0" @click="sendCode">{{ codeLoading ? '发送中…' : (resendSeconds > 0 ? `${resendSeconds}s 后重发` : (codeSent ? '重新发送' : '获取验证码')) }}</button></span>
        </label>
        <label>密码<input v-model="form.password" type="password" :autocomplete="register ? 'new-password' : 'current-password'" minlength="8" maxlength="72" required placeholder="8～72 位"></label>
        <label v-if="register" class="legal-consent"><input v-model="form.acceptedTerms" type="checkbox" required><span>我已阅读并同意 <RouterLink to="/terms" target="_blank">用户协议</RouterLink> 和 <RouterLink to="/privacy" target="_blank">隐私政策</RouterLink></span></label>
        <button class="button full" :disabled="loading">{{ loading ? '处理中…' : (register ? '验证并创建账号 →' : '登录 →') }}</button>
      </form>
      <p class="auth-help-row" v-if="!admin && !register"><RouterLink to="/forgot-password">忘记密码？</RouterLink></p>
      <p class="auth-switch" v-if="!admin">{{ register ? '已经注册？' : '还没有账号？' }} <RouterLink :to="register ? '/login' : '/register'">{{ register ? '返回登录' : '立即创建' }}</RouterLink></p>
      <p class="auth-switch" v-else><RouterLink to="/">返回前台首页</RouterLink></p>
    </section>
  </main>
</template>
