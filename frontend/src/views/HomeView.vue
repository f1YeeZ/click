<script setup>
defineOptions({ name: 'HomeView' })
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import { onRealtime } from '../services/realtime'

const router = useRouter()
const query = ref('')
const latest = ref([])
const total = ref(0)
const contentReady = ref(false)
const quickFilters = [
  { label: '无线', params: { connection: 'wireless_2_4g' } },
  { label: '超轻量', params: { weightMax: 60 } },
  { label: 'PAW3395', params: { q: 'PAW3395' } },
  { label: '人体工学', params: { shape: 'ERGONOMIC' } }
]
const loadLatest = async () => {
  const { data } = await api.get('/mice', { params: { pageSize: 12, sort: 'newest' } })
  latest.value = data.items.slice(0, 4)
  total.value = data.page.totalItems
}
let stopRealtime = () => {}
let realtimeTimer
let initialLoadTimer
let contentReadyTimer
onMounted(() => {
  contentReadyTimer = window.setTimeout(() => { contentReady.value = true }, 190)
  // Let the route fade finish before inserting the initial card grid.
  initialLoadTimer = window.setTimeout(loadLatest, 220)
  stopRealtime = onRealtime((event) => {
    if (event.type !== 'mouse.changed') return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(loadLatest, 250)
  })
})
onBeforeUnmount(() => { stopRealtime(); clearTimeout(realtimeTimer); clearTimeout(initialLoadTimer); clearTimeout(contentReadyTimer) })
const search = () => router.push({ path: '/mice', query: query.value ? { q: query.value } : {} })
const quickSearch = (params) => router.push({ path: '/mice', query: params })
</script>

<template>
  <main class="home-page">
    <section class="home-hero section-shell">
      <div class="hero-copy reveal">
        <p class="eyebrow">CLICKER INDEX / VERIFIED MOUSE DATA</p>
        <h1>找到你的完美点击</h1>
        <p class="hero-lead">面向高性能鼠标的技术数据库。对齐规格、分析参数，用真实数据找到适合你的硬件。</p>
        <form class="hero-search" @submit.prevent="search">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg>
          <input v-model="query" type="search" aria-label="搜索品牌、型号或传感器" placeholder="按型号、传感器或品牌搜索…">
          <button type="submit">搜索</button>
        </form>
        <div class="quick-filters" aria-label="快捷筛选">
          <button v-for="item in quickFilters" :key="item.label" type="button" @click="quickSearch(item.params)">{{ item.label }}</button>
        </div>
      </div>
    </section>
    <template v-if="contentReady">
    <section class="section-shell trending-section">
      <div class="section-heading ruled-heading">
        <div><p class="eyebrow">LATEST ARRIVALS / {{ total }} VERIFIED</p><h2>近期新品</h2></div>
        <RouterLink class="inline-link" to="/mice">查看全部 <span>→</span></RouterLink>
      </div>
      <div class="mouse-grid trending-grid"><MouseCard v-for="(mouse, index) in latest" :key="mouse.id" :mouse="mouse" :index="index" /></div>
    </section>
    <section id="core-systems" class="section-shell systems-section">
      <div class="section-heading ruled-heading"><div><p class="eyebrow">CORE SYSTEMS</p><h2>核心系统</h2></div></div>
      <div class="systems-grid">
        <RouterLink class="system-card system-card-wide" to="/compare">
          <div class="comparison-preview" aria-hidden="true">
            <div><span>规格</span><span>鼠标 A</span><span>鼠标 B</span></div>
            <div><span>重量</span><strong>54g</strong><span>60g</span></div>
            <div><span>回报率</span><span>4000Hz</span><strong>8000Hz</strong></div>
            <div><span>尺寸</span><span>127×64×40</span><span>125×63×40</span></div>
          </div>
          <div class="system-copy"><span class="system-icon">⇆</span><h3>深度参数对比</h3><p>将技术规格逐项对齐，固定网格即时突出产品之间的参数差异。</p></div>
        </RouterLink>
        <RouterLink class="system-card" to="/mice">
          <div class="review-preview" aria-hidden="true"><i style="--value: 92%"></i><i style="--value: 76%"></i><i style="--value: 84%"></i></div>
          <div class="system-copy"><span class="system-icon">▥</span><h3>结构化主观评价</h3><p>固定评价维度与优缺点标签，让主观感受也能够被快速横向阅读。</p></div>
        </RouterLink>
      </div>
    </section>
    </template>
  </main>
</template>
