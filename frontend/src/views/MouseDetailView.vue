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
const HandSupport2D = defineAsyncComponent(() => import('../components/HandSupport2D.vue'))

const route = useRoute()
const auth = useAuthStore()
const compare = useCompareStore()
const publicConfig = usePublicConfigStore()
const mouse = ref(null)
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
const productImageFailed = ref(false)
const productPanelView = ref('image')
const reviewSubmissionEnabled = computed(() => publicConfig.reviewSubmissionEnabled)
const profileReady = computed(() => Boolean(auth.user?.handLengthCm && auth.user?.preferredGripStyle))
const activeGripOption = computed(() => options.value?.gripStyles?.find((item) => item.code === activeSupportGrip.value) || null)
const activeSubmittedGrip = computed(() => Boolean(supportMapForGrip(mine.value, activeSupportGrip.value)))
const personalSupportDabs = computed(() => personalSupportDabsByGrip[activeSupportGrip.value] || [])
const supportCoverage = computed(() => supportCoveragePercentage(personalSupportDabs.value))
const supportHasPaint = computed(() => supportCoverage.value > 0)
const supportGripCount = computed(() => Object.values(personalSupportDabsByGrip).filter((dabs) => supportCoveragePercentage(dabs) > 0).length)
const hasSubmittedSupport = computed(() => supportGripCount.value > 0)
const matchingHandOption = computed(() => options.value?.handSizes?.find((item) => item.code === auth.user?.handSize))
const supportFilterLabel = computed(() => {
  const grip = options.value?.gripStyles?.find((item) => item.code === selectedGrip.value)?.label || '当前握姿'
  const hand = options.value?.handSizes?.find((item) => item.code === selectedHand.value)?.label || '全部手长'
  return `${grip} · ${hand}`
})
const supportMapForGrip = (review, gripStyle) => {
  const mapped = review?.supportByGrip?.find((item) => item.gripStyle === gripStyle)
  if (mapped) return mapped
  const legacyGrip = review?.supportByGrip?.[0]?.gripStyle || review?.gripStyle
  if (legacyGrip === gripStyle && (review?.supportDabs?.length || review?.supportCells?.length)) {
    return { gripStyle, supportDabs: review.supportDabs || [], supportCells: review.supportCells || [] }
  }
  return null
}
const completedReviewGripCount = computed(() => options.value?.gripStyles?.filter((item) => (
  supportMapForGrip(mine.value, item.code)
)).length || 0)
const reviewProgressPercent = computed(() => completedReviewGripCount.value / 4 * 100)
const reviewProgressLabel = computed(() => {
  if (!mine.value) return '还没有提交支撑记录'
  return `已完成 ${completedReviewGripCount.value} / 4 种握姿支撑图`
})
const activePublicSupportGrip = (review) => publicSupportGripSelection[review.id]
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
const connectionModes = computed(() => {
  const value = mouse.value?.connectionModes
  if (Array.isArray(value)) return value
  return String(value || '').split(',').map((item) => item.trim()).filter(Boolean)
})
const connection = computed(() => !mouse.value ? '-' : connectionModes.value.length >= 3 ? '三模' : connectionModes.value.length === 2 ? '双模' : connectionModes.value.includes('wired') ? '有线' : connectionModes.value.length ? '无线' : '-')
const dimensions = computed(() => mouse.value ? `${mouse.value.lengthMm ?? '-'} × ${mouse.value.widthMm ?? '-'} × ${mouse.value.heightMm ?? '-'} mm` : '-')
const yesNo = (value) => value == null ? '-' : value ? '是' : '否'
const valueLabel = (value) => labels[value] || value || '-'
const gripLabel = (value) => ({ PALM: '趴握', CLAW: '抓握', FINGERTIP: '指握', MIXED: '混合' }[value] || value || '-')
const hasValue = (value) => value !== null && value !== undefined && value !== '' && (!Array.isArray(value) || value.length > 0)
const isPublicSourceUrl = (value) => {
  if (!value) return false
  try {
    const host = new URL(value, window.location.origin).hostname.toLowerCase()
    return host !== 'example.com' && host !== 'www.example.com'
  } catch {
    return false
  }
}
const publicSourceUrl = computed(() => isPublicSourceUrl(mouse.value?.primarySourceUrl) ? mouse.value.primarySourceUrl : '')
const objectiveSpecGroups = computed(() => {
  if (!mouse.value) return []
  const groups = [
    { title: '尺寸与重量', items: [
      { label: '尺寸分类', raw: mouse.value.sizeCategory, value: valueLabel(mouse.value.sizeCategory) },
      { label: '长度', unit: 'mm', raw: mouse.value.lengthMm, value: mouse.value.lengthMm },
      { label: '宽度', unit: 'mm', raw: mouse.value.widthMm, value: mouse.value.widthMm },
      { label: '高度', unit: 'mm', raw: mouse.value.heightMm, value: mouse.value.heightMm },
      { label: '重量', unit: 'g', raw: mouse.value.weightG, value: mouse.value.weightG },
    ] },
    { title: '外形与适用性', items: [
      { label: '外形类型', raw: mouse.value.shapeType, value: valueLabel(mouse.value.shapeType) },
      { label: '适用手', raw: mouse.value.handCompatibility, value: valueLabel(mouse.value.handCompatibility) },
    ] },
    { title: '传感器性能', items: [
      { label: '传感器型号', raw: mouse.value.sensorName, value: mouse.value.sensorName },
      { label: '最大 DPI', unit: 'DPI', raw: mouse.value.maxDpi, value: mouse.value.maxDpi },
      { label: '最大回报率', unit: 'Hz', raw: mouse.value.maxPollingRateHz, value: mouse.value.maxPollingRateHz },
      { label: '追踪速度', unit: 'IPS', raw: mouse.value.trackingSpeedIps, value: mouse.value.trackingSpeedIps },
      { label: '最大加速度', unit: 'G', raw: mouse.value.accelerationG, value: mouse.value.accelerationG },
      { label: '可调传感器位置', raw: mouse.value.adjustableSensorPosition, value: yesNo(mouse.value.adjustableSensorPosition) },
    ] },
    { title: '按键微动', items: [
      { label: '微动类型', raw: mouse.value.switchType, value: valueLabel(mouse.value.switchType) },
      { label: '微动寿命', unit: '百万次', raw: mouse.value.switchLifeSpanM, value: mouse.value.switchLifeSpanM },
      { label: '支持热插拔', raw: mouse.value.hotSwappableSwitches, value: yesNo(mouse.value.hotSwappableSwitches) },
    ] },
    { title: '材质与连接', items: [
      { label: '连接方式', raw: connectionModes.value, value: connection.value },
      { label: '主要材质', raw: mouse.value.materialGeneral, value: mouse.value.materialGeneral },
      { label: '具体材质', raw: mouse.value.materialSpecific, value: mouse.value.materialSpecific },
      { label: '购买渠道', raw: mouse.value.purchaseChannels, value: mouse.value.purchaseChannels },
    ] },
  ]
  return groups.map((group) => ({ ...group, items: group.items.filter((item) => hasValue(item.raw)) })).filter((group) => group.items.length)
})

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
      publicSupportGripSelection[review.id] = review.supportByGrip?.[0]?.gripStyle || 'CLAW'
    }
  }
}
const scrollPublicReviews = (direction) => {
  const rail = publicReviewsRail.value
  if (!rail) return
  const card = rail.querySelector('.public-review-ticket')
  const distance = card ? card.getBoundingClientRect().width + 12 : rail.clientWidth * 0.85
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  rail.scrollBy({ left: direction * distance, behavior: reduceMotion ? 'auto' : 'smooth' })
}
const openReport = (targetType, targetId, scope = targetType === 'REVIEW' ? 'REVIEW_ITEM' : 'MOUSE_DATA') => {
  reportTarget.value = { targetType, targetId }
  reportScope.value = scope
  reportCategory.value = scope === 'REVIEW_AGGREGATE' ? 'SUSPICIOUS' : targetType === 'MOUSE' ? 'DATA_ERROR' : 'INAPPROPRIATE'
  reportDescription.value = ''
  nextTick(() => { if (reportDialog.value && !reportDialog.value.open) reportDialog.value.showModal() })
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
    mouse.value = data.mouse; options.value = optionResponse.data; productImageFailed.value = false; productPanelView.value = 'image'
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
  try { await loadSupportSummary(params) }
  catch (e) { error.value = errorMessage(e) }
}
const toggleCompare = () => { try { compare.toggle(mouse.value) } catch (e) { error.value = e.message } }
const refreshReview = async () => { await Promise.all([loadMine(), filterSummary()]) }
const deleteGrip = async (item) => {
  if (!window.confirm(`确定删除${item.label}的支撑位置涂抹吗？`)) return
  error.value = ''
  try { await api.delete(`/mice/${mouse.value.id}/reviews/mine/support-positions/${item.code}`); showToast(`${item.label}支撑位置已删除`); await refreshReview() }
  catch (e) { error.value = errorMessage(e) }
}
const updateSupportDabs = (dabs) => {
  personalSupportDabsByGrip[activeSupportGrip.value] = dabs
  supportError.value = ''
}
const handlePublicSupportModelError = () => { publicSupportError.value = '支撑位置热力图加载失败，请刷新页面后重试' }
const handlePersonalSupportModelError = () => { supportError.value = '二维手掌图片加载失败，请刷新页面后重试' }
const clearSupportSelection = () => {
  personalSupportDabsByGrip[activeSupportGrip.value] = []
  supportError.value = ''
}
const saveGripSupport = async () => {
  const code = activeSupportGrip.value
  if (!supportHasPaint.value) {
    supportError.value = '请先涂抹当前握姿的支撑位置'
    return
  }
  supportLoading.value = true; supportError.value = ''
  try {
    await api.put(`/mice/${mouse.value.id}/reviews/mine/support-positions/${code}`, { dabs: personalSupportDabs.value })
    showToast(`${gripLabel(code)}支撑位置已保存`)
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
          <p class="page-label">{{ mouse.brand }}</p>
          <div><h1>{{ mouse.model }}</h1><span>{{ mouse.variant || '标准版' }}</span></div>
          <div class="detail-tags"><span>{{ connection }}</span><span>{{ mouse.weightG ?? '-' }} g</span><span>{{ valueLabel(mouse.shapeType) }}</span></div>
        </div>
        <div class="detail-product-actions">
          <button class="button primary-action-button" type="button" @click="toggleCompare">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '+ 加入对比' }}</button>
          <a v-if="publicSourceUrl" class="detail-source-link" :href="publicSourceUrl" target="_blank" rel="noopener noreferrer">数据来源 ↗</a>
        </div>
      </div>
      <div class="hero-statline"><div><span>尺寸</span><strong>{{ dimensions }}</strong></div><div><span>重量</span><strong>{{ mouse.weightG ?? '-' }} g</strong></div><div><span>传感器</span><strong>{{ mouse.sensorName || '-' }}</strong></div><div class="hero-polling-stat"><span>回报率</span><strong>{{ mouse.maxPollingRateHz ?? '-' }} Hz</strong></div></div>
      <div class="detail-model-stage">
        <section class="detail-visual-panel detail-mouse-viewport" :class="{ 'image-missing': productPanelView === 'image' && (!mouse.imageUrl || productImageFailed) }" aria-labelledby="product-panel-title">
          <header class="model-panel-heading">
            <div><h2 id="product-panel-title">产品展示</h2></div>
            <div class="product-panel-tabs" role="tablist" aria-label="切换产品图片与完整参数">
              <button id="product-image-tab" type="button" role="tab" :aria-selected="productPanelView === 'image'" aria-controls="product-image-panel" :class="{ active: productPanelView === 'image' }" @click="productPanelView = 'image'">产品图片</button>
              <button id="product-specs-tab" type="button" role="tab" :aria-selected="productPanelView === 'specs'" aria-controls="product-specs-panel" :class="{ active: productPanelView === 'specs' }" @click="productPanelView = 'specs'">完整参数</button>
            </div>
          </header>
          <div v-if="productPanelView === 'image'" id="product-image-panel" role="tabpanel" aria-labelledby="product-image-tab" class="product-panel-content">
            <div v-if="mouse.imageUrl && !productImageFailed" class="detail-product-image-wrap">
              <img class="detail-product-image" :src="mouse.imageUrl" :alt="`${mouse.brand} ${mouse.model} 产品图片`" @error="productImageFailed = true">
            </div>
            <div v-else class="mouse-model-placeholder compact-product-placeholder" role="img" :aria-label="`${mouse.brand} ${mouse.model} 暂无产品图片`">
              <div class="product-placeholder-mark" aria-hidden="true">{{ String(mouse.brand || mouse.model || 'M').slice(0, 2).toUpperCase() }}</div>
              <p><strong>{{ mouse.brand }} {{ mouse.model }}</strong><span>暂无可用产品图片</span></p>
            </div>
          </div>
          <div v-else id="product-specs-panel" role="tabpanel" aria-labelledby="product-specs-tab" class="product-panel-content detail-product-specs" tabindex="0">
            <div class="product-spec-groups">
              <details v-for="group in objectiveSpecGroups" :key="group.title" class="product-spec-group" open>
                <summary><span>{{ group.title }}</span><small>{{ group.items.length }} 项</small><i aria-hidden="true"></i></summary>
                <dl><div v-for="item in group.items" :key="item.label"><dt><span>{{ item.label }}</span><small v-if="item.unit">{{ item.unit }}</small></dt><dd>{{ item.value }}</dd></div></dl>
              </details>
              <p v-if="!objectiveSpecGroups.length" class="objective-empty-data">暂无补充数据</p>
              <div class="product-spec-source">
                <span>数据来源</span>
                <p v-if="mouse.sourceNotes">{{ mouse.sourceNotes }}</p>
                <p v-else-if="!publicSourceUrl">来源待核验</p>
                <a v-if="publicSourceUrl" :href="publicSourceUrl" target="_blank" rel="noopener noreferrer">查看原始数据来源 ↗</a>
              </div>
            </div>
          </div>
          <footer class="model-panel-footer"><span>{{ productPanelView === 'image' ? '产品外观' : '完整参数' }}</span><small>{{ productPanelView === 'image' ? (mouse.imageUrl && !productImageFailed ? '图片仅供外形参考' : '图片待补充') : (publicSourceUrl ? '参数来源可查' : '参数来源待核验') }}</small></footer>
        </section>

        <section class="detail-visual-panel detail-hand-viewport" aria-labelledby="hand-heatmap-title">
          <header class="model-panel-heading">
            <div><h2 id="hand-heatmap-title">3D 手掌支撑热力图</h2></div>
            <div class="heatmap-heading-meta">
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
              <RouterLink v-if="!auth.authenticated" class="button primary-action-button review-write-button heatmap-write-button" to="/login">登录后标记支撑位置</RouterLink>
              <button v-else-if="options && reviewSubmissionEnabled" class="button primary-action-button review-write-button heatmap-write-button" type="button" @click="openReviewEditor">
                {{ mine ? '管理我的支撑记录' : '标记支撑位置' }}<span aria-hidden="true">→</span>
              </button>
              <span v-else class="sample-badge low heatmap-review-disabled">支撑记录提交暂时关闭</span>
              <button v-if="auth.authenticated" type="button" class="support-report-button" @click="openReport('MOUSE', mouse.id, 'REVIEW_AGGREGATE')">反馈异常</button>
              <RouterLink v-else class="support-report-button" to="/login">登录后反馈</RouterLink>
            </div>
          </footer>
        </section>
      </div>
    </section>

    <section class="section-shell community-review-section">
      <div class="section-heading compact">
        <div><h2>用户评论</h2><p>轮播展示匿名用户提交的握姿与支撑位置，不公开邮箱与个人资料。</p></div>
        <button v-if="auth.authenticated" class="button button-ghost" type="button" @click="openReport('MOUSE', mouse.id)">提交参数纠错</button>
        <RouterLink v-else class="button button-ghost" to="/login">登录后纠错</RouterLink>
      </div>
      <div class="public-review-rail-shell" :class="{ single: publicReviews.items.length <= 1 }">
        <button v-if="publicReviews.items.length > 1" class="public-review-rail-arrow previous" type="button" aria-label="查看上一条用户评论" @click="scrollPublicReviews(-1)">←</button>
        <div ref="publicReviewsRail" class="public-review-rail" tabindex="0" aria-label="横向浏览用户评论">
          <article v-for="review in publicReviews.items" :key="review.id" class="public-review-ticket">
            <header class="ticket-header"><div><strong>{{ review.author }}</strong><small>{{ new Date(review.createdAt).toLocaleDateString('zh-CN') }} · {{ valueLabel(review.handSize) }}</small></div><span>{{ publicSupportCount(review) }} 份支撑图</span></header>
            <div class="ticket-grip-row" role="tablist" :aria-label="`${review.author} 的握姿支撑记录`"><button v-for="support in review.supportByGrip" :key="support.gripStyle" type="button" role="tab" :aria-selected="activePublicSupportGrip(review) === support.gripStyle" :class="{ active: activePublicSupportGrip(review) === support.gripStyle, painted: true }" @click="selectPublicSupportGrip(review, support.gripStyle)"><em>{{ gripLabel(support.gripStyle) }}</em><i aria-hidden="true"></i></button><span v-if="!review.supportByGrip?.length" class="ticket-empty-score">暂无握姿支撑记录</span></div>
            <div class="ticket-support-map" :class="{ empty: !activePublicSupportMap(review)?.supportDabs?.length && !activePublicSupportMap(review)?.supportCells?.length }">
              <HandSupport2D v-if="activePublicSupportMap(review)?.supportDabs?.length || activePublicSupportMap(review)?.supportCells?.length" :key="`${review.id}-${activePublicSupportGrip(review)}`" :summary-cells="activePublicSupportMap(review).supportCells || []" :max-count="activePublicSupportMap(review).supportCells?.length ? 1 : 0" :grid-columns="24" :grid-rows="32" :dabs="activePublicSupportMap(review).supportDabs || []" tool="view" :editable="false" :aria-label="`${review.author} 的${gripLabel(activePublicSupportGrip(review))}支撑位置二维图`" />
              <span v-else>{{ gripLabel(activePublicSupportGrip(review)) }}暂未提交支撑位置涂抹</span>
              <small class="ticket-support-label">{{ gripLabel(activePublicSupportGrip(review)) }}支撑位置</small>
            </div>
            <footer class="ticket-footer"><span>{{ review.supportByGrip?.map((support) => gripLabel(support.gripStyle)).join(' / ') || '未填写握姿' }}</span><button v-if="auth.authenticated" type="button" @click="openReport('REVIEW', review.id)">举报</button></footer>
          </article>
          <p v-if="!publicReviews.items.length" class="table-empty">暂无可公开的支撑记录</p>
        </div>
        <button v-if="publicReviews.items.length > 1" class="public-review-rail-arrow next" type="button" aria-label="查看下一条用户评论" @click="scrollPublicReviews(1)">→</button>
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
              <h2 id="report-dialog-title">{{ reportScope === 'REVIEW_AGGREGATE' ? '反馈评价汇总异常' : reportTarget.targetType === 'MOUSE' ? '提交参数纠错' : '举报这条评价' }}</h2>
              <p>{{ reportScope === 'REVIEW_AGGREGATE' ? `当前范围：${supportFilterLabel}。反馈会帮助管理员定位污染聚合结果的评价包。` : '请提供可复核的信息；恶意或重复提交可能被忽略。' }}</p>
            </div>
            <button class="report-dialog-close" type="button" aria-label="关闭反馈窗口" @click="closeReport">×</button>
          </header>
          <div class="report-dialog-body">
            <div class="report-context-card"><span>反馈对象</span><strong>{{ mouse?.brand }} {{ mouse?.model }}</strong><small>{{ reportScope === 'REVIEW_AGGREGATE' ? `聚合评价 · ${supportFilterLabel}` : reportTarget.targetType === 'REVIEW' ? '当前匿名评价' : '鼠标客观参数' }}</small></div>
            <label class="report-field">问题分类<select v-model="reportCategory"><option v-if="reportScope === 'REVIEW_AGGREGATE'" value="SUSPICIOUS">疑似异常汇总</option><option v-if="reportScope === 'REVIEW_AGGREGATE'" value="HEATMAP_ERROR">热力图结果异常</option><option v-if="reportTarget.targetType === 'MOUSE' && reportScope !== 'REVIEW_AGGREGATE'" value="DATA_ERROR">参数错误</option><option v-if="reportTarget.targetType === 'MOUSE' && reportScope !== 'REVIEW_AGGREGATE'" value="SOURCE_UPDATE">来源需要更新</option><option v-if="reportTarget.targetType === 'REVIEW'" value="INAPPROPRIATE">不当内容</option><option v-if="reportTarget.targetType === 'REVIEW'" value="SUSPICIOUS">疑似异常评价</option><option value="OTHER">其他</option></select></label>
            <label class="report-field">详细说明<textarea v-model.trim="reportDescription" maxlength="1000" required :placeholder="reportScope === 'REVIEW_AGGREGATE' ? '例如：中手 / 抓握下的热力图集中在不合理区域…' : '说明具体问题、正确数据或判断依据…'"></textarea><small>{{ reportDescription.length }} / 1000</small></label>
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
              <h2 id="review-dialog-title">标记 {{ mouse.model }} 的支撑位置</h2>
              <p>每种握姿独立保存，可以随时回来继续完成。</p>
            </div>
            <button class="review-dialog-close" type="button" aria-label="关闭评价窗口" @click="closeReviewEditor">×</button>
          </header>
          <div class="review-dialog-status" aria-live="polite">
            <span>{{ reviewProgressLabel }}</span>
            <i><b :style="{ transform: `scaleX(${reviewProgressPercent / 100})` }"></b></i>
          </div>
          <div class="review-dialog-body">
            <div class="flash error" v-if="error">{{ error }}</div>
            <div class="profile-required" v-if="!profileReady"><span>需要完善资料</span><p>提交支撑位置时会读取个人资料中的手长和习惯握姿，请先填写后再回来提交。</p><RouterLink class="button button-ghost" to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
            <div class="review-entry-stack">
              <section class="review-entry-card support-entry personal-support-editor">
                <header>
                  <div><span>步骤 1</span><h3>握姿与支撑位置</h3></div>
                  <em>{{ supportGripCount }} / 4 已涂抹</em>
                </header>
                <p class="review-hint">先选择握姿，再标记鼠标实际托住手部的位置。</p>
                <div class="support-grip-tabs" role="tablist" aria-label="选择要编辑的握姿支撑图">
                  <button v-for="item in options.gripStyles" :key="item.code" type="button" role="tab" :aria-selected="activeSupportGrip === item.code" :class="{ active: activeSupportGrip === item.code, completed: personalSupportDabsByGrip[item.code]?.length }" @click="activeSupportGrip = item.code; supportError = ''">
                    <span>{{ item.label }}</span><small>{{ personalSupportDabsByGrip[item.code]?.length ? '已涂抹' : '未涂抹' }}</small>
                  </button>
                </div>
                <div class="support-editor-layout">
                  <div class="support-editor-controls">
                    <div class="support-tools" aria-label="个人支撑位置涂抹工具">
                      <button type="button" :class="{ active: supportTool === 'paint' }" :disabled="!profileReady" @click="supportTool = 'paint'">涂抹</button>
                      <button type="button" :class="{ active: supportTool === 'erase' }" :disabled="!profileReady" @click="supportTool = 'erase'">擦除</button>
                      <button type="button" :disabled="!profileReady || !personalSupportDabs.length" @click="clearSupportSelection">清空</button>
                    </div>
                    <label class="support-brush-size">
                      <span><strong>画笔大小</strong><output>{{ supportBrushSize }}%</output></span>
                      <input v-model.number="supportBrushSize" type="range" min="4" max="20" step="1" :disabled="!profileReady">
                    </label>
                    <div class="support-selection-status">
                      <strong>{{ supportHasPaint ? `已涂抹约 ${supportCoverage}% 的掌面` : '尚未涂抹支撑区域' }}</strong>
                      <span>{{ supportHasPaint ? '可以继续涂抹或擦除，保存后才会更新公共热力图' : '按住鼠标或用手指，在掌面连续涂抹鼠标实际托住的位置' }}</span>
                    </div>
                    <div class="flash error" v-if="supportError">{{ supportError }}</div>
                    <div class="support-profile-required" v-if="!profileReady"><span>需要先填写手长与习惯握姿</span><RouterLink to="/profile" @click="closeReviewEditor">完善个人资料 →</RouterLink></div>
                    <button v-if="activeSubmittedGrip && activeGripOption" class="item-delete-button compact support-grip-delete" type="button" @click="deleteGrip(activeGripOption)">删除{{ activeGripOption.label }}支撑图</button>
                    <button class="button full support-submit combined-review-submit" type="button" :disabled="!profileReady || !supportHasPaint || supportLoading" @click="saveGripSupport">
                      {{ supportLoading ? '保存中…' : activeSubmittedGrip ? `更新${gripLabel(activeSupportGrip)}支撑位置` : `保存${gripLabel(activeSupportGrip)}支撑位置` }}
                    </button>
                  </div>
                  <div class="support-editor-canvas">
                    <div class="support-map personal-support-map" :class="{ readonly: !profileReady }">
                      <HandSupport2D
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
                      <span class="support-mode-hint">{{ !profileReady ? '完善个人资料后即可涂抹' : supportTool === 'erase' ? '在掌面拖动擦除' : '在掌面拖动连续涂抹' }}</span>
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
  <main v-else class="section-shell error-page"><div class="flash error" v-if="error">{{ error }}</div><div v-else class="loading-state">正在加载鼠标参数…</div></main>
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
.hero-polling-stat { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; column-gap: 16px; row-gap: 12px; }
.hero-polling-stat > span { grid-column: 1 / -1; }
.hero-polling-stat > strong { min-width: 0; margin-top: 0; }
.product-panel-tabs { display: flex; flex: 0 0 auto; gap: 4px; padding: 3px; border: 1px solid var(--figma-line-strong, var(--line)); border-radius: 6px; background: var(--figma-bg, var(--black)); }
.product-panel-tabs button { position: relative; min-height: 36px; padding: 5px 9px; border: 0; border-radius: 4px; background: transparent; color: var(--figma-muted, var(--muted)); font: 600 .68rem var(--dv-mono, var(--mono)); cursor: pointer; transition: color 160ms ease, background 160ms ease; }
.product-panel-tabs button::before { content: ""; position: absolute; inset: -4px 0; }
.product-panel-tabs button:hover { color: var(--figma-text, var(--text)); }
.product-panel-tabs button.active { background: var(--figma-cyan, var(--acid)); color: #071011; }
.product-panel-tabs button:focus-visible { outline: 2px solid var(--figma-cyan, var(--acid)); outline-offset: 2px; }
.detail-mouse-viewport { height: 520px; }
.product-panel-content { display: grid; min-height: 0; overflow: hidden; }
.detail-product-specs { display: block; overflow: auto; padding: 14px; background: var(--figma-bg, var(--black)); scrollbar-color: var(--figma-line-strong, var(--line)) transparent; scrollbar-width: thin; }
.product-spec-groups { min-height: 100%; }
.product-spec-group { min-width: 0; margin: 0; border-bottom: 1px solid var(--figma-line, var(--line)); background: var(--figma-surface, var(--panel)); }
.product-spec-group:first-child { border-top: 1px solid var(--figma-line, var(--line)); }
.product-spec-group summary { display: grid; min-height: 44px; grid-template-columns: minmax(0, 1fr) auto 14px; align-items: center; gap: 8px; padding: 8px 12px; background: var(--figma-surface-high, var(--panel2)); color: var(--figma-text, var(--text)); cursor: pointer; list-style: none; }
.product-spec-group summary::-webkit-details-marker { display: none; }
.product-spec-group summary:hover { background: color-mix(in srgb, var(--figma-surface-high, var(--panel2)) 97%, var(--figma-cyan, var(--acid))); }
.product-spec-group summary:focus-visible { position: relative; z-index: 1; outline: 2px solid var(--figma-cyan, var(--acid)); outline-offset: -2px; }
.product-spec-group summary > span { overflow: hidden; font-size: .78rem; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.product-spec-group summary > small { color: var(--figma-muted, var(--muted)); font: .65rem var(--dv-mono, var(--mono)); }
.product-spec-group summary > i { width: 8px; height: 8px; border-right: 1.5px solid currentColor; border-bottom: 1.5px solid currentColor; color: var(--figma-muted, var(--muted)); transform: rotate(45deg) translate(-1px, -1px); transition: transform 160ms cubic-bezier(.22, 1, .36, 1), color 160ms ease; }
.product-spec-group[open] summary { border-bottom: 1px solid var(--figma-line, var(--line)); color: var(--figma-cyan, var(--acid)); }
.product-spec-group[open] summary > i { color: var(--figma-cyan, var(--acid)); transform: rotate(225deg) translate(-1px, -1px); }
.product-spec-group dl { display: grid; grid-template-columns: 1fr; margin: 0; }
.product-spec-group dl div { display: grid; min-height: 46px; grid-template-columns: minmax(0, 1fr) minmax(88px, 1fr); align-items: center; gap: 16px; padding: 8px 12px; border-bottom: 1px solid var(--figma-line, var(--line)); }
.product-spec-group dl div:last-child { border-bottom: 0; }
.product-spec-group dt { display: flex; min-width: 0; align-items: center; gap: 7px; overflow: hidden; color: var(--figma-text-soft, var(--muted)); font-size: .72rem; }
.product-spec-group dt > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-spec-group dt > small { flex: 0 0 auto; padding: 2px 5px; border-radius: 4px; background: var(--figma-surface-high, var(--panel2)); color: var(--figma-muted, var(--muted)); font: .61rem var(--dv-mono, var(--mono)); text-transform: none; }
.product-spec-group dd { min-width: 0; margin: 0; overflow: hidden; color: var(--figma-text, var(--text)); font: 600 .75rem var(--dv-mono, var(--mono)); text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.product-spec-source { min-width: 0; padding: 12px; border-bottom: 1px solid var(--figma-line, var(--line)); background: var(--figma-surface, var(--panel)); }
.product-spec-source > span { display: block; margin-bottom: 4px; color: var(--figma-cyan, var(--acid)); font: .65rem var(--dv-mono, var(--mono)); }
.product-spec-source p { margin: 0; overflow: hidden; color: var(--figma-muted, var(--muted)); font-size: .68rem; text-overflow: ellipsis; white-space: nowrap; }
.product-spec-source a { display: inline-block; max-width: 100%; margin-top: 4px; overflow: hidden; color: var(--figma-text-soft, var(--text)); font-size: .68rem; text-overflow: ellipsis; white-space: nowrap; }
.detail-product-image-wrap { display: grid; min-height: 260px; padding: 22px; place-items: center; overflow: hidden; background: radial-gradient(circle at 50% 42%, rgba(255,255,255,.08), transparent 62%), var(--dv-background, #0b0b0b); }
.detail-product-image { display: block; width: 100%; height: 270px; object-fit: contain; filter: drop-shadow(0 20px 28px rgba(0,0,0,.34)); }
.compact-product-placeholder { min-height: 190px !important; padding: 28px !important; }
.detail-mouse-viewport.image-missing { min-height: 270px !important; }
.product-placeholder-mark { display: grid; width: 74px; height: 74px; place-items: center; border: 1px solid var(--dv-outline, var(--line)); border-radius: 18px; background: var(--dv-surface-high, var(--panel2)); color: var(--dv-text, var(--text)); font: 700 1.25rem var(--dv-mono, var(--mono)); }
.objective-empty-data { grid-column: 1 / -1; margin: 0; padding: 24px; border: 1px solid var(--dv-border, var(--line)); border-radius: 10px; color: var(--dv-muted, var(--muted)); text-align: center; }
@media (max-width: 900px) {
  .detail-mouse-viewport { height: 470px; }
}
@media (max-width: 760px) {
  .review-heading { align-items: flex-start; gap: 12px; }
  .review-heading-actions { flex-wrap: wrap; gap: 8px; }
  .review-heading-actions .sample-badge { font-size: .75rem; }
  .detail-mouse-viewport { height: 340px; }
  .detail-product-image-wrap { min-height: 190px; padding: 16px; }
  .detail-product-image { height: 210px; }
  .compact-product-placeholder { min-height: 150px !important; }
  .detail-mouse-viewport.image-missing { min-height: 230px !important; }
  .product-panel-tabs button { padding-inline: 8px; }
  .detail-product-specs { padding: 10px; }
  .product-spec-group dl div { min-height: 44px; padding-inline: 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .product-spec-group summary > i { transition: none; }
}
@media (max-width: 480px) {
  .detail-mouse-viewport { height: 320px; }
}
.public-review-rail-shell { display: grid; grid-template-columns: 40px minmax(0, 1fr) 40px; align-items: center; gap: 10px; width: 100%; min-width: 0; margin-top: 18px; }
.public-review-rail-shell.single { grid-template-columns: minmax(0, 1fr); }
.public-review-rail { display: flex; gap: 12px; width: 100%; max-width: 100%; min-width: 0; overflow-x: auto; padding: 2px 2px 10px; scroll-behavior: smooth; scroll-snap-type: x mandatory; scrollbar-color: var(--dv-outline) transparent; scrollbar-width: thin; }
.public-review-ticket { display: grid; flex: 0 0 clamp(270px, 30vw, 318px); min-width: 0; grid-template-rows: auto auto minmax(112px, 1fr) auto; gap: 10px; min-height: 286px; padding: 14px; scroll-snap-align: start; border: 1px solid var(--dv-border); border-radius: 12px; background: var(--dv-surface); color: var(--dv-text); transition: border-color 160ms ease, background 160ms ease, transform 160ms ease; }
.public-review-ticket:hover { border-color: var(--dv-primary-line); background: var(--dv-surface-high); transform: translateY(-2px); }
.ticket-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.ticket-header > div { min-width: 0; }
.ticket-header strong, .ticket-header small { display: block; }
.ticket-header strong { overflow: hidden; color: var(--dv-text); font-size: .85rem; text-overflow: ellipsis; white-space: nowrap; }
.ticket-header small { margin-top: 4px; color: var(--dv-muted); font: .75rem var(--dv-mono); }
.ticket-header > span { flex: 0 0 auto; color: var(--dv-text-soft); font-size: .72rem; white-space: nowrap; }
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
.ticket-support-map { position: relative; min-height: 112px; overflow: hidden; border: 1px solid var(--dv-border); border-radius: 9px; background: radial-gradient(circle at 50% 42%, rgba(255,255,255,.10), transparent 58%), var(--dv-background); }
.ticket-support-map .hand-support-2d {
  width: min(104px, 34%);
  height: auto;
  aspect-ratio: 3 / 4;
  margin-inline: auto;
}
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
.public-review-rail-arrow { display: grid; width: 40px; height: 40px; place-items: center; border: 1px solid var(--dv-outline); border-radius: 8px; background: var(--dv-surface-high); color: var(--dv-text-soft); font-size: 1rem; cursor: pointer; }
.public-review-rail-arrow:hover { border-color: var(--dv-primary-line); background: var(--dv-primary-soft); color: var(--dv-primary-bright); }
.public-review-rail:focus-visible { outline: 2px solid var(--dv-primary); outline-offset: 3px; }
@media (max-width: 600px) {
  .support-grip-tabs { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .public-review-rail-shell { grid-template-columns: minmax(0, 1fr) 44px 44px; align-items: center; gap: 6px; }
  .public-review-rail-shell.single { display: block; }
  .public-review-rail { grid-column: 1 / -1; grid-row: 2; }
  .public-review-rail-arrow.previous { grid-column: 2; grid-row: 1; }
  .public-review-rail-arrow.next { grid-column: 3; grid-row: 1; }
  .public-review-rail-arrow { width: 44px; height: 44px; }
  .public-review-ticket { flex-basis: 100%; min-height: 272px; }
}
@media (prefers-reduced-motion: reduce) {
  .public-review-rail { scroll-behavior: auto; }
  .public-review-ticket { transition: none; }
}
</style>
