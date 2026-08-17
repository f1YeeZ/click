<script setup>
import { computed, defineAsyncComponent, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import api, { errorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useCompareStore } from '../stores/compare'
import { usePublicConfigStore } from '../stores/publicConfig'
import { onRealtime } from '../services/realtime'
import { showToast } from '../services/toast'
import { legacyCellsToDabs, supportCoveragePercentage } from '../utils/supportHeatmap'

const HandSupport3D = defineAsyncComponent(() => import('../components/HandSupport3D.vue'))

const route = useRoute()
const auth = useAuthStore()
const compare = useCompareStore()
const publicConfig = usePublicConfigStore()
const mouse = ref(null)
const summary = ref(null)
const options = ref(null)
const supportSummary = ref({ sampleCount: 0, positions: [], cells: [], maxCount: 0 })
const personalSupportDabsByGrip = reactive({ PALM: [], CLAW: [], FINGERTIP: [], MIXED: [] })
const activeSupportGrip = ref('PALM')
const publicSupportGripSelection = reactive({})
const supportTool = ref('paint')
const supportBrushSize = ref(12)
const selectedGrip = ref('')
const selectedHand = ref('')
const reviewFiltersInitialized = ref(false)
const mine = ref(null)
const supportLoading = ref(false)
const reviewEditorOpen = ref(false)
const reviewDialog = ref(null)
const publicSupportError = ref('')
const supportError = ref('')
const error = ref('')
const publicReviews = ref({ items: [], page: { number: 1, totalPages: 1, totalItems: 0 } })
const publicReviewsRail = ref(null)
const reportTarget = ref(null)
const reportScope = ref('MOUSE_DATA')
const reportDialog = ref(null)
const reportCategory = ref('DATA_ERROR')
const reportDescription = ref('')
const reportLoading = ref(false)
const objectiveDialogOpen = ref(false)
const objectiveDialog = ref(null)
const reviewSubmissionEnabled = computed(() => publicConfig.reviewSubmissionEnabled)
const gripScores = reactive({ PALM: 8, CLAW: 8, FINGERTIP: 8, MIXED: 8 })
const gripScoreTouched = reactive({ PALM: false, CLAW: false, FINGERTIP: false, MIXED: false })
const profileReady = computed(() => Boolean(auth.user?.handLengthCm && auth.user?.preferredGripStyle))
const submittedGrip = (code) => mine.value?.gripComforts?.find((item) => item.gripStyle === code)
const activeGripOption = computed(() => options.value?.gripStyles?.find((item) => item.code === activeSupportGrip.value) || null)
const activeSubmittedGrip = computed(() => submittedGrip(activeSupportGrip.value))
const activeGripScoreReady = computed(() => Boolean(
  activeGripOption.value
  && gripScoreTouched[activeGripOption.value.code]
  && gripScores[activeGripOption.value.code] >= 1
  && gripScores[activeGripOption.value.code] <= 10
))
const personalSupportDabs = computed(() => personalSupportDabsByGrip[activeSupportGrip.value] || [])
const supportCoverage = computed(() => supportCoveragePercentage(personalSupportDabs.value))
const supportHasPaint = computed(() => supportCoverage.value > 0)
const supportGripCount = computed(() => Object.values(personalSupportDabsByGrip).filter((dabs) => supportCoveragePercentage(dabs) > 0).length)
const hasSubmittedSupport = computed(() => supportGripCount.value > 0)
const completedGripCount = computed(() => mine.value?.gripComforts?.length || 0)
const gripSummaryLabel = computed(() => selectedGrip.value
  ? `${options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'}总评`
  : '暂无握姿')
const matchingHandOption = computed(() => options.value?.handSizes?.find((item) => item.code === auth.user?.handSize))
const supportFilterLabel = computed(() => {
  const grip = options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'
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
const completedReviewGripCount = computed(() => options.value?.gripStyles?.filter((item) => (
  submittedGrip(item.code) && supportMapForGrip(mine.value, item.code)
)).length || 0)
const reviewProgressPercent = computed(() => completedReviewGripCount.value / 4 * 100)
const reviewProgressLabel = computed(() => {
  if (!mine.value) return '还没有提交评价'
  return `已完成 ${completedReviewGripCount.value} / 4 种握姿评价`
})
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
const gripLabel = (value) => ({ PALM: '趴握', CLAW: '抓握', FINGERTIP: '指握', MIXED: '混合' }[value] || value || '—')

const loadMine = async () => {
  if (!auth.authenticated || !mouse.value) return
  try {
    const { data } = await api.get(`/mice/${mouse.value.id}/reviews/mine`)
    mine.value = data || null
    for (const code of Object.keys(gripScores)) {
      gripScores[code] = 8
      gripScoreTouched[code] = false
    }
    for (const score of data?.gripComforts || data?.gripScores || []) {
      gripScores[score.gripStyle] = score.comfortScore
      gripScoreTouched[score.gripStyle] = true
    }
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
  reportDescription.value = ''
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
const openObjectiveData = () => {
  objectiveDialogOpen.value = true
  nextTick(() => { if (objectiveDialog.value && !objectiveDialog.value.open) objectiveDialog.value.showModal() })
}
const closeObjectiveData = () => {
  if (objectiveDialog.value?.open) objectiveDialog.value.close()
  else objectiveDialogOpen.value = false
}
const closeObjectiveDataFromBackdrop = (event) => {
  if (event.target === objectiveDialog.value) closeObjectiveData()
}
const submitReport = async () => {
  if (!reportTarget.value || !reportDescription.value.trim()) return
  reportLoading.value = true
  try {
    await api.post('/reports', { ...reportTarget.value, category: reportCategory.value, description: reportDescription.value.trim() })
    showToast('反馈已提交，管理员处理后会保留完整记录')
    reportDescription.value = ''; closeReport()
  } catch (e) { error.value = errorMessage(e) } finally { reportLoading.value = false }
}
const initializeReviewFilters = () => {
  if (reviewFiltersInitialized.value) return
  const preferredGrip = options.value?.gripStyles?.find((item) => item.code === auth.user?.preferredGripStyle)?.code
  selectedGrip.value = preferredGrip || options.value?.gripStyles?.[0]?.code || 'PALM'
  if (matchingHandOption.value) selectedHand.value = matchingHandOption.value.code
  reviewFiltersInitialized.value = true
}
const load = async () => {
  error.value = ''
  try {
    const [{ data }, optionResponse] = await Promise.all([api.get(`/mice/${route.params.id}`), api.get('/review-options'), publicConfig.load().catch(() => null)])
    mouse.value = data.mouse; summary.value = data.reviewSummary; options.value = optionResponse.data
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
const toggleCompare = () => { try { compare.toggle(mouse.value) } catch (e) { error.value = e.message } }
const refreshReview = async () => { await Promise.all([loadMine(), filterSummary()]) }
const deleteGrip = async (item) => {
  if (!window.confirm(`确定删除${item.label}的舒适度评分及对应支撑涂抹吗？`)) return
  error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${item.code}`); showToast(`${item.label}评分及支撑涂抹已删除`); await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const updateSupportDabs = (dabs) => {
  personalSupportDabsByGrip[activeSupportGrip.value] = dabs
  supportError.value = ''
}
const handlePublicSupportModelError = () => { publicSupportError.value = '支撑位置热力图加载失败，请刷新页面后重试' }
const handlePersonalSupportModelError = () => { supportError.value = '个人支撑位置画布加载失败，请刷新页面后重试' }
const clearSupportSelection = () => {
  personalSupportDabsByGrip[activeSupportGrip.value] = []
  supportError.value = ''
}
const saveGripReview = async () => {
  const code = activeSupportGrip.value
  if (!activeGripScoreReady.value || !supportHasPaint.value) {
    supportError.value = '请先完成当前握姿评分和支撑位置涂抹'
    return
  }
  supportLoading.value = true; supportError.value = ''
  try {
    await Promise.all([
      api.put(`/mice/${mouse.value.id}/reviews/mine/grip-scores/${code}`, { comfortScore: gripScores[code] }),
      api.put(`/mice/${mouse.value.id}/reviews/mine/support-positions/${code}`, { dabs: personalSupportDabs.value }),
    ])
    showToast(`${gripLabel(code)}评分与支撑图已一并保存`)
    await refreshReview()
  } catch (e) { supportError.value = errorMessage(e) } finally { supportLoading.value = false }
}
const openReviewEditor = async () => {
  error.value = ''
  activeSupportGrip.value = 'PALM'
  supportError.value = ''
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
const startViewRealtime = () => {
  stopRealtime()
  stopRealtime = onRealtime((event) => {
    if (!mouse.value) return
    if (event.type === 'sync.required') {
      pendingRealtimeTypes.add('mouse.changed')
      pendingRealtimeTypes.add('review.changed')
    } else {
      if (event.mouseId && event.mouseId !== mouse.value.id) return
      pendingRealtimeTypes.add(event.type)
    }
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(() => {
      const reloadMouse = pendingRealtimeTypes.has('mouse.changed')
      const reloadReview = pendingRealtimeTypes.has('review.changed')
      pendingRealtimeTypes.clear()
      if (reloadMouse) load()
      else if (reloadReview) refreshReview()
    }, 200)
  })
}
onMounted(() => {
  load()
})
onActivated(() => {
  startViewRealtime()
  if (!resetFiltersOnNextActivation) return
  resetFiltersOnNextActivation = false
  reviewFiltersInitialized.value = false
  selectedGrip.value = ''
  selectedHand.value = ''
  load()
})
onDeactivated(() => {
  stopRealtime()
  clearTimeout(realtimeTimer)
  pendingRealtimeTypes.clear()
  resetFiltersOnNextActivation = true
  closeReviewEditor()
})
onBeforeUnmount(() => { closeReviewEditor(); stopRealtime(); clearTimeout(realtimeTimer); pendingRealtimeTypes.clear() })
</script>

<template>
  <main v-if="mouse">
    <section class="detail-hero section-shell">
      <div class="breadcrumb"><RouterLink to="/mice">鼠标库</RouterLink><span>/</span><span>{{ mouse.brand }}</span><span>/</span><strong>{{ mouse.model }}</strong></div>
      <div class="detail-product-header">
        <div class="detail-product-identity">
          <p class="page-label">{{ mouse.brand }} / SPEC SHEET</p>
          <div><h1>{{ mouse.model }}</h1><span>{{ mouse.variant || 'STANDARD EDITION' }}</span></div>
          <div class="detail-tags"><span>{{ connection }}</span><span>{{ mouse.weightG ?? '—' }} g</span><span>{{ valueLabel(mouse.shapeType) }}</span></div>
        </div>
        <div class="detail-product-actions">
          <button class="button primary-action-button" type="button" @click="toggleCompare">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '+ 加入对比' }}</button>
          <button class="button button-ghost objective-trigger" type="button" aria-haspopup="dialog" aria-controls="objective-data-dialog" @click="openObjectiveData">查看完整参数</button>
          <a v-if="mouse.primarySourceUrl" class="detail-source-link" :href="mouse.primarySourceUrl" target="_blank" rel="noopener noreferrer">数据来源 ↗</a>
        </div>
      </div>
      <div class="detail-model-stage">
        <section class="detail-visual-panel detail-mouse-viewport" aria-labelledby="mouse-model-title">
          <header class="model-panel-heading">
            <div><span>MOUSE MODEL</span><h2 id="mouse-model-title">鼠标三维模型</h2></div>
            <em>模型待接入</em>
          </header>
          <div class="mouse-model-placeholder" role="img" :aria-label="`${mouse.brand} ${mouse.model} 三维模型暂未接入`">
            <span class="model-axis" aria-hidden="true"><i>X</i><i>Y</i><i>Z</i></span>
            <div class="mouse-model-slot"><strong>3D</strong><small>MODEL SLOT</small></div>
            <p><strong>{{ mouse.brand }} {{ mouse.model }}</strong><span>模型资源接入后，可在此旋转查看外形与尺寸比例</span></p>
          </div>
          <footer class="model-panel-footer"><span>VIEWPORT / LEFT</span><small>当前仅预留模型视口</small></footer>
        </section>

        <section class="detail-visual-panel detail-hand-viewport" aria-labelledby="hand-heatmap-title">
          <header class="model-panel-heading">
            <div><span>CONTACT HEATMAP</span><h2 id="hand-heatmap-title">3D 手掌支撑热力图</h2></div>
            <div class="heatmap-heading-meta">
              <div class="heatmap-score"><strong>{{ summary.sampleCount ? summary.overallAverage : '—' }}</strong><span>/ 10</span><small>{{ gripSummaryLabel }} · {{ summary.sampleCount }} 份评价</small></div>
              <em :class="{ low: supportSummary.sampleCount < 5 }">{{ supportSummary.sampleCount }} 份标记</em>
            </div>
          </header>
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
            <span class="support-mode-hint">{{ supportSummary.cells?.length ? `汇总 ${supportSummary.sampleCount} 份握姿标记，左键拖动旋转` : '当前筛选暂无支撑标记，左键拖动旋转模型' }}</span>
          </div>
          <div v-if="publicSupportError" class="flash error model-panel-error">{{ publicSupportError }}</div>
          <footer class="model-panel-footer hand-heatmap-footer">
            <div class="heatmap-filter-bar">
              <label><span>握姿</span><select v-model="selectedGrip" @change="filterSummary"><option v-for="item in options?.gripStyles || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label>
              <label><span>手长</span><select v-model="selectedHand" @change="filterSummary"><option value="">全部</option><option v-for="item in options?.handSizes || []" :key="item.code" :value="item.code">{{ item.label }}</option></select></label>
            </div>
            <div class="heat-legend" v-if="supportSummary.cells?.length"><span>较少</span><i></i><span>较多</span></div>
            <div class="heatmap-footer-actions">
              <RouterLink v-if="!auth.authenticated" class="button primary-action-button review-write-button heatmap-write-button" to="/login">登录后写评价</RouterLink>
              <button v-else-if="options && reviewSubmissionEnabled" class="button primary-action-button review-write-button heatmap-write-button" type="button" @click="openReviewEditor">
                {{ mine ? '管理我的评价' : '写评价' }}<span aria-hidden="true">→</span>
              </button>
              <span v-else class="sample-badge low heatmap-review-disabled">评价提交暂时关闭</span>
              <button v-if="auth.authenticated" type="button" class="support-report-button" @click="openReport('MOUSE', mouse.id, 'REVIEW_AGGREGATE')">反馈异常</button>
              <RouterLink v-else class="support-report-button" to="/login">登录后反馈</RouterLink>
            </div>
          </footer>
        </section>
      </div>
      <div class="hero-statline"><div><span>DIMENSIONS</span><strong>{{ dimensions }}</strong></div><div><span>WEIGHT</span><strong>{{ mouse.weightG ?? '—' }} g</strong></div><div><span>SENSOR</span><strong>{{ mouse.sensorName || '—' }}</strong></div><div class="hero-polling-stat"><span>POLLING</span><strong>{{ mouse.maxPollingRateHz ?? '—' }} Hz</strong></div></div>
    </section>

    <section class="section-shell community-review-section">
      <div class="section-heading compact">
        <div><p class="eyebrow">COMMUNITY RECORDS</p><h2>逐条公开评价</h2><p>仅展示结构化评分和匿名用户标识，不公开邮箱与个人资料。</p></div>
        <button v-if="auth.authenticated" class="button button-ghost" type="button" @click="openReport('MOUSE', mouse.id)">提交参数纠错</button>
        <RouterLink v-else class="button button-ghost" to="/login">登录后纠错</RouterLink>
      </div>
      <div class="public-review-rail-shell" :class="{ single: publicReviews.items.length <= 1 }">
        <button v-if="publicReviews.items.length > 1" class="public-review-rail-arrow previous" type="button" aria-label="查看上一组评价" @click="scrollPublicReviews(-1)">←</button>
        <div ref="publicReviewsRail" class="public-review-rail" tabindex="0" aria-label="横向浏览逐条公开评价">
          <article v-for="review in publicReviews.items" :key="review.id" class="public-review-ticket">
            <header class="ticket-header"><div><strong>{{ review.author }}</strong><small>{{ new Date(review.createdAt).toLocaleDateString('zh-CN') }} · {{ valueLabel(review.handSize) }}</small></div><div class="ticket-score"><b>{{ review.comfortAverage || '—' }}</b><small>/ 10</small></div></header>
            <div class="ticket-grip-row" role="tablist" :aria-label="`${review.author} 的握姿评价`"><button v-for="score in review.gripScores" :key="score.gripStyle" type="button" role="tab" :aria-selected="activePublicSupportGrip(review) === score.gripStyle" :class="{ active: activePublicSupportGrip(review) === score.gripStyle, painted: supportMapForGrip(review, score.gripStyle) }" @click="selectPublicSupportGrip(review, score.gripStyle)"><em>{{ gripLabel(score.gripStyle) }}</em><b>{{ score.comfortScore }}</b><i aria-hidden="true"></i></button><span v-if="!review.gripScores?.length" class="ticket-empty-score">暂无握姿评分</span></div>
            <div class="ticket-support-map" :class="{ empty: !activePublicSupportMap(review)?.supportDabs?.length && !activePublicSupportMap(review)?.supportCells?.length }">
              <HandSupport3D v-if="activePublicSupportMap(review)?.supportDabs?.length || activePublicSupportMap(review)?.supportCells?.length" :key="`${review.id}-${activePublicSupportGrip(review)}`" :summary-cells="activePublicSupportMap(review).supportCells || []" :max-count="activePublicSupportMap(review).supportCells?.length ? 1 : 0" :grid-columns="24" :grid-rows="32" :dabs="activePublicSupportMap(review).supportDabs || []" tool="view" :editable="false" :aria-label="`${review.author} 的${gripLabel(activePublicSupportGrip(review))}支撑位置 3D 涂抹`" />
              <span v-else>{{ gripLabel(activePublicSupportGrip(review)) }}暂未提交支撑位置涂抹</span>
              <small class="ticket-support-label">{{ gripLabel(activePublicSupportGrip(review)) }} / SUPPORT MAP</small>
            </div>
            <footer class="ticket-footer"><span>{{ review.gripScores?.map((score) => gripLabel(score.gripStyle)).join(' / ') || '未填写握姿' }} · {{ publicSupportCount(review) }} 份支撑图</span><button v-if="auth.authenticated" type="button" @click="openReport('REVIEW', review.id)">举报</button></footer>
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
        v-if="objectiveDialogOpen"
        id="objective-data-dialog"
        ref="objectiveDialog"
        class="objective-dialog"
        aria-labelledby="objective-dialog-title"
        @click="closeObjectiveDataFromBackdrop"
        @close="objectiveDialogOpen = false"
      >
        <div class="objective-dialog-shell">
          <header class="objective-dialog-header">
            <div>
              <small>OBJECTIVE DATA / VERIFIED</small>
              <h2 id="objective-dialog-title">客观参数</h2>
              <p>{{ mouse.brand }} {{ mouse.model }} 的尺寸、性能与硬件规格。</p>
            </div>
            <button class="objective-dialog-close" type="button" aria-label="关闭客观参数窗口" @click="closeObjectiveData">×</button>
          </header>
          <div class="objective-dialog-body">
            <div class="objective-feature-strip" aria-label="关键客观参数">
              <div><span>DIMENSIONS</span><strong>{{ dimensions }}</strong></div>
              <div><span>WEIGHT</span><strong>{{ mouse.weightG ?? '—' }} g</strong></div>
              <div><span>SENSOR</span><strong>{{ mouse.sensorName || '—' }}</strong></div>
              <div><span>POLLING</span><strong>{{ mouse.maxPollingRateHz ?? '—' }} Hz</strong></div>
            </div>
            <div class="spec-groups-grid objective-spec-groups">
              <div class="spec-group"><h3>尺寸与重量</h3><dl><div><dt>尺寸分类</dt><dd>{{ labels[mouse.sizeCategory] || '—' }}</dd></div><div><dt>长度</dt><dd>{{ mouse.lengthMm ?? '—' }} mm</dd></div><div><dt>宽度</dt><dd>{{ mouse.widthMm ?? '—' }} mm</dd></div><div><dt>高度</dt><dd>{{ mouse.heightMm ?? '—' }} mm</dd></div><div><dt>重量</dt><dd>{{ mouse.weightG ?? '—' }} g</dd></div></dl></div>
              <div class="spec-group"><h3>外形细节</h3><dl>
                <div><dt>外形类型</dt><dd>{{ valueLabel(mouse.shapeType) }}</dd></div><div><dt>适用手</dt><dd>{{ valueLabel(mouse.handCompatibility) }}</dd></div>
                <div><dt>隆起位置</dt><dd>{{ valueLabel(mouse.humpPlacement) }}</dd></div><div><dt>前端外扩</dt><dd>{{ valueLabel(mouse.frontFlare) }}</dd></div>
                <div><dt>侧面曲率</dt><dd>{{ valueLabel(mouse.sideCurvature) }}</dd></div><div><dt>拇指托</dt><dd>{{ yesNo(mouse.thumbRest) }}</dd></div><div><dt>无名指托</dt><dd>{{ yesNo(mouse.ringFingerRest) }}</dd></div>
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
                <div><dt>主要材质</dt><dd>{{ mouse.materialGeneral || mouse.material || '—' }}</dd></div><div><dt>具体材质</dt><dd>{{ mouse.materialSpecific || '—' }}</dd></div><div><dt>购买渠道</dt><dd>{{ mouse.purchaseChannels || '—' }}</dd></div>
              </dl></div>
            </div>
            <div class="source-card objective-source-card"><span>DATA SOURCE</span><p v-if="mouse.sourceNotes">{{ mouse.sourceNotes }}</p><a v-if="mouse.primarySourceUrl" :href="mouse.primarySourceUrl" target="_blank" rel="noopener noreferrer">查看原始数据来源 ↗</a></div>
          </div>
          <footer class="objective-dialog-footer"><button class="button button-ghost" type="button" @click="closeObjectiveData">关闭窗口</button></footer>
        </div>
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
            <div class="flash error" v-if="error">{{ error }}</div>
            <div class="profile-required" v-if="!profileReady"><span>PROFILE REQUIRED</span><p>评分时会自动读取个人资料中的手长和习惯握姿，请先填写后再回来提交。</p><RouterLink class="button button-ghost" to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
            <div class="review-entry-stack">
              <section class="review-entry-card support-entry personal-support-editor">
                <header>
                  <div><span>01 / GRIP REVIEW</span><h3>握姿评价与支撑位置</h3></div>
                  <em>{{ completedGripCount }} / 4 已评分 · {{ supportGripCount }} / 4 已涂抹</em>
                </header>
                <p class="review-hint">先选择握姿，再标记鼠标实际托住手部的位置并填写该握姿的舒适度评分。</p>
                <div class="support-grip-tabs" role="tablist" aria-label="选择要编辑的握姿支撑图">
                  <button v-for="item in options.gripStyles" :key="item.code" type="button" role="tab" :aria-selected="activeSupportGrip === item.code" :class="{ active: activeSupportGrip === item.code, completed: personalSupportDabsByGrip[item.code]?.length }" @click="activeSupportGrip = item.code; supportError = ''">
                    <span>{{ item.label }}</span><small>{{ submittedGrip(item.code) ? `已评分 ${submittedGrip(item.code).comfortScore}` : '未评分' }} · {{ personalSupportDabsByGrip[item.code]?.length ? '已涂抹' : '未涂抹' }}</small>
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
                    <div class="flash error" v-if="supportError">{{ supportError }}</div>
                    <div class="support-profile-required" v-if="!profileReady"><span>需要先填写手长与习惯握姿</span><RouterLink to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
                    <section v-if="activeGripOption" class="support-grip-score" :class="{ completed: activeSubmittedGrip }" aria-labelledby="active-grip-score-title">
                      <header class="support-grip-score-head">
                        <div><span>GRIP COMFORT</span><strong id="active-grip-score-title">{{ activeGripOption.label }}舒适度</strong><small>{{ activeGripOption.code }}</small></div>
                        <b>{{ gripScores[activeGripOption.code] }}</b>
                      </header>
                      <p>{{ activeSubmittedGrip ? '当前为已提交评分，可以拖动滑杆后与支撑图一并更新。' : gripScoreTouched[activeGripOption.code] ? '评分已确认，完成支撑位置涂抹后即可统一提交。' : '请拖动滑杆，明确确认当前握姿的舒适度评分。' }}</p>
                      <input v-model.number="gripScores[activeGripOption.code]" type="range" min="1" max="10" :aria-label="`${activeGripOption.label}舒适度评分`" @input="gripScoreTouched[activeGripOption.code] = true">
                      <button v-if="activeSubmittedGrip" class="item-delete-button compact support-grip-delete" type="button" @click="deleteGrip(activeGripOption)">删除评分和支撑图</button>
                    </section>
                    <button class="button full support-submit combined-review-submit" type="button" :disabled="!profileReady || !supportHasPaint || !activeGripScoreReady || supportLoading" @click="saveGripReview">
                      {{ supportLoading ? '提交中…' : activeSubmittedGrip && supportMapForGrip(mine, activeSupportGrip) ? `更新${gripLabel(activeSupportGrip)}完整评价` : `提交${gripLabel(activeSupportGrip)}完整评价` }}
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
                        :aria-label="`可涂抹的${gripLabel(activeSupportGrip)}个人支撑位置图`"
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

  </main>
  <main v-else class="section-shell error-page"><div class="flash error" v-if="error">{{ error }}</div><div v-else class="loading-state">LOADING SPEC SHEET...</div></main>
</template>

<style scoped>
.community-review-section{margin-top:32px}.community-review-section .section-heading>div>p:last-child{margin:.35rem 0 0;color:var(--muted,#6f6f6f)}.support-feedback-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:14px}.support-feedback-row .public-support-note{margin:0}.support-report-button{flex:0 0 auto;padding:7px 10px;border:1px solid #d4d4d4;border-radius:999px;background:#fafafa;color:#6a6a6a;font-size:11px;text-decoration:none;cursor:pointer}.support-report-button:hover{border-color:#a2a2a2;background:#f4f4f4}.public-review-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}.public-review-grid article{padding:18px;border:1px solid #e3e3e3;border-radius:18px;background:#fff}.public-review-grid article header,.public-review-grid article footer{display:flex;justify-content:space-between;align-items:center}.public-review-grid article header strong,.public-review-grid article header small{display:block}.public-review-grid article header>span{font-size:24px;font-weight:800}.public-review-grid article header>span small{font-size:11px}.public-score-strip{display:grid;grid-template-columns:repeat(5,1fr);gap:6px;margin:16px 0}.public-score-strip span{padding:8px 4px;background:#f6f6f6;border-radius:9px;text-align:center;font-size:10px}.public-score-strip b{display:block;font-size:16px}.public-review-grid footer{font-size:12px;color:#737373}.public-review-grid footer button{border:0;background:none;color:#4f4f4f}.public-review-pagination{display:flex;justify-content:center;align-items:center;gap:12px;margin-top:18px}.report-dialog{width:min(560px,calc(100vw - 32px));max-width:none;max-height:min(760px,calc(100dvh - 32px));padding:0;border:1px solid #d9d9d9;border-radius:22px;background:#fdfdfd;color:#2d2d2d;box-shadow:0 30px 90px rgba(45, 45, 45,.28)}.report-dialog::backdrop{background:rgba(25, 25, 25,.52);backdrop-filter:blur(8px)}.report-dialog[open]{animation:report-dialog-in 190ms cubic-bezier(.22,1,.36,1)}.report-dialog-shell{display:grid;grid-template-rows:auto minmax(0,1fr) auto;max-height:min(760px,calc(100dvh - 32px));margin:0}.report-dialog-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:25px 26px 20px;border-bottom:1px solid #e8e8e8;background:linear-gradient(135deg,#f9f9f9,#f4f4f4)}.report-dialog-header>div{display:grid;gap:6px}.report-dialog-header small{color:#7f7f7f;font:600 .75rem var(--mono);letter-spacing:.12em}.report-dialog-header h2{margin:0;color:#333333;font-size:1.45rem;letter-spacing:-.035em}.report-dialog-header p{margin:0;color:#7b7b7b;font-size:.75rem;line-height:1.55}.report-dialog-close{display:grid;place-items:center;flex:0 0 38px;width:38px;height:38px;border:1px solid #d5d5d5;border-radius:11px;background:rgba(255,255,255,.58);color:#696969;font-size:1.25rem;cursor:pointer}.report-dialog-close:hover{background:#fff;color:#2d2d2d}.report-dialog-body{display:grid;gap:16px;overflow:auto;padding:22px 26px 24px}.report-context-card{display:grid;gap:5px;padding:13px 14px;border:1px solid #e2e2e2;border-radius:13px;background:#f8f8f8}.report-context-card span,.report-field{color:#6b6b6b;font-size:.75rem;font-weight:700}.report-context-card strong{color:#2d2d2d;font-size:.9rem}.report-context-card small{color:#848484;font-size:.75rem}.report-field{display:grid;gap:7px}.report-field select,.report-field textarea{width:100%;box-sizing:border-box;padding:11px 12px;border:1px solid #d8d8d8;border-radius:11px;background:#fff;color:#2d2d2d;font:inherit}.report-field select:focus,.report-field textarea:focus{border-color:#9d9d9d;outline:3px solid rgba(157, 157, 157,.18)}.report-field textarea{min-height:140px;resize:vertical;line-height:1.55}.report-field>small{justify-self:end;color:#8e8e8e;font-size:.75rem;font-weight:400}.report-dialog-footer{display:flex;justify-content:flex-end;gap:9px;padding:16px 26px 20px;border-top:1px solid #e8e8e8;background:#fafafa}.report-dialog-footer .button{min-width:106px}.report-dialog-footer .primary-action-button{background:#424242;color:#f9f9f9}.report-dialog-footer .primary-action-button:hover{background:#323232}.community-review-section .public-report-form{display:none}@keyframes report-dialog-in{from{opacity:0;transform:translateY(12px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}@media(max-width:760px){.public-review-grid{grid-template-columns:1fr}.report-dialog{width:100vw;height:100dvh;max-height:100dvh;border-radius:0}.report-dialog-shell{height:100dvh;max-height:100dvh}.report-dialog-header{padding:20px 18px 16px}.report-dialog-header h2{font-size:1.2rem}.report-dialog-body{padding:18px}.report-dialog-footer{padding:13px 18px}.report-dialog-footer .button{width:100%}.report-dialog-footer{display:grid;grid-template-columns:1fr 1fr}.support-feedback-row{align-items:flex-start;flex-direction:column}.support-report-button{width:fit-content}}
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
  background: rgba(5, 5, 5, .82);
  backdrop-filter: blur(8px);
}
.report-dialog-header {
  border-bottom-color: var(--dv-border);
  background: linear-gradient(135deg, #1b1b1b, #141414);
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
.report-dialog-footer .primary-action-button { border-color: var(--dv-primary); background: var(--dv-primary); color: #0b0b0b; }
.report-dialog-footer .primary-action-button:hover { border-color: #ffffff; background: #ffffff; color: #0b0b0b; }
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
.public-review-grid footer button:hover { color: #ffffff; }
.public-review-pagination { color: var(--dv-muted); }
.public-review-pagination button { border-color: var(--dv-border); background: var(--dv-surface); color: var(--dv-text-soft); }
.public-review-pagination button:hover:not(:disabled) { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
.review-heading { align-items: center; }
.review-heading-actions { display: flex; align-items: center; justify-content: flex-end; gap: 12px; }
.objective-trigger {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  padding: 7px 12px;
  border: 1px solid var(--dv-primary-line, var(--line));
  border-radius: 999px;
  background: var(--dv-primary-soft, var(--panel2));
  color: var(--dv-primary-bright, var(--acid));
  font: 600 .75rem var(--dv-mono, var(--mono));
  letter-spacing: .04em;
  cursor: pointer;
  transition: border-color 160ms ease, background 160ms ease, transform 160ms ease;
}
.objective-trigger span { font-size: .95rem; line-height: 1; }
.objective-trigger:hover { border-color: var(--dv-primary-bright, var(--acid)); background: var(--dv-surface-high, var(--panel2)); transform: translateY(-1px); }
.hero-polling-stat { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; column-gap: 16px; row-gap: 12px; }
.hero-polling-stat > span { grid-column: 1 / -1; }
.hero-polling-stat > strong { min-width: 0; margin-top: 0; }
.hero-polling-stat > .hero-objective-trigger { justify-self: end; }
.objective-dialog {
  width: min(960px, calc(100vw - 32px));
  max-width: none;
  max-height: min(840px, calc(100dvh - 32px));
  margin: auto;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--dv-border, var(--line));
  border-radius: 16px;
  background: var(--dv-surface, var(--black));
  color: var(--dv-text, var(--text));
  box-shadow: 0 32px 100px rgba(0, 0, 0, .58);
}
.objective-dialog::backdrop { background: rgba(5, 5, 5, .78); backdrop-filter: blur(10px); }
.objective-dialog[open] { animation: objective-dialog-in 190ms cubic-bezier(.22, 1, .36, 1); }
.objective-dialog-shell { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; max-height: min(840px, calc(100dvh - 32px)); }
.objective-dialog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; padding: 24px 26px 20px; border-bottom: 1px solid var(--dv-border, var(--line)); background: linear-gradient(135deg, var(--dv-surface-high, var(--panel2)), var(--dv-surface, var(--black))); }
.objective-dialog-header small { color: var(--dv-primary-bright, var(--acid)); font: 600 .75rem var(--dv-mono, var(--mono)); letter-spacing: .14em; }
.objective-dialog-header h2 { margin: 7px 0 0; color: var(--dv-text, var(--text)); font-size: 1.5rem; letter-spacing: -.035em; }
.objective-dialog-header p { margin: 6px 0 0; color: var(--dv-text-soft, var(--muted)); font-size: .75rem; line-height: 1.5; }
.objective-dialog-close { display: grid; place-items: center; flex: 0 0 36px; width: 36px; height: 36px; border: 1px solid var(--dv-outline, var(--line)); border-radius: 10px; background: var(--dv-surface-high, var(--panel2)); color: var(--dv-text-soft, var(--muted)); font-size: 1.2rem; cursor: pointer; }
.objective-dialog-close:hover { border-color: var(--dv-primary-line, var(--acid)); color: var(--dv-primary-bright, var(--acid)); }
.objective-dialog-body { min-height: 0; overflow: auto; padding: 22px 26px 26px; }
.objective-feature-strip { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--dv-border, var(--line)); background: var(--dv-background, var(--black)); }
.objective-feature-strip > div { min-width: 0; padding: 15px 16px 17px; border-right: 1px solid var(--dv-border, var(--line)); }
.objective-feature-strip > div:last-child { border-right: 0; }
.objective-feature-strip span { display: block; margin-bottom: 10px; color: var(--dv-muted, var(--muted)); font: .75rem var(--dv-mono, var(--mono)); letter-spacing: .12em; }
.objective-feature-strip strong { display: block; overflow: hidden; color: var(--dv-text, var(--text)); font: 600 .88rem var(--dv-mono, var(--mono)); text-overflow: ellipsis; white-space: nowrap; }
.objective-spec-groups { gap: 12px 14px; margin-top: 18px; }
.objective-spec-groups .spec-group { margin-top: 0; overflow: hidden; border: 1px solid var(--dv-border, var(--line)); border-radius: 10px; background: var(--dv-surface, var(--black)); }
.objective-spec-groups .spec-group h3 { border-bottom: 1px solid var(--dv-border, var(--line)); border-radius: 0; }
.objective-spec-groups .spec-group dl div { padding: 12px; }
.objective-source-card { margin-top: 18px !important; }
.objective-dialog-footer { display: flex; justify-content: flex-end; padding: 14px 26px 18px; border-top: 1px solid var(--dv-border, var(--line)); background: var(--dv-surface-high, var(--panel2)); }
.objective-dialog-footer .button { min-width: 110px; }
@keyframes objective-dialog-in { from { opacity: 0; transform: translateY(12px) scale(.985); } to { opacity: 1; transform: translateY(0) scale(1); } }
@media (max-width: 760px) {
  .review-heading { align-items: flex-start; gap: 12px; }
  .review-heading-actions { flex-wrap: wrap; gap: 8px; }
  .review-heading-actions .sample-badge { font-size: .75rem; }
  .objective-trigger { min-height: 31px; padding: 6px 10px; }
  .objective-dialog { width: 100vw; max-height: 100dvh; border-radius: 0; }
  .objective-dialog-shell { height: 100dvh; max-height: 100dvh; }
  .objective-dialog-header { padding: 20px 18px 16px; }
  .objective-dialog-body { padding: 18px; }
  .objective-feature-strip { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .objective-feature-strip > div:nth-child(2) { border-right: 0; }
  .objective-feature-strip > div:nth-child(-n + 2) { border-bottom: 1px solid var(--dv-border, var(--line)); }
  .objective-spec-groups { grid-template-columns: 1fr; }
  .objective-spec-groups .spec-group:last-child:nth-child(odd) { grid-column: auto; }
  .objective-dialog-footer { padding: 13px 18px; }
  .objective-dialog-footer .button { width: 100%; }
}
.public-review-rail-shell { display: grid; grid-template-columns: 36px minmax(0, 1fr) 36px; align-items: center; gap: 10px; width: 100%; min-width: 0; margin-top: 18px; }
.public-review-rail-shell.single { grid-template-columns: minmax(0, 1fr); }
.public-review-rail { display: flex; gap: 12px; width: 100%; max-width: 100%; min-width: 0; overflow-x: auto; padding: 2px 2px 14px; scroll-snap-type: x mandatory; scrollbar-color: var(--dv-outline) transparent; scrollbar-width: thin; }
.public-review-ticket { display: grid; flex: 0 0 min(342px, calc(100vw - 116px)); grid-template-rows: auto auto minmax(148px, 1fr) auto; gap: 12px; min-height: 368px; padding: 15px; scroll-snap-align: start; border: 1px solid var(--dv-border); border-radius: 12px; background: var(--dv-surface); color: var(--dv-text); box-shadow: 0 12px 32px rgba(0,0,0,.18); transition: border-color 160ms ease, background 160ms ease, transform 160ms ease; }
.public-review-ticket:hover { border-color: var(--dv-primary-line); background: var(--dv-surface-high); transform: translateY(-2px); }
.ticket-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ticket-header strong, .ticket-header small { display: block; }
.ticket-header strong { color: var(--dv-text); font-size: .85rem; }
.ticket-header small { margin-top: 4px; color: var(--dv-muted); font: .75rem var(--dv-mono); }
.ticket-score { display: flex; align-items: baseline; gap: 3px; }
.ticket-score b { color: var(--dv-primary-bright); font: 700 1.45rem var(--dv-mono); }
.ticket-score small { color: var(--dv-muted); font-size: .75rem; }
.ticket-grip-row { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 5px; }
.ticket-grip-row > button { position: relative; display: grid; gap: 3px; min-width: 0; padding: 7px 4px; border: 1px solid var(--dv-border); border-radius: 7px; background: var(--dv-surface-high); color: inherit; text-align: center; cursor: pointer; transition: border-color 150ms ease, background 150ms ease, transform 150ms ease; }
.ticket-grip-row > button:hover { border-color: var(--dv-primary-line); transform: translateY(-1px); }
.ticket-grip-row > button.active { border-color: var(--dv-primary); background: var(--dv-primary-soft); box-shadow: inset 0 -2px 0 var(--dv-primary); }
.ticket-grip-row > button i { position: absolute; top: 5px; right: 5px; width: 4px; height: 4px; border-radius: 50%; background: var(--dv-outline); }
.ticket-grip-row > button.painted i { background: var(--dv-primary-bright); box-shadow: 0 0 7px var(--dv-primary); }
.ticket-grip-row em { color: var(--dv-muted); font-size: .75rem; font-style: normal; }
.ticket-grip-row button.active em { color: var(--dv-primary-bright); }
.ticket-grip-row b { color: var(--dv-text); font: 700 .86rem var(--dv-mono); }
.ticket-grip-row .ticket-empty-score { grid-column: 1 / -1; display: block; color: var(--dv-muted); font-size: .75rem; }
.ticket-support-map { position: relative; min-height: 148px; overflow: hidden; border: 1px solid var(--dv-border); border-radius: 9px; background: radial-gradient(circle at 50% 42%, rgba(255,255,255,.10), transparent 58%), var(--dv-background); }
.ticket-support-map.empty { display: grid; place-items: center; color: var(--dv-muted); font-size: .75rem; }
.ticket-support-label { position: absolute; z-index: 3; top: 8px; left: 9px; padding: 4px 6px; border: 1px solid var(--dv-border); border-radius: 5px; background: rgba(10,10,10,.78); color: var(--dv-primary-bright); font: .75rem var(--dv-mono); letter-spacing: .08em; pointer-events: none; }
.support-grip-tabs { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 7px; margin: 14px 0; }
.support-grip-tabs button { display: grid; gap: 4px; min-width: 0; padding: 10px 8px; border: 1px solid var(--dv-border); border-radius: 8px; background: var(--dv-surface-high); color: var(--dv-text-soft); cursor: pointer; }
.support-grip-tabs button span { font-size: .75rem; font-weight: 700; }
.support-grip-tabs button small { color: var(--dv-muted); font: .75rem var(--dv-mono); }
.support-grip-tabs button.completed small { color: var(--dv-primary-bright); }
.support-grip-tabs button.active { border-color: var(--dv-primary); background: var(--dv-primary-soft); color: var(--dv-text); box-shadow: inset 0 -2px 0 var(--dv-primary); }
.support-grip-score { display: grid; gap: 12px; margin-top: 4px; padding: 14px; border: 1px solid var(--dv-border); border-radius: 10px; background: var(--dv-background); }
.support-grip-score.completed { border-color: var(--dv-primary-line); }
.support-grip-score-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.support-grip-score-head span, .support-grip-score-head small { display: block; color: var(--dv-muted); font: .75rem var(--dv-mono); letter-spacing: .08em; }
.support-grip-score-head strong { display: block; margin-top: 4px; color: var(--dv-text); font-size: .85rem; }
.support-grip-score-head small { margin-top: 3px; }
.support-grip-score-head b { color: var(--dv-primary-bright); font: 700 1.55rem/1 var(--dv-mono); }
.support-grip-score > p { margin: 0; color: var(--dv-muted); font-size: .75rem; line-height: 1.55; }
.support-grip-score > input { width: 100%; margin: 2px 0; accent-color: var(--dv-primary); }
.support-grip-score .support-grip-delete { min-height: 40px; margin: 0; }
.support-grip-score .support-grip-delete { width: 100%; border-color: var(--dv-outline); background: var(--dv-surface-high); color: var(--dv-text-soft); }
.support-grip-score .support-grip-delete:hover { border-color: var(--dv-text-soft); background: var(--dv-text); color: var(--dv-background); }
.ticket-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--dv-muted); font-size: .75rem; }
.ticket-footer button { border: 0; background: transparent; color: var(--dv-error); font-size: .75rem; cursor: pointer; }
.ticket-footer button:hover { color: #ffffff; }
.public-review-rail-arrow { display: grid; place-items: center; width: 34px; height: 34px; border: 1px solid var(--dv-outline); border-radius: 8px; background: var(--dv-surface-high); color: var(--dv-text-soft); font-size: 1rem; cursor: pointer; }
.public-review-rail-arrow:hover { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
@media (max-width: 600px) { .support-grip-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
