<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import { showToast } from '../services/toast'

const form = reactive({ email: '', verificationCode: '', newPassword: '', confirmPassword: '' })
const stage = ref('request')
const loading = ref(false)
const error = ref('')
const resendSeconds = ref(0)
let countdownTimer

const startCountdown = (seconds) => {
  resendSeconds.value = seconds
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}

const requestCode = async () => {
  loading.value = true
  error.value = ''
  try {
    const { data } = await api.post('/password-reset-verification-codes', { email: form.email })
    stage.value = 'reset'
    showToast(`验证码已发送。${data.message}`)
    startCountdown(data.resendAfterSeconds)
  } catch (exception) {
    error.value = errorMessage(exception)
  } finally {
    loading.value = false
  }
}

const resetPassword = async () => {
  error.value = ''
  if (form.newPassword !== form.confirmPassword) {
    error.value = '两次输入的新密码不一致'
    return
  }
  loading.value = true
  try {
    const { data } = await api.put('/password-reset', {
      email: form.email,
      verificationCode: form.verificationCode,
      newPassword: form.newPassword
    })
    showToast(data.message)
    stage.value = 'success'
    clearInterval(countdownTimer)
  } catch (exception) {
    error.value = errorMessage(exception)
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<template>
  <main class="auth-shell password-reset-shell">
    <section class="auth-aside">
      <p class="eyebrow">ACCOUNT RECOVERY</p>
      <h1 class="visually-hidden">重置密码</h1>
      <p class="auth-note">验证码仅发送至已注册邮箱，有效期内完成验证即可设置新密码。</p>
    </section>

    <section class="auth-form-card">
      <div>
        <p class="eyebrow">RESET PASSWORD</p>
        <h2>{{ stage === 'success' ? '密码已经重置' : '忘记密码' }}</h2>
        <p>{{ stage === 'request' ? '输入注册邮箱，我们会发送一次性验证码。' : stage === 'reset' ? '填写邮件中的验证码并设置新密码。' : '原密码已经失效，现在可以返回登录。' }}</p>
      </div>

      <div class="flash error" v-if="error">{{ error }}</div>

      <form v-if="stage === 'request'" @submit.prevent="requestCode">
        <label>邮箱<input v-model.trim="form.email" type="email" autocomplete="email" required placeholder="you@example.com"></label>
        <button class="button full" :disabled="loading">{{ loading ? '发送中…' : '获取重置验证码' }}</button>
      </form>

      <form v-else-if="stage === 'reset'" @submit.prevent="resetPassword">
        <label>邮箱<input v-model.trim="form.email" type="email" autocomplete="email" required readonly></label>
        <label>邮箱验证码<input v-model.trim="form.verificationCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="\d{6}" maxlength="6" required placeholder="6 位验证码"></label>
        <label>新密码<input v-model="form.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="8～72 位"></label>
        <label>确认新密码<input v-model="form.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="再次输入新密码"></label>
        <button class="button full" :disabled="loading">{{ loading ? '重置中…' : '确认重置密码' }}</button>
        <button class="password-reset-resend" type="button" :disabled="loading || resendSeconds > 0" @click="requestCode">
          {{ resendSeconds > 0 ? `${resendSeconds}s 后可重新发送` : '重新发送验证码' }}
        </button>
      </form>

      <p class="auth-switch"><RouterLink to="/login">返回登录</RouterLink></p>
    </section>
  </main>
</template>
