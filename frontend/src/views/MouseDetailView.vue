<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'

const route = useRoute()
const auth = useAuthStore()
const compare = useCompareStore()
const mouse = ref(null)
const summary = ref(null)
const options = ref(null)
const hasReview = ref(false)
const message = ref('')
const error = ref('')
const form = reactive({ gripStyle: '', handSize: '', usageDuration: '', comfortScore: 5, clickScore: 5, scrollScore: 5, buildScore: 5, valueScore: 5, proTags: [], conTags: [] })
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
    if (data) { Object.assign(form, data); hasReview.value = true }
  } catch { hasReview.value = false }
}
const load = async () => {
  error.value = ''
  try {
    const [{ data }, optionResponse] = await Promise.all([api.get(`/mice/${route.params.slug}`), api.get('/review-options')])
    mouse.value = data.mouse; summary.value = data.reviewSummary; options.value = optionResponse.data
    await loadMine()
  } catch (e) { error.value = errorMessage(e) }
}
const toggleCompare = () => { try { compare.toggle(mouse.value) } catch (e) { error.value = e.message } }
const limitTags = (key, event) => { if (form[key].length > 3) { form[key].splice(form[key].indexOf(event.target.value), 1); window.alert('每类标签最多选择 3 个') } }
const save = async () => {
  message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/my-review`, form); message.value = '评价已保存'; hasReview.value = true; await load() }
  catch (e) { error.value = errorMessage(e) }
}
const remove = async () => { if (!window.confirm('确定删除自己的评价吗？')) return; await api.delete(`/mice/${mouse.value.id}/my-review`); hasReview.value = false; message.value = '评价已删除'; await load() }
onMounted(load)
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
      <aside class="review-panel"><div class="section-heading compact"><div><p class="eyebrow">SUBJECTIVE INDEX</p><h2>用户评价</h2></div><span class="sample-badge" :class="{ low: summary.lowSample }">{{ summary.sampleCount }} SAMPLES</span></div>
        <div class="score-overview"><div class="score-dial"><strong>{{ summary.sampleCount ? summary.overallAverage : '—' }}</strong><span>/ 5.0</span></div><p>{{ summary.sampleCount ? (summary.lowSample ? '样本较少，数据仅供参考。' : '样本量已达到公开排行门槛。') : '暂无结构化评价，成为第一位评价者。' }}</p></div>
        <div class="dimension-bars" v-if="summary.sampleCount"><div v-for="(label, key) in { comfort:'握持舒适', click:'按键手感', scroll:'滚轮手感', build:'做工质量', value:'性价比' }" :key="key"><span>{{ label }}</span><i><b :style="{ width: summary.dimensionAverages[key] * 20 + '%' }"></b></i><strong>{{ summary.dimensionAverages[key] }}</strong></div></div>
        <div class="flash success" v-if="message">{{ message }}</div><div class="flash error" v-if="error">{{ error }}</div>
        <div class="login-callout" v-if="!auth.authenticated"><span>LOGIN REQUIRED</span><h3>用固定模板分享体验</h3><p>无自由文本，所有评价都可直接聚合比较。</p><RouterLink class="button" to="/login">登录后评价</RouterLink></div>
        <form class="review-form" v-else-if="options" @submit.prevent="save"><h3>{{ hasReview ? '更新我的评价' : '提交我的评价' }}</h3>
          <div class="review-context"><label>握持方式<select v-model="form.gripStyle" required><option value="">请选择</option><option v-for="item in options.gripStyles" :key="item.code" :value="item.code">{{ item.label }}</option></select></label><label>手长范围<select v-model="form.handSize" required><option value="">请选择</option><option v-for="item in options.handSizes" :key="item.code" :value="item.code">{{ item.label }}</option></select></label><label>使用时长<select v-model="form.usageDuration" required><option value="">请选择</option><option v-for="item in options.usageDurations" :key="item.code" :value="item.code">{{ item.label }}</option></select></label></div>
          <div class="score-inputs"><label v-for="field in [['comfortScore','握持舒适度'],['clickScore','按键手感'],['scrollScore','滚轮手感'],['buildScore','做工质量'],['valueScore','性价比']]" :key="field[0]">{{ field[1] }} <output>{{ form[field[0]] }}</output><input v-model.number="form[field[0]]" type="range" min="1" max="5"></label></div>
          <fieldset class="tag-picker"><legend>优点标签 <small>最多 3 个</small></legend><label v-for="tag in options.proTags" :key="tag.code"><input v-model="form.proTags" type="checkbox" :value="tag.code" @change="limitTags('proTags', $event)"><span>{{ tag.label }}</span></label></fieldset>
          <fieldset class="tag-picker"><legend>问题标签 <small>最多 3 个</small></legend><label v-for="tag in options.conTags" :key="tag.code"><input v-model="form.conTags" type="checkbox" :value="tag.code" @change="limitTags('conTags', $event)"><span>{{ tag.label }}</span></label></fieldset>
          <button class="button full">{{ hasReview ? '保存修改' : '提交评价' }}</button><button v-if="hasReview" class="text-button danger delete-review" type="button" @click="remove">删除我的评价</button>
        </form>
      </aside>
    </div>
  </main>
  <main v-else class="section-shell error-page"><div class="flash error" v-if="error">{{ error }}</div><div v-else class="loading-state">LOADING SPEC SHEET...</div></main>
</template>
