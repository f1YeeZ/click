<script setup>
defineOptions({ name: 'LeaderboardView' })
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import api, { errorMessage } from '../api/client'

const sort = ref('comfort')
const gripStyle = ref('')
const displayedSort = ref('comfort')
const displayedGripStyle = ref('')
const data = ref({ items: [], globalAverage: 7, priorSampleSize: 20, totalReviews: 0 })
const loading = ref(false)
const error = ref('')
const hasSettled = ref(false)
let requestSequence = 0
let initialLoadTimer
const contentReady = ref(false)
let contentReadyTimer
const introReady = ref(false)
let introFrame
let introFrameNext

const cancelIntroFrames = () => {
  cancelAnimationFrame(introFrame)
  cancelAnimationFrame(introFrameNext)
}
const playIntro = async () => {
  cancelIntroFrames()
  introReady.value = false
  await nextTick()
  introFrame = requestAnimationFrame(() => {
    introFrameNext = requestAnimationFrame(() => { introReady.value = true })
  })
}

const sortOptions = [{ value: 'comfort', label: '握持舒适', note: 'Comfort' }]
const displayedOption = computed(() => sortOptions.find(option => option.value === displayedSort.value) || sortOptions[0])
const gripOptions = [
  { value: '', label: '全部握姿', note: 'ALL GRIPS' },
  { value: 'PALM', label: '趴握', note: 'PALM' },
  { value: 'CLAW', label: '抓握', note: 'CLAW' },
  { value: 'FINGERTIP', label: '指握', note: 'FINGERTIP' },
  { value: 'MIXED', label: '混合', note: 'MIXED' }
]
const displayedGrip = computed(() => gripOptions.find(option => option.value === displayedGripStyle.value) || gripOptions[0])
const resultKey = computed(() => `${displayedSort.value}-${displayedGripStyle.value || 'all'}`)
const podium = computed(() => data.value.items.slice(0, 3))
const remaining = computed(() => data.value.items.slice(3))
const imageUrl = (mouse) => mouse?.imageUrl || ''
const mouseName = (mouse) => mouse?.displayName || [mouse?.brand, mouse?.model].filter(Boolean).join(' ') || '未命名鼠标'
const scoreText = (value) => Number(value || 0).toFixed(1)
const load = async () => {
  const sequence = ++requestSequence
  const requestedSort = sort.value
  const requestedGripStyle = gripStyle.value
  loading.value = true; error.value = ''
  try {
    const params = { dimension: requestedSort }
    if (requestedGripStyle) params.gripStyle = requestedGripStyle
    const response = (await api.get('/mouse-rankings', { params })).data
    if (sequence !== requestSequence) return
    data.value = response
    displayedSort.value = requestedSort
    displayedGripStyle.value = requestedGripStyle
  }
  catch (e) {
    if (sequence === requestSequence) error.value = errorMessage(e)
  }
  finally {
    if (sequence === requestSequence) {
      loading.value = false
      hasSettled.value = true
    }
  }
}
watch(gripStyle, load)
onMounted(() => {
  contentReadyTimer = window.setTimeout(() => { contentReady.value = true }, 190)
  initialLoadTimer = window.setTimeout(load, 220)
})
onActivated(playIntro)
onDeactivated(() => { cancelIntroFrames(); introReady.value = false })
onBeforeUnmount(() => { requestSequence++; clearTimeout(initialLoadTimer); clearTimeout(contentReadyTimer); cancelIntroFrames() })
</script>

<template>
  <main class="section-shell leaderboard-page" :class="{ 'is-intro-ready': introReady }">
    <section class="leaderboard-hero">
      <div class="leaderboard-hero-copy">
        <p class="eyebrow">TRUST-WEIGHTED INDEX / 2026</p>
        <h1 class="visually-hidden">鼠标排行榜</h1>
        <p class="leaderboard-lead">握姿舒适度会按样本量进行可信度校准。评价越少，分数越接近全站基准；评价越多，排名越接近真实口碑。</p>
        <div class="leaderboard-stats"><div><strong>{{ data.items.length }}</strong><span>已收录鼠标</span></div><div><strong>{{ data.totalReviews }}</strong><span>有效舒适评分</span></div><div><strong>{{ data.priorSampleSize }}</strong><span>先验样本量</span></div></div>
      </div>
      <aside class="calibration-card">
        <div class="calibration-orbit"><span>μ</span><i></i><b></b></div>
        <div><p class="panel-kicker">CALIBRATION NOTE</p><h2>可信度加权</h2><p>小样本高分会向全站平均值回归，避免一两条评价决定榜首。</p><span class="calibration-formula">(n × 原始分 + 20 × 全站均分) / (n + 20)</span></div>
      </aside>
    </section>

    <section v-if="contentReady" class="leaderboard-workbench">
      <div class="leaderboard-toolbar">
        <div>
          <p class="eyebrow">RANKING MODE</p>
          <Transition name="ranking-heading" mode="out-in">
            <h2 :key="resultKey">{{ displayedOption.label }} · {{ displayedGrip.label }}</h2>
          </Transition>
        </div>
      </div>
      <div class="grip-tabs" role="tablist" aria-label="握姿分类">
        <span>COMFORT BY GRIP</span>
        <button v-for="option in gripOptions" :key="option.value || 'all'" type="button" role="tab" :aria-selected="gripStyle === option.value" :class="{ active: gripStyle === option.value }" @click="gripStyle = option.value"><b>{{ option.label }}</b><small>{{ option.note }}</small></button>
      </div>

      <div class="leaderboard-results-shell" :class="{ 'is-refreshing': loading && hasSettled }" :aria-busy="loading">
        <span v-if="loading && hasSettled" class="leaderboard-refresh-track" role="status"><span class="visually-hidden">正在更新排行榜</span><i></i></span>
        <Transition name="ranking-swap">
          <div v-if="error" key="ranking-error" class="flash error">{{ error }}</div>
          <div v-else-if="loading && !hasSettled" key="ranking-loading" class="loading-state leaderboard-loading"><span>CALIBRATING SCORES...</span><i></i></div>
          <div v-else :key="resultKey" class="leaderboard-results">
            <div v-if="podium.length" class="podium-grid">
              <article v-for="(item, index) in podium" :key="item.mouse.id" class="podium-card" :class="`podium-${index + 1}`" :style="{ '--ranking-delay': `${index * 38}ms` }">
            <div class="podium-rank"><span>{{ String(index + 1).padStart(2, '0') }}</span><i>{{ index === 0 ? 'TOP SIGNAL' : 'RANKED' }}</i></div>
            <div class="podium-product"><img v-if="imageUrl(item.mouse)" :src="imageUrl(item.mouse)" :alt="mouseName(item.mouse)"><div v-else class="podium-placeholder">{{ item.mouse.brand?.slice(0, 1) || 'M' }}</div></div>
            <div class="podium-copy"><span>{{ item.mouse.brand }}</span><h3><RouterLink :to="`/mice/${item.mouse.id}`">{{ mouseName(item.mouse) }}</RouterLink></h3><div class="podium-score"><strong>{{ scoreText(item.score) }}</strong><small>/ 10.0 · {{ item.sampleCount }} 份握姿评分</small></div></div>
              </article>
            </div>

            <div v-if="remaining.length" class="ranking-list">
              <div class="ranking-list-head"><span>名次 / 型号</span><span>{{ displayedOption.label }}</span><span>证据量</span><span>校准状态</span></div>
              <article v-for="(item, index) in remaining" :key="item.mouse.id" class="ranking-row" :style="{ '--ranking-delay': `${Math.min(index, 5) * 26 + 80}ms` }">
            <div class="rank-number">{{ String(item.rank).padStart(2, '0') }}</div>
            <div class="ranking-identity"><div class="ranking-thumb"><img v-if="imageUrl(item.mouse)" :src="imageUrl(item.mouse)" :alt="mouseName(item.mouse)"><span v-else>{{ item.mouse.brand?.slice(0, 1) || 'M' }}</span></div><div><span>{{ item.mouse.brand }}</span><h3><RouterLink :to="`/mice/${item.mouse.id}`">{{ mouseName(item.mouse) }}</RouterLink></h3></div></div>
            <div class="ranking-score"><strong>{{ scoreText(item.score) }}</strong><i><b :style="{ '--score-scale': Math.min(1, Number(item.score || 0) / 10) }"></b></i><small>原始 {{ scoreText(item.rawScore) }}</small></div>
            <div class="ranking-evidence"><strong>{{ item.sampleCount }} <small>份</small></strong><span>{{ item.lowSample ? '仍在积累' : '样本稳定' }}</span></div>
            <div class="ranking-state" :class="{ stable: !item.lowSample }"><span>{{ item.lowSample ? '先验校准中' : '高可信度' }}</span><small>{{ item.dimensionSamples.comfort || 0 }} 条当前项</small></div>
              </article>
            </div>
            <div v-else-if="!podium.length" class="empty-state leaderboard-empty">暂时还没有已发布的鼠标。</div>
          </div>
        </Transition>
      </div>
      <Transition name="ranking-heading" mode="out-in">
        <p :key="resultKey" class="leaderboard-footnote">当前排序：{{ displayedOption.label }} / {{ displayedGrip.label }}。校准基准为全站握姿舒适均值 {{ scoreText(data.globalAverage) }} / 10；排名不是广告位，评价数据会实时更新。</p>
      </Transition>
    </section>
  </main>
</template>
