<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'
import { onRealtime } from '../services/realtime'

const route = useRoute()
const auth = useAuthStore()
const compare = useCompareStore()
const mouse = ref(null)
const summary = ref(null)
const options = ref(null)
const selectedGrip = ref('')
const selectedHand = ref('')
const mine = ref(null)
const baseLoading = ref(false)
const gripLoading = ref('')
const message = ref('')
const error = ref('')
const baseForm = reactive({ clickScore: 8, scrollScore: 8, buildScore: 8, coatingScore: 8 })
const gripScores = reactive({ PALM: 8, CLAW: 8, FINGERTIP: 8, MIXED: 8 })
const hasBase = computed(() => Boolean(mine.value?.baseSubmitted))
const profileReady = computed(() => Boolean(auth.user?.handLengthCm))
const submittedGrip = (code) => mine.value?.gripComforts?.find((item) => item.gripStyle === code)
const gripSummaryLabel = computed(() => selectedGrip.value
  ? `${options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'}总评`
  : '全部握姿总评')
const labels = {
  FINGERTIP: 'Fingertip', EXTRA_SMALL: '超小', SMALL: '小', MEDIUM: '中', LARGE: '大',
  SYMMETRICAL: '对称', ERGONOMIC: '人体工学', HYBRID: '混合', RIGHT: '右手', LEFT: '左手', AMBIDEXTROUS: '双手',
  FRONT: '前部', CENTER: '中部', BACK: '后部', NARROW: '内收', NEUTRAL: '平直', FLARED: '外扩',
  FLAT: '平直', MILD: '轻微', CURVED: '明显', OPTICAL: '光学', LASER: '激光', MECHANICAL: '机械',
  INDUCTIVE: '电感', MAGNETIC: '磁性'
}
const connection = computed(() => !mouse.value ? '—' : mouse.value.connectionModes.length >= 3 ? '三模' : mouse.value.connectionModes.length === 2 ? '双模' : mouse.value.connectionModes.includes('wired') ? '有线' : '无线')
const dimensions = computed(() => mouse.value ? `${mouse.value.lengthMm ?? '—'} × ${mouse.value.widthMm ?? '—'} × ${mouse.value.heightMm ?? '—'} mm` : '—')
const yesNo = (value) => value == null ? '—' : value ? '是' : '否'
const valueLabel = (value) => labels[value] || value || '—'

const loadMine = async () => {
  if (!auth.authenticated || !mouse.value) return
  try {
    const { data } = await api.get(`/mice/${mouse.value.id}/my-review`)
    mine.value = data || null
    if (data) {
      baseForm.clickScore = data.clickScore || 8; baseForm.scrollScore = data.scrollScore || 8
      baseForm.buildScore = data.buildScore || 8; baseForm.coatingScore = data.coatingScore || 8
    }
  } catch { mine.value = null }
}
const load = async () => {
  error.value = ''
  try {
    const [{ data }, optionResponse] = await Promise.all([api.get(`/mice/${route.params.slug}`), api.get('/review-options')])
    mouse.value = data.mouse; summary.value = data.reviewSummary; options.value = optionResponse.data
    if (auth.authenticated) await auth.refresh()
    await loadMine()
  } catch (e) { error.value = errorMessage(e) }
}
const filterSummary = async () => {
  if (!mouse.value) return
  const params = new URLSearchParams()
  if (selectedGrip.value) params.set('gripStyle', selectedGrip.value)
  if (selectedHand.value) params.set('handSize', selectedHand.value)
  try { summary.value = (await api.get(`/mice/${mouse.value.id}/review-summary?${params}`)).data } catch (e) { error.value = errorMessage(e) }
}
const toggleCompare = () => { try { compare.toggle(mouse.value) } catch (e) { error.value = e.message } }
const refreshReview = async () => { await loadMine(); await filterSummary() }
const saveBase = async () => {
  baseLoading.value = true; message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/my-review/base`, baseForm); message.value = '四项基础评分已提交'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) } finally { baseLoading.value = false }
}
const saveGrip = async (code) => {
  gripLoading.value = code; message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/my-review/grips/${code}`, { comfortScore: gripScores[code] }); message.value = '握持舒适度已提交'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) } finally { gripLoading.value = '' }
}
const deleteBase = async () => {
  if (!window.confirm('确定只删除四项基础评分吗？已提交的握姿评分会保留。')) return
  message.value = ''; error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/my-review/base`); message.value = '基础四项评分已删除'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const deleteGrip = async (item) => {
  if (!window.confirm(`确定删除${item.label}的舒适度评分吗？`)) return
  message.value = ''; error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/my-review/grips/${item.code}`); message.value = `${item.label}评分已删除`; await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
let realtimeTimer
let stopRealtime = () => {}
const pendingRealtimeTypes = new Set()
onMounted(() => {
  load()
  stopRealtime = onRealtime((event) => {
    if (!mouse.value || event.mouseId !== mouse.value.id) return
    pendingRealtimeTypes.add(event.type)
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(() => {
      const reloadMouse = pendingRealtimeTypes.has('mouse.changed')
      const reloadReview = pendingRealtimeTypes.has('review.changed')
      pendingRealtimeTypes.clear()
      if (reloadMouse) load()
      else if (reloadReview) refreshReview()
    }, 200)
  })
})
onBeforeUnmount(() => { stopRealtime(); clearTimeout(realtimeTimer); pendingRealtimeTypes.clear() })
watch(() => route.params.slug, load)
</script>

<template>
  <main v-if="mouse">
    <section class="detail-hero section-shell">
      <div class="breadcrumb"><RouterLink to="/mice">鼠标库</RouterLink><span>/</span><span>{{ mouse.brand }}</span><span>/</span><strong>{{ mouse.model }}</strong></div>
      <div class="detail-title"><div><p class="eyebrow">{{ mouse.brand }} / SPEC SHEET</p><h1>{{ mouse.model }}</h1><p class="detail-variant">{{ mouse.variant || 'STANDARD EDITION' }}</p></div><button class="button" @click="toggleCompare">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '+ 加入对比清单' }}</button></div>
      <div class="hero-statline"><div><span>DIMENSIONS</span><strong>{{ dimensions }}</strong></div><div><span>WEIGHT</span><strong>{{ mouse.weightG ?? '—' }} g</strong></div><div><span>SENSOR</span><strong>{{ mouse.sensorName || '—' }}</strong></div><div><span>POLLING</span><strong>{{ mouse.maxPollingRateHz ?? '—' }} Hz</strong></div></div>
    </section>
    <div class="section-shell detail-grid">
      <section class="spec-sheet"><div class="section-heading compact"><div><p class="eyebrow">OBJECTIVE DATA</p><h2>客观参数</h2></div><span class="verified-mark">● DATA VERIFIED</span></div>
        <div class="spec-group"><h3>尺寸与重量</h3><dl><div><dt>尺寸分类</dt><dd>{{ labels[mouse.sizeCategory] || '—' }}</dd></div><div><dt>长度</dt><dd>{{ mouse.lengthMm ?? '—' }} mm</dd></div><div><dt>宽度</dt><dd>{{ mouse.widthMm ?? '—' }} mm</dd></div><div><dt>高度</dt><dd>{{ mouse.heightMm ?? '—' }} mm</dd></div><div><dt>重量</dt><dd>{{ mouse.weightG ?? '—' }} g</dd></div></dl></div>
        <div class="spec-group"><h3>外形细节</h3><dl>
          <div><dt>外形类型</dt><dd>{{ valueLabel(mouse.shapeType) }}</dd></div><div><dt>适用手</dt><dd>{{ valueLabel(mouse.handCompatibility) }}</dd></div>
          <div><dt>隆起位置</dt><dd>{{ valueLabel(mouse.humpPlacement) }}</dd></div><div><dt>前端外扩</dt><dd>{{ valueLabel(mouse.frontFlare) }}</dd></div>
          <div><dt>侧面曲率</dt><dd>{{ valueLabel(mouse.sideCurvature) }}</dd></div><div><dt>拇指托</dt><dd>{{ yesNo(mouse.thumbRest) }}</dd></div>
          <div><dt>无名指托</dt><dd>{{ yesNo(mouse.ringFingerRest) }}</dd></div>
        </dl></div>
        <div class="spec-group"><h3>传感器与性能</h3><dl>
          <div><dt>传感器型号</dt><dd>{{ mouse.sensorName || '—' }}</dd></div><div><dt>传感器类型</dt><dd>{{ valueLabel(mouse.sensorType) }}</dd></div>
          <div><dt>最大 DPI</dt><dd>{{ mouse.maxDpi ?? '—' }}</dd></div><div><dt>最大回报率</dt><dd>{{ mouse.maxPollingRateHz ?? '—' }} Hz</dd></div>
          <div><dt>追踪速度</dt><dd>{{ mouse.trackingSpeedIps ?? '—' }} IPS</dd></div><div><dt>最大加速度</dt><dd>{{ mouse.accelerationG ?? '—' }} G</dd></div>
          <div><dt>可调位置</dt><dd>{{ yesNo(mouse.adjustableSensorPosition) }}</dd></div><div><dt>传感器位置</dt><dd>{{ mouse.sensorPositionX ?? '—' }} / {{ mouse.sensorPositionY ?? '—' }}</dd></div>
          <div v-if="mouse.sensorPositionX2 != null || mouse.sensorPositionY2 != null"><dt>第二位置</dt><dd>{{ mouse.sensorPositionX2 ?? '—' }} / {{ mouse.sensorPositionY2 ?? '—' }}</dd></div>
        </dl></div>
        <div class="spec-group"><h3>按键与微动</h3><dl>
          <div><dt>总按键数</dt><dd>{{ mouse.buttonCount ?? '—' }}</dd></div><div><dt>侧键数</dt><dd>{{ mouse.sideButtonCount ?? '—' }}</dd></div>
          <div><dt>微动型号</dt><dd>{{ mouse.switchName || '—' }}</dd></div><div><dt>微动类型</dt><dd>{{ valueLabel(mouse.switchType) }}</dd></div>
          <div><dt>微动寿命</dt><dd>{{ mouse.switchLifeSpanM != null ? `${mouse.switchLifeSpanM} 百万次` : '—' }}</dd></div><div><dt>热插拔微动</dt><dd>{{ yesNo(mouse.hotSwappableSwitches) }}</dd></div>
        </dl></div>
        <div class="spec-group"><h3>滚轮、材质与连接</h3><dl>
          <div><dt>编码器型号</dt><dd>{{ mouse.encoderName || '—' }}</dd></div><div><dt>编码器类型</dt><dd>{{ valueLabel(mouse.encoderType) }}</dd></div>
          <div><dt>滚轮步数</dt><dd>{{ mouse.encoderSteps ?? '—' }}</dd></div><div><dt>连接方式</dt><dd>{{ connection }}</dd></div>
          <div><dt>主要材质</dt><dd>{{ mouse.materialGeneral || mouse.material || '—' }}</dd></div><div><dt>具体材质</dt><dd>{{ mouse.materialSpecific || '—' }}</dd></div>
          <div><dt>购买渠道</dt><dd>{{ mouse.purchaseChannels || '—' }}</dd></div>
        </dl></div>
        <div class="source-card"><span>DATA SOURCE</span><p v-if="mouse.sourceNotes">{{ mouse.sourceNotes }}</p><a v-if="mouse.primarySourceUrl" :href="mouse.primarySourceUrl" target="_blank" rel="noopener noreferrer">查看原始数据来源 ↗</a></div>
      </section>
      <aside class="review-panel"><div class="section-heading compact"><div><p class="eyebrow">SUBJECTIVE INDEX</p><h2>用户评价</h2></div><span class="sample-badge" :class="{ low: summary.lowSample }">基础 {{ summary.baseSampleCount }} · 握姿 {{ summary.gripSampleCount }}</span></div>
        <div class="review-filters"><label><span>握持方式</span><select v-model="selectedGrip" @change="filterSummary"><option value="">全部握持方式</option><option v-for="item in options?.gripStyles || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label><label><span>手长范围</span><select v-model="selectedHand" @change="filterSummary"><option value="">全部手长</option><option v-for="item in options?.handSizes || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label></div>
        <div class="split-score-overview"><article class="score-summary-card base-summary"><div class="score-dial"><strong>{{ summary.baseSampleCount ? summary.baseAverage : '—' }}</strong><span>/ 10.0</span></div><div><small>BASE SCORE</small><h3>基础综合评分</h3><p>{{ summary.baseSampleCount ? `全部 ${summary.baseSampleCount} 份基础评价 · 不受筛选影响` : '暂无基础评分' }}</p></div></article><article class="score-summary-card grip-summary"><div class="score-dial"><strong>{{ summary.gripSampleCount ? summary.gripAverage : '—' }}</strong><span>/ 10.0</span></div><div><small>GRIP SCORE</small><h3>{{ gripSummaryLabel }}</h3><p>{{ summary.gripSampleCount ? `${summary.gripSampleCount} 份握姿评分` : '暂无对应握姿评分' }}</p></div></article></div>
        <div class="dimension-bars" v-if="summary.baseSampleCount"><div class="dimension-title">基础四项明细</div><div v-for="(label, key) in { click:'按键手感', scroll:'滚轮手感', build:'做工质量', coating:'涂层质感' }" :key="key"><span>{{ label }}</span><i><b :style="{ width: (summary.dimensionAverages[key] || 0) * 10 + '%' }"></b></i><strong>{{ summary.dimensionAverages[key] }}</strong></div></div>
        <div class="flash success" v-if="message">{{ message }}</div><div class="flash error" v-if="error">{{ error }}</div>
        <div class="login-callout" v-if="!auth.authenticated"><span>LOGIN REQUIRED</span><h3>用固定模板分享体验</h3><p>无自由文本，所有评价都可直接聚合比较。</p><RouterLink class="button" to="/login">登录后评价</RouterLink></div>
        <div class="review-entry-stack" v-else-if="options">
          <div class="profile-required" v-if="!profileReady"><span>PROFILE REQUIRED</span><p>评分时会自动读取个人资料中的手长，请先填写后再回来提交。</p><RouterLink class="button button-ghost" to="/profile">填写个人手长 →</RouterLink></div>
          <section class="review-entry-card base-entry" :class="{ locked: hasBase }">
            <header><div><span>01 / BASE SCORE</span><h3>四项基础评分</h3></div><em>{{ hasBase ? '已提交 · 不可重复' : '每款鼠标仅一次' }}</em></header>
            <template v-if="hasBase"><div class="locked-score-grid"><div v-for="field in [['clickScore','按键手感'],['scrollScore','滚轮手感'],['buildScore','做工质量'],['coatingScore','涂层质感']]" :key="field[0]"><span>{{ field[1] }}</span><strong>{{ mine[field[0]] }}</strong><small>/ 10</small></div></div><button class="item-delete-button" type="button" @click="deleteBase">删除基础四项</button></template>
            <form v-else @submit.prevent="saveBase">
              <div class="score-inputs"><label v-for="field in [['clickScore','按键手感'],['scrollScore','滚轮手感'],['buildScore','做工质量'],['coatingScore','涂层质感']]" :key="field[0]">{{ field[1] }} <output>{{ baseForm[field[0]] }}</output><input v-model.number="baseForm[field[0]]" type="range" min="1" max="10"></label></div>
              <button class="button full" :disabled="!profileReady || baseLoading">{{ baseLoading ? '提交中…' : '确认提交四项评分' }}</button>
            </form>
          </section>
          <section class="review-entry-card grip-entry">
            <header><div><span>02 / GRIP COMFORT</span><h3>握持舒适度</h3></div><em>{{ mine?.gripComforts?.length || 0 }} / 4 已评价</em></header>
            <p class="review-hint">四种握持方式分别记录，每种方式仅可提交一次。</p>
            <div class="grip-score-list">
              <article v-for="item in options.gripStyles" :key="item.code" :class="{ completed: submittedGrip(item.code) }">
                <div class="grip-score-head"><div><span>{{ item.label }}</span><small>{{ item.code }}</small></div><strong>{{ submittedGrip(item.code)?.comfortScore ?? gripScores[item.code] }}</strong></div>
                <template v-if="submittedGrip(item.code)"><div class="completed-grip-actions"><span class="grip-complete-mark">✓ 已完成该握姿评分</span><button class="item-delete-button compact" type="button" @click="deleteGrip(item)">删除此项</button></div></template>
                <template v-else><input v-model.number="gripScores[item.code]" type="range" min="1" max="10"><button type="button" @click="saveGrip(item.code)" :disabled="!profileReady || !hasBase || gripLoading === item.code">{{ !hasBase ? '先提交基础四项' : gripLoading === item.code ? '提交中…' : `提交${item.label}评分` }}</button></template>
              </article>
            </div>
          </section>
        </div>
      </aside>
    </div>
  </main>
  <main v-else class="section-shell error-page"><div class="flash error" v-if="error">{{ error }}</div><div v-else class="loading-state">LOADING SPEC SHEET...</div></main>
</template>
