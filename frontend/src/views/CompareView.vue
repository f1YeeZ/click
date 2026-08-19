<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { errorMessage } from '../api/client'
import HandSupport2D from '../components/HandSupport2D.vue'
import { useCompareStore } from '../stores/compare'
import { onRealtime } from '../services/realtime'
import { normalizeComparison } from '../utils/comparison'

const route = useRoute()
const router = useRouter()
const store = useCompareStore()

const comparison = ref({ items: [], rows: [] })
const onlyDifference = ref(false)
const error = ref('')
const searchError = ref('')
const searchQuery = ref('')
const searchInput = ref(null)
const searchResults = ref([])
const searchOpen = ref(false)
const searchLoading = ref(false)
const supportGrip = ref('')
const supportSummaries = ref({})
const supportLoading = ref(false)
const supportError = ref('')
const supportGripOptions = [
  { code: '', label: '全部握姿' },
  { code: 'PALM', label: '趴握' },
  { code: 'CLAW', label: '抓握' },
  { code: 'FINGERTIP', label: '指握' },
  { code: 'MIXED', label: '混合' }
]
let searchTimer
let searchRequest = 0
let supportRequest = 0
let realtimeTimer
let stopRealtime = () => {}

const selectedItems = computed(() => store.items.map((item) => comparison.value.items.find((full) => full.id === item.id) || item))
const selectedCount = computed(() => store.items.length)
const visibleRows = computed(() => onlyDifference.value ? comparison.value.rows.filter((row) => row.different) : comparison.value.rows)
const ids = computed(() => store.ids.join(','))
const supportGripLabel = computed(() => supportGripOptions.find((item) => item.code === supportGrip.value)?.label || '全部握姿')

const compactName = (mouse) => mouse.displayName || [mouse.brand, mouse.model, mouse.variant].filter(Boolean).join(' ')
const initials = (mouse) => String(mouse.brand || mouse.model || 'M').slice(0, 2).toUpperCase()
const isSelected = (id) => store.contains(id)
const deltaClass = (delta) => ({
  positive: delta?.startsWith('+'),
  negative: delta?.startsWith('-'),
  different: delta === '不同'
})

const emptySupportSummary = () => ({ sampleCount: 0, positions: [], cells: [], maxCount: 0, gridColumns: 64, gridRows: 96 })
const supportSummaryFor = (id) => supportSummaries.value[id] || emptySupportSummary()
const loadSupportSummaries = async (items = comparison.value.items) => {
  const requestId = ++supportRequest
  supportError.value = ''
  if (!items.length) {
    supportSummaries.value = {}
    supportLoading.value = false
    return
  }
  supportLoading.value = true
  const results = await Promise.all(items.map(async (item) => {
    try {
      const { data } = await api.get(`/mice/${item.id}/support-summary`, {
        params: supportGrip.value ? { gripStyle: supportGrip.value } : {}
      })
      return [item.id, data || emptySupportSummary(), false]
    } catch {
      return [item.id, emptySupportSummary(), true]
    }
  }))
  if (requestId !== supportRequest) return
  supportSummaries.value = Object.fromEntries(results.map(([id, summary]) => [id, summary]))
  if (results.some(([, , failed]) => failed)) supportError.value = '部分鼠标的支撑位置暂时无法加载'
  supportLoading.value = false
}

const load = async () => {
  error.value = ''
  const queryIds = String(route.query.ids || store.ids.join(',')).trim()
  if (!queryIds) {
    comparison.value = { items: [], rows: [] }
    supportSummaries.value = {}
    return
  }
  try {
    const { data } = await api.get('/mouse-comparisons', { params: { mouseIds: queryIds } })
    comparison.value = normalizeComparison(data)
    store.replace(data.items)
    if (data.items.length) await router.replace({ query: { ids: data.items.map((item) => item.id).join(',') } })
    await loadSupportSummaries(data.items)
  } catch (e) {
    error.value = errorMessage(e)
  }
}

const syncRoute = async () => {
  await router.replace({ query: store.ids.length ? { ids: ids.value } : {} })
  await load()
}

const addMouse = async (mouse) => {
  if (isSelected(mouse.id)) return
  try {
    store.toggle(mouse)
    searchOpen.value = false
    searchQuery.value = ''
    searchResults.value = []
    await syncRoute()
  } catch (e) {
    searchError.value = e.message
  }
}

const remove = async (id) => {
  store.remove(id)
  await syncRoute()
}

const clearAll = async () => {
  store.clear()
  comparison.value = { items: [], rows: [] }
  await router.replace({ query: {} })
}

const searchCatalog = async () => {
  const requestId = ++searchRequest
  searchLoading.value = true
  searchError.value = ''
  try {
    const { data } = await api.get('/mice', {
      params: { q: searchQuery.value.trim() || undefined, page: 1, pageSize: 8, sort: 'brand_asc' }
    })
    if (requestId === searchRequest) searchResults.value = data.items || []
  } catch (e) {
    if (requestId === searchRequest) searchError.value = errorMessage(e)
  } finally {
    if (requestId === searchRequest) searchLoading.value = false
  }
}

const openSearch = () => {
  searchOpen.value = true
  if (!searchResults.value.length) searchCatalog()
}

const focusSearch = () => {
  openSearch()
  requestAnimationFrame(() => searchInput.value?.focus())
}

watch(searchQuery, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (searchOpen.value) searchCatalog()
  }, 240)
})
watch(supportGrip, () => loadSupportSummaries())
watch(() => route.query.ids, load)
onActivated(() => {
  load()
  stopRealtime()
  stopRealtime = onRealtime((event) => {
    if (event.type === 'sync.required') {
      clearTimeout(realtimeTimer)
      realtimeTimer = setTimeout(load, 250)
      return
    }
    if (event.type !== 'mouse.changed' || (event.mouseId && !store.contains(event.mouseId))) return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(load, 250)
  })
})
onDeactivated(() => { stopRealtime(); clearTimeout(realtimeTimer) })
onBeforeUnmount(() => { stopRealtime(); clearTimeout(searchTimer); clearTimeout(realtimeTimer); supportRequest += 1 })
</script>

<template>
  <main class="section-shell compare-page compare-workbench">
    <section class="compare-intro">
      <div class="compare-page-heading"><div><h1>并排参数对比</h1><span>最多选择四款鼠标，对照规格、尺寸和外形差异。</span></div><button class="button button-ghost" type="button" @click="focusSearch">＋ 添加鼠标</button></div>
      <div v-if="searchOpen" class="compare-search-panel open">
        <div class="compare-search-meta"><span>添加鼠标</span><button type="button" aria-label="关闭添加鼠标搜索" @click="searchOpen = false">×</button></div>
        <div class="compare-search-box">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg>
          <input ref="searchInput" v-model="searchQuery" type="search" placeholder="搜索品牌或型号，例如 Viper V3 Pro" autocomplete="off" aria-label="搜索要对比的鼠标" @focus="openSearch" @keydown.esc="searchOpen = false">
          <button v-if="searchQuery" type="button" aria-label="清除搜索" @click="searchQuery = ''">×</button>
        </div>
        <div v-if="searchOpen" class="compare-search-results">
          <div class="search-results-head"><span>{{ searchQuery ? '搜索结果' : '鼠标库推荐' }}</span><span v-if="!searchLoading">{{ searchResults.length }} 款</span></div>
          <div v-if="searchLoading" class="search-state">正在查找鼠标…</div>
          <div v-else-if="searchError" class="search-state error">{{ searchError }}</div>
          <div v-else-if="!searchResults.length" class="search-state">没有找到匹配的鼠标，试试更短的关键词。</div>
          <button v-for="mouse in searchResults" v-else :key="mouse.id" class="search-result" type="button" :disabled="isSelected(mouse.id)" @click="addMouse(mouse)">
            <span class="result-thumb"><img v-if="mouse.imageUrl" :src="mouse.imageUrl" :alt="compactName(mouse)"><b v-else>{{ initials(mouse) }}</b></span>
            <span class="result-copy"><strong>{{ mouse.model }}</strong><small>{{ mouse.brand }}<i v-if="mouse.variant"> · {{ mouse.variant }}</i></small></span>
            <span class="result-action" :class="{ selected: isSelected(mouse.id) }">{{ isSelected(mouse.id) ? '已加入' : '＋ 添加' }}</span>
          </button>
          <p class="search-hint">点击一项后会立即出现在左侧选择清单</p>
        </div>
      </div>
    </section>

    <section class="compare-layout">
      <aside class="selected-sidebar">
        <div class="selected-sidebar-head">
          <div><h2>已选择 <b>{{ selectedCount }}</b><small> / 4</small></h2></div>
          <button v-if="selectedCount" class="clear-selection" type="button" @click="clearAll">清空</button>
        </div>
        <div v-if="selectedItems.length" class="selected-list">
          <article v-for="(item, index) in selectedItems" :key="item.id" class="selected-mouse-row">
            <div class="selected-row-top"><span class="selected-index">0{{ index + 1 }}</span><span class="selected-brand">{{ item.brand || '鼠标' }}</span><button type="button" :aria-label="`移除 ${compactName(item)}`" @click="remove(item.id)">×</button></div>
            <div class="selected-row-main"><div class="selected-row-thumb"><img v-if="item.imageUrl" :src="item.imageUrl" :alt="compactName(item)"><span v-else>{{ initials(item) }}</span></div><div class="selected-row-copy"><strong>{{ item.model || item.displayName }}</strong><small>{{ item.variant || '标准版' }}</small></div></div>
            <dl class="selected-dimensions"><div><dt>长</dt><dd>{{ item.lengthMm ?? '—' }}<small>mm</small></dd></div><div><dt>宽</dt><dd>{{ item.widthMm ?? '—' }}<small>mm</small></dd></div><div><dt>高</dt><dd>{{ item.heightMm ?? '—' }}<small>mm</small></dd></div></dl>
          </article>
        </div>
        <button v-else class="selected-sidebar-empty" type="button" @click="focusSearch"><span>＋</span><strong>从搜索开始</strong><small>选择鼠标后会出现在这里</small></button>
        <div class="selected-sidebar-note">先选中的鼠标作为基准，参数差值会在右侧标出。</div>
      </aside>

      <div class="compare-main">
        <div class="flash error" v-if="error">{{ error }}</div>
        <section class="compare-empty" v-if="selectedCount < 1">
          <div class="empty-cross">↔</div><h2>先选择两款鼠标</h2><p>从搜索中添加鼠标，选择两款后即可查看参数差异。</p>
        </section>
        <section class="compare-empty" v-else-if="selectedCount < 2">
          <div class="empty-cross">＋</div><h2>再添加一款鼠标</h2><p>参数矩阵会在选择两款鼠标后显示，当前鼠标已保留为比较基准。</p><button class="button primary-action-button" type="button" @click="focusSearch">添加第二款鼠标</button>
        </section>
        <template v-else>
          <section class="support-comparison" aria-labelledby="support-comparison-title">
            <div class="support-comparison-toolbar">
              <div><h2 id="support-comparison-title">支撑位置对比</h2><p>统一筛选握姿，比较用户实际标记的掌面支撑区域。</p></div>
              <label><span>握姿</span><select v-model="supportGrip" aria-label="筛选支撑位置握姿"><option v-for="item in supportGripOptions" :key="item.code || 'ALL'" :value="item.code">{{ item.label }}</option></select></label>
            </div>
            <p v-if="supportError" class="support-comparison-error" role="status">{{ supportError }}</p>
            <div class="support-compare-grid" :style="{ '--support-columns': comparison.items.length }" :aria-busy="supportLoading">
              <article v-for="(item, index) in comparison.items" :key="item.id" class="support-compare-card">
                <header><span>0{{ index + 1 }}</span><div><strong>{{ item.model }}</strong><small>{{ item.brand }}</small></div></header>
                <div class="support-compare-map" :class="{ empty: !supportSummaryFor(item.id).cells?.length }">
                  <HandSupport2D
                    :summary-cells="supportSummaryFor(item.id).cells || []"
                    :max-count="supportSummaryFor(item.id).maxCount || 0"
                    :grid-columns="supportSummaryFor(item.id).gridColumns || 64"
                    :grid-rows="supportSummaryFor(item.id).gridRows || 96"
                    tool="view"
                    :editable="false"
                    :aria-label="`${item.displayName || item.model} 的${supportGripLabel}支撑位置热力图`"
                  />
                  <span v-if="!supportLoading && !supportSummaryFor(item.id).cells?.length">暂无支撑记录</span>
                </div>
                <footer><strong>{{ supportLoading ? '加载中…' : `${supportSummaryFor(item.id).sampleCount || 0} 份记录` }}</strong><span>{{ supportGripLabel }}</span></footer>
              </article>
            </div>
          </section>
          <section class="comparison-wrap">
            <div class="matrix-toolbar"><div><h2>参数矩阵</h2><p class="mobile-scroll-hint">小屏按鼠标逐项排列</p></div><label class="difference-toggle"><input v-model="onlyDifference" type="checkbox"><span>仅显示差异</span></label></div>
            <table class="comparison-table"><thead><tr><th class="parameter-column">参数</th><th v-for="(item,index) in comparison.items" :key="item.id"><span class="channel-label">第 {{ index + 1 }} 款</span><strong>{{ item.model }}</strong><small>{{ item.brand }}</small><button class="compare-remove-inline" type="button" @click="remove(item.id)">移除</button></th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.group + row.label"><th><span>{{ row.group }}</span><strong>{{ row.label }}</strong><small>{{ row.unit }}</small></th><td v-for="(cell,index) in row.cells" :key="index" :data-mouse="comparison.items[index]?.model || `第 ${index + 1} 款`"><strong>{{ cell.value }}</strong><em v-if="cell.delta" :class="deltaClass(cell.delta)">{{ cell.delta }}<small v-if="row.unit">{{ row.unit }}</small></em></td></tr></tbody></table>
          </section>
        </template>
      </div>
    </section>
  </main>
</template>

<style scoped>
.support-comparison {
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid var(--figma-line);
  border-radius: 12px;
  background: var(--figma-surface);
}

.support-comparison-toolbar {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;
}

.support-comparison-toolbar h2,
.support-comparison-toolbar p { margin: 0; }
.support-comparison-toolbar h2 { color: var(--figma-text); font-size: 1.15rem; }
.support-comparison-toolbar p { margin-top: 5px; color: var(--figma-muted); font-size: .78rem; }
.support-comparison-toolbar label { display: grid; grid-template-columns: auto 128px; align-items: center; gap: 8px; color: var(--figma-muted); font-size: .75rem; }
.support-comparison-toolbar select { min-height: 38px; padding: 0 10px; border: 1px solid var(--figma-line-strong); border-radius: 8px; background: var(--figma-surface-high); color: var(--figma-text); }
.support-comparison-error { margin: 0 0 10px; color: var(--figma-muted); font-size: .75rem; }
.support-compare-grid { display: grid; grid-template-columns: repeat(var(--support-columns), minmax(0, 1fr)); gap: 10px; min-width: 0; }
.support-compare-card { display: grid; grid-template-rows: auto minmax(0, 1fr) auto; min-width: 0; overflow: hidden; border-radius: 10px; background: var(--figma-surface-high); }
.support-compare-card header { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 10px; padding: 12px 13px; border-bottom: 1px solid var(--figma-line); }
.support-compare-card header > span { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 7px; background: var(--figma-cyan-soft); color: var(--figma-cyan-strong); font-size: .7rem; }
.support-compare-card header strong,
.support-compare-card header small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.support-compare-card header strong { color: var(--figma-text); font-size: .8rem; }
.support-compare-card header small { margin-top: 2px; color: var(--figma-muted); font-size: .68rem; }
.support-compare-map { position: relative; height: clamp(220px, 24vw, 310px); padding: 10px; background: var(--figma-bg); }
.support-compare-map.empty { opacity: .68; }
.support-compare-map > span { position: absolute; right: 10px; bottom: 10px; left: 10px; z-index: 3; padding: 7px; border-radius: 7px; background: rgba(8, 11, 9, .82); color: var(--figma-muted); font-size: .7rem; text-align: center; }
.support-compare-map :deep(canvas) { cursor: default; }
.support-compare-card footer { display: flex; justify-content: space-between; gap: 8px; padding: 10px 13px; border-top: 1px solid var(--figma-line); color: var(--figma-muted); font-size: .7rem; }
.support-compare-card footer strong { color: var(--figma-text-soft); font-weight: 600; }

@media (max-width: 760px) {
  .support-comparison { padding: 14px; }
  .support-comparison-toolbar { align-items: stretch; flex-direction: column; }
  .support-comparison-toolbar label { grid-template-columns: auto 1fr; }
  .support-compare-grid { grid-template-columns: 1fr; }
  .support-compare-map { height: 250px; }
}
</style>
