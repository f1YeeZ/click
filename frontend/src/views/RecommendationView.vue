<script setup>
defineOptions({ name: 'RecommendationView' })
import { computed, nextTick, ref } from 'vue'
import api, { errorMessage } from '../api/client'
import HandSupport3D from '../components/HandSupport3D.vue'
import { recommendationShapeReady, recommendationShapeRequest } from '../utils/recommendation'
import { supportCoveragePercentage } from '../utils/supportHeatmap'

const gripOptions = [
  { code: 'PALM', label: '趴握', note: '手掌大面积贴合' },
  { code: 'CLAW', label: '抓握', note: '掌根与指尖发力' },
  { code: 'FINGERTIP', label: '指握', note: '主要依靠指尖控制' },
  { code: 'MIXED', label: '混合', note: '介于多种握姿之间' }
]
const gripStyle = ref('')
const supportDabs = ref([])
const result = ref(null)
const loading = ref(false)
const error = ref('')
const supportModelError = ref('')
const supportTool = ref('paint')
const supportBrushSize = ref(12)
const resultsElement = ref(null)
const selectedGrip = computed(() => gripOptions.find((item) => item.code === gripStyle.value))
const supportCoverage = computed(() => supportCoveragePercentage(supportDabs.value))
const supportHasPaint = computed(() => supportCoverage.value > 0)
const ready = computed(() => recommendationShapeReady(gripStyle.value, supportDabs.value))
const exactCount = computed(() => result.value?.items?.filter((item) => item.matchType === 'EXACT').length || 0)
const nearCount = computed(() => result.value?.items?.filter((item) => item.matchType === 'NEAR').length || 0)
const updateSupportDabs = (dabs) => {
  supportDabs.value = dabs
  result.value = null
}
const clearSupportSelection = () => {
  supportDabs.value = []
  result.value = null
}
const chooseGrip = (code) => { gripStyle.value = code; result.value = null }
const recommend = async () => {
  if (!ready.value) return
  loading.value = true; error.value = ''
  try {
    result.value = (await api.post('/mouse-recommendations', recommendationShapeRequest(
      gripStyle.value, supportDabs.value
    ))).data
    await nextTick()
    resultsElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) { error.value = errorMessage(e) }
  finally { loading.value = false }
}
const scoreText = (item) => item.gripComfortSampleCount ? Number(item.gripComfortAverage).toFixed(1) : '—'
const matchLabel = (item) => item.matchType === 'EXACT' ? '完全匹配' : '相近匹配'
const handleSupportModelError = () => { supportModelError.value = '三维模型加载失败，请刷新页面后重试' }
</script>

<template>
  <main class="recommendation-page section-shell">
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
          <div><span>图形范围</span><strong>{{ supportHasPaint ? `已涂抹约 ${supportCoverage}% 的掌面` : '尚未涂抹' }}</strong></div>
        </div>
        <div v-if="error" class="flash error">{{ error }}</div>
        <button class="button recommendation-submit" type="button" :disabled="!ready || loading" @click="recommend">
          {{ loading ? '正在检索评价证据…' : '查找匹配并解释原因 →' }}
        </button>
      </div>

      <div class="recommendation-hand-panel">
        <div class="recommendation-step"><span>02</span><div><small>REQUIRED CONTACT</small><h2>选择期望支撑位置</h2></div></div>
        <p>按住鼠标或用手指，在三维手掌上涂抹期望被鼠标托住的位置。</p>
        <div class="recommendation-hand-map">
          <div class="recommendation-model-stage">
            <HandSupport3D
              :dabs="supportDabs"
              :brush-size="supportBrushSize"
              :tool="supportTool"
              :editable="true"
              aria-label="可涂抹期望支撑位置的三维右手模型"
              @update:dabs="updateSupportDabs"
              @error="handleSupportModelError"
            />
            <span class="recommendation-model-hint">{{ supportTool === 'rotate' ? '拖动旋转模型，确认掌面位置后切回涂抹' : supportTool === 'erase' ? '在掌面按住并拖动擦除' : '在掌面按住并拖动连续涂抹' }}</span>
          </div>
          <div class="support-tools recommendation-support-tools" aria-label="期望支撑位置涂抹工具">
            <button type="button" :class="{ active: supportTool === 'paint' }" @click="supportTool = 'paint'">涂抹</button>
            <button type="button" :class="{ active: supportTool === 'erase' }" @click="supportTool = 'erase'">擦除</button>
            <button type="button" :class="{ active: supportTool === 'rotate' }" @click="supportTool = 'rotate'">旋转查看</button>
            <button type="button" :disabled="!supportDabs.length" @click="clearSupportSelection">清空</button>
          </div>
          <label class="support-brush-size recommendation-brush-size">
            <span><strong>画笔大小</strong><output>{{ supportBrushSize }}%</output></span>
            <input v-model.number="supportBrushSize" type="range" min="4" max="20" step="1">
          </label>
          <div class="support-selection-status recommendation-support-status">
            <strong>{{ supportHasPaint ? `已涂抹约 ${supportCoverage}% 的掌面` : '尚未涂抹期望支撑位置' }}</strong>
            <span>{{ supportHasPaint ? '推荐将直接比较涂抹图形，不再转换为位置按钮' : '请在掌面连续涂抹至少一个区域' }}</span>
          </div>
          <div v-if="supportModelError" class="recommendation-model-error" role="status">{{ supportModelError }}</div>
        </div>
      </div>
    </section>

    <section v-if="result" ref="resultsElement" class="recommendation-results">
      <header>
        <div><p class="eyebrow">EXPLAINED MATCH RESULTS</p><h2>{{ result.items.length ? `${exactCount} 款完全匹配 · ${nearCount} 款相近匹配` : '暂未找到可用证据' }}</h2></div>
        <p>已检查 {{ result.evaluatedMouseCount }} 款在库鼠标 · {{ selectedGrip?.label }} · 期望范围约 {{ supportCoverage }}% 掌面</p>
      </header>

      <div v-if="result.items.length" class="recommendation-result-grid">
        <article v-for="item in result.items" :key="item.mouse.id" class="recommendation-result-card" :class="{ 'is-near': item.matchType === 'NEAR' }">
          <div class="recommendation-rank"><span>{{ String(item.rank).padStart(2, '0') }}</span><small>{{ item.lowSample ? '样本积累中' : '证据稳定' }}</small></div>
          <div class="recommendation-product">
            <img v-if="item.mouse.imageUrl" :src="item.mouse.imageUrl" :alt="item.mouse.displayName">
            <span v-else>{{ item.mouse.brand?.slice(0, 1) || 'M' }}</span>
          </div>
          <div class="recommendation-support-preview" :class="{ empty: !item.matchedSupportCells?.length }">
            <div class="recommendation-support-preview-head">
              <span>MATCHED SUPPORT</span>
              <strong>{{ item.matchedSupportSampleCount || 0 }} 份匹配涂抹</strong>
            </div>
            <div v-if="item.matchedSupportCells?.length" class="recommendation-support-preview-stage">
              <HandSupport3D
                :summary-cells="item.matchedSupportCells"
                :max-count="item.matchedSupportMaxCount || 1"
                :grid-columns="64"
                :grid-rows="96"
                tool="view"
                :editable="false"
                :aria-label="`${item.mouse.displayName} 的${selectedGrip?.label || ''}匹配支撑位置三维涂抹`"
              />
              <span>拖动旋转查看</span>
            </div>
            <div v-else class="recommendation-support-preview-empty"><span>暂无可展示的匹配涂抹</span></div>
          </div>
          <div class="recommendation-result-copy">
            <div class="recommendation-result-topline"><small>{{ item.mouse.brand }}</small><span class="recommendation-match-badge">{{ matchLabel(item) }}</span></div><h3>{{ item.mouse.displayName }}</h3>
            <div class="recommendation-match-stats"><div><strong>{{ item.supportCoveragePercent }}%</strong><span>期望范围覆盖</span></div><div><strong>{{ item.shapeSimilarityPercent }}%</strong><span>形状相似度</span></div><div><strong>{{ scoreText(item) }}</strong><span>{{ selectedGrip?.label }}舒适度</span></div></div>
            <p class="recommendation-explanation">{{ item.explanation }}</p>
            <RouterLink class="recommendation-detail-link" :to="`/mice/${item.mouse.id}`">查看完整参数与评价 →</RouterLink>
          </div>
        </article>
      </div>
      <div v-else class="recommendation-empty">
        <span>NO USABLE EVIDENCE</span><h3>数据库里还没有相关支撑评价</h3><p>可以减少一个期望支撑位置后重试，或等待更多对应握姿用户提交支撑评价。</p>
        <button type="button" @click="result = null; supportTool = 'erase'">返回调整涂抹范围</button>
      </div>
    </section>
  </main>
</template>
