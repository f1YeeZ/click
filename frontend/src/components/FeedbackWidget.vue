<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'
import { showToast } from '../services/toast'

const auth = useAuthStore()
const compare = useCompareStore()
const dialog = ref(null)
const open = ref(false)
const loading = ref(false)
const error = ref('')
const category = ref('MOUSE_MISSING')
const mouseModel = ref('')
const description = ref('')
const contactEmail = ref('')

const categories = [
  { value: 'MOUSE_MISSING', label: '网站没有这款鼠标', hint: '告诉我们品牌、型号或链接' },
  { value: 'BUG', label: '报告 Bug', hint: '页面显示异常、操作不符合预期' },
  { value: 'DATA_ERROR', label: '数据需要修正', hint: '参数、图片或来源信息不准确' },
  { value: 'SUGGESTION', label: '功能建议', hint: '分享你希望加入的功能' },
  { value: 'OTHER', label: '其他反馈', hint: '任何值得让我们知道的事' },
]
const isMissingMouse = computed(() => category.value === 'MOUSE_MISSING')
const canSubmit = computed(() => description.value.trim().length >= 5 && (!isMissingMouse.value || mouseModel.value.trim()))

const reset = () => {
  category.value = 'MOUSE_MISSING'
  mouseModel.value = ''
  description.value = ''
  contactEmail.value = auth.user?.email || ''
  error.value = ''
}
const openDialog = async () => {
  reset()
  open.value = true
  await nextTick()
  if (dialog.value && !dialog.value.open) dialog.value.showModal()
}
const closeDialog = ({ force = false } = {}) => {
  if (loading.value && !force) return
  if (dialog.value?.open) dialog.value.close()
  open.value = false
}
const closeFromBackdrop = event => {
  if (event.target === dialog.value) closeDialog()
}
const submit = async () => {
  if (!canSubmit.value || loading.value) return
  loading.value = true
  error.value = ''
  const page = typeof window !== 'undefined' ? window.location.pathname : '/'
  const details = [
    isMissingMouse.value ? `希望加入的鼠标：${mouseModel.value.trim()}` : '',
    description.value.trim(),
    `提交页面：${page}`,
  ].filter(Boolean).join('\n\n')
  try {
    await api.post('/feedback', {
      category: category.value,
    description: details.slice(0, 1000),
      contactEmail: contactEmail.value.trim() || null,
    })
    showToast('已收到，谢谢你的反馈。我们会在后台尽快查看。')
    description.value = ''
    mouseModel.value = ''
    closeDialog({ force: true })
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

const handleKeydown = event => {
  if (event.key === 'Escape' && open.value) closeDialog()
}
if (typeof window !== 'undefined') window.addEventListener('keydown', handleKeydown)
onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <button class="feedback-fab" :class="{ 'has-compare-tray': compare.items.length }" type="button" aria-haspopup="dialog" aria-controls="site-feedback-dialog" @click="openDialog">
    <span class="feedback-fab-mark" aria-hidden="true">✦</span>
    <span>反馈</span>
  </button>

  <Teleport to="body">
    <dialog
      v-if="open"
      id="site-feedback-dialog"
      ref="dialog"
      class="site-feedback-dialog"
      aria-labelledby="site-feedback-title"
      @click="closeFromBackdrop"
      @close="open = false"
    >
      <form class="site-feedback-shell" @submit.prevent="submit">
        <header class="site-feedback-header">
          <div>
            <small>COMMUNITY SIGNAL / OPEN CHANNEL</small>
            <h2 id="site-feedback-title">告诉我们哪里可以更好</h2>
            <p>缺少的鼠标、遇到的 Bug，或任何让你想留下的一句话。</p>
          </div>
          <button class="site-feedback-close" type="button" aria-label="关闭反馈窗口" @click="closeDialog">×</button>
        </header>
        <div class="site-feedback-body">
          <div v-if="error" class="site-feedback-notice error" role="alert">{{ error }}</div>
          <fieldset class="site-feedback-types">
            <legend>你想反馈什么？</legend>
            <label v-for="item in categories" :key="item.value" :class="{ active: category === item.value }">
              <input v-model="category" type="radio" name="feedback-category" :value="item.value">
              <span><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></span>
            </label>
          </fieldset>
          <label v-if="isMissingMouse" class="site-feedback-field">
            <span>鼠标品牌 / 型号 <b>必填</b></span>
            <input v-model="mouseModel" maxlength="160" placeholder="例如：VAXEE XE Wireless" required>
          </label>
          <label class="site-feedback-field">
            <span>详细说明 <b>必填</b></span>
            <textarea v-model="description" maxlength="800" minlength="5" required placeholder="尽可能提供可复现的细节、链接或正确参数…"></textarea>
            <small>{{ description.length }} / 800</small>
          </label>
          <label class="site-feedback-field">
            <span>联系邮箱 <em>选填</em></span>
            <input v-model="contactEmail" type="email" maxlength="180" placeholder="方便我们回复你（不会公开）">
          </label>
        </div>
        <footer class="site-feedback-footer">
          <span>反馈会匿名展示给运营团队，仅用于改进 GearDB。</span>
          <div><button class="button button-ghost" type="button" :disabled="loading" @click="closeDialog">取消</button><button class="button feedback-submit" type="submit" :disabled="loading || !canSubmit">{{ loading ? '提交中…' : '提交反馈' }}<span aria-hidden="true">→</span></button></div>
        </footer>
      </form>
    </dialog>
  </Teleport>
</template>

<style scoped>
.feedback-fab{position:fixed;right:22px;bottom:22px;z-index:70;display:inline-flex;align-items:center;gap:8px;padding:11px 15px;border:1px solid #5b5b5b;border-radius:999px;background:#191919e8;box-shadow:0 10px 35px #0008;color:#e5e5e5;font:600 .78rem var(--mono);cursor:pointer;backdrop-filter:blur(14px);transition:transform .18s ease,border-color .18s ease,background .18s ease,bottom .2s ease}.feedback-fab.has-compare-tray{bottom:96px}.feedback-fab:hover{transform:translateY(-3px);border-color:#bdbdbd;background:#252525}.feedback-fab:focus-visible{outline:2px solid var(--acid);outline-offset:3px}.feedback-fab-mark{display:grid;width:20px;height:20px;place-items:center;border:1px solid #868686;border-radius:50%;color:#f0f0f0;font-size:.72rem}.site-feedback-dialog{width:min(600px,calc(100vw - 32px));max-width:none;max-height:min(820px,calc(100dvh - 32px));padding:0;border:1px solid #3d3d3d;border-radius:20px;background:#121212;color:var(--ink);box-shadow:0 30px 90px #000b}.site-feedback-dialog::backdrop{background:#050505b8;backdrop-filter:blur(9px)}.site-feedback-dialog[open]{animation:feedback-in .22s cubic-bezier(.22,1,.36,1)}.site-feedback-shell{display:grid;grid-template-rows:auto minmax(0,1fr) auto;max-height:min(820px,calc(100dvh - 32px));margin:0}.site-feedback-header{display:flex;justify-content:space-between;gap:18px;padding:25px 26px 21px;border-bottom:1px solid #313131;background:linear-gradient(135deg,#202020,#151515)}.site-feedback-header>div{display:grid;gap:7px}.site-feedback-header small{color:#929292;font:600 .68rem var(--mono);letter-spacing:.14em}.site-feedback-header h2{margin:0;color:#f0f0f0;font-size:1.5rem;letter-spacing:-.04em}.site-feedback-header p{margin:0;color:#9d9d9d;font-size:.78rem;line-height:1.55}.site-feedback-close{display:grid;place-items:center;flex:0 0 38px;width:38px;height:38px;border:1px solid #414141;border-radius:10px;background:#ffffff08;color:#aaa;font-size:1.3rem;cursor:pointer}.site-feedback-close:hover{background:#fff1;color:#fff}.site-feedback-body{display:grid;gap:17px;overflow:auto;padding:21px 26px 24px}.site-feedback-notice{padding:11px 13px;border:1px solid #444;border-radius:10px;font-size:.77rem}.site-feedback-notice.success{border-color:#6a6a6a;background:#d9d9d90b;color:#ddd}.site-feedback-notice.error{border-color:#777;background:#fff1;color:#f1f1f1}.site-feedback-types{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin:0;padding:0;border:0}.site-feedback-types legend{grid-column:1/-1;margin-bottom:1px;color:#b3b3b3;font:600 .73rem var(--mono);letter-spacing:.09em}.site-feedback-types label{display:flex;align-items:flex-start;gap:10px;min-height:64px;padding:11px;border:1px solid #343434;border-radius:11px;background:#191919;color:#aaa;cursor:pointer;transition:border-color .15s ease,background .15s ease,color .15s ease}.site-feedback-types label.active{border-color:#8e8e8e;background:#e4e4e40a;color:#eee}.site-feedback-types input{position:absolute;opacity:0;pointer-events:none}.site-feedback-types label:focus-within{outline:2px solid #777;outline-offset:2px}.site-feedback-types span{display:grid;gap:4px}.site-feedback-types strong{font-size:.8rem;font-weight:600}.site-feedback-types small{color:#828282;font-size:.68rem;line-height:1.35}.site-feedback-field{display:grid;gap:7px;color:#b4b4b4;font-size:.74rem;font-weight:600}.site-feedback-field>span{display:flex;align-items:center;gap:7px}.site-feedback-field b{color:#a8a8a8;font:600 .63rem var(--mono)}.site-feedback-field em{color:#7d7d7d;font-style:normal;font-weight:400}.site-feedback-field input,.site-feedback-field textarea{width:100%;border:1px solid #3c3c3c;border-radius:10px;padding:11px 12px;background:#0b0b0b;color:#e5e5e5;outline:0;font:inherit}.site-feedback-field textarea{min-height:124px;resize:vertical;line-height:1.55}.site-feedback-field input:focus,.site-feedback-field textarea:focus{border-color:#9b9b9b;box-shadow:0 0 0 3px #9b9b9b1c}.site-feedback-field input::placeholder,.site-feedback-field textarea::placeholder{color:#676767}.site-feedback-field>small{justify-self:end;color:#707070;font: .65rem var(--mono);font-weight:400}.site-feedback-footer{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:15px 26px 19px;border-top:1px solid #303030;background:#151515}.site-feedback-footer>span{max-width:265px;color:#737373;font-size:.66rem;line-height:1.45}.site-feedback-footer>div{display:flex;gap:8px}.site-feedback-footer .button{min-width:95px}.feedback-submit{background:#dedede;color:#121212}.feedback-submit:hover{background:#fff}.feedback-submit:disabled{opacity:.45}
@keyframes feedback-in{from{opacity:0;transform:translateY(12px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}
@media(max-width:820px){.feedback-fab{right:14px;bottom:calc(var(--mobile-nav-height,58px) + max(14px,env(safe-area-inset-bottom)))}.feedback-fab.has-compare-tray{bottom:180px}}
@media(max-width:600px){.feedback-fab{padding:10px 13px}.site-feedback-dialog{width:100vw;height:100dvh;max-height:100dvh;border-radius:0}.site-feedback-shell{height:100dvh;max-height:100dvh}.site-feedback-header{padding:20px 18px 16px}.site-feedback-header h2{font-size:1.25rem}.site-feedback-body{padding:18px}.site-feedback-types{grid-template-columns:1fr}.site-feedback-footer{align-items:stretch;flex-direction:column;padding:13px 18px 16px}.site-feedback-footer>span{max-width:none}.site-feedback-footer>div{display:grid;grid-template-columns:1fr 1fr}.site-feedback-footer .button{width:100%}}
@media(prefers-reduced-motion:reduce){.feedback-fab,.site-feedback-dialog[open]{animation:none;transition:none}}
</style>
