<script setup>
defineOptions({ name: 'HomeView' })
import { nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import { onRealtime } from '../services/realtime'

const router = useRouter()
const query = ref('')
const searchShell = ref(null)
const suggestions = ref([])
const suggestionTotal = ref(0)
const suggestionState = ref('idle')
const suggestionsOpen = ref(false)
const activeSuggestion = ref(-1)
const latest = ref([])
const total = ref(0)
const contentReady = ref(false)
const loadLatest = async () => {
  const { data } = await api.get('/mice', { params: { pageSize: 10, sort: 'newest' } })
  latest.value = data.items.slice(0, 10)
  total.value = data.page.totalItems
}
const resetSuggestions = () => {
  suggestions.value = []
  suggestionTotal.value = 0
  suggestionState.value = 'idle'
  suggestionsOpen.value = false
  activeSuggestion.value = -1
}
const loadSuggestions = async (term, requestId) => {
  try {
    const { data } = await api.get('/mice', { params: { q: term, pageSize: 12, sort: 'newest' } })
    if (requestId !== suggestionRequest || query.value.trim() !== term) return
    suggestions.value = data.items.slice(0, 6)
    suggestionTotal.value = data.page.totalItems
    suggestionState.value = suggestions.value.length ? 'ready' : 'empty'
  } catch {
    if (requestId !== suggestionRequest) return
    suggestions.value = []
    suggestionTotal.value = 0
    suggestionState.value = 'error'
  }
}
const searchAll = () => {
  const term = query.value.trim()
  suggestionsOpen.value = false
  const navigation = router.push({ path: '/mice', query: term ? { q: term } : {} })
  query.value = ''
  return navigation
}
const openSuggestion = (mouse) => {
  suggestionsOpen.value = false
  const navigation = router.push(`/mice/${mouse.id}`)
  query.value = ''
  return navigation
}
const submitSearch = () => searchAll()
const moveSuggestion = async (direction) => {
  if (!suggestionsOpen.value || !suggestions.value.length) return
  activeSuggestion.value = (activeSuggestion.value + direction + suggestions.value.length) % suggestions.value.length
  await nextTick()
  document.getElementById(`home-search-option-${activeSuggestion.value}`)?.scrollIntoView({ block: 'nearest' })
}
const reopenSuggestions = () => {
  if (query.value.trim() && suggestionState.value !== 'idle') suggestionsOpen.value = true
}
const closeSuggestions = () => {
  suggestionsOpen.value = false
  activeSuggestion.value = -1
}
const handleOutsidePointer = (event) => {
  if (!searchShell.value?.contains(event.target)) closeSuggestions()
}
let stopRealtime = () => {}
let realtimeTimer
let initialLoadTimer
let contentReadyTimer
let suggestionTimer
let suggestionRequest = 0
const startViewRealtime = () => {
  stopRealtime()
  stopRealtime = onRealtime((event) => {
    if (event.type !== 'mouse.changed' && event.type !== 'sync.required') return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(loadLatest, 250)
  })
}
watch(query, (value) => {
  clearTimeout(suggestionTimer)
  const requestId = ++suggestionRequest
  const term = value.trim()
  activeSuggestion.value = -1
  if (!term) {
    resetSuggestions()
    return
  }
  suggestionsOpen.value = true
  suggestionState.value = 'loading'
  suggestionTimer = window.setTimeout(() => loadSuggestions(term, requestId), 220)
})
onMounted(() => {
  document.addEventListener('pointerdown', handleOutsidePointer)
  contentReadyTimer = window.setTimeout(() => { contentReady.value = true }, 190)
  // Let the route fade finish before inserting the initial card grid.
  initialLoadTimer = window.setTimeout(loadLatest, 220)
})
onActivated(startViewRealtime)
onDeactivated(() => { stopRealtime(); clearTimeout(realtimeTimer) })
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', handleOutsidePointer)
  stopRealtime()
  clearTimeout(realtimeTimer)
  clearTimeout(initialLoadTimer)
  clearTimeout(contentReadyTimer)
  clearTimeout(suggestionTimer)
  suggestionRequest++
})
</script>

<template>
  <main class="home-page">
    <section class="home-hero section-shell">
      <div class="hero-copy reveal">
        <div ref="searchShell" class="home-search-shell" :class="{ open: suggestionsOpen }">
          <form class="hero-search" role="search" @submit.prevent="submitSearch">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="6.5"></circle><path d="m16 16 4 4"></path></svg>
            <input
              v-model="query"
              type="search"
              role="combobox"
              aria-label="按型号搜索"
              aria-autocomplete="list"
              aria-controls="home-search-suggestions"
              :aria-expanded="suggestionsOpen"
              :aria-activedescendant="activeSuggestion >= 0 ? `home-search-option-${activeSuggestion}` : undefined"
              placeholder="按型号搜索"
              autocomplete="off"
              @focus="reopenSuggestions"
              @keydown.down.prevent="moveSuggestion(1)"
              @keydown.up.prevent="moveSuggestion(-1)"
              @keydown.esc.stop="closeSuggestions"
            >
            <button type="submit">搜索</button>
          </form>
          <div
            v-if="suggestionsOpen"
            id="home-search-suggestions"
            class="home-search-suggestions"
            role="listbox"
            aria-label="鼠标搜索建议"
          >
            <div v-if="suggestionState === 'loading'" class="home-search-state" aria-live="polite">
              <span></span><strong>正在查找匹配鼠标…</strong>
            </div>
            <div v-else-if="suggestionState === 'error'" class="home-search-state error">
              <strong>暂时无法加载搜索建议</strong><button type="button" @click="searchAll">前往鼠标库搜索</button>
            </div>
            <div v-else-if="suggestionState === 'empty'" class="home-search-state empty">
              <strong>没有找到“{{ query.trim() }}”</strong><small>请尝试输入更完整的型号</small>
            </div>
            <template v-else>
              <button
                v-for="(mouse, index) in suggestions"
                :id="`home-search-option-${index}`"
                :key="mouse.id"
                type="button"
                class="home-search-option"
                :class="{ active: activeSuggestion === index }"
                role="option"
                :aria-selected="activeSuggestion === index"
                :aria-label="`查看 ${mouse.displayName} 详情`"
                @mouseenter="activeSuggestion = index"
                @click="openSuggestion(mouse)"
              >
                <span class="home-search-thumb">
                  <img v-if="mouse.imageUrl" :src="mouse.imageUrl" :alt="`${mouse.displayName} 产品图`">
                  <b v-else>{{ mouse.brand.slice(0, 2).toUpperCase() }}</b>
                </span>
                <span class="home-search-copy">
                  <small>{{ mouse.brand }}</small>
                  <strong>{{ mouse.model }}</strong>
                  <em>{{ mouse.sensorName || '传感器待补充' }} · {{ mouse.weightG ?? '—' }}g</em>
                </span>
                <span class="home-search-arrow" aria-hidden="true">→</span>
              </button>
              <button v-if="suggestionState === 'ready'" class="home-search-all" type="button" @click="searchAll">
                <span>查看全部 {{ suggestionTotal }} 个结果</span><strong>进入鼠标库 →</strong>
              </button>
            </template>
          </div>
        </div>
      </div>
    </section>
    <template v-if="contentReady">
    <section class="section-shell trending-section">
      <div class="section-heading ruled-heading">
        <div><p class="eyebrow">LATEST ARRIVALS / {{ total }} VERIFIED</p><h2>近期新品</h2></div>
        <RouterLink class="inline-link" to="/mice">查看全部 <span>→</span></RouterLink>
      </div>
      <div class="trending-grid" aria-label="近期新品，无限循环轮播">
        <div class="trending-track">
          <div class="trending-set">
            <MouseCard v-for="(mouse, index) in latest" :key="mouse.id" :mouse="mouse" :index="index" />
          </div>
          <div class="trending-set" aria-hidden="true" inert>
            <MouseCard v-for="(mouse, index) in latest" :key="`loop-${mouse.id}`" :mouse="mouse" :index="index" />
          </div>
        </div>
      </div>
    </section>
    </template>
  </main>
</template>

<style scoped>
.home-hero {
  min-height: 300px;
  padding: 84px 0 52px;
}

.home-hero::before,
.home-hero::after {
  display: none;
}

.home-hero .hero-copy {
  max-width: 680px;
}

.home-page .trending-section {
  padding-top: 18px;
}

.home-page .trending-grid {
  overflow: hidden;
  padding: 6px 0 10px;
  border: 0;
}

.home-page .trending-track {
  display: flex;
  width: max-content;
  will-change: transform;
  animation: trending-loop 48s linear infinite;
}

.home-page .trending-set {
  display: flex;
  gap: 16px;
  padding-right: 16px;
}

.home-page .trending-set .mouse-card {
  flex: 0 0 clamp(230px, 24vw, 290px);
}

.home-page .trending-grid:hover .trending-track,
.home-page .trending-grid:focus-within .trending-track {
  animation-play-state: paused;
}

@keyframes trending-loop {
  to {
    transform: translate3d(-50%, 0, 0);
  }
}

@media (max-width: 820px) {
  .home-hero {
    min-height: 250px;
    padding: 64px 0 36px;
  }

  .home-page .trending-set .mouse-card {
    flex-basis: min(76vw, 290px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .home-page .trending-track {
    animation: none;
  }
}
</style>
