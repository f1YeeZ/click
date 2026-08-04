<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import api, { errorMessage } from '../api/client'
import AdminFloatingPanel from './AdminFloatingPanel.vue'

const props = defineProps({ activeTab: { type: String, required: true } })
const loading = ref(false)
const error = ref('')
const notice = ref('')
const analytics = ref(null)
const analyticsDays = ref(30)
const notifications = ref({ items: [], page: {} })
const brands = ref([])
const brandForm = reactive({ id: '', name: '', officialUrl: '', logoUrl: '', aliases: '', notes: '', status: 'ACTIVE' })
const brandEditorOpen = ref(false)
const reports = ref({ items: [], page: {} })
const reportStatus = ref('')
const reportType = ref('')
const reportQuery = ref('')
const reportDrafts = reactive({})
const selectedReport = ref(null)
const imports = ref({ items: [], page: {} })
const sessions = ref({ items: [], page: {} })
const sessionQuery = ref('')
const sessionActiveOnly = ref(true)
const settings = ref([])
const settingDrafts = reactive({})
const expansionTabs = new Set(['analytics', 'brands', 'feedback', 'operations'])
const maxTrend = computed(() => Math.max(1, ...(analytics.value?.points || []).map(point => point.users + point.mice + point.reviews + point.adminActions)))

const run = async fn => {
  loading.value = true; error.value = ''
  try { await fn() } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
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
  notice.value = '品牌资料已保存'; await loadBrands(); closeBrandEditor()
})
const loadReports = (page = 1) => run(async () => {
  reports.value = (await api.get('/admin/reports', { params: { q: reportQuery.value || undefined, status: reportStatus.value || undefined, targetType: reportType.value || undefined, page } })).data
  reports.value.items.forEach(item => { reportDrafts[item.id] = { status: item.status, assigneeEmail: item.assigneeEmail || '', resolution: item.resolution || '' } })
})
const saveReport = report => run(async () => {
  await api.patch(`/admin/reports/${report.id}`, reportDrafts[report.id]); notice.value = '反馈工单已更新'; await loadReports(reports.value.page.number || 1); selectedReport.value = null
})
const openReport = report => { selectedReport.value = report }
const loadOperations = () => run(async () => {
  const [importData, sessionData, settingData] = await Promise.all([
    api.get('/admin/mice/imports'), api.get('/admin/sessions', { params: { activeOnly: sessionActiveOnly.value } }), api.get('/admin/settings')
  ])
  imports.value = importData.data; sessions.value = sessionData.data; settings.value = settingData.data
  settings.value.forEach(item => { settingDrafts[item.key] = item.value })
})
const loadSessions = (page = 1) => run(async () => { sessions.value = (await api.get('/admin/sessions', { params: { q: sessionQuery.value || undefined, activeOnly: sessionActiveOnly.value, page } })).data })
const revokeSession = session => run(async () => {
  if (!window.confirm(`确定让 ${session.userEmail} 的该会话立即失效吗？`)) return
  await api.delete(`/admin/sessions/${session.id}`); notice.value = '会话已撤销'; await loadSessions(sessions.value.page.number || 1)
})
const saveSetting = item => run(async () => {
  await api.put(`/admin/settings/${encodeURIComponent(item.key)}`, { value: String(settingDrafts[item.key]) }); notice.value = '系统设置已生效'; await loadOperations()
})
const readNotification = item => run(async () => { await api.patch(`/admin/notifications/${item.id}/read`); await loadAnalytics() })
const readAll = () => run(async () => { await api.post('/admin/notifications/read-all'); await loadAnalytics() })
const download = async (url, filename) => {
  try { const { data } = await api.get(url, { responseType: 'blob' }); const href = URL.createObjectURL(data); const link = document.createElement('a'); link.href = href; link.download = filename; link.click(); URL.revokeObjectURL(href) }
  catch (e) { error.value = errorMessage(e) }
}
const exportData = type => download(`/admin/exports/${type}`, `clicker-${type}.csv`)
const settingLabel = key => ({ 'maintenance.notice': '前台维护公告', 'registration.enabled': '开放用户注册', 'reviews.enabled': '开放评价提交', 'upload.max-mb': '图片上传提示上限（MB）', 'verification.stale-days': '数据核验过期天数', 'security.session-days': '会话有效天数提示' }[key] || key)
const statusLabel = value => ({ ACTIVE: '正常', ARCHIVED: '已归档', OPEN: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已解决', REJECTED: '已驳回', PREVIEW_READY: '预检通过', PREVIEW_FAILED: '预检失败', COMPLETED: '已导入' }[value] || value)
const date = value => value ? new Date(value).toLocaleString('zh-CN') : '—'
const loadActive = () => ({ analytics: loadAnalytics, brands: loadBrands, feedback: loadReports, operations: loadOperations }[props.activeTab]?.())
const refreshListener = event => { if (event.detail === props.activeTab) loadActive() }
watch(() => props.activeTab, value => {
  brandEditorOpen.value = false
  selectedReport.value = null
  if (expansionTabs.has(value)) loadActive()
}, { immediate: true })
onMounted(() => window.addEventListener('admin:refresh', refreshListener))
onBeforeUnmount(() => window.removeEventListener('admin:refresh', refreshListener))
</script>

<template>
  <div class="expansion-shell">
    <div class="admin-toast-stack"><div v-if="notice" class="flash success">{{ notice }}</div><div v-if="error" class="flash error">{{ error }}</div></div>

    <section v-if="activeTab === 'analytics'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><span class="panel-kicker">OPERATIONS PULSE</span><h3>运营分析与通知</h3><p>把增长、治理与安全信号放到同一条时间线上。</p></div><select v-model.number="analyticsDays" @change="loadAnalytics"><option :value="7">近 7 天</option><option :value="14">近 14 天</option><option :value="30">近 30 天</option><option :value="90">近 90 天</option></select></div>
      <div class="signal-cards"><article><span>待处理反馈</span><strong>{{ analytics?.openReports ?? '—' }}</strong></article><article><span>未读通知</span><strong>{{ analytics?.unreadNotifications ?? '—' }}</strong></article><article><span>活跃会话</span><strong>{{ analytics?.activeSessions ?? '—' }}</strong></article><article><span>过期数据</span><strong>{{ analytics?.staleMice ?? '—' }}</strong></article></div>
      <div class="analytics-grid">
        <section class="trend-card"><header><h4>每日运营脉冲</h4><small>用户 / 鼠标 / 评价 / 管理操作</small></header><div class="trend-chart"><div v-for="point in analytics?.points || []" :key="point.date" class="trend-column" :title="`${point.date}：用户 ${point.users}，鼠标 ${point.mice}，评价 ${point.reviews}，操作 ${point.adminActions}`"><i :style="{ height: `${Math.max(3, (point.users + point.mice + point.reviews + point.adminActions) / maxTrend * 100)}%` }"></i><span>{{ point.date.slice(5) }}</span></div></div></section>
        <section class="notification-card"><header><div><h4>通知中心</h4><small>举报、纠错、导入失败和安全事项</small></div><button @click="readAll">全部已读</button></header><div class="notification-list"><button v-for="item in notifications.items" :key="item.id" :class="{ unread: !item.read }" @click="readNotification(item)"><span>{{ item.type }}</span><strong>{{ item.title }}</strong><p>{{ item.message }}</p><small>{{ date(item.createdAt) }}</small></button><p v-if="!notifications.items.length" class="table-empty">暂无通知</p></div></section>
      </div>
    </section>

    <section v-else-if="activeTab === 'brands'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><span class="panel-kicker">BRAND REGISTRY</span><h3>品牌资料中心</h3><p>统一官网、Logo、别名与归档状态；重命名会同步鼠标资产。</p></div><button class="toolbar-action" @click="openBrandCreate">新建品牌</button></div>
      <div class="brand-layout"><div class="brand-list"><button v-for="brand in brands" :key="brand.id" @click="editBrand(brand)"><span v-if="brand.logoUrl" class="brand-mark"><img :src="brand.logoUrl" alt=""></span><span v-else class="brand-mark text">{{ brand.name.slice(0, 2).toUpperCase() }}</span><span><strong>{{ brand.name }}</strong><small>{{ brand.mouseCount }} 款鼠标 · {{ statusLabel(brand.status) }}</small></span><em>编辑品牌</em></button></div></div>
      <AdminFloatingPanel :open="brandEditorOpen" :title="brandForm.id ? '编辑品牌' : '新建品牌'" subtitle="品牌资料保存后会同步用于鼠标资产筛选。" :busy="loading" @close="closeBrandEditor">
        <form id="brand-floating-form" class="brand-editor brand-editor-floating" @submit.prevent="saveBrand"><label>品牌名称<input v-model.trim="brandForm.name" maxlength="80" required></label><label>官方网站<input v-model.trim="brandForm.officialUrl" type="url" placeholder="https://"></label><label>Logo 地址<input v-model.trim="brandForm.logoUrl" type="url" placeholder="https://"></label><label>品牌别名<input v-model.trim="brandForm.aliases" placeholder="多个别名用逗号分隔"></label><label>状态<select v-model="brandForm.status"><option value="ACTIVE">正常</option><option value="ARCHIVED">归档</option></select></label><label class="wide">运营备注<textarea v-model.trim="brandForm.notes" maxlength="1000"></textarea></label></form>
        <template #footer><div class="expansion-modal-actions"><button type="button" class="button button-ghost" :disabled="loading" @click="closeBrandEditor">取消编辑</button><button class="button" form="brand-floating-form" :disabled="loading">{{ loading ? '正在保存…' : brandForm.id ? '保存品牌修改' : '创建品牌' }}</button></div></template>
      </AdminFloatingPanel>
    </section>

    <section v-else-if="activeTab === 'feedback'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><span class="panel-kicker">TRUST DESK</span><h3>举报与数据纠错</h3><p>从用户反馈到受理、处理和结论的完整工单闭环。</p></div></div>
      <div class="toolbar"><div class="toolbar-search"><span>⌕</span><input v-model="reportQuery" placeholder="搜索提交人、分类或说明…" @keyup.enter="loadReports(1)"></div><select v-model="reportType" @change="loadReports(1)"><option value="">全部对象</option><option value="MOUSE">数据纠错</option><option value="REVIEW">评价举报</option></select><select v-model="reportStatus" @change="loadReports(1)"><option value="">全部状态</option><option value="OPEN">待处理</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已解决</option><option value="REJECTED">已驳回</option></select></div>
      <div class="report-board"><article v-for="report in reports.items" :key="report.id" :class="`report-${report.status.toLowerCase()}`"><header><div><span>{{ report.targetType === 'MOUSE' ? '数据纠错' : '评价举报' }}</span><h4>{{ report.targetLabel }}</h4></div><em>{{ statusLabel(report.status) }}</em></header><p>{{ report.description }}</p><dl><div><dt>分类</dt><dd>{{ report.category }}</dd></div><div><dt>提交人</dt><dd>{{ report.reporterEmail }}</dd></div><div><dt>提交时间</dt><dd>{{ date(report.createdAt) }}</dd></div></dl><button class="button button-ghost report-open-action" @click="openReport(report)">处理工单</button></article><p v-if="!reports.items.length" class="table-empty">暂无反馈工单</p></div>
      <AdminFloatingPanel :open="Boolean(selectedReport)" title="处理反馈工单" :subtitle="selectedReport ? `${selectedReport.targetLabel} · ${selectedReport.reporterEmail}` : ''" :busy="loading" @close="selectedReport = null">
        <section v-if="selectedReport" class="report-modal-content"><div class="report-original"><span>{{ selectedReport.targetType === 'MOUSE' ? '数据纠错' : '评价举报' }}</span><p>{{ selectedReport.description }}</p></div><div class="report-workflow report-workflow-floating"><label>处理状态<select v-model="reportDrafts[selectedReport.id].status"><option value="OPEN">待处理</option><option value="IN_PROGRESS">处理中</option><option value="RESOLVED">已解决</option><option value="REJECTED">已驳回</option></select></label><label>负责人<input v-model.trim="reportDrafts[selectedReport.id].assigneeEmail" placeholder="默认当前管理员"></label><label class="wide">处理结论<textarea v-model.trim="reportDrafts[selectedReport.id].resolution" maxlength="1000"></textarea></label></div></section>
        <template #footer><div v-if="selectedReport" class="expansion-modal-actions"><button type="button" class="button button-ghost" :disabled="loading" @click="selectedReport = null">取消处理</button><button class="button" :disabled="loading" @click="saveReport(selectedReport)">{{ loading ? '正在保存…' : '保存处理结果' }}</button></div></template>
      </AdminFloatingPanel>
    </section>

    <section v-else-if="activeTab === 'operations'" class="admin-panel full-panel expansion-panel">
      <div class="panel-heading expansion-heading"><div><span class="panel-kicker">SYSTEM OPERATIONS</span><h3>系统运营</h3><p>数据流转、账号会话和运行开关集中管理。</p></div></div>
      <div class="operations-grid">
        <section class="ops-card"><header><div><h4>数据导出</h4><small>CSV 统一使用 UTF-8 BOM</small></div></header><div class="export-actions"><button @click="exportData('mice')">导出鼠标</button><button @click="exportData('users')">导出用户</button><button @click="exportData('reviews')">导出评价</button><button @click="exportData('audit')">导出审计</button></div></section>
        <section class="ops-card settings-card"><header><div><h4>系统设置</h4><small>保存后立即作用于注册、评价和前台公告</small></div></header><label v-for="item in settings" :key="item.key"><span><strong>{{ settingLabel(item.key) }}</strong><small>{{ item.description }}</small></span><select v-if="item.key.endsWith('.enabled')" v-model="settingDrafts[item.key]"><option value="true">开启</option><option value="false">关闭</option></select><input v-else v-model="settingDrafts[item.key]"><button @click="saveSetting(item)">保存</button></label></section>
        <section class="ops-card wide-card"><header><div><h4>导入历史</h4><small>保留预检结果、错误报告和最终写入数量</small></div></header><table class="admin-table"><thead><tr><th>文件</th><th>状态</th><th>数据量</th><th>操作人</th><th>时间</th><th></th></tr></thead><tbody><tr v-for="item in imports.items" :key="item.checksum"><td><strong>{{ item.filename }}</strong><small class="mono">{{ item.checksum.slice(0, 12) }}</small></td><td><em>{{ statusLabel(item.status) }}</em></td><td>{{ item.totalCount || 0 }} 行 · +{{ item.createdCount || 0 }} / ↻{{ item.updatedCount || 0 }}</td><td>{{ item.actorEmail }}</td><td>{{ date(item.completedAt || item.createdAt) }}</td><td><button v-if="item.hasErrorReport" @click="download(`/admin/mice/imports/${item.checksum}/errors`, `import-errors-${item.checksum}.csv`)">下载错误</button></td></tr></tbody></table></section>
        <section class="ops-card wide-card"><header><div><h4>登录会话</h4><small>查看最后活动并按设备撤销访问</small></div><div class="session-filter"><input v-model="sessionQuery" placeholder="搜索邮箱" @keyup.enter="loadSessions(1)"><label><input v-model="sessionActiveOnly" type="checkbox" @change="loadSessions(1)"> 仅活跃</label></div></header><table class="admin-table"><thead><tr><th>用户</th><th>设备</th><th>网络</th><th>最后活动</th><th>到期</th><th></th></tr></thead><tbody><tr v-for="session in sessions.items" :key="session.id"><td><strong>{{ session.userEmail }}</strong><small>{{ session.active ? '活跃' : '已失效' }}</small></td><td class="session-agent">{{ session.userAgent || '未知设备' }}</td><td class="mono">{{ session.ipAddress || '—' }}</td><td>{{ date(session.lastUsedAt || session.createdAt) }}</td><td>{{ date(session.expiresAt) }}</td><td><button v-if="session.active" class="danger-link" @click="revokeSession(session)">强制下线</button></td></tr></tbody></table></section>
      </div>
    </section>
    <div v-if="loading" class="expansion-loading">正在同步运营数据…</div>
  </div>
</template>

<style scoped>
.expansion-shell{position:relative;color:var(--dv-text)}.expansion-panel{min-height:620px}.expansion-heading{align-items:flex-start}.expansion-heading p{margin:.35rem 0 0;color:var(--dv-muted);max-width:580px}.signal-cards{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:20px 0}.signal-cards article{padding:18px;border-radius:16px;background:var(--dv-surface-high);border:1px solid var(--dv-border)}.signal-cards span{display:block;font-size:12px;color:var(--dv-muted)}.signal-cards strong{color:var(--dv-text);font-size:30px;line-height:1.2}.analytics-grid{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(300px,.75fr);gap:18px}.trend-card,.notification-card,.ops-card{border:1px solid var(--dv-border);border-radius:18px;background:var(--dv-surface);padding:20px;color:var(--dv-text)}.trend-card header,.notification-card header,.ops-card header{display:flex;justify-content:space-between;gap:12px;align-items:center}.trend-card h4,.notification-card h4,.ops-card h4{margin:0}.trend-card header small,.notification-card header small,.ops-card header small{color:var(--dv-muted)}.trend-chart{height:260px;display:flex;align-items:flex-end;gap:4px;margin-top:24px;border-bottom:1px solid var(--dv-outline)}.trend-column{height:100%;flex:1;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;min-width:4px}.trend-column i{display:block;width:80%;min-height:3px;background:var(--dv-primary);border-radius:5px 5px 0 0}.trend-column span{font-size:8px;color:var(--dv-muted);writing-mode:vertical-rl;margin-top:4px}.notification-list{display:grid;gap:8px;margin-top:16px;max-height:330px;overflow:auto}.notification-list button{text-align:left;border:1px solid transparent;border-radius:12px;padding:12px;background:var(--dv-surface-high);color:var(--dv-text)}.notification-list button.unread{border-color:var(--dv-primary-line);background:var(--dv-primary-soft)}.notification-list span,.notification-list small{font-size:10px;color:var(--dv-muted)}.notification-list strong,.notification-list p{display:block;margin:3px 0}.brand-layout{display:block}.brand-editor{display:grid;grid-template-columns:1fr 1fr;gap:14px;padding:20px;background:var(--dv-surface-high);border-radius:18px}.brand-editor label,.report-workflow label{display:grid;gap:6px;font-size:12px;font-weight:700}.brand-editor .wide,.report-workflow .wide{grid-column:1/-1}.brand-editor textarea{min-height:90px}.brand-editor .form-actions{grid-column:1/-1}.brand-editor-floating{padding:0;border-radius:0;background:transparent}.brand-list{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:9px;align-content:start}.brand-list>button{display:grid;grid-template-columns:46px 1fr auto;align-items:center;gap:12px;text-align:left;border:1px solid var(--dv-border);border-radius:14px;background:var(--dv-surface);color:var(--dv-text);padding:12px}.brand-list strong,.brand-list small{display:block}.brand-list small,.brand-list em{color:var(--dv-muted)}.brand-mark{width:42px;height:42px;border-radius:12px;background:var(--dv-surface-highest);display:grid;place-items:center;overflow:hidden}.brand-mark img{width:100%;height:100%;object-fit:contain}.report-board{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;margin-top:18px}.report-board article{border:1px solid var(--dv-border);border-radius:18px;padding:18px;background:var(--dv-surface);color:var(--dv-text)}.report-board article>header{display:flex;justify-content:space-between}.report-board h4{margin:5px 0}.report-board dl{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.report-board dt{font-size:10px;color:var(--dv-muted)}.report-board dd{margin:2px 0;font-size:12px}.report-workflow{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding-top:12px;border-top:1px solid var(--dv-border)}.report-workflow button{justify-self:start}.report-workflow-floating{padding-top:0;border-top:0}.report-open-action{margin-top:14px}.report-original{margin-bottom:18px;padding:14px;border:1px solid var(--dv-border);border-radius:10px;background:var(--dv-surface-high)}.report-original span{color:var(--dv-muted);font-size:11px}.report-original p{margin:6px 0 0;line-height:1.55}.expansion-modal-actions{display:flex;justify-content:flex-end;gap:8px;width:100%}.operations-grid{display:grid;grid-template-columns:1fr 1.5fr;gap:16px}.wide-card{grid-column:1/-1}.export-actions{display:grid;grid-template-columns:repeat(2,1fr);gap:8px;margin-top:16px}.export-actions button{border:1px solid var(--dv-outline);border-radius:12px;background:var(--dv-surface-high);color:var(--dv-text);padding:16px;font-weight:700}.settings-card label{display:grid;grid-template-columns:1fr minmax(100px,180px) auto;gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid var(--dv-border)}.settings-card strong,.settings-card small{display:block}.settings-card small{color:var(--dv-muted)}.settings-card button,.session-filter button,.danger-link{color:var(--dv-text)}.session-filter{display:flex;align-items:center;gap:10px}.session-filter label{white-space:nowrap}.session-agent{max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.danger-link{color:var(--dv-error)}.expansion-loading{position:absolute;inset:0;display:grid;place-items:center;background:rgba(11,11,12,.78);color:var(--dv-text);backdrop-filter:blur(2px);font-weight:700}@media(max-width:900px){.signal-cards{grid-template-columns:1fr 1fr}.analytics-grid,.operations-grid{grid-template-columns:1fr}.report-board{grid-template-columns:1fr}.wide-card{grid-column:auto}.brand-editor{grid-template-columns:1fr}.brand-editor .wide{grid-column:auto}}
.brand-editor input,.brand-editor select,.brand-editor textarea,.report-workflow input,.report-workflow select,.report-workflow textarea,.settings-card input,.settings-card select,.session-filter input{width:100%;min-height:42px;padding:9px 11px;border:1px solid var(--dv-outline);border-radius:8px;outline:0;background:var(--dv-background);color:var(--dv-text);color-scheme:dark}.brand-editor textarea,.report-workflow textarea{resize:vertical}.brand-editor input:focus,.brand-editor select:focus,.brand-editor textarea:focus,.report-workflow input:focus,.report-workflow select:focus,.report-workflow textarea:focus,.settings-card input:focus,.settings-card select:focus,.session-filter input:focus{border-color:var(--dv-primary);box-shadow:0 0 0 3px var(--dv-primary-soft)}.brand-editor input::placeholder,.report-workflow input::placeholder,.session-filter input::placeholder{color:var(--dv-muted)}.trend-card header button,.notification-card header button,.settings-card label>button,.ops-card .admin-table button{min-height:38px;padding:7px 10px;border:1px solid var(--dv-outline);border-radius:8px;background:var(--dv-surface-high);color:var(--dv-text)}
@media(max-width:600px){.expansion-panel{min-height:auto}.signal-cards article,.trend-card,.notification-card,.ops-card,.brand-editor,.report-board article{padding:14px;border-radius:12px}.signal-cards strong{font-size:24px}.trend-card,.ops-card{overflow-x:auto}.trend-card header,.notification-card header,.ops-card header{align-items:flex-start;flex-direction:column}.trend-chart{width:520px;height:210px}.brand-list>button{grid-template-columns:42px 1fr}.brand-list>button>em{grid-column:2}.report-board dl{grid-template-columns:1fr 1fr}.report-workflow{grid-template-columns:1fr}.report-workflow .wide{grid-column:auto}.report-workflow button{width:100%}.settings-card label{grid-template-columns:1fr;align-items:stretch}.settings-card label button{width:100%}.session-filter{width:100%;align-items:stretch;flex-wrap:wrap}.session-filter>input{width:100%}.ops-card .admin-table{display:block;width:calc(100% + 28px);margin:0 -14px -14px;padding:0 14px 10px;overflow-x:auto}.export-actions{grid-template-columns:1fr}}
</style>
