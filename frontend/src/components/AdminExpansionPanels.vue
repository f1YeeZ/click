<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api, { errorMessage } from '../api/client'
import AdminFloatingPanel from './AdminFloatingPanel.vue'
import AdminActionDialog from './AdminActionDialog.vue'
import { onRealtime } from '../services/realtime'
import { useAdminActionDialog } from '../composables/useAdminActionDialog'

const props = defineProps({ activeTab: { type: String, required: true } })
const emit = defineEmits(['toast'])
const loading = ref(false)
const { actionDialog, requestAdminAction, confirmAdminAction, cancelAdminAction } = useAdminActionDialog()
const analytics = ref(null)
const analyticsDays = ref(30)
const notifications = ref({ items: [], page: {} })
const brands = ref([])
const brandForm = reactive({ id: '', name: '', officialUrl: '', logoUrl: '', aliases: '', notes: '', status: 'ACTIVE' })
const brandEditorOpen = ref(false)
const reports = ref({ items: [], page: {} })
const reportStatus = ref('')
const reportType = ref('SITE,MOUSE')
const reportQuery = ref('')
const reportDrafts = reactive({})
const selectedReport = ref(null)
let stopFeedbackRealtime = () => {}
const imports = ref({ items: [], page: {} })
const sessions = ref({ items: [], page: {} })
const sessionQuery = ref('')
const sessionActiveOnly = ref(true)
const settings = ref([])
const settingDrafts = reactive({})
const generalSettings = computed(() => settings.value.filter(item => !item.key.startsWith('advertising.')))
const chartHover = reactive({})
const expansionTabs = new Set(['analytics', 'brands', 'feedback', 'operations'])
const analyticsMetrics = [
  { key: 'uniqueVisitors', label: '独立访客 UV', description: '每日访问前台的独立浏览器数', color: '#7198ff', chartType: 'line', group: 'traffic', totalField: 'periodUniqueVisitors', totalLabel: '区间独立访客' },
  { key: 'pageViews', label: '页面浏览 PV', description: '前台页面的每日有效浏览次数', color: '#7198ff', chartType: 'line', group: 'traffic', totalField: 'periodPageViews', totalLabel: '区间页面浏览' },
  { key: 'users', label: '新增用户', description: '新注册账户的每日变化', color: '#7198ff', chartType: 'line', group: 'operations' },
  { key: 'mice', label: '新增鼠标', description: '新建鼠标数据的每日变化', color: '#7198ff', chartType: 'line', group: 'operations' },
  { key: 'reviews', label: '新增支撑记录', description: '用户每日提交的支撑位置记录数', color: '#7198ff', chartType: 'line', group: 'operations' },
  { key: 'adminActions', label: '管理员操作', description: '后台治理与维护操作次数', color: '#7198ff', chartType: 'bar', group: 'operations' },
]
const metricPoints = key => (analytics.value?.points || []).map(point => Number(point[key] || 0))
const chartWidth = 720
const chartTop = 10
const chartBottom = 174
const niceStep = max => {
  if (max <= 1) return 1
  const roughStep = max / 4
  const magnitude = 10 ** Math.floor(Math.log10(roughStep))
  const fraction = roughStep / magnitude
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10
  return niceFraction * magnitude
}
const comparison = values => {
  const days = Math.min(7, Math.floor(values.length / 2))
  if (!days) return { label: '暂无对比区间', value: '-', direction: 'flat' }
  const recent = values.slice(-days).reduce((sum, value) => sum + value, 0)
  const previous = values.slice(-(days * 2), -days).reduce((sum, value) => sum + value, 0)
  if (!previous) return recent
    ? { label: `较前 ${days} 天`, value: `新增 ${recent}`, direction: 'up' }
    : { label: `较前 ${days} 天`, value: '持平', direction: 'flat' }
  const change = Math.round((recent - previous) / previous * 100)
  return {
    label: `较前 ${days} 天`,
    value: change === 0 ? '持平' : `${change > 0 ? '+' : ''}${change}%`,
    direction: change > 0 ? 'up' : change < 0 ? 'down' : 'flat',
  }
}
const buildChart = metric => {
  const source = analytics.value?.points || []
  const values = metricPoints(metric.key)
  const calculatedTotal = values.reduce((total, value) => total + value, 0)
  const peak = Math.max(0, ...values)
  const step = niceStep(peak)
  const scaleMax = Math.max(1, Math.ceil(peak / step) * step)
  const plotHeight = chartBottom - chartTop
  const points = source.map((point, index) => ({
    x: source.length === 1 ? chartWidth / 2 : index / Math.max(1, source.length - 1) * chartWidth,
    y: chartBottom - Number(point[metric.key] || 0) / scaleMax * plotHeight,
    date: point.date,
    value: Number(point[metric.key] || 0),
  }))
  const ticks = []
  for (let value = scaleMax; value >= 0; value -= step) {
    ticks.push({ value, y: chartBottom - value / scaleMax * plotHeight })
  }
  if (ticks.at(-1)?.value !== 0) ticks.push({ value: 0, y: chartBottom })
  const tickInterval = Math.max(1, Math.ceil(source.length / 6))
  const dates = points.filter((point, index) => index === 0 || index === points.length - 1 || index % tickInterval === 0)
  const peakPoint = points.find(point => point.value === peak)
  const barWidth = Math.max(2, Math.min(18, chartWidth / Math.max(1, points.length) * .62))
  return {
    ...metric,
    values,
    points,
    ticks,
    dates,
    peak,
    peakPoint,
    barWidth,
    total: metric.totalField ? Number(analytics.value?.[metric.totalField] || 0) : calculatedTotal,
    average: values.length ? (calculatedTotal / values.length).toFixed(1) : '0.0',
    latest: values.at(-1) || 0,
    trend: comparison(values),
    linePath: points.map((point, index) => `${index ? 'L' : 'M'} ${point.x} ${point.y}`).join(' '),
  }
}
const analyticsCharts = computed(() => analyticsMetrics.map(buildChart))
const analyticsGroups = computed(() => [
  { key: 'traffic', title: '访问表现', description: '仅统计前台公开页面，后台访问和常见爬虫已排除。', charts: analyticsCharts.value.filter(chart => chart.group === 'traffic') },
  { key: 'operations', title: '内容与治理', description: '内容供给和后台维护的每日变化。', charts: analyticsCharts.value.filter(chart => chart.group === 'operations') },
])
const dateLabel = value => String(value || '').slice(5)
const chartPoint = chart => {
  const index = chartHover[chart.key]
  return Number.isInteger(index) ? chart.points[Math.min(index, chart.points.length - 1)] : null
}
const updateChartHover = (chart, event) => {
  if (!chart.points.length) return
  const bounds = event.currentTarget.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (event.clientX - bounds.left) / Math.max(1, bounds.width)))
  chartHover[chart.key] = Math.round(ratio * (chart.points.length - 1))
}
const moveChartHover = (chart, direction) => {
  if (!chart.points.length) return
  const current = Number.isInteger(chartHover[chart.key]) ? chartHover[chart.key] : chart.points.length - 1
  chartHover[chart.key] = Math.max(0, Math.min(chart.points.length - 1, current + direction))
}
const showLatestChartPoint = chart => {
  if (chart.points.length && !Number.isInteger(chartHover[chart.key])) chartHover[chart.key] = chart.points.length - 1
}
const clearChartHover = key => { delete chartHover[key] }
const formatChartDate = value => {
  const [year, month, day] = String(value || '').split('-').map(Number)
  return year && month && day ? `${year}年${month}月${day}日` : String(value || '')
}
const formatChartValue = value => Number(value || 0).toLocaleString('zh-CN')
const showNotice = message => emit('toast', { type: 'success', message })
const showError = message => emit('toast', { type: 'error', message })

const run = async fn => {
  loading.value = true
  try { await fn() } catch (e) { showError(errorMessage(e)) } finally { loading.value = false }
}
const loadAnalytics = () => run(async () => {
  analytics.value = (await api.get('/admin/analytics', { params: { days: analyticsDays.value } })).data
  notifications.value = (await api.get('/admin/notifications', { params: { page: 1 } })).data
})
const loadBrands = () => run(async () => { brands.value = (await api.get('/admin/brand-profiles')).data })
const resetBrand = () => Object.assign(brandForm, { id: '', name: '', officialUrl: '', logoUrl: '', aliases: '', notes: '', status: 'ACTIVE' })
const openBrandCreate = () => { resetBrand(); brandEditorOpen.value = true }
const editBrand = brand => { Object.assign(brandForm, brand); brandEditorOpen.value = true }
const closeBrandEditor = () => { brandEditorOpen.value = false; resetBrand() }
const saveBrand = () => run(async () => {
  const payload = { ...brandForm }; delete payload.id; delete payload.mouseCount; delete payload.updatedAt
  if (brandForm.id) await api.put(`/admin/brand-profiles/${brandForm.id}`, payload)
  else await api.post('/admin/brand-profiles', payload)
  await loadBrands(); closeBrandEditor(); showNotice('品牌资料已保存')
})
const loadReports = (page = 1) => run(async () => {
  reports.value = (await api.get('/admin/reports', { params: { q: reportQuery.value || undefined, status: reportStatus.value || undefined, targetType: reportType.value || undefined, page } })).data
  reports.value.items.forEach(item => {
    if (selectedReport.value?.id === item.id && reportDrafts[item.id]) return
    reportDrafts[item.id] = { status: item.status, assigneeEmail: item.assigneeEmail || '', resolution: item.resolution || '' }
  })
})
const saveReport = report => run(async () => {
  await api.patch(`/admin/reports/${report.id}`, reportDrafts[report.id]); selectedReport.value = null; await loadReports(reports.value.page.number || 1); showNotice('反馈工单已更新')
})
const openReport = report => { selectedReport.value = report }
const applySettings = values => {
  settings.value = values
  settings.value.forEach(item => { settingDrafts[item.key] = item.value })
}
const refreshSettings = async () => applySettings((await api.get('/admin/settings')).data)
const loadOperations = () => run(async () => {
  const [importData, sessionData, settingData] = await Promise.all([
    api.get('/admin/mice/imports'), api.get('/admin/sessions', { params: { activeOnly: sessionActiveOnly.value } }), api.get('/admin/settings')
  ])
  imports.value = importData.data; sessions.value = sessionData.data; applySettings(settingData.data)
})
const loadSessions = (page = 1) => run(async () => { sessions.value = (await api.get('/admin/sessions', { params: { q: sessionQuery.value || undefined, activeOnly: sessionActiveOnly.value, page } })).data })
const revokeSession = async session => {
  const confirmed = await requestAdminAction({
    title: '强制用户下线',
    subtitle: session.userEmail,
    message: `该设备的登录凭证将立即失效。\n设备：${session.userAgent || '未知设备'}\n网络：${session.ipAddress || '未知地址'}`,
    confirmLabel: '确认强制下线',
    tone: 'danger',
  })
  if (!confirmed) return
  return run(async () => {
  await api.delete(`/admin/sessions/${session.id}`); await loadSessions(sessions.value.page.number || 1); showNotice('会话已撤销')
  })
}
const saveSetting = item => run(async () => {
  const value = String(settingDrafts[item.key] ?? '')
  await api.put(`/admin/settings/${encodeURIComponent(item.key)}`, { value })
  await refreshSettings()
  showNotice(item.key === 'maintenance.notice' && !value.trim() ? '前台维护公告已关闭' : '系统设置已生效')
})
const readNotification = item => run(async () => { await api.patch(`/admin/notifications/${item.id}/read`); await loadAnalytics(); showNotice('通知已标记为已读') })
const readAll = () => run(async () => { await api.post('/admin/notifications/read-all'); await loadAnalytics(); showNotice('全部通知已标记为已读') })
const download = async (url, filename) => {
  try { const { data } = await api.get(url, { responseType: 'blob' }); const href = URL.createObjectURL(data); const link = document.createElement('a'); link.href = href; link.download = filename; link.click(); URL.revokeObjectURL(href); showNotice('导出文件已开始下载') }
  catch (e) { showError(errorMessage(e)) }
}
const exportData = type => download(`/admin/exports/${type}`, `clicker-${type}.csv`)
const settingLabel = key => ({ 'maintenance.notice': '前台维护公告', 'registration.enabled': '开放用户注册', 'reviews.enabled': '开放支撑记录提交', 'upload.max-mb': '图片上传提示上限（MB）', 'verification.stale-days': '数据核验过期天数', 'security.session-days': '会话有效天数提示' }[key] || key)
const statusLabel = value => ({ ACTIVE: '正常', ARCHIVED: '已归档', OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', REJECTED: '已驳回', PREVIEW_READY: '预检通过', PREVIEW_FAILED: '预检失败', COMPLETED: '已导入' }[value] || value)
const reportTypeLabel = value => ({ SITE: '前台反馈', MOUSE: '数据纠错', REVIEW: '支撑记录举报' }[value] || value)
const reportCategoryLabel = value => ({ MOUSE_MISSING: '缺失鼠标型号', BUG: '网站 Bug', DATA_ERROR: '数据修正', SUGGESTION: '功能建议', OTHER: '其他反馈' }[value] || value)
const reportPriority = report => {
  if (report.status === 'REJECTED' || report.status === 'RESOLVED') return 'low'
  const category = String(report.category || '').toUpperCase()
  if (category === 'BUG' || category.includes('BUG')) return 'high'
  if (report.targetType === 'MOUSE' || category === 'DATA_ERROR' || category === 'MOUSE_MISSING' || category.includes('纠错')) return 'medium'
  return 'low'
}
const reportPriorityLabel = report => ({ high: '高优先级', medium: '需关注', low: '常规' }[reportPriority(report)])
const reportStatusIcon = status => ({ OPEN: '!', IN_PROGRESS: '↻', RESOLVED: '✓', REJECTED: '×' }[status] || '•')
const date = value => value ? new Date(value).toLocaleString('zh-CN') : '-'
const loadActive = () => ({ analytics: loadAnalytics, brands: loadBrands, feedback: loadReports, operations: loadOperations }[props.activeTab]?.())
const refreshListener = event => { if (event.detail === props.activeTab) loadActive() }
watch(() => props.activeTab, value => {
  brandEditorOpen.value = false
  selectedReport.value = null
  if (expansionTabs.has(value)) loadActive()
}, { immediate: true })
onMounted(() => window.addEventListener('admin:refresh', refreshListener))
onMounted(() => {
  stopFeedbackRealtime = onRealtime(event => {
    if (props.activeTab !== 'feedback') return
    if (event.type === 'feedback.changed' || event.type === 'sync.required') loadReports(reports.value.page.number || 1)
  })
})
onBeforeUnmount(() => {
  window.removeEventListener('admin:refresh', refreshListener)
  stopFeedbackRealtime()
})
</script>

<template>
  <div class="expansion-shell">
    <section v-if="activeTab === 'analytics'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><h3>运营分析与通知</h3><p>按指标查看每日变化、区间累计和近期趋势。</p></div><select v-model.number="analyticsDays" aria-label="选择统计时间范围" @change="loadAnalytics"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option><option :value="90">近 90 天</option></select></div>
      <div class="signal-cards"><article><span>待处理反馈</span><strong>{{ analytics?.openReports ?? '-' }}</strong></article><article><span>未读通知</span><strong>{{ analytics?.unreadNotifications ?? '-' }}</strong></article><article><span>活跃会话</span><strong>{{ analytics?.activeSessions ?? '-' }}</strong></article><article><span>过期数据</span><strong>{{ analytics?.staleMice ?? '-' }}</strong></article></div>
      <div class="analytics-report">
        <section v-for="group in analyticsGroups" :key="group.key" class="analytics-report-group">
          <header class="analytics-group-heading"><div><h4>{{ group.title }}</h4><p>{{ group.description }}</p></div><span>{{ group.charts.length }} 项指标</span></header>
          <div class="analytics-metric-grid">
            <section v-for="chart in group.charts" :key="chart.key" class="metric-report-row" :style="{ '--chart-accent': chart.color }">
              <header class="metric-report-summary">
                <div class="metric-report-title"><i aria-hidden="true"></i><div><h4>{{ chart.label }}</h4><p>{{ chart.description }}</p></div></div>
                <div class="metric-total"><span>{{ chart.totalLabel || `${analyticsDays} 天累计` }}</span><strong>{{ chart.total }}</strong></div>
              </header>
              <div class="metric-plot" role="group" :aria-label="`${chart.label}每日统计${chart.chartType === 'bar' ? '柱状图' : '折线图'}`">
                <div class="metric-y-axis" aria-hidden="true"><span v-for="tick in chart.ticks" :key="tick.value" :style="{ top: `${tick.y / 184 * 100}%` }">{{ tick.value }}</span></div>
                <div class="metric-plot-canvas" tabindex="0" :aria-label="`使用鼠标悬停或左右方向键查看${chart.label}每日数据`" @pointermove="updateChartHover(chart, $event)" @pointerdown="updateChartHover(chart, $event)" @pointerleave="clearChartHover(chart.key)" @focus="showLatestChartPoint(chart)" @blur="clearChartHover(chart.key)" @keydown.left.prevent="moveChartHover(chart, -1)" @keydown.right.prevent="moveChartHover(chart, 1)">
                  <svg viewBox="0 0 720 184" preserveAspectRatio="none" role="img">
                    <title>{{ chart.label }}每日统计{{ chart.chartType === 'bar' ? '柱状图' : '折线图' }}</title>
                    <line v-for="tick in chart.ticks" :key="tick.value" class="metric-grid-line" x1="0" x2="720" :y1="tick.y" :y2="tick.y" />
                    <template v-if="chart.chartType === 'line'">
                      <path class="metric-line" :d="chart.linePath" />
                    </template>
                    <template v-else>
                      <rect v-for="point in chart.points" :key="point.date" class="metric-bar" :x="point.x - chart.barWidth / 2" :y="point.y" :width="chart.barWidth" :height="Math.max(1, 174 - point.y)" rx="2"><title>{{ point.date }}：{{ point.value }}</title></rect>
                    </template>
                    <template v-if="chartPoint(chart)">
                      <line class="metric-hover-guide" :x1="chartPoint(chart).x" :x2="chartPoint(chart).x" :y1="chartTop" :y2="chartBottom" />
                      <circle class="metric-hover-point" :cx="chartPoint(chart).x" :cy="chartPoint(chart).y" r="5" />
                    </template>
                  </svg>
                  <div v-if="chartPoint(chart)" class="metric-tooltip" :class="{ left: chartPoint(chart).x < 150, right: chartPoint(chart).x > 570, below: chartPoint(chart).y < 68 }" :style="{ left: `${chartPoint(chart).x / 720 * 100}%`, top: `${chartPoint(chart).y / 184 * 100}%` }">
                    <time>{{ formatChartDate(chartPoint(chart).date) }}</time>
                    <p><i aria-hidden="true"></i><span>{{ chart.label }}</span><strong>{{ formatChartValue(chartPoint(chart).value) }}</strong></p>
                  </div>
                </div>
                <div class="metric-x-axis" aria-hidden="true"><span v-for="point in chart.dates" :key="point.date" :style="{ left: `${point.x / 720 * 100}%` }">{{ dateLabel(point.date) }}</span></div>
              </div>
              <footer class="metric-report-meta">
                <dl><div><dt>今日</dt><dd>{{ chart.latest }}</dd></div><div><dt>日均</dt><dd>{{ chart.average }}</dd></div><div><dt>峰值</dt><dd>{{ chart.peak }}</dd></div></dl>
                <div class="metric-comparison" :class="`trend-${chart.trend.direction}`"><span>{{ chart.trend.label }}</span><strong>{{ chart.trend.value }}</strong></div>
              </footer>
            </section>
          </div>
        </section>
      </div>
      <section class="notification-card analytics-notification-card"><header><div><h4>通知中心</h4><small>举报、纠错、导入失败和安全事项</small></div><button @click="readAll">全部已读</button></header><div class="notification-list"><button v-for="item in notifications.items" :key="item.id" :class="{ unread: !item.read }" @click="readNotification(item)"><span>{{ item.type }}</span><strong>{{ item.title }}</strong><p>{{ item.message }}</p><small>{{ date(item.createdAt) }}</small></button><p v-if="!notifications.items.length" class="table-empty">暂无通知</p></div></section>
    </section>

    <section v-else-if="activeTab === 'brands'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><h3>品牌资料中心</h3><p>统一官网、Logo、别名与归档状态；重命名会同步鼠标资产。</p></div><button class="toolbar-action" @click="openBrandCreate">新建品牌</button></div>
      <div class="brand-layout"><div class="brand-list"><button v-for="brand in brands" :key="brand.id" @click="editBrand(brand)"><span v-if="brand.logoUrl" class="brand-mark"><img :src="brand.logoUrl" alt=""></span><span v-else class="brand-mark text">{{ brand.name.slice(0, 2).toUpperCase() }}</span><span><strong>{{ brand.name }}</strong><small>{{ brand.mouseCount }} 款鼠标 · {{ statusLabel(brand.status) }}</small></span><em>编辑品牌</em></button></div></div>
      <AdminFloatingPanel :open="brandEditorOpen" :title="brandForm.id ? '编辑品牌' : '新建品牌'" subtitle="品牌资料保存后会同步用于鼠标资产筛选。" :busy="loading" @close="closeBrandEditor">
        <form id="brand-floating-form" class="brand-editor brand-editor-floating" @submit.prevent="saveBrand"><label>品牌名称<input v-model.trim="brandForm.name" maxlength="80" required></label><label>官方网站<input v-model.trim="brandForm.officialUrl" type="url" placeholder="https://"></label><label>Logo 地址<input v-model.trim="brandForm.logoUrl" type="url" placeholder="https://"></label><label>品牌别名<input v-model.trim="brandForm.aliases" placeholder="多个别名用逗号分隔"></label><label>状态<select v-model="brandForm.status"><option value="ACTIVE">正常</option><option value="ARCHIVED">归档</option></select></label><label class="wide">运营备注<textarea v-model.trim="brandForm.notes" maxlength="1000"></textarea></label></form>
        <template #footer><div class="expansion-modal-actions"><button type="button" class="button button-ghost" :disabled="loading" @click="closeBrandEditor">取消编辑</button><button class="button" form="brand-floating-form" :disabled="loading">{{ loading ? '正在保存…' : brandForm.id ? '保存品牌修改' : '创建品牌' }}</button></div></template>
      </AdminFloatingPanel>
    </section>

    <section v-else-if="activeTab === 'feedback'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><h3>前台反馈与数据纠错</h3><p>处理网站反馈和鼠标数据问题；支撑记录举报请在“支撑记录”中处理。</p></div></div>
      <div class="toolbar"><div class="toolbar-search"><span>⌕</span><input v-model="reportQuery" placeholder="搜索提交人、分类或说明…" @keyup.enter="loadReports(1)"></div><select v-model="reportType" @change="loadReports(1)"><option value="SITE,MOUSE">全部反馈</option><option value="SITE">前台反馈</option><option value="MOUSE">数据纠错</option></select><select v-model="reportStatus" @change="loadReports(1)"><option value="">全部状态</option><option value="OPEN">待处理</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已解决</option><option value="REJECTED">已驳回</option></select></div>
      <div class="report-board"><article v-for="report in reports.items" :key="report.id" class="report-row" :class="[`report-${report.status.toLowerCase()}`, `report-priority-${reportPriority(report)}`]" :aria-label="`${reportTypeLabel(report.targetType)}，${report.targetLabel}，${statusLabel(report.status)}，${reportPriorityLabel(report)}`"><header class="report-lead"><span class="report-status-mark" aria-hidden="true">{{ reportStatusIcon(report.status) }}</span><div><span class="report-type">{{ reportTypeLabel(report.targetType) }}</span><h4>{{ report.targetLabel }}</h4></div></header><div class="report-summary"><p>{{ report.description }}</p><div class="report-tags"><em class="report-priority">{{ reportPriorityLabel(report) }}</em><em class="report-category">{{ reportCategoryLabel(report.category) }}</em></div></div><dl><div><dt>状态</dt><dd><em class="report-status">{{ reportStatusIcon(report.status) }} {{ statusLabel(report.status) }}</em></dd></div><div><dt>提交人</dt><dd>{{ report.reporterEmail }}</dd></div><div><dt>提交时间</dt><dd>{{ date(report.createdAt) }}</dd></div></dl><button class="button button-ghost report-open-action" @click="openReport(report)">处理工单</button></article><p v-if="!reports.items.length" class="table-empty">暂无反馈工单</p></div>
      <AdminFloatingPanel :open="Boolean(selectedReport)" title="处理反馈工单" :subtitle="selectedReport ? `${selectedReport.targetLabel} · ${selectedReport.reporterEmail}` : ''" :busy="loading" @close="selectedReport = null">
        <section v-if="selectedReport" class="report-modal-content"><div class="report-original"><span>{{ reportTypeLabel(selectedReport.targetType) }} · {{ reportCategoryLabel(selectedReport.category) }}</span><p>{{ selectedReport.description }}</p></div><div class="report-workflow report-workflow-floating"><label>处理状态<select v-model="reportDrafts[selectedReport.id].status"><option value="OPEN">待处理</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已解决</option><option value="REJECTED">已驳回</option></select></label><label>负责人<input v-model.trim="reportDrafts[selectedReport.id].assigneeEmail" placeholder="默认当前管理员"></label><label class="wide">处理结论<textarea v-model.trim="reportDrafts[selectedReport.id].resolution" maxlength="1000"></textarea></label></div></section>
        <template #footer><div v-if="selectedReport" class="expansion-modal-actions"><button type="button" class="button button-ghost" :disabled="loading" @click="selectedReport = null">取消处理</button><button class="button" :disabled="loading" @click="saveReport(selectedReport)">{{ loading ? '正在保存…' : '保存处理结果' }}</button></div></template>
      </AdminFloatingPanel>
    </section>

    <section v-else-if="activeTab === 'operations'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><h3>系统运营</h3><p>数据流转、账号会话和前台运行开关集中管理。</p></div></div>
      <div class="operations-grid">
        <section class="ops-card"><header><div><h4>数据导出</h4><small>CSV 统一使用 UTF-8 BOM</small></div></header><div class="export-actions"><button @click="exportData('mice')">导出鼠标</button><button @click="exportData('users')">导出用户</button><button @click="exportData('reviews')">导出支撑记录</button><button @click="exportData('audit')">导出审计</button></div></section>
        <section class="ops-card settings-card"><header><div><h4>前台运行设置</h4><small>保存后会实时同步到已打开的前台页面</small></div></header><label v-for="item in generalSettings" :key="item.key"><span><strong>{{ settingLabel(item.key) }}</strong><small>{{ item.description }}</small></span><select v-if="item.key.endsWith('.enabled')" v-model="settingDrafts[item.key]"><option value="true">开启</option><option value="false">关闭</option></select><input v-else v-model="settingDrafts[item.key]"><button @click="saveSetting(item)">保存</button></label></section>
        <section class="ops-card wide-card"><header><div><h4>导入历史</h4><small>保留预检结果、错误报告和最终写入数量</small></div></header><table class="admin-table"><thead><tr><th>文件</th><th>状态</th><th>数据量</th><th>操作人</th><th>时间</th><th></th></tr></thead><tbody><tr v-for="item in imports.items" :key="item.checksum"><td><strong>{{ item.filename }}</strong><small class="mono">{{ item.checksum.slice(0, 12) }}</small></td><td><em>{{ statusLabel(item.status) }}</em></td><td>{{ item.totalCount || 0 }} 行 · +{{ item.createdCount || 0 }} / ↻{{ item.updatedCount || 0 }}</td><td>{{ item.actorEmail }}</td><td>{{ date(item.completedAt || item.createdAt) }}</td><td><button v-if="item.hasErrorReport" @click="download(`/admin/mice/imports/${item.checksum}/errors`, `import-errors-${item.checksum}.csv`)">下载错误</button></td></tr></tbody></table></section>
        <section class="ops-card wide-card"><header><div><h4>登录会话</h4><small>查看最后活动并按设备撤销访问</small></div><div class="session-filter"><input v-model="sessionQuery" class="session-search" placeholder="搜索邮箱" aria-label="搜索登录会话邮箱" @keyup.enter="loadSessions(1)"><label class="session-active-toggle"><input v-model="sessionActiveOnly" type="checkbox" @change="loadSessions(1)"><span class="session-check-indicator" aria-hidden="true"></span><span>仅活跃</span></label></div></header><table class="admin-table"><thead><tr><th>用户</th><th>设备</th><th>网络</th><th>最后活动</th><th>到期</th><th></th></tr></thead><tbody><tr v-for="session in sessions.items" :key="session.id"><td><strong>{{ session.userEmail }}</strong><small>{{ session.active ? '活跃' : '已失效' }}</small></td><td class="session-agent">{{ session.userAgent || '未知设备' }}</td><td class="mono">{{ session.ipAddress || '-' }}</td><td>{{ date(session.lastUsedAt || session.createdAt) }}</td><td>{{ date(session.expiresAt) }}</td><td><button v-if="session.active" class="danger-link" @click="revokeSession(session)">强制下线</button></td></tr></tbody></table></section>
      </div>
    </section>
    <div v-if="loading" class="expansion-loading">正在同步运营数据…</div>
  </div>
  <AdminActionDialog :config="actionDialog" :busy="loading" @confirm="confirmAdminAction" @close="cancelAdminAction" />
</template>

<style scoped>
.expansion-shell{position:relative;color:var(--dv-text)}.expansion-panel{min-height:620px}.expansion-heading{align-items:flex-start}.expansion-heading p{margin:.35rem 0 0;color:var(--dv-muted);max-width:580px}.signal-cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:20px 0}.signal-cards article{padding:18px;border-radius:16px;background:var(--dv-surface-high);border:1px solid var(--dv-border)}.signal-cards span{display:block;font-size:12px;color:var(--dv-muted)}.signal-cards strong{color:var(--dv-text);font-size:30px;line-height:1.2}.analytics-grid{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(300px,.75fr);gap:18px}.trend-card,.notification-card,.ops-card{border:1px solid var(--dv-border);border-radius:18px;background:var(--dv-surface);padding:20px;color:var(--dv-text)}.trend-card header,.notification-card header,.ops-card header{display:flex;justify-content:space-between;gap:12px;align-items:center}.trend-card h4,.notification-card h4,.ops-card h4{margin:0}.trend-card header small,.notification-card header small,.ops-card header small{color:var(--dv-muted)}.trend-chart{height:260px;display:flex;align-items:flex-end;gap:4px;margin-top:24px;border-bottom:1px solid var(--dv-outline)}.trend-column{height:100%;flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;min-width:4px}.trend-column i{display:block;width:80%;min-height:3px;background:var(--dv-primary);border-radius:5px 5px 0 0}.trend-column span{font-size:8px;color:var(--dv-muted);writing-mode:vertical-rl;margin-top:4px}.notification-list{display:grid;gap:8px;margin-top:16px;max-height:330px;overflow:auto}.notification-list button{text-align:left;border:1px solid transparent;border-radius:12px;padding:12px;background:var(--dv-surface-high);color:var(--dv-text)}.notification-list button.unread{border-color:var(--dv-primary-line);background:var(--dv-primary-soft)}.notification-list span,.notification-list small{font-size:10px;color:var(--dv-muted)}.notification-list strong,.notification-list p{display:block;margin:3px 0}.brand-layout{display:block}.brand-editor{display:grid;grid-template-columns:1fr 1fr;gap:14px;padding:20px;background:var(--dv-surface-high);border-radius:18px}.brand-editor label,.report-workflow label{display:grid;gap:6px;font-size:12px;font-weight:700}.brand-editor .wide,.report-workflow .wide{grid-column:1/-1}.brand-editor textarea{min-height:90px}.brand-editor .form-actions{grid-column:1/-1}.brand-editor-floating{padding:0;border-radius:0;background:transparent}.brand-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:9px;align-content:start}.brand-list>button{display:grid;grid-template-columns:46px 1fr auto;align-items:center;gap:12px;text-align:left;border:1px solid var(--dv-border);border-radius:14px;background:var(--dv-surface);color:var(--dv-text);padding:12px}.brand-list strong,.brand-list small{display:block}.brand-list small,.brand-list em{color:var(--dv-muted)}.brand-mark{width:42px;height:42px;border-radius:12px;background:var(--dv-surface-highest);display:grid;place-items:center;overflow:hidden}.brand-mark img{width:100%;height:100%;object-fit:contain}.report-board{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}.report-board article{border:1px solid var(--dv-border);border-radius:18px;padding:18px;background:var(--dv-surface);color:var(--dv-text)}.report-board article>header{display:flex;justify-content:space-between}.report-board h4{margin:5px 0}.report-board dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.report-board dt{font-size:10px;color:var(--dv-muted)}.report-board dd{margin:2px 0;font-size:12px}.report-workflow{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding-top:12px;border-top:1px solid var(--dv-border)}.report-workflow button{justify-self:start}.report-workflow-floating{padding-top:0;border-top:0}.report-open-action{margin-top:14px}.report-original{margin-bottom:18px;padding:14px;border:1px solid var(--dv-border);border-radius:10px;background:var(--dv-surface-high)}.report-original span{color:var(--dv-muted);font-size:11px}.report-original p{margin:6px 0 0;line-height:1.55}.expansion-modal-actions{display:flex;justify-content:flex-end;gap:8px;width:100%}.operations-grid{display:grid;grid-template-columns:1fr 1.5fr;gap:16px}.wide-card{grid-column:1/-1}.export-actions{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-top:16px}.export-actions button{border:1px solid var(--dv-outline);border-radius:12px;background:var(--dv-surface-high);color:var(--dv-text);padding:16px;font-weight:700}.settings-card label{display:grid;grid-template-columns:1fr minmax(100px,180px) auto;gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid var(--dv-border)}.settings-card strong,.settings-card small{display:block}.settings-card small{color:var(--dv-muted)}.settings-card button,.session-filter button,.danger-link{color:var(--dv-text)}.session-filter{display:flex;align-items:center;gap:10px}.session-filter label{white-space:nowrap}.session-agent{max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.danger-link{color:var(--dv-error)}.expansion-loading{position:absolute;inset:0;display:grid;place-items:center;background:rgba(11,11,12,.78);color:var(--dv-text);backdrop-filter:blur(2px);font-weight:700}@media(max-width:900px){.signal-cards{grid-template-columns:1fr 1fr}.analytics-grid,.operations-grid{grid-template-columns:1fr}.report-board{grid-template-columns:1fr}.wide-card{grid-column:auto}.brand-editor{grid-template-columns:1fr}.brand-editor .wide{grid-column:auto}}
.brand-editor input,.brand-editor select,.brand-editor textarea,.report-workflow input,.report-workflow select,.report-workflow textarea,.settings-card input,.settings-card select,.session-filter input{width:100%;min-height:42px;padding:9px 11px;border:1px solid var(--dv-outline);border-radius:8px;outline:0;background:var(--dv-background);color:var(--dv-text);color-scheme:dark}.brand-editor textarea,.report-workflow textarea{resize:vertical}.brand-editor input:focus,.brand-editor select:focus,.brand-editor textarea:focus,.report-workflow input:focus,.report-workflow select:focus,.report-workflow textarea:focus,.settings-card input:focus,.settings-card select:focus,.session-filter input:focus{border-color:var(--dv-primary);box-shadow:0 0 0 3px var(--dv-primary-soft)}.brand-editor input::placeholder,.report-workflow input::placeholder,.session-filter input::placeholder{color:var(--dv-muted)}.trend-card header button,.notification-card header button,.settings-card label>button,.ops-card .admin-table button{min-height:38px;padding:7px 10px;border:1px solid var(--dv-outline);border-radius:8px;background:var(--dv-surface-high);color:var(--dv-text)}
@media(max-width:600px){.expansion-panel{min-height:auto}.signal-cards article,.trend-card,.notification-card,.ops-card,.brand-editor,.report-board article{padding:14px;border-radius:12px}.signal-cards strong{font-size:24px}.trend-card,.ops-card{overflow-x:auto}.trend-card header,.notification-card header,.ops-card header{align-items:flex-start;flex-direction:column}.trend-chart{width:520px;height:210px}.brand-list>button{grid-template-columns:42px 1fr}.brand-list>button>em{grid-column:2}.report-board dl{grid-template-columns:1fr 1fr}.report-workflow{grid-template-columns:1fr}.report-workflow .wide{grid-column:auto}.report-workflow button{width:100%}.settings-card label{grid-template-columns:1fr;align-items:stretch}.settings-card label button{width:100%}.session-filter{width:100%;align-items:stretch;flex-wrap:wrap}.session-filter>input{width:100%}.ops-card .admin-table{display:block;width:calc(100% + 28px);margin:0 -14px -14px;padding:0 14px 10px;overflow-x:auto}.export-actions{grid-template-columns:1fr}}
</style>

<style scoped>
.notification-list { max-height: none; overflow: visible; }
.signal-cards {
  display: flex;
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--dv-border);
  border-radius: 12px;
  background: var(--dv-surface);
}
.signal-cards article {
  flex: 1 1 0;
  padding: 14px 18px;
  border: 0;
  border-right: 1px solid var(--dv-border);
  border-radius: 0;
  background: transparent;
}
.signal-cards article:last-child { border-right: 0; }
.signal-cards strong { margin-top: 4px; font: 700 1.35rem/1.2 var(--dv-mono); }
.session-filter .session-search {
  flex: 0 1 220px;
  width: 220px;
}
.session-active-toggle {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
  min-height: 42px;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--dv-outline);
  border-radius: 8px;
  background: var(--dv-surface-high);
  color: var(--dv-text-soft);
  font-size: .72rem;
  cursor: pointer;
  user-select: none;
}
.session-filter .session-active-toggle input {
  position: absolute;
  width: 1px;
  height: 1px;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 0;
  opacity: 0;
  clip-path: inset(50%);
}
.session-check-indicator {
  display: grid;
  flex: 0 0 16px;
  width: 16px;
  height: 16px;
  place-items: center;
  border: 1px solid #666;
  border-radius: 4px;
  background: #111;
  color: #111;
  font: 800 .62rem/1 var(--dv-mono);
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease;
}
.session-active-toggle input:checked + .session-check-indicator {
  border-color: #dedede;
  background: #dedede;
  color: #111;
}
.session-active-toggle input:checked + .session-check-indicator::after { content: '✓'; }
.session-active-toggle:hover { border-color: #686868; color: var(--dv-text); }
.session-active-toggle input:focus-visible + .session-check-indicator {
  outline: 2px solid var(--dv-primary-bright);
  outline-offset: 2px;
}
.session-active-toggle input:disabled ~ span { opacity: .48; cursor: not-allowed; }
.analytics-report {
  display: grid;
  gap: 20px;
  margin-top: 18px;
}
.analytics-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 2px 9px;
  border-bottom: 1px solid var(--dv-border);
}
.analytics-group-heading h4 { margin: 0; color: var(--dv-text); font-size: .86rem; }
.analytics-group-heading p { margin: 3px 0 0; color: var(--dv-muted); font-size: .66rem; }
.analytics-group-heading > span { color: var(--dv-muted); font: 500 .62rem var(--dv-mono); white-space: nowrap; }
.analytics-metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}
.metric-report-row {
  --chart-accent: var(--dv-primary);
  display: grid;
  grid-template-rows: auto auto auto;
  min-width: 0;
  padding: 14px 14px 11px;
  border: 1px solid var(--dv-border);
  border-radius: 12px;
  background: var(--dv-surface);
  color: var(--dv-text);
}
.metric-report-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 12px;
  min-width: 0;
}
.metric-report-title { display: flex; min-width: 0; align-items: flex-start; gap: 8px; }
.metric-report-title > i { flex: 0 0 13px; width: 13px; height: 2px; margin-top: 7px; border-radius: 1px; background: var(--chart-accent); }
.metric-report-title h4 { margin: 0; color: var(--dv-text); font-size: .88rem; }
.metric-report-title p { overflow: hidden; margin: 3px 0 0; color: var(--dv-muted); font-size: .65rem; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.metric-total { min-width: 86px; text-align: right; }
.metric-total span { display: block; color: var(--dv-muted); font-size: .58rem; }
.metric-total strong { display: block; margin-top: 3px; color: var(--chart-accent); font: 700 1.2rem/1 var(--dv-mono); }
.metric-report-meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 7px;
  padding-top: 9px;
  border-top: 1px solid var(--dv-border);
}
.metric-report-meta dl { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; }
.metric-report-meta dl > div { display: flex; min-width: 0; align-items: baseline; gap: 5px; }
.metric-report-meta dt { color: var(--dv-muted); font-size: .58rem; white-space: nowrap; }
.metric-report-meta dd { overflow: hidden; margin: 0; color: var(--dv-text-soft); font: 650 .7rem var(--dv-mono); text-overflow: ellipsis; }
.metric-comparison { display: flex; align-items: center; gap: 6px; }
.metric-comparison span { color: var(--dv-muted); font-size: .65rem; }
.metric-comparison strong { color: var(--dv-text-soft); font: 650 .72rem var(--dv-mono); }
.metric-comparison.trend-up strong::before { content: '↑ '; }
.metric-comparison.trend-down strong::before { content: '↓ '; }
.metric-plot {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  grid-template-rows: 148px 22px;
  min-width: 0;
  margin-top: 12px;
}
.metric-y-axis { position: relative; min-width: 0; }
.metric-y-axis span {
  position: absolute;
  right: 9px;
  color: #8f8f8f;
  font: 500 .58rem/1 var(--dv-mono);
  transform: translateY(-50%);
}
.metric-plot-canvas {
  position: relative;
  min-width: 0;
  height: 148px;
  outline: none;
  cursor: crosshair;
  touch-action: pan-y;
}
.metric-plot-canvas:focus-visible { box-shadow: inset 0 0 0 1px #777; }
.metric-plot-canvas svg { display: block; width: 100%; height: 148px; overflow: visible; }
.metric-grid-line { stroke: #292929; stroke-width: 1; vector-effect: non-scaling-stroke; }
.metric-line {
  fill: none;
  stroke: var(--chart-accent);
  stroke-width: 2.4;
  stroke-linecap: butt;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}
.metric-hover-guide {
  stroke: #6c6c6c;
  stroke-width: 1;
  stroke-dasharray: 3 3;
  pointer-events: none;
  vector-effect: non-scaling-stroke;
}
.metric-hover-point {
  fill: var(--chart-accent);
  stroke: #111;
  stroke-width: 3;
  pointer-events: none;
  vector-effect: non-scaling-stroke;
}
.metric-bar { fill: var(--chart-accent); opacity: .74; transition: opacity 160ms ease; }
.metric-bar:hover { opacity: 1; }
.metric-tooltip {
  position: absolute;
  z-index: 4;
  min-width: 178px;
  padding: 11px 12px;
  border-radius: 8px;
  background: #f0f0f0;
  box-shadow: 0 6px 8px rgba(0, 0, 0, .46);
  color: #222;
  transform: translate(-50%, calc(-100% - 10px));
  pointer-events: none;
}
.metric-tooltip.left { transform: translate(0, calc(-100% - 10px)); }
.metric-tooltip.right { transform: translate(-100%, calc(-100% - 10px)); }
.metric-tooltip.below { transform: translate(-50%, 10px); }
.metric-tooltip.left.below { transform: translate(0, 10px); }
.metric-tooltip.right.below { transform: translate(-100%, 10px); }
.metric-tooltip time { display: block; color: #777; font-size: .65rem; white-space: nowrap; }
.metric-tooltip p { display: grid; grid-template-columns: 7px minmax(0, 1fr) auto; align-items: center; gap: 7px; margin: 9px 0 0; }
.metric-tooltip p i { width: 7px; height: 7px; border-radius: 50%; background: var(--chart-accent); }
.metric-tooltip p span { color: #3b3b3b; font-size: .68rem; white-space: nowrap; }
.metric-tooltip p strong { color: #222; font: 700 .72rem/1 var(--dv-mono); }
.metric-x-axis { position: relative; grid-column: 2; min-width: 0; border-top: 1px solid var(--dv-outline); }
.metric-x-axis span {
  position: absolute;
  top: 7px;
  color: #8f8f8f;
  font: 500 .57rem/1 var(--dv-mono);
  white-space: nowrap;
  transform: translateX(-50%);
}
.metric-x-axis span:first-child { transform: none; }
.metric-x-axis span:last-child { transform: translateX(-100%); }
.analytics-notification-card { margin-top: 18px; }
.expansion-loading { background: rgba(11, 11, 11, .78); }
@media (prefers-reduced-motion: reduce) { .metric-bar { transition: none; } }
@media (max-width: 980px) {
  .analytics-metric-grid { grid-template-columns: 1fr; }
}
@media (max-width: 700px) {
  .signal-cards { display: grid; grid-template-columns: 1fr 1fr; }
  .signal-cards article:nth-child(2) { border-right: 0; }
  .signal-cards article:nth-child(-n + 2) { border-bottom: 1px solid var(--dv-border); }
  .analytics-group-heading { align-items: flex-start; }
  .metric-report-row { padding: 13px 12px 10px; }
  .metric-report-meta { grid-template-columns: 1fr; gap: 7px; }
  .metric-comparison { justify-content: space-between; }
  .metric-x-axis span:nth-child(even):not(:last-child) { display: none; }
}
@media (max-width: 440px) {
  .metric-report-title p { white-space: normal; }
  .metric-report-meta dl { gap: 7px; }
  .metric-report-meta dl > div { display: block; }
  .metric-report-meta dd { margin-top: 2px; }
}
@media (prefers-reduced-motion: reduce) {
  .session-check-indicator { transition: none; }
}
.expansion-shell .signal-cards article,
.expansion-shell .trend-card,
.expansion-shell .notification-card,
.expansion-shell .ops-card,
.expansion-shell .brand-list > button,
.expansion-shell .report-board article,
.expansion-shell .analytics-report-group { border-radius: 10px; box-shadow: none; }
.expansion-shell .operations-grid { gap: 12px; }
.expansion-shell .ops-card { padding: 18px; }
.expansion-shell .export-actions button { border-radius: 8px; padding: 13px; }
.expansion-shell .expansion-loading { backdrop-filter: none; }
.expansion-shell .report-board {
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
}
.expansion-shell .report-board article {
  display: grid;
  grid-template-columns: minmax(210px, 1.05fr) minmax(240px, 1.35fr) minmax(360px, 1.4fr) auto;
  align-items: center;
  gap: 20px;
  padding: 16px 18px;
}
.expansion-shell .report-board article > header { gap: 12px; }
.expansion-shell .report-board article > p { margin: 0; line-height: 1.55; }
.expansion-shell .report-board dl { gap: 12px; margin: 0; }
.expansion-shell .report-board dd { overflow: hidden; text-overflow: ellipsis; }
.expansion-shell .report-open-action { margin-top: 0; white-space: nowrap; }
.expansion-shell .report-row {
  --report-color: var(--admin-accent, var(--dv-primary));
  --report-soft: rgba(220, 229, 223, .08);
  --priority-color: var(--admin-muted, var(--dv-muted));
  --priority-soft: transparent;
  transition: border-color 160ms ease, background-color 160ms ease;
}
.expansion-shell .report-row:hover { border-color: color-mix(in srgb, var(--report-color) 45%, var(--admin-border, #2d3339)); background: var(--admin-surface-raised, var(--dv-surface-high)); }
.expansion-shell .report-row.report-open { --report-color: #f2b866; --report-soft: rgba(242, 184, 102, .10); }
.expansion-shell .report-row.report-in_progress { --report-color: #73b8ff; --report-soft: rgba(115, 184, 255, .10); }
.expansion-shell .report-row.report-resolved { --report-color: #73d49a; --report-soft: rgba(115, 212, 154, .10); }
.expansion-shell .report-row.report-rejected { --report-color: #ed858d; --report-soft: rgba(237, 133, 141, .10); }
.expansion-shell .report-row.report-priority-high { --priority-color: #ed858d; --priority-soft: rgba(237, 133, 141, .10); }
.expansion-shell .report-row.report-priority-medium { --priority-color: #f2b866; --priority-soft: rgba(242, 184, 102, .10); }
.expansion-shell .report-row.report-priority-low { --priority-color: #aab3bb; --priority-soft: rgba(170, 179, 187, .07); }
.expansion-shell .report-lead { display: flex; align-items: center; justify-content: flex-start; gap: 11px; min-width: 0; }
.expansion-shell .report-status-mark { display: grid; flex: 0 0 28px; width: 28px; height: 28px; place-items: center; border: 1px solid color-mix(in srgb, var(--report-color) 58%, transparent); border-radius: 8px; background: var(--report-soft); color: var(--report-color); font: 750 .82rem/1 var(--dv-mono); }
.expansion-shell .report-type { color: var(--report-color); font-size: .66rem; font-weight: 700; }
.expansion-shell .report-row h4 { overflow: hidden; margin: 4px 0 0; color: var(--admin-text, var(--dv-text)); font-size: .82rem; text-overflow: ellipsis; white-space: nowrap; }
.expansion-shell .report-summary { min-width: 0; }
.expansion-shell .report-summary > p { display: -webkit-box; overflow: hidden; margin: 0; color: var(--admin-text-soft, var(--dv-text-soft)); line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.expansion-shell .report-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
.expansion-shell .report-tags em { display: inline-flex; align-items: center; width: fit-content; padding: 3px 7px; border: 1px solid color-mix(in srgb, var(--report-color) 42%, transparent); border-radius: 999px; background: var(--report-soft); color: var(--report-color); font-size: .62rem; font-style: normal; white-space: nowrap; }
.expansion-shell .report-tags .report-priority { border-color: color-mix(in srgb, var(--priority-color) 46%, transparent); background: var(--priority-soft); color: var(--priority-color); }
.expansion-shell .report-tags .report-category { border-color: var(--admin-border, var(--dv-border)); background: transparent; color: var(--admin-muted, var(--dv-muted)); }
.expansion-shell .report-row dt { color: var(--admin-muted, var(--dv-muted)); font-size: .6rem; }
.expansion-shell .report-row dd { overflow: hidden; margin: 3px 0 0; color: var(--admin-text-soft, var(--dv-text-soft)); font-size: .68rem; text-overflow: ellipsis; white-space: nowrap; }
.expansion-shell .report-row .report-status { display: inline-flex; align-items: center; gap: 4px; width: fit-content; padding: 3px 6px; border: 1px solid color-mix(in srgb, var(--report-color) 52%, transparent); border-radius: 999px; background: var(--report-soft); color: var(--report-color); font-size: .64rem; font-style: normal; }
.expansion-shell .report-open-action { justify-self: end; }
@media (max-width: 1400px) {
  .expansion-shell .report-board article { grid-template-columns: minmax(210px, 1fr) minmax(260px, 1.4fr) auto; }
  .expansion-shell .report-row .report-summary { grid-column: 1 / 3; grid-row: 2; }
  .expansion-shell .report-row dl { grid-column: 1 / 3; grid-row: 3; }
  .expansion-shell .report-open-action { grid-column: 3; grid-row: 1 / 4; }
}
@media (max-width: 900px) {
  .expansion-shell .report-board article { grid-template-columns: 1fr; }
  .expansion-shell .report-row .report-summary,
  .expansion-shell .report-row dl,
  .expansion-shell .report-open-action { grid-column: auto; grid-row: auto; }
  .expansion-shell .report-open-action { justify-self: start; }
}
</style>
