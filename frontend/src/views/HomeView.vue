<script setup>
defineOptions({ name: 'HomeView' })
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import api from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import { onRealtime } from '../services/realtime'

const latest = ref([])
const total = ref(0)
const contentReady = ref(false)
const shouldLoopLatest = computed(() => latest.value.length > 4)
const loadLatest = async () => {
  const { data } = await api.get('/mice', { params: { pageSize: 10, sort: 'newest' } })
  latest.value = data.items.slice(0, 10)
  total.value = data.page.totalItems
}
let stopRealtime = () => {}
let realtimeTimer
let initialLoadTimer
let contentReadyTimer
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
onActivated(() => { startViewRealtime() })
onDeactivated(() => { stopRealtime(); clearTimeout(realtimeTimer) })
onBeforeUnmount(() => {
  stopRealtime()
  clearTimeout(realtimeTimer)
  clearTimeout(initialLoadTimer)
  clearTimeout(contentReadyTimer)
})
</script>

<template>
  <main class="home-page">
    <section class="home-hero section-shell">
      <div class="hero-copy">
        <h1>找到真正适合你的鼠标</h1>
        <p class="hero-summary">按重量、尺寸、外形和传感器筛选，再结合握姿支撑热力图与参数对比做出判断。</p>
        <div class="hero-actions">
          <RouterLink class="hero-cta hero-cta-primary" to="/mice">浏览鼠标库 <span aria-hidden="true">→</span></RouterLink>
          <RouterLink class="hero-cta hero-cta-secondary" to="/recommend">开始鼠标推荐</RouterLink>
        </div>
      </div>
    </section>
    <template v-if="contentReady">
    <section class="section-shell trending-section">
      <div class="section-heading ruled-heading">
        <div><h2>近期新品</h2><p>{{ total }} 款鼠标已收录</p></div>
        <div class="trending-heading-actions">
          <RouterLink class="inline-link" to="/mice">查看全部 <span>→</span></RouterLink>
        </div>
      </div>
      <div class="trending-grid" :aria-label="shouldLoopLatest ? '近期新品，自动循环轮播' : '近期新品'">
        <div class="trending-track" :class="{ 'is-static': !shouldLoopLatest }">
          <div class="trending-set">
            <div v-for="(mouse, index) in latest" :key="mouse.id" class="trending-card-slot" :style="{ '--card-index': index }">
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
  animation: home-section-arrive 420ms cubic-bezier(0.22, 1, 0.36, 1) both;
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

.home-page .trending-set:first-child .trending-card-slot {
  animation: home-card-arrive 480ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--card-index, 0) * 38ms);
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

@keyframes home-section-arrive {
  from {
    opacity: 0;
    transform: translate3d(0, 10px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes home-card-arrive {
  from {
    opacity: 0;
    transform: translate3d(18px, 0, 0) scale(0.985);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
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
  .home-hero .hero-copy,
  .home-page .trending-section,
  .home-page .trending-set:first-child .trending-card-slot {
    animation: none;
  }

  .hero-cta,
  .hero-cta-primary span,
  .trending-heading-actions .inline-link span,
  .home-page .trending-card-slot :deep(.card-product-image) {
    transition-duration: 1ms;
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
