<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { showToast } from '../services/toast'

const auth = useAuthStore()
const handLengthCm = ref(null)
const preferredGripStyle = ref('')
const gripPicker = ref(null)
const gripTrigger = ref(null)
const gripOptionRefs = ref([])
const gripOpen = ref(false)
const activeGripIndex = ref(0)
const gripOptions = [
  { code: 'PALM', label: '趴握', description: '手掌大面积贴合鼠背' },
  { code: 'CLAW', label: '抓握', description: '掌心支撑，手指弯曲发力' },
  { code: 'FINGERTIP', label: '指握', description: '主要依靠指尖控制鼠标' },
  { code: 'MIXED', label: '混合', description: '介于多种握姿之间' }
]
const profileLocked = computed(() => Boolean(auth.user?.handLengthCm && auth.user?.preferredGripStyle))
const handLocked = computed(() => profileLocked.value)
const gripLocked = computed(() => profileLocked.value)
const selectedGrip = computed(() => gripOptions.find((item) => item.code === preferredGripStyle.value))
const loading = ref(false)
const error = ref('')
const passwordForm = reactive({ verificationCode: '', newPassword: '', confirmPassword: '' })
const passwordLoading = ref(false)
const codeLoading = ref(false)
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
  preferredGripStyle.value = auth.user?.preferredGripStyle ?? ''
}
const focusGripOption = (index) => {
  activeGripIndex.value = (index + gripOptions.length) % gripOptions.length
  nextTick(() => gripOptionRefs.value[activeGripIndex.value]?.focus())
}
const openGrip = (direction = 1) => {
  if (gripLocked.value) return
  const selectedIndex = gripOptions.findIndex((item) => item.code === preferredGripStyle.value)
  gripOpen.value = true
  focusGripOption(selectedIndex >= 0 ? selectedIndex : direction > 0 ? 0 : gripOptions.length - 1)
}
const closeGrip = (restoreFocus = false) => {
  gripOpen.value = false
  if (restoreFocus) nextTick(() => gripTrigger.value?.focus())
}
const toggleGrip = () => gripOpen.value ? closeGrip() : openGrip()
const chooseGrip = (code) => {
  preferredGripStyle.value = code
  closeGrip(true)
}
const handleGripTriggerKey = (event) => {
  if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
    event.preventDefault(); openGrip(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault(); openGrip(-1)
  }
}
const handleGripOptionKey = (event, index) => {
  if (event.key === 'ArrowDown') { event.preventDefault(); focusGripOption(index + 1) }
  else if (event.key === 'ArrowUp') { event.preventDefault(); focusGripOption(index - 1) }
  else if (event.key === 'Home') { event.preventDefault(); focusGripOption(0) }
  else if (event.key === 'End') { event.preventDefault(); focusGripOption(gripOptions.length - 1) }
  else if (event.key === 'Escape') closeGrip(true)
}
const handleGripFocusOut = (event) => {
  if (!event.currentTarget.contains(event.relatedTarget)) closeGrip()
}
const handleOutsidePointer = (event) => {
  if (gripOpen.value && !gripPicker.value?.contains(event.target)) closeGrip()
}
const save = async () => {
  if (!handLengthCm.value || !preferredGripStyle.value) {
    error.value = '请填写个人手长并选择习惯握姿'
    return
  }
  loading.value = true; error.value = ''
  try {
    const { data } = await api.patch('/users/me', {
      handLengthCm: Number(handLengthCm.value),
      preferredGripStyle: preferredGripStyle.value || null
    })
    auth.user = data
    sessionStorage.setItem('clicker.user', JSON.stringify(data))
    showToast('个人资料已保存')
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
  codeLoading.value = true; passwordError.value = ''
  try {
    const { data } = await api.post('/password-verification-codes')
    showToast(data.message)
    startCountdown(data.resendAfterSeconds)
  } catch (e) { passwordError.value = errorMessage(e) }
  finally { codeLoading.value = false }
}
const changePassword = async () => {
  passwordError.value = ''
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  passwordLoading.value = true
  try {
    const { data } = await api.put('/users/me/password', {
      verificationCode: passwordForm.verificationCode,
      newPassword: passwordForm.newPassword
    })
    showToast(data.message)
    passwordForm.verificationCode = ''; passwordForm.newPassword = ''; passwordForm.confirmPassword = ''
  } catch (e) { passwordError.value = errorMessage(e) }
  finally { passwordLoading.value = false }
}
onMounted(() => {
  load()
  document.addEventListener('pointerdown', handleOutsidePointer)
})
onBeforeUnmount(() => {
  clearInterval(countdownTimer)
  document.removeEventListener('pointerdown', handleOutsidePointer)
})
</script>

<template>
  <main class="profile-page section-shell">
    <header class="profile-heading"><div><h1>个人资料</h1><p>手长和习惯握姿只在这里维护。提交鼠标评价时，系统会自动使用这些数据。</p></div><span class="profile-id">{{ auth.user?.email }}</span></header>
    <section class="profile-card">
      <div class="profile-measure"><span>手长 / cm</span><strong>{{ handLengthCm || '—' }}</strong><small>{{ handRange }}</small></div>
      <form @submit.prevent="save">
        <label>个人手长 <small>{{ handLocked ? '已锁定，不可更改' : '从掌根到中指指尖' }}</small><div class="unit-input"><input v-model.number="handLengthCm" type="number" min="10" max="30" step="0.1" required :disabled="handLocked" placeholder="例如 18.5"><span>cm</span></div></label>
        <label>习惯握姿 <small>{{ gripLocked ? '已锁定，不可更改' : '用于筛选对应的支撑记录' }}</small>
          <div ref="gripPicker" class="grip-picker" :class="{ open: gripOpen, locked: gripLocked }" @focusout="handleGripFocusOut">
            <button ref="gripTrigger" class="grip-picker-trigger" type="button" :disabled="gripLocked" aria-haspopup="listbox" :aria-expanded="gripOpen" aria-controls="profile-grip-options" @click="toggleGrip" @keydown="handleGripTriggerKey">
              <span class="grip-picker-value" :class="{ placeholder: !selectedGrip }">
                <strong>{{ selectedGrip?.label || '选择最常用的握持方式' }}</strong>
                <small v-if="selectedGrip">{{ selectedGrip.description }}</small>
              </span>
              <i class="grip-picker-chevron" aria-hidden="true"></i>
            </button>
            <Transition name="grip-menu">
              <div v-if="gripOpen" id="profile-grip-options" class="grip-picker-menu" role="listbox" aria-label="习惯握姿">
                <button v-for="(item, index) in gripOptions" :key="item.code" :ref="(element) => { if (element) gripOptionRefs[index] = element }" type="button" role="option" :aria-selected="preferredGripStyle === item.code" :class="{ selected: preferredGripStyle === item.code }" @click="chooseGrip(item.code)" @keydown="handleGripOptionKey($event, index)">
                  <span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
                  <i aria-hidden="true">✓</i>
                </button>
              </div>
            </Transition>
          </div>
        </label>
        <p class="profile-tip">个人手长和习惯握姿保存后均不可更改。习惯握姿权重为 1，其他握姿权重为 0.3。</p>
        <div v-if="error" class="flash error">{{ error }}</div>
        <button v-if="!profileLocked" class="button" :disabled="loading || !handLengthCm || !preferredGripStyle">{{ loading ? '保存中…' : '保存并锁定个人资料 →' }}</button>
        <div v-else class="profile-tip">✓ 个人资料已锁定</div>
      </form>
    </section>
    <section class="password-card">
      <div class="password-card-intro"><p class="eyebrow">账户安全</p><h2>修改密码</h2><p>验证码将发送至当前账号邮箱 <strong>{{ auth.user?.email }}</strong>，有效期以邮件提示为准。</p></div>
      <form @submit.prevent="changePassword">
        <label>邮箱验证码
          <span class="verification-input"><input v-model.trim="passwordForm.verificationCode" type="text" inputmode="numeric" autocomplete="one-time-code" pattern="\d{6}" maxlength="6" required placeholder="6 位验证码"><button type="button" :disabled="codeLoading || resendSeconds > 0" @click="sendPasswordCode">{{ codeLoading ? '发送中…' : (resendSeconds > 0 ? `${resendSeconds}s 后重发` : '获取验证码') }}</button></span>
        </label>
        <label>新密码<input v-model="passwordForm.newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="8～72 位"></label>
        <label>确认新密码<input v-model="passwordForm.confirmPassword" type="password" autocomplete="new-password" minlength="8" maxlength="72" required placeholder="再次输入新密码"></label>
        <div v-if="passwordError" class="flash error">{{ passwordError }}</div>
        <button class="button" :disabled="passwordLoading">{{ passwordLoading ? '修改中…' : '验证并修改密码 →' }}</button>
      </form>
    </section>
  </main>
</template>
