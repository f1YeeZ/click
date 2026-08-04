<script setup>
import { computed, defineAsyncComponent, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'
import { onRealtime } from '../services/realtime'
import { legacyCellsToDabs, supportCoveragePercentage } from '../utils/supportHeatmap'

const HandSupport3D = defineAsyncComponent(() => import('../components/HandSupport3D.vue'))

const route = useRoute()
const auth = useAuthStore()
const compare = useCompareStore()
const mouse = ref(null)
const summary = ref(null)
const options = ref(null)
const supportSummary = ref({ sampleCount: 0, positions: [], cells: [], maxCount: 0 })
const personalSupportDabs = ref([])
const supportTool = ref('paint')
const supportBrushSize = ref(12)
const selectedGrip = ref('')
const selectedHand = ref('')
const reviewFiltersInitialized = ref(false)
const mine = ref(null)
const baseLoading = ref(false)
const gripLoading = ref('')
const supportLoading = ref(false)
const reviewEditorOpen = ref(false)
const reviewDialog = ref(null)
const publicSupportError = ref('')
const supportMessage = ref('')
const supportError = ref('')
const message = ref('')
const error = ref('')
const baseForm = reactive({ clickScore: 8, scrollScore: 8, buildScore: 8, coatingScore: 8 })
const gripScores = reactive({ PALM: 8, CLAW: 8, FINGERTIP: 8, MIXED: 8 })
const hasBase = computed(() => Boolean(mine.value?.baseSubmitted))
const profileReady = computed(() => Boolean(auth.user?.handLengthCm && auth.user?.preferredGripStyle))
const submittedGrip = (code) => mine.value?.gripComforts?.find((item) => item.gripStyle === code)
const supportCoverage = computed(() => supportCoveragePercentage(personalSupportDabs.value))
const supportHasPaint = computed(() => supportCoverage.value > 0)
const hasSubmittedSupport = computed(() => Boolean(mine.value?.supportDabs?.length || mine.value?.supportCells?.length))
const completedGripCount = computed(() => mine.value?.gripComforts?.length || 0)
const reviewProgressLabel = computed(() => {
  if (!mine.value) return '还没有提交评价'
  const completed = Number(hasBase.value) + Number(completedGripCount.value > 0) + Number(hasSubmittedSupport.value)
  return `已完成 ${completed} / 3 类评价`
})
const gripSummaryLabel = computed(() => selectedGrip.value
  ? `${options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'}总评`
  : '全部握姿总评')
const distributionRows = (distribution) => Object.entries(distribution || {}).map(([score, count]) => ({
  score: Number(score), count: Number(count),
})).sort((a, b) => b.score - a.score)
const baseDistribution = computed(() => distributionRows(summary.value?.baseScoreDistribution))
const gripDistribution = computed(() => distributionRows(summary.value?.gripScoreDistribution))
const baseDistributionMax = computed(() => Math.max(1, ...baseDistribution.value.map((item) => item.count)))
const gripDistributionMax = computed(() => Math.max(1, ...gripDistribution.value.map((item) => item.count)))
const reviewUpdatedLabel = computed(() => summary.value?.lastUpdatedAt
  ? new Date(summary.value.lastUpdatedAt).toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  : '暂无评价更新')
const matchingHandOption = computed(() => options.value?.handSizes?.find((item) => item.code === auth.user?.handSize))
const handMatchActive = computed(() => Boolean(
  auth.authenticated && auth.user?.handLengthCm && matchingHandOption.value && selectedHand.value === auth.user.handSize
))
const supportFilterLabel = computed(() => {
  const grip = options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '全部握姿'
  const hand = options.value?.handSizes?.find((item) => item.code === selectedHand.value)?.label || '全部手长'
  return `${grip} · ${hand}`
})
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
    const { data } = await api.get(`/mice/${mouse.value.id}/reviews/mine`)
    mine.value = data || null
    if (data) {
      baseForm.clickScore = data.clickScore || 8; baseForm.scrollScore = data.scrollScore || 8
      baseForm.buildScore = data.buildScore || 8; baseForm.coatingScore = data.coatingScore || 8
      personalSupportDabs.value = data.supportDabs?.length
        ? [...data.supportDabs]
        : legacyCellsToDabs(data.supportCells || [])
    }
    else personalSupportDabs.value = []
  } catch { mine.value = null; personalSupportDabs.value = [] }
}
const reviewFilterParams = () => {
  const params = new URLSearchParams()
  if (selectedGrip.value) params.set('gripStyle', selectedGrip.value)
  if (selectedHand.value) params.set('handSize', selectedHand.value)
  return params
}
const loadSupportSummary = async (params = reviewFilterParams()) => {
  if (!mouse.value) return
  supportSummary.value = (await api.get(`/mice/${mouse.value.id}/support-summary?${params}`)).data
}
const initializeReviewFilters = () => {
  if (reviewFiltersInitialized.value) return
  if (matchingHandOption.value) selectedHand.value = matchingHandOption.value.code
  reviewFiltersInitialized.value = true
}
const load = async () => {
  error.value = ''
  try {
    const [{ data }, optionResponse] = await Promise.all([api.get(`/mice/${route.params.id}`), api.get('/review-options')])
    mouse.value = data.mouse; summary.value = data.reviewSummary; options.value = optionResponse.data
    if (auth.authenticated) await auth.refresh()
    initializeReviewFilters()
    if (selectedGrip.value || selectedHand.value) await filterSummary()
    else await loadSupportSummary()
    await loadMine()
  } catch (e) { error.value = errorMessage(e) }
}
const filterSummary = async () => {
  if (!mouse.value) return
  const params = reviewFilterParams()
  try {
    const [reviewResponse] = await Promise.all([
      api.get(`/mice/${mouse.value.id}/review-summary?${params}`),
      loadSupportSummary(params)
    ])
    summary.value = reviewResponse.data
  } catch (e) { error.value = errorMessage(e) }
}
const showAllHandReviews = async () => {
  selectedHand.value = ''
  await filterSummary()
}
const toggleCompare = () => { try { compare.toggle(mouse.value) } catch (e) { error.value = e.message } }
const refreshReview = async () => { await Promise.all([loadMine(), filterSummary()]) }
const saveBase = async () => {
  baseLoading.value = true; message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/reviews/mine/base-score`, baseForm); message.value = '四项基础评分已提交'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) } finally { baseLoading.value = false }
}
const saveGrip = async (code) => {
  gripLoading.value = code; message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${code}`, { comfortScore: gripScores[code] }); message.value = '握持舒适度已提交'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) } finally { gripLoading.value = '' }
}
const deleteBase = async () => {
  if (!window.confirm('确定只删除四项基础评分吗？已提交的握姿评分会保留。')) return
  message.value = ''; error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/reviews/mine/base-score`); message.value = '基础四项评分已删除'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const deleteGrip = async (item) => {
  if (!window.confirm(`确定删除${item.label}的舒适度评分吗？`)) return
  message.value = ''; error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${item.code}`); message.value = `${item.label}评分已删除`; await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const updateSupportDabs = (dabs) => {
  personalSupportDabs.value = dabs
  supportMessage.value = ''; supportError.value = ''
}
const handlePublicSupportModelError = () => { publicSupportError.value = '支撑位置热力图加载失败，请刷新页面后重试' }
const handlePersonalSupportModelError = () => { supportError.value = '个人支撑位置画布加载失败，请刷新页面后重试' }
const clearSupportSelection = () => {
  personalSupportDabs.value = []
  supportMessage.value = ''
  supportError.value = ''
}
const saveSupport = async () => {
  supportLoading.value = true; supportMessage.value = ''; supportError.value = ''
  try {
    await api.put(`/mice/${mouse.value.id}/reviews/mine/support-positions`, { dabs: personalSupportDabs.value })
    supportMessage.value = '涂抹区域已保存并计入热力图'
    await refreshReview()
  } catch (e) { supportError.value = errorMessage(e) } finally { supportLoading.value = false }
}
const openReviewEditor = async () => {
  message.value = ''
  error.value = ''
  reviewEditorOpen.value = true
  await nextTick()
  if (reviewDialog.value && !reviewDialog.value.open) reviewDialog.value.showModal()
}
const closeReviewEditor = () => {
  if (reviewDialog.value?.open) reviewDialog.value.close()
  else reviewEditorOpen.value = false
}
const closeReviewEditorFromBackdrop = (event) => {
  if (event.target === reviewDialog.value) closeReviewEditor()
}
let realtimeTimer
let stopRealtime = () => {}
let resetFiltersOnNextActivation = false
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
onActivated(() => {
  if (!resetFiltersOnNextActivation) return
  resetFiltersOnNextActivation = false
  reviewFiltersInitialized.value = false
  selectedHand.value = ''
  load()
})
onDeactivated(() => {
  resetFiltersOnNextActivation = true
  closeReviewEditor()
})
onBeforeUnmount(() => { closeReviewEditor(); stopRealtime(); clearTimeout(realtimeTimer); pendingRealtimeTypes.clear() })
</script>

<template>
  <main v-if="mouse">
    <section class="detail-hero section-shell">
      <div class="breadcrumb"><RouterLink to="/mice">鼠标库</RouterLink><span>/</span><span>{{ mouse.brand }}</span><span>/</span><strong>{{ mouse.model }}</strong></div>
      <div class="detail-title"><div><p class="eyebrow">{{ mouse.brand }} / SPEC SHEET</p><h1 class="visually-hidden">{{ mouse.model }}</h1><p class="detail-variant">{{ mouse.variant || 'STANDARD EDITION' }}</p></div><button class="button" @click="toggleCompare">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '+ 加入对比清单' }}</button></div>
      <div class="hero-statline"><div><span>DIMENSIONS</span><strong>{{ dimensions }}</strong></div><div><span>WEIGHT</span><strong>{{ mouse.weightG ?? '—' }} g</strong></div><div><span>SENSOR</span><strong>{{ mouse.sensorName || '—' }}</strong></div><div><span>POLLING</span><strong>{{ mouse.maxPollingRateHz ?? '—' }} Hz</strong></div></div>
    </section>
    <div class="section-shell detail-experience-grid">
      <section class="review-panel"><div class="section-heading compact"><div><p class="eyebrow">SUBJECTIVE INDEX</p><h2>用户评价</h2></div><span class="sample-badge" :class="{ low: summary.baseLowSample || summary.gripLowSample || summary.lowSample }">基础 {{ summary.baseSampleCount }} · 握姿 {{ summary.gripSampleCount }}</span></div>
        <div class="review-filters"><label><span>握持方式</span><select v-model="selectedGrip" @change="filterSummary"><option value="">全部握持方式</option><option v-for="item in options?.gripStyles || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label><label><span>手长范围</span><select v-model="selectedHand" @change="filterSummary"><option value="">全部手长</option><option v-for="item in options?.handSizes || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label></div>
        <div v-if="handMatchActive" class="review-match-context">
          <span aria-hidden="true">✓</span>
          <p><strong>已优先展示匹配手长的评价</strong><small>你的手长为 {{ auth.user.handLengthCm }} cm，对应 {{ matchingHandOption.label }}；基础四项仍采用全部评价。</small></p>
          <button type="button" @click="showAllHandReviews">查看全部</button>
        </div>
        <div class="split-score-overview"><article class="score-summary-card base-summary"><div class="score-dial"><strong>{{ summary.baseSampleCount ? summary.baseAverage : '—' }}</strong><span>/ 10.0</span></div><div><small>BASE SCORE</small><h3>基础综合评分</h3><p>{{ summary.baseSampleCount ? `全部 ${summary.baseSampleCount} 份基础评价 · 不受筛选影响` : '暂无基础评分' }}</p></div></article><article class="score-summary-card grip-summary"><div class="score-dial"><strong>{{ summary.gripSampleCount ? summary.gripAverage : '—' }}</strong><span>/ 10.0</span></div><div><small>GRIP SCORE</small><h3>{{ gripSummaryLabel }}</h3><p>{{ summary.gripSampleCount ? `${summary.gripSampleCount} 份握姿评分` : '暂无对应握姿评分' }}</p></div></article></div>
        <div class="dimension-bars" v-if="summary.baseSampleCount"><div class="dimension-title">基础四项明细</div><div v-for="(label, key) in { click:'按键手感', scroll:'滚轮手感', build:'做工质量', coating:'涂层质感' }" :key="key"><span>{{ label }}</span><i><b :style="{ width: (summary.dimensionAverages[key] || 0) * 10 + '%' }"></b></i><strong>{{ summary.dimensionAverages[key] }}</strong></div></div>
        <details class="score-distribution-section" v-if="summary.baseSampleCount || summary.gripSampleCount">
          <summary class="score-distribution-toggle">
            <span class="score-distribution-title"><small>SCORE DISTRIBUTION</small><h3>评分分布</h3></span>
            <span class="score-distribution-meta"><small>最后更新：{{ reviewUpdatedLabel }}</small><em><span class="collapsed-label">展开</span><span class="expanded-label">收起</span><i aria-hidden="true"></i></em></span>
          </summary>
          <div class="score-distribution-content">
            <div class="score-distribution-grid">
              <article v-if="summary.baseSampleCount"><h4>基础综合评分</h4><p>每位用户四项基础评分的平均值，四舍五入后归入 1—10 分区间。</p><div class="distribution-bars"><div v-for="item in baseDistribution" :key="`base-${item.score}`"><span>{{ item.score }}</span><i><b :style="{ width: `${item.count / baseDistributionMax * 100}%` }"></b></i><strong>{{ item.count }}</strong></div></div></article>
              <article v-if="summary.gripSampleCount"><h4>{{ gripSummaryLabel }}</h4><p>按当前握姿与手长筛选统计；一位用户评价多种握姿时分别计入对应样本。</p><div class="distribution-bars"><div v-for="item in gripDistribution" :key="`grip-${item.score}`"><span>{{ item.score }}</span><i><b :style="{ width: `${item.count / gripDistributionMax * 100}%` }"></b></i><strong>{{ item.count }}</strong></div></div></article>
            </div>
            <p class="score-method-note">口径说明：基础四项不受握姿筛选影响；握姿评分随上方筛选变化。样本少于 5 份时仅供参考，排序时会自动置于充足样本之后。</p>
          </div>
        </details>
        <div class="review-action-bar">
          <div class="review-action-copy">
            <small>MY REVIEW</small>
            <strong>{{ auth.authenticated ? reviewProgressLabel : '分享你的真实使用感受' }}</strong>
            <p>{{ auth.authenticated ? '基础评分、握持舒适度和支撑位置可以分别提交。' : '登录后使用固定模板评价，结果会匿名计入汇总。' }}</p>
          </div>
          <RouterLink v-if="!auth.authenticated" class="button primary-action-button review-write-button" to="/login">登录后写评价</RouterLink>
          <button v-else-if="options" class="button primary-action-button review-write-button" type="button" @click="openReviewEditor">
            {{ mine ? '管理我的评价' : '写评价' }}<span aria-hidden="true">→</span>
          </button>
        </div>
      </section>

      <aside class="support-panel">
        <div class="section-heading compact support-heading">
          <div><p class="eyebrow">CONTACT HEATMAP</p><h2>支撑位置评价</h2></div>
          <span class="sample-badge" :class="{ low: supportSummary.sampleCount < 5 }">{{ supportSummary.sampleCount }} 人标记</span>
        </div>
        <p class="support-intro">这里仅展示全部用户提交后的匿名汇总结果。被更多用户标记的区域颜色更深，不会叠加你尚未保存的个人笔迹。</p>
        <div class="support-filter-context"><span>同步筛选</span><strong>{{ supportFilterLabel }}</strong><small>握姿按用户资料中的习惯握姿归类</small></div>
        <div class="support-map public-support-map readonly" :class="{ empty: !supportSummary.cells?.length }">
          <HandSupport3D
            :summary-cells="supportSummary.cells || []"
            :max-count="supportSummary.maxCount || 0"
            :grid-columns="supportSummary.gridColumns || 64"
            :grid-rows="supportSummary.gridRows || 96"
            :dabs="[]"
            tool="view"
            :editable="false"
            aria-label="所有用户支撑位置热力图"
            @error="handlePublicSupportModelError"
          />
          <span class="support-mode-hint">{{ supportSummary.cells?.length ? `当前展示 ${supportSummary.sampleCount} 人的汇总结果，可用左键拖动旋转` : '当前筛选下暂无支撑位置评价，可用左键拖动旋转模型' }}</span>
        </div>
        <div class="heat-legend" v-if="supportSummary.cells?.length"><span>覆盖较少</span><i></i><span>覆盖较多</span></div>
        <div class="flash error" v-if="publicSupportError">{{ publicSupportError }}</div>
        <p class="public-support-note">想添加或修改自己的支撑位置，请点击左侧的“写评价”按钮。</p>
      </aside>
    </div>

    <Teleport to="body">
      <dialog
        v-if="auth.authenticated && options && reviewEditorOpen"
        ref="reviewDialog"
        class="review-dialog"
        aria-labelledby="review-dialog-title"
        @click="closeReviewEditorFromBackdrop"
        @close="reviewEditorOpen = false"
      >
        <div class="review-dialog-shell">
          <header class="review-dialog-header">
            <div>
              <small>MY REVIEW / {{ mouse.brand }}</small>
              <h2 id="review-dialog-title">评价 {{ mouse.model }}</h2>
              <p>各部分独立保存，可以随时回来继续完成。</p>
            </div>
            <button class="review-dialog-close" type="button" aria-label="关闭评价窗口" @click="closeReviewEditor">×</button>
          </header>
          <div class="review-dialog-status" aria-live="polite">
            <span>{{ reviewProgressLabel }}</span>
            <i><b :style="{ width: `${(Number(hasBase) + Number(completedGripCount > 0) + Number(hasSubmittedSupport)) / 3 * 100}%` }"></b></i>
          </div>
          <div class="review-dialog-body">
            <div class="flash success" v-if="message">{{ message }}</div>
            <div class="flash error" v-if="error">{{ error }}</div>
            <div class="profile-required" v-if="!profileReady"><span>PROFILE REQUIRED</span><p>评分时会自动读取个人资料中的手长和习惯握姿，请先填写后再回来提交。</p><RouterLink class="button button-ghost" to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
            <div class="review-entry-stack">
              <section class="review-entry-card base-entry" :class="{ locked: hasBase }">
                <header><div><span>01 / BASE SCORE</span><h3>四项基础评分</h3></div><em>{{ hasBase ? '已提交 · 不可重复' : '每款鼠标仅一次' }}</em></header>
                <template v-if="hasBase"><div class="locked-score-grid"><div v-for="field in [['clickScore','按键手感'],['scrollScore','滚轮手感'],['buildScore','做工质量'],['coatingScore','涂层质感']]" :key="field[0]"><span>{{ field[1] }}</span><strong>{{ mine[field[0]] }}</strong><small>/ 10</small></div></div><button class="item-delete-button" type="button" @click="deleteBase">删除基础四项</button></template>
                <form v-else @submit.prevent="saveBase">
                  <div class="score-inputs"><label v-for="field in [['clickScore','按键手感'],['scrollScore','滚轮手感'],['buildScore','做工质量'],['coatingScore','涂层质感']]" :key="field[0]">{{ field[1] }} <output>{{ baseForm[field[0]] }}</output><input v-model.number="baseForm[field[0]]" type="range" min="1" max="10"></label></div>
                  <button class="button full" :disabled="!profileReady || baseLoading">{{ baseLoading ? '提交中…' : '确认提交四项评分' }}</button>
                </form>
              </section>
              <section class="review-entry-card grip-entry">
                <header><div><span>02 / GRIP COMFORT</span><h3>握持舒适度</h3></div><em>{{ completedGripCount }} / 4 已评价</em></header>
                <p class="review-hint">四种握持方式分别记录，每种方式仅可提交一次；汇总会按用户习惯握姿加权。</p>
                <div class="grip-score-list">
                  <article v-for="item in options.gripStyles" :key="item.code" :class="{ completed: submittedGrip(item.code) }">
                    <div class="grip-score-head"><div><span>{{ item.label }}</span><small>{{ item.code }}</small></div><strong>{{ submittedGrip(item.code)?.comfortScore ?? gripScores[item.code] }}</strong></div>
                    <template v-if="submittedGrip(item.code)"><div class="completed-grip-actions"><span class="grip-complete-mark">✓ 已完成该握姿评分</span><button class="item-delete-button compact" type="button" @click="deleteGrip(item)">删除此项</button></div></template>
                    <template v-else><input v-model.number="gripScores[item.code]" type="range" min="1" max="10"><button type="button" @click="saveGrip(item.code)" :disabled="!profileReady || gripLoading === item.code">{{ gripLoading === item.code ? '提交中…' : `提交${item.label}评分` }}</button></template>
                  </article>
                </div>
              </section>
              <section class="review-entry-card support-entry personal-support-editor">
                <header>
                  <div><span>03 / SUPPORT MAP</span><h3>我的支撑位置</h3></div>
                  <em>{{ supportHasPaint ? `已涂抹约 ${supportCoverage}%` : '尚未涂抹' }}</em>
                </header>
                <p class="review-hint">这张手掌图只用于编辑你的评价。保存后，笔迹才会匿名计入详情页的全部用户热力图。</p>
                <div class="support-editor-layout">
                  <div class="support-editor-controls">
                    <div class="support-tools" aria-label="个人支撑位置涂抹工具">
                      <button type="button" :class="{ active: supportTool === 'paint' }" :disabled="!profileReady" @click="supportTool = 'paint'">涂抹</button>
                      <button type="button" :class="{ active: supportTool === 'erase' }" :disabled="!profileReady" @click="supportTool = 'erase'">擦除</button>
                      <button type="button" :class="{ active: supportTool === 'rotate' }" @click="supportTool = 'rotate'">旋转查看</button>
                      <button type="button" :disabled="!profileReady || !personalSupportDabs.length" @click="clearSupportSelection">清空</button>
                    </div>
                    <label class="support-brush-size">
                      <span><strong>画笔大小</strong><output>{{ supportBrushSize }}%</output></span>
                      <input v-model.number="supportBrushSize" type="range" min="4" max="20" step="1" :disabled="!profileReady">
                    </label>
                    <div class="support-selection-status">
                      <strong>{{ supportHasPaint ? `已涂抹约 ${supportCoverage}% 的掌面画布` : '尚未涂抹支撑区域' }}</strong>
                      <span>{{ supportHasPaint ? '可以继续涂抹或擦除，保存后才会更新公共热力图' : '按住鼠标或用手指，在掌面连续涂抹鼠标实际托住的位置' }}</span>
                    </div>
                    <div class="flash success" v-if="supportMessage">{{ supportMessage }}</div>
                    <div class="flash error" v-if="supportError">{{ supportError }}</div>
                    <div class="support-profile-required" v-if="!profileReady"><span>需要先填写手长与习惯握姿</span><RouterLink to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
                    <button class="button full support-submit" type="button" :disabled="!profileReady || !supportHasPaint || supportLoading" @click="saveSupport">
                      {{ supportLoading ? '保存中…' : mine?.supportDabs?.length || mine?.supportCells?.length ? '更新我的支撑位置' : '提交我的支撑位置' }}
                    </button>
                  </div>
                  <div class="support-editor-canvas">
                    <div class="support-map personal-support-map" :class="{ readonly: !profileReady }">
                      <HandSupport3D
                        :summary-cells="[]"
                        :max-count="0"
                        :dabs="personalSupportDabs"
                        :brush-size="supportBrushSize"
                        :tool="supportTool"
                        :editable="profileReady"
                        aria-label="可涂抹的个人支撑位置图"
                        @update:dabs="updateSupportDabs"
                        @error="handlePersonalSupportModelError"
                      />
                      <span class="support-mode-hint">{{ !profileReady ? '完善个人资料后即可涂抹；左键拖动模型可旋转查看' : supportTool === 'rotate' ? '按住左键拖动旋转模型，确认位置后切回涂抹' : supportTool === 'erase' ? '左键或手指擦除；需要旋转时请切换到“旋转查看”' : '左键或手指涂抹；需要旋转时请切换到“旋转查看”' }}</span>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>
      </dialog>
    </Teleport>

    <section class="section-shell detail-spec-section">
      <div class="spec-sheet"><div class="section-heading compact"><div><p class="eyebrow">OBJECTIVE DATA</p><h2>客观参数</h2></div><span class="verified-mark">● DATA VERIFIED</span></div>
        <div class="spec-groups-grid">
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
        </div>
        <div class="source-card"><span>DATA SOURCE</span><p v-if="mouse.sourceNotes">{{ mouse.sourceNotes }}</p><a v-if="mouse.primarySourceUrl" :href="mouse.primarySourceUrl" target="_blank" rel="noopener noreferrer">查看原始数据来源 ↗</a></div>
      </div>
    </section>
  </main>
  <main v-else class="section-shell error-page"><div class="flash error" v-if="error">{{ error }}</div><div v-else class="loading-state">LOADING SPEC SHEET...</div></main>
</template>
