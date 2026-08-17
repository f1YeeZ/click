<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { errorMessage } from '../api/client'
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
let searchTimer
let searchRequest = 0
let realtimeTimer
let stopRealtime = () => {}

const selectedItems = computed(() => store.items.map((item) => comparison.value.items.find((full) => full.id === item.id) || item))
const selectedCount = computed(() => store.items.length)
const visibleRows = computed(() => onlyDifference.value ? comparison.value.rows.filter((row) => row.different) : comparison.value.rows)
const ids = computed(() => store.ids.join(','))

const compactName = (mouse) => mouse.displayName || [mouse.brand, mouse.model, mouse.variant].filter(Boolean).join(' ')
const initials = (mouse) => String(mouse.brand || mouse.model || 'M').slice(0, 2).toUpperCase()
const isSelected = (id) => store.contains(id)
const deltaClass = (delta) => ({
  positive: delta?.startsWith('+'),
  negative: delta?.startsWith('-'),
  different: delta === '不同'
})

const load = async () => {
  error.value = ''
  const queryIds = String(route.query.ids || store.ids.join(',')).trim()
  if (!queryIds) {
    comparison.value = { items: [], rows: [] }
    return
  }
  try {
    const { data } = await api.get('/mouse-comparisons', { params: { mouseIds: queryIds } })
    comparison.value = normalizeComparison(data)
    store.replace(data.items)
    if (data.items.length) await router.replace({ query: { ids: data.items.map((item) => item.id).join(',') } })
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
onBeforeUnmount(() => { stopRealtime(); clearTimeout(searchTimer); clearTimeout(realtimeTimer) })
</script>

<template>
  <main class="section-shell compare-page compare-workbench">
    <section class="compare-intro">
      <div class="compare-page-heading"><div><p class="page-label">COMPARE / UP TO FOUR</p><h1>并排参数对比</h1><span>对照规格差异、尺寸变化和握姿评价，快速找到更适合的一款。</span></div><button class="button button-ghost" type="button" @click="focusSearch">＋ 添加鼠标</button></div>
      <div v-if="searchOpen" class="compare-search-panel open">
        <div class="compare-search-meta"><span>ADD A MOUSE</span><button type="button" aria-label="关闭添加鼠标搜索" @click="searchOpen = false">×</button></div>
        <div class="compare-search-box">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg>
          <input ref="searchInput" v-model="searchQuery" type="search" placeholder="搜索品牌或型号，例如 Viper V3 Pro" autocomplete="off" aria-label="搜索要对比的鼠标" @focus="openSearch" @keydown.esc="searchOpen = false">
          <button v-if="searchQuery" type="button" aria-label="清除搜索" @click="searchQuery = ''">×</button>
        </div>
        <div v-if="searchOpen" class="compare-search-results">
          <div class="search-results-head"><span>{{ searchQuery ? 'SEARCH RESULTS' : 'POPULAR IN DATABASE' }}</span><span v-if="!searchLoading">{{ searchResults.length }} 款</span></div>
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
          <div><p class="eyebrow">SELECTED MICE</p><h2>已选择 <b>{{ selectedCount }}</b><small> / 4</small></h2></div>
          <button v-if="selectedCount" class="clear-selection" type="button" @click="clearAll">清空</button>
        </div>
        <div v-if="selectedItems.length" class="selected-list">
          <article v-for="(item, index) in selectedItems" :key="item.id" class="selected-mouse-row">
            <div class="selected-row-top"><span class="selected-index">0{{ index + 1 }}</span><span class="selected-brand">{{ item.brand || 'MOUSE' }}</span><button type="button" :aria-label="`移除 ${compactName(item)}`" @click="remove(item.id)">×</button></div>
            <div class="selected-row-main"><div class="selected-row-thumb"><img v-if="item.imageUrl" :src="item.imageUrl" :alt="compactName(item)"><span v-else>{{ initials(item) }}</span></div><div class="selected-row-copy"><strong>{{ item.model || item.displayName }}</strong><small>{{ item.variant || 'STANDARD EDITION' }}</small></div></div>
            <dl class="selected-dimensions"><div><dt>长</dt><dd>{{ item.lengthMm ?? '—' }}<small>mm</small></dd></div><div><dt>宽</dt><dd>{{ item.widthMm ?? '—' }}<small>mm</small></dd></div><div><dt>高</dt><dd>{{ item.heightMm ?? '—' }}<small>mm</small></dd></div></dl>
          </article>
        </div>
        <button v-else class="selected-sidebar-empty" type="button" @click="focusSearch"><span>＋</span><strong>从搜索开始</strong><small>选择鼠标后会出现在这里</small></button>
        <div class="selected-sidebar-note">先选中的鼠标作为基准，参数差值会在右侧标出。</div>
      </aside>

      <div class="compare-main">
        <div class="flash error" v-if="error">{{ error }}</div>
        <section class="compare-empty" v-if="selectedCount < 1">
          <div class="empty-cross">↔</div><p class="eyebrow">READY WHEN YOU ARE</p><h2>选择一款鼠标查看参数</h2><p>已选择的鼠标会固定在左侧，继续搜索即可添加。</p>
        </section>
        <section class="comparison-wrap" v-else>
          <div class="matrix-toolbar"><div><p class="eyebrow">LIVE COMPARISON</p><h2>参数矩阵</h2><p class="mobile-scroll-hint">左右滑动查看全部参数</p></div><label class="difference-toggle"><input v-model="onlyDifference" type="checkbox"><span>仅显示差异</span></label></div>
          <table class="comparison-table"><thead><tr><th class="parameter-column">参数</th><th v-for="(item,index) in comparison.items" :key="item.id"><span class="channel-label">CH {{ String(index + 1).padStart(2,'0') }}</span><strong>{{ item.model }}</strong><small>{{ item.brand }}</small><button class="compare-remove-inline" type="button" @click="remove(item.id)">移除</button></th></tr></thead><tbody><tr v-for="row in visibleRows" :key="row.group + row.label"><th><span>{{ row.group }}</span><strong>{{ row.label }}</strong><small>{{ row.unit }}</small></th><td v-for="(cell,index) in row.cells" :key="index"><strong>{{ cell.value }}</strong><em v-if="cell.delta" :class="deltaClass(cell.delta)">{{ cell.delta }}<small v-if="row.unit">{{ row.unit }}</small></em></td></tr></tbody></table>
        </section>
      </div>
    </section>
  </main>
</template>
