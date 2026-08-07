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
const personalSupportDabsByGrip = reactive({ PALM: [], CLAW: [], FINGERTIP: [], MIXED: [] })
const activeSupportGrip = ref('')
const publicSupportGripSelection = reactive({})
const supportTool = ref('paint')
const supportBrushSize = ref(12)
const selectedGrip = ref('')
const selectedHand = ref('')
const reviewFiltersInitialized = ref(false)
const mine = ref(null)
const gripLoading = ref('')
const supportLoading = ref(false)
const reviewEditorOpen = ref(false)
const reviewDialog = ref(null)
const publicSupportError = ref('')
const supportMessage = ref('')
const supportError = ref('')
const message = ref('')
const error = ref('')
const publicReviews = ref({ items: [], page: { number: 1, totalPages: 1, totalItems: 0 } })
const publicReviewsRail = ref(null)
const reportTarget = ref(null)
const reportScope = ref('MOUSE_DATA')
const reportDialog = ref(null)
const reportCategory = ref('DATA_ERROR')
const reportDescription = ref('')
const reportNotice = ref('')
const reportLoading = ref(false)
const reviewSubmissionEnabled = ref(true)
const gripScores = reactive({ PALM: 8, CLAW: 8, FINGERTIP: 8, MIXED: 8 })
const profileReady = computed(() => Boolean(auth.user?.handLengthCm && auth.user?.preferredGripStyle))
const submittedGrip = (code) => mine.value?.gripComforts?.find((item) => item.gripStyle === code)
const personalSupportDabs = computed(() => personalSupportDabsByGrip[activeSupportGrip.value] || [])
const supportCoverage = computed(() => supportCoveragePercentage(personalSupportDabs.value))
const supportHasPaint = computed(() => supportCoverage.value > 0)
const supportGripCount = computed(() => Object.values(personalSupportDabsByGrip).filter((dabs) => supportCoveragePercentage(dabs) > 0).length)
const hasSubmittedSupport = computed(() => supportGripCount.value > 0)
const completedGripCount = computed(() => mine.value?.gripComforts?.length || 0)
const reviewCompletionCount = computed(() => completedGripCount.value + supportGripCount.value)
const reviewProgressPercent = computed(() => reviewCompletionCount.value / 8 * 100)
const reviewProgressLabel = computed(() => {
  if (!mine.value) return '还没有提交评价'
  return `已完成 ${reviewCompletionCount.value} / 8 项评价`
})
const gripSummaryLabel = computed(() => selectedGrip.value
  ? `${options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'}总评`
  : '全部握姿总评')
const distributionRows = (distribution) => Object.entries(distribution || {}).map(([score, count]) => ({
  score: Number(score), count: Number(count),
})).sort((a, b) => b.score - a.score)
const gripDistribution = computed(() => distributionRows(summary.value?.scoreDistribution))
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
const supportMapForGrip = (review, gripStyle) => {
  const mapped = review?.supportByGrip?.find((item) => item.gripStyle === gripStyle)
  if (mapped) return mapped
  const legacyGrip = review?.gripScores?.[0]?.gripStyle || review?.gripStyle
  if (legacyGrip === gripStyle && (review?.supportDabs?.length || review?.supportCells?.length)) {
    return { gripStyle, supportDabs: review.supportDabs || [], supportCells: review.supportCells || [] }
  }
  return null
}
const activePublicSupportGrip = (review) => publicSupportGripSelection[review.id]
  || review.gripScores?.[0]?.gripStyle
  || review.supportByGrip?.[0]?.gripStyle
  || 'CLAW'
const activePublicSupportMap = (review) => supportMapForGrip(review, activePublicSupportGrip(review))
const publicSupportCount = (review) => review?.supportByGrip?.length || (review?.supportDabs?.length || review?.supportCells?.length ? 1 : 0)
const selectPublicSupportGrip = (review, gripStyle) => { publicSupportGripSelection[review.id] = gripStyle }
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
    for (const grip of Object.keys(personalSupportDabsByGrip)) personalSupportDabsByGrip[grip] = []
    if (data?.supportByGrip?.length) {
      for (const support of data.supportByGrip) {
        personalSupportDabsByGrip[support.gripStyle] = support.supportDabs?.length
          ? [...support.supportDabs]
          : legacyCellsToDabs(support.supportCells || [])
      }
    } else if (data) {
      const legacyGrip = auth.user?.preferredGripStyle || 'MIXED'
      personalSupportDabsByGrip[legacyGrip] = data.supportDabs?.length
        ? [...data.supportDabs]
        : legacyCellsToDabs(data.supportCells || [])
    }
    if (!activeSupportGrip.value) activeSupportGrip.value = auth.user?.preferredGripStyle || 'CLAW'
  } catch {
    mine.value = null
    for (const grip of Object.keys(personalSupportDabsByGrip)) personalSupportDabsByGrip[grip] = []
  }
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
const loadPublicReviews = async (page = 1) => {
  if (!mouse.value) return
  publicReviews.value = (await api.get(`/mice/${mouse.value.id}/reviews`, { params: { page } })).data
  for (const review of publicReviews.value.items || []) {
    if (!publicSupportGripSelection[review.id]) {
      publicSupportGripSelection[review.id] = review.gripScores?.[0]?.gripStyle || review.supportByGrip?.[0]?.gripStyle || 'CLAW'
    }
  }
}
const openReport = (targetType, targetId, scope = targetType === 'REVIEW' ? 'REVIEW_ITEM' : 'MOUSE_DATA') => {
  reportTarget.value = { targetType, targetId }
  reportScope.value = scope
  reportCategory.value = scope === 'REVIEW_AGGREGATE' ? 'SUSPICIOUS' : targetType === 'MOUSE' ? 'DATA_ERROR' : 'INAPPROPRIATE'
  reportDescription.value = ''; reportNotice.value = ''
  nextTick(() => { if (reportDialog.value && !reportDialog.value.open) reportDialog.value.showModal() })
}
const scrollPublicReviews = (direction) => {
  publicReviewsRail.value?.scrollBy({ left: direction * 390, behavior: 'smooth' })
}
const closeReport = () => {
  if (reportDialog.value?.open) reportDialog.value.close()
  else reportTarget.value = null
}
const closeReportFromBackdrop = (event) => {
  if (event.target === reportDialog.value) closeReport()
}
const submitReport = async () => {
  if (!reportTarget.value || !reportDescription.value.trim()) return
  reportLoading.value = true
  try {
    await api.post('/reports', { ...reportTarget.value, category: reportCategory.value, description: reportDescription.value.trim() })
    reportNotice.value = '反馈已提交，管理员处理后会保留完整记录'
    reportDescription.value = ''; closeReport()
  } catch (e) { error.value = errorMessage(e) } finally { reportLoading.value = false }
}
const initializeReviewFilters = () => {
  if (reviewFiltersInitialized.value) return
  if (matchingHandOption.value) selectedHand.value = matchingHandOption.value.code
  reviewFiltersInitialized.value = true
}
const load = async () => {
  error.value = ''
  try {
    const [{ data }, optionResponse, configResponse] = await Promise.all([api.get(`/mice/${route.params.id}`), api.get('/review-options'), api.get('/config')])
    mouse.value = data.mouse; summary.value = data.reviewSummary; options.value = optionResponse.data
    reviewSubmissionEnabled.value = configResponse.data.reviewSubmissionEnabled !== false
    if (auth.authenticated) await auth.refresh()
    initializeReviewFilters()
    if (selectedGrip.value || selectedHand.value) await filterSummary()
    else await loadSupportSummary()
    await Promise.all([loadMine(), loadPublicReviews()])
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
const saveGrip = async (code) => {
  gripLoading.value = code; message.value = ''; error.value = ''
  try { await api.put(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${code}`, { comfortScore: gripScores[code] }); message.value = '握持舒适度已提交'; await refreshReview() }
  catch (e) { error.value = errorMessage(e) } finally { gripLoading.value = '' }
}
const deleteGrip = async (item) => {
  if (!window.confirm(`确定删除${item.label}的舒适度评分及对应支撑涂抹吗？`)) return
  message.value = ''; error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${item.code}`); message.value = `${item.label}评分及支撑涂抹已删除`; await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const updateSupportDabs = (dabs) => {
  personalSupportDabsByGrip[activeSupportGrip.value] = dabs
  supportMessage.value = ''; supportError.value = ''
}
const handlePublicSupportModelError = () => { publicSupportError.value = '支撑位置热力图加载失败，请刷新页面后重试' }
const handlePersonalSupportModelError = () => { supportError.value = '个人支撑位置画布加载失败，请刷新页面后重试' }
const clearSupportSelection = () => {
  personalSupportDabsByGrip[activeSupportGrip.value] = []
  supportMessage.value = ''
  supportError.value = ''
}
const saveSupport = async () => {
  supportLoading.value = true; supportMessage.value = ''; supportError.value = ''
  try {
    await api.put(`/mice/${mouse.value.id}/reviews/mine/support-positions/${activeSupportGrip.value}`, { dabs: personalSupportDabs.value })
    supportMessage.value = `${valueLabel(activeSupportGrip.value)}支撑涂抹已保存并计入热力图`
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
      <section class="review-panel"><div class="section-heading compact"><div><p class="eyebrow">SUBJECTIVE INDEX</p><h2>用户评价</h2></div><span class="sample-badge" :class="{ low: summary.lowSample }">{{ summary.sampleCount }} 份握姿评价</span></div>
        <div class="review-filters"><label><span>握持方式</span><select v-model="selectedGrip" @change="filterSummary"><option value="">全部握持方式</option><option v-for="item in options?.gripStyles || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label><label><span>手长范围</span><select v-model="selectedHand" @change="filterSummary"><option value="">全部手长</option><option v-for="item in options?.handSizes || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label></div>
        <div v-if="handMatchActive" class="review-match-context">
          <span aria-hidden="true">✓</span>
          <p><strong>已优先展示匹配手长的评价</strong><small>你的手长为 {{ auth.user.handLengthCm }} cm，对应 {{ matchingHandOption.label }}。</small></p>
          <button type="button" @click="showAllHandReviews">查看全部</button>
        </div>
        <div class="split-score-overview single"><article class="score-summary-card grip-summary"><div class="score-dial"><strong>{{ summary.sampleCount ? summary.overallAverage : '—' }}</strong><span>/ 10.0</span></div><div><small>GRIP COMFORT</small><h3>{{ gripSummaryLabel }}</h3><p>{{ summary.sampleCount ? `${summary.sampleCount} 份握姿评分` : '暂无对应握姿评分' }}</p></div></article></div>
        <details class="score-distribution-section" v-if="summary.sampleCount">
          <summary class="score-distribution-toggle">
            <span class="score-distribution-title"><small>SCORE DISTRIBUTION</small><h3>评分分布</h3></span>
            <span class="score-distribution-meta"><small>最后更新：{{ reviewUpdatedLabel }}</small><em><span class="collapsed-label">展开</span><span class="expanded-label">收起</span><i aria-hidden="true"></i></em></span>
          </summary>
          <div class="score-distribution-content">
            <div class="score-distribution-grid single">
              <article><h4>{{ gripSummaryLabel }}</h4><p>按当前握姿与手长筛选统计；一位用户评价多种握姿时分别计入对应样本。</p><div class="distribution-bars"><div v-for="item in gripDistribution" :key="`grip-${item.score}`"><span>{{ item.score }}</span><i><b :style="{ width: `${item.count / gripDistributionMax * 100}%` }"></b></i><strong>{{ item.count }}</strong></div></div></article>
            </div>
            <p class="score-method-note">口径说明：评分随上方握姿与手长筛选变化。样本少于 5 份时仅供参考，排序时会自动置于充足样本之后。</p>
          </div>
        </details>
        <div class="review-action-bar">
          <div class="review-action-copy">
            <small>MY REVIEW</small>
            <strong>{{ auth.authenticated ? reviewProgressLabel : '分享你的真实使用感受' }}</strong>
            <p>{{ auth.authenticated ? '握持舒适度和支撑位置可以分别提交。' : '登录后使用固定模板评价，结果会匿名计入汇总。' }}</p>
          </div>
          <RouterLink v-if="!auth.authenticated" class="button primary-action-button review-write-button" to="/login">登录后写评价</RouterLink>
          <button v-else-if="options && reviewSubmissionEnabled" class="button primary-action-button review-write-button" type="button" @click="openReviewEditor">
            {{ mine ? '管理我的评价' : '写评价' }}<span aria-hidden="true">→</span>
          </button>
          <span v-else class="sample-badge low">评价提交暂时关闭</span>
        </div>
      </section>

      <aside class="support-panel">
        <div class="section-heading compact support-heading">
          <div><p class="eyebrow">CONTACT HEATMAP</p><h2>支撑位置评价</h2></div>
          <span class="sample-badge" :class="{ low: supportSummary.sampleCount < 5 }">{{ supportSummary.sampleCount }} 份握姿标记</span>
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
          <span class="support-mode-hint">{{ supportSummary.cells?.length ? `当前展示 ${supportSummary.sampleCount} 份握姿涂抹的汇总结果，可用左键拖动旋转` : '当前筛选下暂无支撑位置评价，可用左键拖动旋转模型' }}</span>
        </div>
        <div class="heat-legend" v-if="supportSummary.cells?.length"><span>覆盖较少</span><i></i><span>覆盖较多</span></div>
        <div class="flash error" v-if="publicSupportError">{{ publicSupportError }}</div>
        <div class="support-feedback-row">
          <p class="public-support-note">想添加或修改自己的支撑位置，请点击左侧的“写评价”按钮。</p>
          <button v-if="auth.authenticated" type="button" class="support-report-button" @click="openReport('MOUSE', mouse.id, 'REVIEW_AGGREGATE')">反馈汇总异常</button>
          <RouterLink v-else class="support-report-button" to="/login">登录后反馈异常</RouterLink>
        </div>
      </aside>
    </div>

    <section class="section-shell community-review-section">
      <div class="section-heading compact">
        <div><p class="eyebrow">COMMUNITY RECORDS</p><h2>逐条公开评价</h2><p>仅展示结构化评分和匿名用户标识，不公开邮箱与个人资料。</p></div>
        <button v-if="auth.authenticated" class="button button-ghost" type="button" @click="openReport('MOUSE', mouse.id)">提交参数纠错</button>
        <RouterLink v-else class="button button-ghost" to="/login">登录后纠错</RouterLink>
      </div>
      <div v-if="reportNotice" class="flash success">{{ reportNotice }}</div>
      <div class="public-review-rail-shell" :class="{ single: publicReviews.items.length <= 1 }">
        <button v-if="publicReviews.items.length > 1" class="public-review-rail-arrow previous" type="button" aria-label="查看上一组评价" @click="scrollPublicReviews(-1)">←</button>
        <div ref="publicReviewsRail" class="public-review-rail" tabindex="0" aria-label="横向浏览逐条公开评价">
          <article v-for="review in publicReviews.items" :key="review.id" class="public-review-ticket">
            <header class="ticket-header"><div><strong>{{ review.author }}</strong><small>{{ new Date(review.createdAt).toLocaleDateString('zh-CN') }} · {{ valueLabel(review.handSize) }}</small></div><div class="ticket-score"><b>{{ review.comfortAverage || '—' }}</b><small>/ 10</small></div></header>
            <div class="ticket-grip-row" role="tablist" :aria-label="`${review.author} 的握姿评价`"><button v-for="score in review.gripScores" :key="score.gripStyle" type="button" role="tab" :aria-selected="activePublicSupportGrip(review) === score.gripStyle" :class="{ active: activePublicSupportGrip(review) === score.gripStyle, painted: supportMapForGrip(review, score.gripStyle) }" @click="selectPublicSupportGrip(review, score.gripStyle)"><em>{{ valueLabel(score.gripStyle) }}</em><b>{{ score.comfortScore }}</b><i aria-hidden="true"></i></button><span v-if="!review.gripScores?.length" class="ticket-empty-score">暂无握姿评分</span></div>
            <div class="ticket-support-map" :class="{ empty: !activePublicSupportMap(review)?.supportDabs?.length && !activePublicSupportMap(review)?.supportCells?.length }">
              <HandSupport3D v-if="activePublicSupportMap(review)?.supportDabs?.length || activePublicSupportMap(review)?.supportCells?.length" :key="`${review.id}-${activePublicSupportGrip(review)}`" :summary-cells="activePublicSupportMap(review).supportCells || []" :max-count="activePublicSupportMap(review).supportCells?.length ? 1 : 0" :grid-columns="24" :grid-rows="32" :dabs="activePublicSupportMap(review).supportDabs || []" tool="view" :editable="false" :aria-label="`${review.author} 的${valueLabel(activePublicSupportGrip(review))}支撑位置 3D 涂抹`" />
              <span v-else>{{ valueLabel(activePublicSupportGrip(review)) }}暂未提交支撑位置涂抹</span>
              <small class="ticket-support-label">{{ valueLabel(activePublicSupportGrip(review)) }} / SUPPORT MAP</small>
            </div>
            <footer class="ticket-footer"><span>{{ review.gripScores?.map((score) => valueLabel(score.gripStyle)).join(' / ') || '未填写握姿' }} · {{ publicSupportCount(review) }} 份支撑图</span><button v-if="auth.authenticated" type="button" @click="openReport('REVIEW', review.id)">举报</button></footer>
          </article>
          <p v-if="!publicReviews.items.length" class="table-empty">暂无可公开的逐条评价</p>
        </div>
        <button v-if="publicReviews.items.length > 1" class="public-review-rail-arrow next" type="button" aria-label="查看下一组评价" @click="scrollPublicReviews(1)">→</button>
      </div>
      <div v-if="publicReviews.page.totalPages > 1" class="public-review-pagination"><button :disabled="publicReviews.page.number <= 1" @click="loadPublicReviews(publicReviews.page.number - 1)">上一页</button><span>{{ publicReviews.page.number }} / {{ publicReviews.page.totalPages }}</span><button :disabled="publicReviews.page.number >= publicReviews.page.totalPages" @click="loadPublicReviews(publicReviews.page.number + 1)">下一页</button></div>
    </section>

    <Teleport to="body">
      <dialog
        v-if="reportTarget"
        ref="reportDialog"
        class="report-dialog"
        aria-labelledby="report-dialog-title"
        @click="closeReportFromBackdrop"
        @close="reportTarget = null"
      >
        <form class="report-dialog-shell" @submit.prevent="submitReport">
          <header class="report-dialog-header">
            <div>
              <small>{{ reportScope === 'REVIEW_AGGREGATE' ? 'COMMUNITY SIGNAL / AGGREGATE' : reportTarget.targetType === 'REVIEW' ? 'COMMUNITY SIGNAL / REVIEW' : 'COMMUNITY SIGNAL / DATA' }}</small>
              <h2 id="report-dialog-title">{{ reportScope === 'REVIEW_AGGREGATE' ? '反馈评价汇总异常' : reportTarget.targetType === 'MOUSE' ? '提交参数纠错' : '举报这条评价' }}</h2>
              <p>{{ reportScope === 'REVIEW_AGGREGATE' ? `当前范围：${supportFilterLabel}。反馈会帮助管理员定位污染聚合结果的评价包。` : '请提供可复核的信息；恶意或重复提交可能被忽略。' }}</p>
            </div>
            <button class="report-dialog-close" type="button" aria-label="关闭反馈窗口" @click="closeReport">×</button>
          </header>
          <div class="report-dialog-body">
            <div class="report-context-card"><span>反馈对象</span><strong>{{ mouse?.brand }} {{ mouse?.model }}</strong><small>{{ reportScope === 'REVIEW_AGGREGATE' ? `聚合评价 · ${supportFilterLabel}` : reportTarget.targetType === 'REVIEW' ? '当前匿名评价' : '鼠标客观参数' }}</small></div>
            <label class="report-field">问题分类<select v-model="reportCategory"><option v-if="reportScope === 'REVIEW_AGGREGATE'" value="SUSPICIOUS">疑似异常汇总</option><option v-if="reportScope === 'REVIEW_AGGREGATE'" value="HEATMAP_ERROR">热力图结果异常</option><option v-if="reportTarget.targetType === 'MOUSE' && reportScope !== 'REVIEW_AGGREGATE'" value="DATA_ERROR">参数错误</option><option v-if="reportTarget.targetType === 'MOUSE' && reportScope !== 'REVIEW_AGGREGATE'" value="SOURCE_UPDATE">来源需要更新</option><option v-if="reportTarget.targetType === 'REVIEW'" value="INAPPROPRIATE">不当内容</option><option v-if="reportTarget.targetType === 'REVIEW'" value="SUSPICIOUS">疑似异常评价</option><option value="OTHER">其他</option></select></label>
            <label class="report-field">详细说明<textarea v-model.trim="reportDescription" maxlength="1000" required :placeholder="reportScope === 'REVIEW_AGGREGATE' ? '例如：中手 / 抓握下的热力图集中在不合理区域，或评分分布明显失真…' : '说明具体问题、正确数据或判断依据…'"></textarea><small>{{ reportDescription.length }} / 1000</small></label>
          </div>
          <footer class="report-dialog-footer"><button type="button" class="button button-ghost" :disabled="reportLoading" @click="closeReport">取消</button><button class="button primary-action-button" :disabled="reportLoading || !reportDescription.trim()">{{ reportLoading ? '提交中…' : '提交反馈' }}<span aria-hidden="true">→</span></button></footer>
        </form>
      </dialog>
    </Teleport>

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
            <i><b :style="{ width: `${reviewProgressPercent}%` }"></b></i>
          </div>
          <div class="review-dialog-body">
            <div class="flash success" v-if="message">{{ message }}</div>
            <div class="flash error" v-if="error">{{ error }}</div>
            <div class="profile-required" v-if="!profileReady"><span>PROFILE REQUIRED</span><p>评分时会自动读取个人资料中的手长和习惯握姿，请先填写后再回来提交。</p><RouterLink class="button button-ghost" to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
            <div class="review-entry-stack">
              <section class="review-entry-card grip-entry">
                <header><div><span>01 / GRIP COMFORT</span><h3>握持舒适度</h3></div><em>{{ completedGripCount }} / 4 已评价</em></header>
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
                  <div><span>02 / SUPPORT MAPS</span><h3>不同握姿的支撑位置</h3></div>
                  <em>{{ supportGripCount }} / 4 已涂抹</em>
                </header>
                <p class="review-hint">四种握姿分别保存一份 3D 涂抹。先选择握姿，再标记该握姿下鼠标实际托住手部的位置。</p>
                <div class="support-grip-tabs" role="tablist" aria-label="选择要编辑的握姿支撑图">
                  <button v-for="item in options.gripStyles" :key="item.code" type="button" role="tab" :aria-selected="activeSupportGrip === item.code" :class="{ active: activeSupportGrip === item.code, completed: personalSupportDabsByGrip[item.code]?.length }" @click="activeSupportGrip = item.code; supportMessage = ''; supportError = ''">
                    <span>{{ item.label }}</span><small>{{ personalSupportDabsByGrip[item.code]?.length ? '已涂抹' : '未填写' }}</small>
                  </button>
                </div>
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
                      {{ supportLoading ? '保存中…' : supportMapForGrip(mine, activeSupportGrip) ? `更新${valueLabel(activeSupportGrip)}支撑图` : `提交${valueLabel(activeSupportGrip)}支撑图` }}
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
                        :aria-label="`可涂抹的${valueLabel(activeSupportGrip)}个人支撑位置图`"
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

<style scoped>
.community-review-section{margin-top:32px}.community-review-section .section-heading>div>p:last-child{margin:.35rem 0 0;color:var(--muted,#647278)}.support-feedback-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px}.support-feedback-row .public-support-note{margin:0}.support-report-button{flex:0 0 auto;padding:7px 10px;border:1px solid #c6d8d6;border-radius:999px;background:#f7fbfa;color:#56706e;font-size:11px;text-decoration:none;cursor:pointer}.support-report-button:hover{border-color:#8aa9a5;background:#edf6f5}.public-review-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}.public-review-grid article{padding:18px;border:1px solid #dde5e6;border-radius:18px;background:#fff}.public-review-grid article header,.public-review-grid article footer{display:flex;justify-content:space-between;align-items:center}.public-review-grid article header strong,.public-review-grid article header small{display:block}.public-review-grid article header>span{font-size:24px;font-weight:800}.public-review-grid article header>span small{font-size:11px}.public-score-strip{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin:16px 0}.public-score-strip span{padding:8px 4px;background:#f4f7f7;border-radius:9px;text-align:center;font-size:10px}.public-score-strip b{display:block;font-size:16px}.public-review-grid footer{font-size:12px;color:#66767a}.public-review-grid footer button{border:0;background:none;color:#9e3a3a}.public-review-pagination{display:flex;justify-content:center;align-items:center;gap:12px;margin-top:18px}.report-dialog{width:min(560px,calc(100vw - 32px));max-width:none;max-height:min(760px,calc(100dvh - 32px));padding:0;border:1px solid #cfdcda;border-radius:22px;background:#fbfdfc;color:#173332;box-shadow:0 30px 90px rgba(17,53,50,.28)}.report-dialog::backdrop{background:rgba(11,29,28,.52);backdrop-filter:blur(8px)}.report-dialog[open]{animation:report-dialog-in 190ms cubic-bezier(.22,1,.36,1)}.report-dialog-shell{display:grid;grid-template-rows:auto minmax(0,1fr) auto;max-height:min(760px,calc(100dvh - 32px));margin:0}.report-dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:25px 26px 20px;border-bottom:1px solid #e0eae8;background:linear-gradient(135deg,#f4fbfa,#eef6f4)}.report-dialog-header>div{display:grid;gap:6px}.report-dialog-header small{color:#6b8581;font:600 .6rem var(--mono);letter-spacing:.12em}.report-dialog-header h2{margin:0;color:#183a37;font-size:1.45rem;letter-spacing:-.035em}.report-dialog-header p{margin:0;color:#6c7f7d;font-size:.73rem;line-height:1.55}.report-dialog-close{display:grid;place-items:center;flex:0 0 38px;width:38px;height:38px;border:1px solid #c8d9d6;border-radius:11px;background:rgba(255,255,255,.58);color:#52706c;font-size:1.25rem;cursor:pointer}.report-dialog-close:hover{background:#fff;color:#173332}.report-dialog-body{display:grid;gap:16px;overflow:auto;padding:22px 26px 24px}.report-context-card{display:grid;gap:5px;padding:13px 14px;border:1px solid #d6e5e2;border-radius:13px;background:#f4f9f8}.report-context-card span,.report-field{color:#56716e;font-size:.68rem;font-weight:700}.report-context-card strong{color:#173332;font-size:.9rem}.report-context-card small{color:#748885;font-size:.63rem}.report-field{display:grid;gap:7px}.report-field select,.report-field textarea{width:100%;box-sizing:border-box;padding:11px 12px;border:1px solid #cbdcd9;border-radius:11px;background:#fff;color:#173332;font:inherit}.report-field select:focus,.report-field textarea:focus{border-color:#6faaa2;outline:3px solid rgba(111,170,162,.18)}.report-field textarea{min-height:140px;resize:vertical;line-height:1.55}.report-field>small{justify-self:end;color:#81928f;font-size:.6rem;font-weight:400}.report-dialog-footer{display:flex;justify-content:flex-end;gap:9px;padding:16px 26px 20px;border-top:1px solid #e0eae8;background:#f8fbfa}.report-dialog-footer .button{min-width:106px}.report-dialog-footer .primary-action-button{background:#214c47;color:#f3fbf9}.report-dialog-footer .primary-action-button:hover{background:#173a36}.community-review-section .public-report-form{display:none}@keyframes report-dialog-in{from{opacity:0;transform:translateY(12px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}@media(max-width:760px){.public-review-grid{grid-template-columns:1fr}.report-dialog{width:100vw;height:100dvh;max-height:100dvh;border-radius:0}.report-dialog-shell{height:100dvh;max-height:100dvh}.report-dialog-header{padding:20px 18px 16px}.report-dialog-header h2{font-size:1.2rem}.report-dialog-body{padding:18px}.report-dialog-footer{padding:13px 18px}.report-dialog-footer .button{width:100%}.report-dialog-footer{display:grid;grid-template-columns:1fr 1fr}.support-feedback-row{align-items:flex-start;flex-direction:column}.support-report-button{width:fit-content}}
</style>

<style scoped>
.support-report-button {
  border-color: var(--dv-outline);
  background: var(--dv-surface-high);
  color: var(--dv-text-soft);
}
.support-report-button:hover {
  border-color: var(--dv-primary-line);
  background: var(--dv-primary-soft);
  color: var(--dv-primary-bright);
}
.report-dialog {
  border-color: var(--dv-border);
  background: var(--dv-surface);
  color: var(--dv-text);
  box-shadow: 0 30px 90px rgba(0, 0, 0, .52);
}
.report-dialog::backdrop {
  background: rgba(5, 5, 6, .82);
  backdrop-filter: blur(8px);
}
.report-dialog-header {
  border-bottom-color: var(--dv-border);
  background: linear-gradient(135deg, #18191d, #141416);
}
.report-dialog-header small { color: var(--dv-primary-bright); }
.report-dialog-header h2 { color: var(--dv-text); }
.report-dialog-header p { color: var(--dv-text-soft); }
.report-dialog-close {
  border-color: var(--dv-outline);
  background: var(--dv-surface-high);
  color: var(--dv-text-soft);
}
.report-dialog-close:hover { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
.report-context-card {
  border-color: var(--dv-border);
  background: var(--dv-surface-high);
}
.report-context-card span, .report-field { color: var(--dv-primary-bright); }
.report-context-card strong { color: var(--dv-text); }
.report-context-card small { color: var(--dv-muted); }
.report-field select, .report-field textarea {
  border-color: var(--dv-outline);
  background: var(--dv-background);
  color: var(--dv-text);
}
.report-field select:focus, .report-field textarea:focus { border-color: var(--dv-primary); outline-color: var(--dv-primary-soft); }
.report-field > small { color: var(--dv-muted); }
.report-dialog-footer { border-top-color: var(--dv-border); background: var(--dv-surface-high); }
.report-dialog-footer .button-ghost { border-color: var(--dv-outline); background: transparent; color: var(--dv-text-soft); }
.report-dialog-footer .primary-action-button { border-color: var(--dv-primary); background: var(--dv-primary); color: #fff; }
.report-dialog-footer .primary-action-button:hover { border-color: #6ca3fa; background: #6ca3fa; }
.community-review-section { color: var(--dv-text); }
.community-review-section .section-heading h2 { color: var(--dv-text); }
.community-review-section .section-heading > div > p:last-child { color: var(--dv-muted); }
.public-review-grid article {
  border-color: var(--dv-border);
  background: var(--dv-surface);
  color: var(--dv-text);
  box-shadow: none;
}
.public-review-grid article:hover { border-color: var(--dv-primary-line); background: var(--dv-surface-high); }
.public-review-grid article header strong { color: var(--dv-text); }
.public-review-grid article header small { color: var(--dv-muted); }
.public-review-grid article header > span { color: var(--dv-primary-bright); }
.public-review-grid article header > span small { color: var(--dv-muted); }
.public-score-strip span { background: var(--dv-surface-high); color: var(--dv-text-soft); }
.public-score-strip b { color: var(--dv-text); }
.public-review-grid footer { color: var(--dv-muted); }
.public-review-grid footer button { color: var(--dv-error); }
.public-review-grid footer button:hover { color: #f2a6ad; }
.public-review-pagination { color: var(--dv-muted); }
.public-review-pagination button { border-color: var(--dv-border); background: var(--dv-surface); color: var(--dv-text-soft); }
.public-review-pagination button:hover:not(:disabled) { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
.public-review-rail-shell { display: grid; grid-template-columns: 36px minmax(0, 1fr) 36px; align-items: center; gap: 10px; width: 100%; min-width: 0; margin-top: 18px; }
.public-review-rail-shell.single { grid-template-columns: minmax(0, 1fr); }
.public-review-rail { display: flex; gap: 12px; width: 100%; max-width: 100%; min-width: 0; overflow-x: auto; padding: 2px 2px 14px; scroll-snap-type: x mandatory; scrollbar-color: var(--dv-outline) transparent; scrollbar-width: thin; }
.public-review-ticket { display: grid; flex: 0 0 min(342px, calc(100vw - 116px)); grid-template-rows: auto auto minmax(148px, 1fr) auto; gap: 12px; min-height: 368px; padding: 15px; scroll-snap-align: start; border: 1px solid var(--dv-border); border-radius: 12px; background: var(--dv-surface); color: var(--dv-text); box-shadow: 0 12px 32px rgba(0,0,0,.18); transition: border-color 160ms ease, background 160ms ease, transform 160ms ease; }
.public-review-ticket:hover { border-color: var(--dv-primary-line); background: var(--dv-surface-high); transform: translateY(-2px); }
.ticket-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ticket-header strong, .ticket-header small { display: block; }
.ticket-header strong { color: var(--dv-text); font-size: .78rem; }
.ticket-header small { margin-top: 4px; color: var(--dv-muted); font: .58rem var(--dv-mono); }
.ticket-score { display: flex; align-items: baseline; gap: 3px; }
.ticket-score b { color: var(--dv-primary-bright); font: 700 1.45rem var(--dv-mono); }
.ticket-score small { color: var(--dv-muted); font-size: .58rem; }
.ticket-grip-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 5px; }
.ticket-grip-row > button { position: relative; display: grid; gap: 3px; min-width: 0; padding: 7px 4px; border: 1px solid var(--dv-border); border-radius: 7px; background: var(--dv-surface-high); color: inherit; text-align: center; cursor: pointer; transition: border-color 150ms ease, background 150ms ease, transform 150ms ease; }
.ticket-grip-row > button:hover { border-color: var(--dv-primary-line); transform: translateY(-1px); }
.ticket-grip-row > button.active { border-color: var(--dv-primary); background: var(--dv-primary-soft); box-shadow: inset 0 -2px 0 var(--dv-primary); }
.ticket-grip-row > button i { position: absolute; top: 5px; right: 5px; width: 4px; height: 4px; border-radius: 50%; background: var(--dv-outline); }
.ticket-grip-row > button.painted i { background: var(--dv-primary-bright); box-shadow: 0 0 7px var(--dv-primary); }
.ticket-grip-row em { color: var(--dv-muted); font-size: .52rem; font-style: normal; }
.ticket-grip-row button.active em { color: var(--dv-primary-bright); }
.ticket-grip-row b { color: var(--dv-text); font: 700 .86rem var(--dv-mono); }
.ticket-grip-row .ticket-empty-score { grid-column: 1 / -1; display: block; color: var(--dv-muted); font-size: .62rem; }
.ticket-support-map { position: relative; min-height: 148px; overflow: hidden; border: 1px solid var(--dv-border); border-radius: 9px; background: radial-gradient(circle at 50% 42%, rgba(59,130,246,.13), transparent 58%), var(--dv-background); }
.ticket-support-map.empty { display: grid; place-items: center; color: var(--dv-muted); font-size: .6rem; }
.ticket-support-label { position: absolute; z-index: 3; top: 8px; left: 9px; padding: 4px 6px; border: 1px solid var(--dv-border); border-radius: 5px; background: rgba(9,10,12,.78); color: var(--dv-primary-bright); font: .48rem var(--dv-mono); letter-spacing: .08em; pointer-events: none; }
.support-grip-tabs { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px; margin: 14px 0; }
.support-grip-tabs button { display: grid; gap: 4px; min-width: 0; padding: 10px 8px; border: 1px solid var(--dv-border); border-radius: 8px; background: var(--dv-surface-high); color: var(--dv-text-soft); cursor: pointer; }
.support-grip-tabs button span { font-size: .66rem; font-weight: 700; }
.support-grip-tabs button small { color: var(--dv-muted); font: .5rem var(--dv-mono); }
.support-grip-tabs button.completed small { color: var(--dv-primary-bright); }
.support-grip-tabs button.active { border-color: var(--dv-primary); background: var(--dv-primary-soft); color: var(--dv-text); box-shadow: inset 0 -2px 0 var(--dv-primary); }
.ticket-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--dv-muted); font-size: .58rem; }
.ticket-footer button { border: 0; background: transparent; color: var(--dv-error); font-size: .62rem; cursor: pointer; }
.ticket-footer button:hover { color: #f2a6ad; }
.public-review-rail-arrow { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid var(--dv-outline); border-radius: 8px; background: var(--dv-surface-high); color: var(--dv-text-soft); font-size: 1rem; cursor: pointer; }
.public-review-rail-arrow:hover { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
@media (max-width: 600px) { .support-grip-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
