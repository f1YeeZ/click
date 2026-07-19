<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const handLengthCm = ref(null)
const loading = ref(false)
const message = ref('')
const error = ref('')
const passwordForm = reactive({ verificationCode: '', newPassword: '', confirmPassword: '' })
const passwordLoading = ref(false)
const codeLoading = ref(false)
const passwordMessage = ref('')
const passwordError = ref('')
const resendSeconds = ref(0)
let countdownTimer
const handRange = computed(() => {
  const value = Number(handLengthCm.value)
  if (!value) return '尚未填写'
  if (value < 17) return '小手范围 · 小于 17 cm'
  if (value < 19) return '中手范围 · 17～19 cm'
  return '大手范围 · 19 cm 及以上'
})

const load = async () => {
  await auth.refresh()
  handLengthCm.value = auth.user?.handLengthCm ?? null
}
const save = async () => {
  loading.value = true; message.value = ''; error.value = ''
  try {
    const { data } = await api.put('/auth/me', { handLengthCm: Number(handLengthCm.value) })
    auth.user = data
    localStorage.setItem('clicker.user', JSON.stringify(data))
    message.value = '个人资料已保存'
  } catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}
const startCountdown = (seconds) => {
  resendSeconds.value = seconds
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    resendSeconds.value -= 1
    if (resendSeconds.value <= 0) clearInterval(countdownTimer)
  }, 1000)
}
const sendPasswordCode = async () => {
  codeLoading.value = true; passwordMessage.value = ''; passwordError.value = ''
  try {
    const { data } = await api.post('/auth/password/code')
    passwordMessage.value = data.message
    startCountdown(data.resendAfterSeconds)
  } catch (e) { passwordError.value = errorMessage(e) }
  finally { codeLoading.value = false }
}
const changePassword = async () => {
  passwordMessage.value = ''; passwordError.value = ''
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  passwordLoading.value = true
  try {
    const { data } = await api.put('/auth/password', {
      verificationCode: passwordForm.verificationCode,
      newPassword: passwordForm.newPassword
    })
    passwordMessage.value = data.message
    passwordForm.verificationCode = ''; passwordForm.newPassword = ''; passwordForm.confirmPassword = ''
  } catch (e) { passwordError.value = errorMessage(e) }
  finally { passwordLoading.value = false }
}
onMounted(load)
onBeforeUnmount(() => clearInterval(countdownTimer))
</script>

<template>
  <main class="profile-page section-shell">
    <header class="profile-heading"><div><p class="eyebrow">MEMBER PROFILE</p><h1>个人资料</h1><p>手长只在这里维护。提交鼠标评价时，系统会自动使用该数据，不会再次询问。</p></div><span class="profile-id">{{ auth.user?.email }}</span></header>
    <section class="profile-card">
      <div class="profile-measure"><span>HAND LENGTH / CM</span><strong>{{ handLengthCm || '—' }}</strong><small>{{ handRange }}</small></div>
      <form @submit.prevent="save">
        <label>个人手长 <small>从掌根到中指指尖</small><div class="unit-input"><input v-model.number="handLengthCm" type="number" min="10" max="30" step="0.1" required placeholder="例如 18.5"><span>cm</span></div></label>
        <p class="profile-tip">该数据用于用户评价中的手长范围筛选，不会在评价表单内重复选择。</p>
        <div v-if="message" class="flash success">{{ message }}</div><div v-if="error" class="flash error">{{ error }}</div>
        <button class="button" :disabled="loading">{{ loading ? '保存中…' : '保存个人资料 →' }}</button>
      </form>
    </section>
    <section class="password-card">
      <div class="password-card-intro"><p class="eyebrow">ACCOUNT SECURITY</p><h2>修改密码</h2><p>验证码将发送至当前账号邮箱 <strong>{{ auth.user?.email }}</strong>，有效期以邮件提示为准。</p></div>
      <form @submit.prevent="changePassword">
        <label>邮箱验证码
          <span class="verification-input"><input v-model.trim="passwordForm.verificationCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="\d{6}" maxlength="6" required placeholder="6 位验证码"><button type="button" :disabled="codeLoading || resendSeconds > 0" @click="sendPasswordCode">{{ codeLoading ? '发送中…' : (resendSeconds > 0 ? `${resendSeconds}s 后重发` : '获取验证码') }}</button></span>
        </label>
        <label>新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="8～72 位"></label>
        <label>确认新密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="再次输入新密码"></label>
        <div v-if="passwordMessage" class="flash success">{{ passwordMessage }}</div><div v-if="passwordError" class="flash error">{{ passwordError }}</div>
        <button class="button" :disabled="passwordLoading">{{ passwordLoading ? '修改中…' : '验证并修改密码 →' }}</button>
      </form>
    </section>
  </main>
</template>
