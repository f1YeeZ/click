<script setup>
defineOptions({ name: 'HomeView' })
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import api from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import { onRealtime } from '../services/realtime'

gsap.registerPlugin(ScrollTrigger)

const latest = ref([])
const total = ref(0)
const homeQuery = ref('')
const searchForm = ref(null)
const searchInput = ref(null)
const trendingSection = ref(null)
const trendingRevealState = ref('pending')
const searchFocused = ref(false)
const suggestions = ref([])
const suggestionsLoading = ref(false)
const suggestionsError = ref('')
const activeSuggestionIndex = ref(-1)
const router = useRouter()
const contentReady = ref(false)
const shouldLoopLatest = computed(() => latest.value.length > 4)
const normalizedHomeQuery = computed(() => homeQuery.value.trim())
const suggestionsOpen = computed(() => searchFocused.value && normalizedHomeQuery.value.length > 0)
const activeSuggestionId = computed(() => {
  const mouse = suggestions.value[activeSuggestionIndex.value]
  return mouse ? `home-search-option-${mouse.id}` : undefined
})
const mouseName = mouse => mouse.displayName || [mouse.brand, mouse.model, mouse.variant].filter(Boolean).join(' ')
const openSuggestion = mouse => {
  if (!mouse?.id) return
  searchFocused.value = false
  router.push(`/mice/${mouse.id}`)
}
const searchCatalog = () => {
  const selected = suggestions.value[activeSuggestionIndex.value]
  if (selected) {
    openSuggestion(selected)
    return
  }
  const q = homeQuery.value.trim()
  searchFocused.value = false
  router.push({ path: '/mice', query: q ? { q } : {} })
}
const handleSearchKeydown = event => {
  if (event.key === 'ArrowDown') {
    if (!suggestions.value.length) return
    event.preventDefault()
    activeSuggestionIndex.value = (activeSuggestionIndex.value + 1) % suggestions.value.length
  } else if (event.key === 'ArrowUp') {
    if (!suggestions.value.length) return
    event.preventDefault()
    activeSuggestionIndex.value = activeSuggestionIndex.value <= 0
      ? suggestions.value.length - 1
      : activeSuggestionIndex.value - 1
  } else if (event.key === 'Escape') {
    event.preventDefault()
    searchFocused.value = false
    activeSuggestionIndex.value = -1
    searchInput.value?.blur()
  }
}
const handleSearchFocusOut = event => {
  if (searchForm.value?.contains(event.relatedTarget)) return
  searchFocused.value = false
  activeSuggestionIndex.value = -1
}
const loadLatest = async () => {
  const { data } = await api.get('/mice', { params: { pageSize: 10, sort: 'newest' } })
  latest.value = data.items.slice(0, 10)
  total.value = data.page.totalItems
}
let stopRealtime = () => {}
let realtimeTimer
let initialLoadTimer
let contentReadyTimer
let suggestionTimer
let suggestionRequest = 0
let trendingRevealContext
let trendingRevealMedia
let trendingHasRevealed = false

const clearTrendingReveal = () => {
  trendingRevealMedia?.revert()
  trendingRevealContext?.revert()
  trendingRevealMedia = undefined
  trendingRevealContext = undefined
  if (!trendingHasRevealed) trendingRevealState.value = 'pending'
}

const setupTrendingReveal = async () => {
  await nextTick()
  const section = trendingSection.value
  if (!section || !latest.value.length || trendingRevealContext) return
  if (trendingHasRevealed) {
    trendingRevealState.value = 'complete'
    return
  }

  trendingRevealContext = gsap.context(() => {
    trendingRevealMedia = gsap.matchMedia()
    trendingRevealMedia.add({
      reduceMotion: '(prefers-reduced-motion: reduce)',
      allowMotion: '(prefers-reduced-motion: no-preference)'
    }, ({ conditions }) => {
      const revealContent = section.querySelector('.trending-reveal-content')
      const cards = section.querySelectorAll('.trending-set:first-child .trending-card-slot')
      if (conditions.reduceMotion || trendingHasRevealed) {
        gsap.set([section, revealContent, ...cards], { clearProps: 'opacity,transform,visibility' })
        trendingHasRevealed = true
        trendingRevealState.value = conditions.reduceMotion ? 'reduced' : 'complete'
        return
      }

      trendingRevealState.value = 'pending'
      gsap.set(section, { autoAlpha: 0 })
      gsap.set(revealContent, {
        y: 38,
        scale: 0.975,
        transformOrigin: '50% 0%'
      })
      gsap.set(cards, { autoAlpha: 0, y: 22, scale: 0.985 })

      const timeline = gsap.timeline({
        defaults: { overwrite: 'auto' },
        scrollTrigger: {
          trigger: section,
          start: 'top+=100 bottom',
          once: true,
          onEnter: () => { trendingRevealState.value = 'running' }
        },
        onComplete: () => {
          trendingHasRevealed = true
          trendingRevealState.value = 'complete'
          gsap.set([section, revealContent, ...cards], { clearProps: 'opacity,transform,visibility' })
        }
      })

      timeline
        .to(section, {
          autoAlpha: 1,
          duration: 0.95,
          ease: 'back.out(1.18)'
        })
        .to(revealContent, {
          y: 0,
          scale: 1,
          duration: 0.95,
          ease: 'back.out(1.18)'
        }, 0)
        .to(cards, {
          autoAlpha: 1,
          y: 0,
          scale: 1,
          duration: 0.65,
          stagger: 0.075,
          ease: 'power3.out'
        }, 0.15)
    })
  }, section)

  ScrollTrigger.refresh()
}

watch([contentReady, () => latest.value.length], ([ready, itemCount]) => {
  if (ready && itemCount) setupTrendingReveal()
}, { flush: 'post' })

watch(homeQuery, () => {
  clearTimeout(suggestionTimer)
  activeSuggestionIndex.value = -1
  suggestionsError.value = ''
  const query = normalizedHomeQuery.value
  if (!query) {
    suggestions.value = []
    suggestionsLoading.value = false
    suggestionRequest += 1
    return
  }
  suggestions.value = []
  suggestionsLoading.value = true
  const request = ++suggestionRequest
  suggestionTimer = window.setTimeout(async () => {
    try {
      const { data } = await api.get('/mice', {
        params: { q: query, page: 1, pageSize: 12, sort: 'brand_asc' }
      })
      if (request !== suggestionRequest || query !== normalizedHomeQuery.value) return
      suggestions.value = (data.items || []).slice(0, 6)
    } catch {
      if (request !== suggestionRequest) return
      suggestions.value = []
      suggestionsError.value = '暂时无法加载匹配项'
    } finally {
      if (request === suggestionRequest) suggestionsLoading.value = false
    }
  }, 180)
})
const startViewRealtime = () => {
  stopRealtime()
  stopRealtime = onRealtime((event) => {
    if (event.type !== 'mouse.changed' && event.type !== 'sync.required') return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(loadLatest, 250)
  })
}
onMounted(() => {
  contentReadyTimer = window.setTimeout(() => { contentReady.value = true }, 190)
  // Let the route fade finish before inserting the initial card grid.
  initialLoadTimer = window.setTimeout(loadLatest, 220)
})
onActivated(() => {
  startViewRealtime()
  setupTrendingReveal()
})
onDeactivated(() => {
  stopRealtime()
  clearTimeout(realtimeTimer)
  clearTrendingReveal()
})
onBeforeUnmount(() => {
  clearTrendingReveal()
  stopRealtime()
  clearTimeout(realtimeTimer)
  clearTimeout(initialLoadTimer)
  clearTimeout(contentReadyTimer)
  clearTimeout(suggestionTimer)
  suggestionRequest += 1
})
</script>

<template>
  <main class="home-page">
    <section class="home-hero section-shell">
      <div class="hero-copy">
        <p class="hero-kicker">GearDB 鼠标数据库</p>
        <h1>找到真正适合你的鼠标</h1>
        <p class="hero-summary">按重量、尺寸、外形和传感器筛选，再结合握姿支撑热力图与参数对比做出判断。</p>
        <form ref="searchForm" class="home-search tech-search" role="search" @submit.prevent="searchCatalog" @focusout="handleSearchFocusOut">
          <span class="tech-search-glow" aria-hidden="true"></span>
          <span class="tech-search-border" aria-hidden="true"></span>
          <div class="tech-search-main">
            <span class="tech-search-icon" aria-hidden="true"></span>
            <input
              ref="searchInput"
              v-model="homeQuery"
              class="tech-search-input"
              type="search"
              role="combobox"
              aria-label="全站搜索鼠标"
              aria-autocomplete="list"
              aria-controls="home-search-suggestions"
              :aria-expanded="suggestionsOpen"
              :aria-activedescendant="activeSuggestionId"
              placeholder="搜索品牌或型号"
              autocomplete="off"
              @focus="searchFocused = true"
              @keydown="handleSearchKeydown"
            >
            <button class="tech-search-button" type="submit">搜索</button>
          </div>
          <div v-if="suggestionsOpen" id="home-search-suggestions" class="home-search-suggestions" role="listbox" aria-label="匹配的鼠标">
            <div v-if="suggestionsLoading" class="home-search-state" role="status">
              <span class="suggestion-loading-bar"></span>
              <span class="suggestion-loading-bar"></span>
              <span class="suggestion-loading-bar short"></span>
            </div>
            <p v-else-if="suggestionsError" class="home-search-state is-error" role="status">{{ suggestionsError }}</p>
            <template v-else-if="suggestions.length">
              <button
                v-for="(mouse, index) in suggestions"
                :id="`home-search-option-${mouse.id}`"
                :key="mouse.id"
                class="home-search-option"
                :class="{ 'is-active': index === activeSuggestionIndex }"
                type="button"
                role="option"
                :aria-selected="index === activeSuggestionIndex"
                @mouseenter="activeSuggestionIndex = index"
                @mousedown.prevent
                @click="openSuggestion(mouse)"
              >
                <span class="home-search-option-copy">
                  <strong>{{ mouseName(mouse) }}</strong>
                  <small>{{ mouse.sensorName || '传感器信息待补充' }}</small>
                </span>
                <span class="home-search-option-specs">
                  <b>{{ mouse.weightG ? `${mouse.weightG}g` : '重量待补充' }}</b>
                  <b>{{ mouse.maxPollingRateHz ? `${mouse.maxPollingRateHz}Hz` : '回报率待补充' }}</b>
                </span>
              </button>
              <button class="home-search-all" type="submit" @mouseenter="activeSuggestionIndex = -1">查看“{{ normalizedHomeQuery }}”的全部结果</button>
            </template>
            <p v-else class="home-search-state" role="status">没有找到匹配的鼠标</p>
          </div>
        </form>
        <div class="hero-actions">
          <RouterLink class="hero-cta hero-cta-primary" to="/mice">浏览鼠标库 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink class="hero-cta hero-cta-secondary" to="/recommend">开始鼠标推荐</RouterLink>
        </div>
      </div>
    </section>
    <template v-if="contentReady">
    <section
      ref="trendingSection"
      class="section-shell trending-section"
      :data-reveal-state="trendingRevealState"
    >
      <div class="trending-reveal-content">
        <div class="section-heading ruled-heading">
          <div><h2>近期新品</h2><p>{{ total }} 款鼠标已收录</p></div>
          <div class="trending-heading-actions">
            <RouterLink class="inline-link" to="/mice">查看全部 <span>→</span></RouterLink>
          </div>
        </div>
        <div class="trending-grid" :aria-label="shouldLoopLatest ? '近期新品，自动循环轮播' : '近期新品'">
          <div class="trending-track" :class="{ 'is-static': !shouldLoopLatest }">
            <div class="trending-set">
              <div v-for="(mouse, index) in latest" :key="mouse.id" class="trending-card-slot" :data-reveal-order="index">
                <MouseCard :mouse="mouse" :index="index" />
              </div>
            </div>
            <div v-if="shouldLoopLatest" class="trending-set" aria-hidden="true" inert>
              <div v-for="(mouse, index) in latest" :key="`loop-${mouse.id}`" class="trending-card-slot">
                <MouseCard :mouse="mouse" :index="index" />
              </div>
            </div>
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
  grid-template-columns: minmax(0, 1fr) !important;
  place-items: center;
  padding: 84px 0 52px;
}

.home-hero::before,
.home-hero::after {
  display: none;
}

.home-hero .hero-copy {
  width: 100%;
  max-width: 680px;
  margin-inline: auto;
  text-align: center;
  animation: home-hero-slide-in 680ms cubic-bezier(0.16, 1, 0.3, 1) both;
}

.hero-kicker {
  margin: 0;
  color: var(--figma-cyan);
  font-size: 0.76rem;
  font-weight: 650;
  letter-spacing: 0.035em;
}

.hero-copy h1 {
  margin: 12px 0 0;
  color: var(--figma-text);
  font-size: 2.8rem;
  font-weight: 680;
  line-height: 1.08;
  letter-spacing: -0.035em;
  text-wrap: balance;
}

.hero-summary {
  max-width: 610px;
  margin: 16px auto 0;
  color: var(--figma-text-soft);
  font-size: 1rem;
  line-height: 1.65;
  text-wrap: pretty;
}

/* Adapted from Uiverse Lakshay-art: technical input with one restrained accent border. */
.tech-search {
  position: relative;
  isolation: isolate;
  width: min(100%, 580px);
  margin-top: 28px;
}

.tech-search-glow {
  position: absolute;
  z-index: -2;
  inset: 10px 18px -7px;
  border-radius: var(--gear-radius);
  background: rgb(120 223 92 / 20%);
  filter: blur(22px);
  opacity: 0;
  pointer-events: none;
  transition: opacity 220ms ease;
}

.tech-search-border {
  position: absolute;
  z-index: 0;
  inset: -1px;
  overflow: hidden;
  border-radius: calc(var(--gear-radius) + 1px);
  background: var(--gear-line-strong);
  pointer-events: none;
}

.tech-search-border::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  width: 760px;
  height: 760px;
  background: conic-gradient(transparent 0 72%, var(--gear-accent) 80%, transparent 88%);
  transform: translate(-50%, -50%) rotate(36deg);
  animation: tech-search-border-spin 4.8s linear infinite;
  animation-play-state: paused;
}

.tech-search-main {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: 10px;
  padding: 6px 6px 6px 17px;
  border-radius: var(--gear-radius);
  background: var(--gear-bg-soft);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 5%);
}

.tech-search-icon {
  position: relative;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
}

.tech-search-icon::before {
  content: "";
  position: absolute;
  top: 2px;
  left: 2px;
  width: 9px;
  height: 9px;
  border: 2px solid var(--gear-accent);
  border-radius: 50%;
}

.tech-search-icon::after {
  content: "";
  position: absolute;
  right: 1px;
  bottom: 3px;
  width: 7px;
  height: 2px;
  border-radius: 2px;
  background: var(--gear-accent);
  transform: rotate(45deg);
  transform-origin: right center;
}

.tech-search-input {
  min-width: 0;
  width: 100%;
  min-height: 42px;
  padding: 0;
  border: 0 !important;
  border-radius: 0 !important;
  outline: 0;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--gear-text);
  font-size: 0.94rem;
}

.tech-search-input::placeholder {
  color: var(--gear-muted);
  opacity: 1;
}

.tech-search-button {
  flex: 0 0 auto;
  min-width: 72px;
  min-height: 42px;
  padding: 0 15px;
  border: 1px solid var(--gear-line-strong);
  border-radius: var(--gear-control-radius);
  background: rgb(255 255 255 / 3%);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 5%);
  color: var(--gear-text-soft);
  font-size: 0.84rem;
  font-weight: 750;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease, transform 140ms ease;
}

.tech-search:hover .tech-search-border::before,
.tech-search:focus-within .tech-search-border::before {
  animation-play-state: running;
}

.tech-search:hover .tech-search-glow,
.tech-search:focus-within .tech-search-glow {
  opacity: 0.7;
}

.tech-search-button:hover {
  border-color: var(--gear-accent-line);
  background: var(--gear-accent-soft);
  color: var(--gear-accent-strong);
}

.tech-search-button:active {
  transform: scale(0.96);
}

.home-search-suggestions {
  position: absolute;
  z-index: 12;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  overflow: hidden;
  border: 1px solid var(--gear-line-strong);
  border-radius: var(--gear-control-radius);
  background: #0d110e;
  box-shadow: 0 18px 48px rgb(0 0 0 / 38%);
  text-align: left;
}

.home-search-option {
  display: grid;
  width: 100%;
  min-height: 58px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 9px 13px;
  border: 0;
  border-bottom: 1px solid var(--gear-line);
  background: transparent;
  color: var(--gear-text);
  cursor: pointer;
  text-align: left;
  transition: background-color 120ms ease, color 120ms ease;
}

.home-search-option.is-active,
.home-search-option:focus-visible {
  outline: 0;
  background: var(--gear-accent-soft);
}

.home-search-option-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.home-search-option-copy strong,
.home-search-option-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-search-option-copy strong {
  color: var(--gear-text);
  font-size: 0.82rem;
  font-weight: 720;
}

.home-search-option-copy small {
  color: var(--gear-muted);
  font-size: 0.68rem;
}

.home-search-option-specs {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--gear-text-soft);
  font-family: var(--gear-mono);
  font-size: 0.65rem;
}

.home-search-option-specs b {
  padding: 4px 6px;
  border: 1px solid var(--gear-line);
  border-radius: 5px;
  background: rgb(255 255 255 / 2%);
  font-weight: 650;
  white-space: nowrap;
}

.home-search-all {
  display: block;
  width: 100%;
  min-height: 42px;
  padding: 0 13px;
  border: 0;
  background: rgb(255 255 255 / 2%);
  color: var(--gear-accent-strong);
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
  text-align: left;
  transition: background-color 120ms ease;
}

.home-search-all:hover,
.home-search-all:focus-visible {
  outline: 0;
  background: var(--gear-accent-soft);
}

.home-search-state {
  display: grid;
  min-height: 70px;
  place-content: center;
  gap: 7px;
  margin: 0;
  padding: 12px;
  color: var(--gear-muted);
  font-size: 0.75rem;
  text-align: center;
}

.home-search-state.is-error {
  color: #e8a6a0;
}

.suggestion-loading-bar {
  width: min(330px, 68vw);
  height: 7px;
  border-radius: 4px;
  background: linear-gradient(90deg, rgb(255 255 255 / 4%), rgb(255 255 255 / 9%), rgb(255 255 255 / 4%));
  background-size: 200% 100%;
  animation: suggestion-loading 900ms linear infinite;
}

.suggestion-loading-bar.short {
  width: min(220px, 48vw);
}

@keyframes suggestion-loading {
  to { background-position: -200% 0; }
}

@keyframes tech-search-border-spin {
  to { transform: translate(-50%, -50%) rotate(396deg); }
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 24px;
}

.hero-cta {
  display: inline-flex;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 19px;
  border-radius: 999px;
  font-size: 0.88rem;
  font-weight: 650;
  line-height: 1;
  text-decoration: none;
  transition: background-color 180ms ease, color 180ms ease, transform 140ms cubic-bezier(0.25, 1, 0.5, 1);
}

.hero-cta:active {
  transform: scale(0.97);
}

.hero-cta-primary span {
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.hero-cta-primary,
.hero-cta-primary:visited {
  background: var(--figma-cyan);
  color: #071006;
}

.hero-cta-primary:hover {
  background: var(--figma-cyan-strong);
  color: #071006;
}

.hero-cta-primary:hover span,
.hero-cta-primary:focus-visible span {
  transform: translate3d(3px, 0, 0);
}

.hero-cta-secondary,
.hero-cta-secondary:visited {
  background: var(--figma-surface-high);
  color: var(--figma-text-soft);
}

.hero-cta-secondary:hover {
  background: var(--figma-surface-hover);
  color: var(--figma-text);
}

.home-page .trending-section {
  padding-top: 18px;
}

.home-page .trending-section[data-reveal-state='pending'] {
  opacity: 0;
  visibility: hidden;
}

.trending-heading-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.trending-heading-actions .inline-link span {
  display: inline-block;
  transition: transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.trending-heading-actions .inline-link:hover span,
.trending-heading-actions .inline-link:focus-visible span {
  transform: translate3d(3px, 0, 0);
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

.home-page .trending-track.is-static {
  animation: none;
  will-change: auto;
}

.home-page .trending-set {
  display: flex;
  gap: 16px;
  padding-right: 16px;
}

.home-page .trending-card-slot {
  flex: 0 0 clamp(230px, 24vw, 290px);
  min-width: 0;
}

.home-page .trending-card-slot :deep(.mouse-card) {
  height: 100%;
  animation: none;
}

.home-page .trending-card-slot :deep(.card-product-image) {
  transition: transform 360ms cubic-bezier(0.22, 1, 0.36, 1), filter 260ms ease-out;
}

.home-page .trending-card-slot :deep(.mouse-card:hover .card-product-image),
.home-page .trending-card-slot :deep(.card-detail-link:focus-visible .card-product-image) {
  transform: scale(1.035);
  filter: saturate(1.05);
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

@keyframes home-hero-slide-in {
  from {
    opacity: 0;
    transform: translate3d(clamp(72px, 10vw, 150px), 0, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@media (max-width: 820px) {
  .home-hero {
    min-height: 250px;
    padding: 64px 0 36px;
  }

  .hero-copy h1 {
    font-size: 2.05rem;
  }

  .hero-summary {
    max-width: 34rem;
    font-size: 0.94rem;
  }

  .home-page .trending-card-slot {
    flex-basis: min(76vw, 290px);
  }
}

@media (max-width: 480px) {
  .home-hero {
    padding: 44px 0 30px;
  }

  .hero-kicker {
    font-size: 0.7rem;
  }

  .hero-copy h1 {
    font-size: 1.8rem;
  }

  .tech-search {
    min-height: 52px;
    margin-top: 22px;
  }

  .tech-search-main {
    min-height: 52px;
    gap: 8px;
    padding-left: 13px;
  }

  .tech-search-button {
    min-width: 62px;
    padding-inline: 12px;
  }

  .home-search-option {
    min-height: 56px;
    gap: 8px;
    padding-inline: 10px;
  }

  .home-search-option-specs b:last-child {
    display: none;
  }

  .hero-actions {
    flex-direction: row;
    margin-top: 20px;
  }

  .hero-cta {
    flex: 1 1 150px;
    padding-inline: 14px;
  }

}

@media (prefers-reduced-motion: reduce) {
  .home-hero .hero-copy {
    animation: none;
  }

  .hero-cta,
  .tech-search-glow,
  .tech-search-button,
  .hero-cta-primary span,
  .trending-heading-actions .inline-link span,
  .home-page .trending-card-slot :deep(.card-product-image) {
    transition-duration: 1ms;
  }

  .tech-search-border::before {
    animation: none;
  }

  .suggestion-loading-bar {
    animation: none;
  }

  .hero-cta:active,
  .hero-cta-primary:hover span,
  .hero-cta-primary:focus-visible span,
  .trending-heading-actions .inline-link:hover span,
  .trending-heading-actions .inline-link:focus-visible span,
  .home-page .trending-card-slot :deep(.mouse-card:hover .card-product-image),
  .home-page .trending-card-slot :deep(.card-detail-link:focus-visible .card-product-image) {
    transform: none;
  }

  .home-page .trending-track {
    animation: none;
    will-change: auto;
  }

}
</style>
