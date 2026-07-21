<script setup>
defineOptions({ name: 'RecommendationView' })
import { computed, nextTick, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import palmSupportMap from '../assets/palm-support-map.svg'
import { recommendationParams, recommendationReady, toggleSelection } from '../utils/recommendation'

const gripOptions = [
  { code: 'PALM', label: '趴握', note: '手掌大面积贴合' },
  { code: 'CLAW', label: '抓握', note: '掌根与指尖发力' },
  { code: 'FINGERTIP', label: '指握', note: '主要依靠指尖控制' },
  { code: 'MIXED', label: '混合', note: '介于多种握姿之间' }
]
const supportAreas = [
  { code: 'THUMB_BASE', label: '拇指根部', x: 22, y: 52 },
  { code: 'INDEX_BASE', label: '食指根部', x: 35, y: 38 },
  { code: 'MIDDLE_BASE', label: '中指根部', x: 49, y: 36 },
  { code: 'RING_BASE', label: '无名指根部', x: 62, y: 39 },
  { code: 'LITTLE_BASE', label: '小指根部', x: 74, y: 46 },
  { code: 'PALM_CENTER', label: '掌心', x: 50, y: 61 },
  { code: 'PALM_HEEL', label: '掌根', x: 50, y: 78 }
]

const gripStyle = ref('')
const selectedPositions = ref([])
const result = ref(null)
const loading = ref(false)
const error = ref('')
const resultsElement = ref(null)
const selectedGrip = computed(() => gripOptions.find((item) => item.code === gripStyle.value))
const selectedLabels = computed(() => supportAreas.filter((area) => selectedPositions.value.includes(area.code)).map((area) => area.label))
const ready = computed(() => recommendationReady(gripStyle.value, selectedPositions.value))
const togglePosition = (code) => {
  selectedPositions.value = toggleSelection(selectedPositions.value, code)
  result.value = null
}
const chooseGrip = (code) => { gripStyle.value = code; result.value = null }
const recommend = async () => {
  if (!ready.value) return
  loading.value = true; error.value = ''
  try {
    result.value = (await api.get('/mouse-recommendations', {
      params: recommendationParams(gripStyle.value, selectedPositions.value)
    })).data
    await nextTick()
    resultsElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}
const positionLabel = (code) => supportAreas.find((area) => area.code === code)?.label || code
const scoreText = (item) => item.gripComfortSampleCount ? Number(item.gripComfortAverage).toFixed(1) : '—'
</script>

<template>
  <main class="recommendation-page section-shell">
    <section class="recommendation-hero">
      <div>
        <p class="eyebrow">EVIDENCE MATCHER / STRICT MODE</p>
        <h1>让你的手，<br><em>反向筛选鼠标。</em></h1>
        <p>选择真实握姿和必须获得支撑的手掌部位。系统只返回已有评价中同时满足全部条件的鼠标，不用参数猜测代替人体感受。</p>
      </div>
      <aside class="match-rule-card">
        <span>匹配规则</span>
        <strong>ALL CONDITIONS</strong>
        <p>同一位对应握姿用户的单份评价，必须覆盖你选择的每一个支撑位置。</p>
        <i><b></b></i>
      </aside>
    </section>

    <section class="recommendation-lab">
      <div class="recommendation-controls">
        <div class="recommendation-step"><span>01</span><div><small>GRIP PROFILE</small><h2>选择你的握姿</h2></div></div>
        <div class="recommendation-grips" role="radiogroup" aria-label="选择握持方式">
          <button v-for="item in gripOptions" :key="item.code" type="button" role="radio" :aria-checked="gripStyle === item.code" :class="{ selected: gripStyle === item.code }" @click="chooseGrip(item.code)">
            <span>{{ item.label }}</span><small>{{ item.note }}</small><i>{{ item.code }}</i>
          </button>
        </div>
        <div class="recommendation-contract">
          <small>YOUR MATCH CONTRACT</small>
          <div><span>握姿</span><strong>{{ selectedGrip?.label || '尚未选择' }}</strong></div>
          <div><span>必要支撑</span><strong>{{ selectedLabels.length ? selectedLabels.join('、') : '尚未选择' }}</strong></div>
        </div>
        <div v-if="error" class="flash error">{{ error }}</div>
        <button class="button recommendation-submit" type="button" :disabled="!ready || loading" @click="recommend">
          {{ loading ? '正在检索评价证据…' : '查找完全匹配的鼠标 →' }}
        </button>
      </div>

      <div class="recommendation-hand-panel">
        <div class="recommendation-step"><span>02</span><div><small>REQUIRED CONTACT</small><h2>选择期望支撑位置</h2></div></div>
        <p>可多选。选得越多，匹配越严格。</p>
        <div class="recommendation-hand-map">
          <img :src="palmSupportMap" alt="手心朝上的手掌支撑位置选择图">
          <button v-for="area in supportAreas" :key="area.code" type="button" :style="{ left: `${area.x}%`, top: `${area.y}%` }" :class="{ selected: selectedPositions.includes(area.code) }" :aria-pressed="selectedPositions.includes(area.code)" :aria-label="area.label" @click="togglePosition(area.code)">
            <span>{{ selectedPositions.includes(area.code) ? '✓' : '+' }}</span><small>{{ area.label }}</small>
          </button>
        </div>
        <div class="recommendation-position-list">
          <button v-for="area in supportAreas" :key="area.code" type="button" :class="{ selected: selectedPositions.includes(area.code) }" @click="togglePosition(area.code)">{{ area.label }}</button>
        </div>
      </div>
    </section>

    <section v-if="result" ref="resultsElement" class="recommendation-results">
      <header>
        <div><p class="eyebrow">STRICT MATCH RESULTS</p><h2>{{ result.items.length ? `${result.items.length} 款完全匹配` : '暂未找到完全匹配' }}</h2></div>
        <p>已检查 {{ result.evaluatedMouseCount }} 款在库鼠标 · {{ selectedGrip?.label }} · {{ selectedLabels.join(' + ') }}</p>
      </header>

      <div v-if="result.items.length" class="recommendation-result-grid">
        <article v-for="item in result.items" :key="item.mouse.id" class="recommendation-result-card">
          <div class="recommendation-rank"><span>{{ String(item.rank).padStart(2, '0') }}</span><small>{{ item.lowSample ? '样本积累中' : '证据稳定' }}</small></div>
          <div class="recommendation-product">
            <img v-if="item.mouse.imageUrl" :src="item.mouse.imageUrl" :alt="item.mouse.displayName">
            <span v-else>{{ item.mouse.brand?.slice(0, 1) || 'M' }}</span>
          </div>
          <div class="recommendation-result-copy">
            <small>{{ item.mouse.brand }}</small><h3>{{ item.mouse.displayName }}</h3>
            <div class="recommendation-match-stats"><div><strong>{{ item.exactMatchCount }}</strong><span>完全匹配评价</span></div><div><strong>{{ scoreText(item) }}</strong><span>{{ selectedGrip?.label }}舒适度</span></div></div>
            <div class="recommendation-evidence"><span v-for="(count, code) in item.positionEvidence" :key="code">{{ positionLabel(code) }} <b>{{ count }}</b></span></div>
            <RouterLink class="recommendation-detail-link" :to="`/mice/${item.mouse.id}`">查看完整参数与评价 →</RouterLink>
          </div>
        </article>
      </div>
      <div v-else class="recommendation-empty">
        <span>NO COMPLETE EVIDENCE</span><h3>数据库里还没有同时覆盖这些位置的评价</h3><p>可以减少一个必要支撑位置后重试，或等待更多对应握姿用户提交支撑评价。系统不会用部分匹配冒充推荐。</p>
        <button type="button" @click="selectedPositions = selectedPositions.slice(0, -1); result = null">减少一个位置重新选择</button>
      </div>
    </section>
  </main>
</template>
