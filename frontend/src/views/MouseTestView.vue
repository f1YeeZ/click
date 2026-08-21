<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { calculatePollingStats, estimatePollingRate } from '../utils/pollingRate'

const testInputs = [
  { id: 'left', label: '左键', shortLabel: 'L', hardwareButton: 0 },
  { id: 'right', label: '右键', shortLabel: 'R', hardwareButton: 2 },
  { id: 'wheel', label: '滚轮键', shortLabel: 'M', hardwareButton: 1 },
  { id: 'wheel-up', label: '滚轮上滚', shortLabel: '↑' },
  { id: 'wheel-down', label: '滚轮下滚', shortLabel: '↓' },
  { id: 'side-forward', label: '前侧键', shortLabel: 'F', hardwareButton: 4 },
  { id: 'side-back', label: '后侧键', shortLabel: 'B', hardwareButton: 3 },
]

const mouseButtons = testInputs.filter(input => Number.isInteger(input.hardwareButton))
const hardwareButtonMap = Object.fromEntries(mouseButtons.map(button => [button.hardwareButton, button.id]))
const counts = reactive(Object.fromEntries(testInputs.map(input => [input.id, 0])))
const activeButton = ref('')
const lastInput = ref(null)
const history = ref([])
const activeTool = ref('buttons')
const pollingState = ref('idle')
const pollingSamples = ref([])
const pollingHistory = ref(Array(32).fill(0))
const pollingCurrentRate = ref(0)
const pollingAverageRate = ref(0)
const pollingPeakRate = ref(0)
const pollingSampleCount = ref(0)
const pollingElapsedMs = ref(0)
const pollingPointerActive = ref(false)
const reaction = reactive({ phase: 'idle', results: [], lastTime: 0 })
const reactionRoundCount = 5
let activeTimer
let pollingTimer
let reactionTimer
let reactionFrame
let pollingRunStartedAt = 0
let pollingElapsedBeforeRun = 0
let pollingLastSampleAt = 0
let pollingTotalSamples = 0
let pollingIntervalTotalMs = 0
let pollingIntervalCount = 0
let reactionCueStartedAt = 0

const completedCount = computed(() => testInputs.filter(input => counts[input.id] > 0).length)
const completionLabel = computed(() => completedCount.value === testInputs.length
  ? '七项输入均已响应'
  : `已检测 ${completedCount.value} / ${testInputs.length}`)
const pollingStatusLabel = computed(() => ({
  idle: '等待开始',
  running: pollingPointerActive.value ? '正在采样' : '等待移动',
  paused: '测试已暂停',
})[pollingState.value])
const pollingActionLabel = computed(() => pollingState.value === 'paused' ? '继续测试' : '开始测试')
const pollingEstimatedRate = computed(() => estimatePollingRate(pollingAverageRate.value || pollingPeakRate.value))
const pollingElapsedLabel = computed(() => `${(pollingElapsedMs.value / 1000).toFixed(1)} s`)
const pollingChartCeiling = computed(() => Math.max(
  1000,
  estimatePollingRate(Math.max(pollingPeakRate.value, pollingCurrentRate.value)),
))
const pollingChartSummary = computed(() => pollingSampleCount.value
  ? `最近采样趋势，当前 ${pollingCurrentRate.value} Hz，峰值 ${pollingPeakRate.value} Hz`
  : '尚无回报率采样数据')
const reactionAverage = computed(() => reaction.results.length
  ? Math.round(reaction.results.reduce((sum, result) => sum + result, 0) / reaction.results.length)
  : 0)
const reactionBest = computed(() => reaction.results.length ? Math.min(...reaction.results) : 0)
const reactionProgressLabel = computed(() => `${reaction.results.length} / ${reactionRoundCount}`)
const reactionRoundLabel = computed(() => Math.min(reaction.results.length + 1, reactionRoundCount))
const reactionStatusLabel = computed(() => ({
  idle: '等待开始',
  waiting: '等待信号',
  ready: '现在点击',
  falseStart: '抢跑了',
  result: `${reaction.lastTime} ms`,
  complete: '测试完成',
})[reaction.phase])
const reactionActionLabel = computed(() => ({
  idle: '开始测试',
  falseStart: '重新本轮',
  result: '下一轮',
  complete: '再测一次',
})[reaction.phase] ?? '')
const reactionHint = computed(() => ({
  idle: '开始后等待测试区变绿',
  waiting: '信号出现前不要点击',
  ready: '立即点击测试区',
  falseStart: '本轮不计成绩',
  result: '准备好后进入下一轮',
  complete: '五轮成绩已汇总',
})[reaction.phase])

const getInput = id => testInputs.find(input => input.id === id)

const activateButton = (id, source = '轮廓图') => {
  const input = getInput(id)
  if (!input) return

  counts[id] += 1
  activeButton.value = id
  lastInput.value = { id, label: input.label, source, count: counts[id] }
  history.value = [
    { id: `${Date.now()}-${id}`, label: input.label, source },
    ...history.value,
  ].slice(0, 5)

  clearTimeout(activeTimer)
  activeTimer = window.setTimeout(() => {
    if (activeButton.value === id) activeButton.value = ''
  }, 260)
}

const isInsideCaptureBand = event => {
  const headerBottom = document.querySelector('.site-header')?.getBoundingClientRect().bottom ?? 0
  const footerTop = document.querySelector('.site-footer')?.getBoundingClientRect().top ?? window.innerHeight
  return event.clientY >= headerBottom && event.clientY < footerTop
}

const handleHardwarePointer = event => {
  if (activeTool.value !== 'buttons') return
  if (event.pointerType !== 'mouse' || !isInsideCaptureBand(event)) return
  const id = hardwareButtonMap[event.button]
  if (!id) return
  if (event.button !== 0) event.preventDefault()
  activateButton(id, '实体按键')
}

const handleHardwareWheel = event => {
  if (activeTool.value !== 'buttons') return
  if (!isInsideCaptureBand(event) || event.deltaY === 0) return
  activateButton(event.deltaY < 0 ? 'wheel-up' : 'wheel-down', '实体滚轮')
}

const preventHardwareDefault = event => {
  if (activeTool.value === 'buttons' && isInsideCaptureBand(event)) event.preventDefault()
}

const resetTest = () => {
  testInputs.forEach(input => { counts[input.id] = 0 })
  activeButton.value = ''
  lastInput.value = null
  history.value = []
  clearTimeout(activeTimer)
}

const refreshPollingStats = () => {
  if (pollingState.value !== 'running') return
  const now = performance.now()
  const stats = calculatePollingStats(pollingSamples.value, now)
  pollingCurrentRate.value = stats.currentRate
  pollingAverageRate.value = pollingIntervalCount && pollingIntervalTotalMs > 0
    ? Math.round((pollingIntervalCount * 1000) / pollingIntervalTotalMs)
    : 0
  pollingSampleCount.value = pollingTotalSamples
  pollingPeakRate.value = Math.max(pollingPeakRate.value, stats.currentRate)
  pollingPointerActive.value = Boolean(pollingSamples.value.length && now - pollingSamples.value.at(-1) < 180)

  pollingElapsedMs.value = pollingElapsedBeforeRun + now - pollingRunStartedAt
  pollingHistory.value = [...pollingHistory.value.slice(1), stats.currentRate]
}

const recordPollingEvent = event => {
  if (pollingState.value !== 'running' || event.pointerType !== 'mouse') return
  const coalescedEvents = event.getCoalescedEvents?.() ?? []
  const pointerEvents = coalescedEvents.length ? coalescedEvents : [event]
  const nextSamples = pollingSamples.value

  pointerEvents.forEach(pointerEvent => {
    const timestamp = pointerEvent.timeStamp
    if (!Number.isFinite(timestamp) || timestamp <= (nextSamples.at(-1) ?? -1)) return
    nextSamples.push(timestamp)
    const interval = timestamp - pollingLastSampleAt
    if (pollingLastSampleAt && interval <= 100) {
      pollingIntervalTotalMs += interval
      pollingIntervalCount += 1
    }
    pollingLastSampleAt = timestamp
    pollingTotalSamples += 1
  })

  if (nextSamples.length > 30000) nextSamples.splice(0, nextSamples.length - 30000)
  pollingPointerActive.value = true
}

const handlePollingPointer = event => {
  if (activeTool.value !== 'polling' || !isInsideCaptureBand(event)) return
  recordPollingEvent(event)
}

const resetPollingTest = () => {
  pollingState.value = 'idle'
  pollingSamples.value = []
  pollingHistory.value = Array(32).fill(0)
  pollingCurrentRate.value = 0
  pollingAverageRate.value = 0
  pollingPeakRate.value = 0
  pollingSampleCount.value = 0
  pollingElapsedMs.value = 0
  pollingPointerActive.value = false
  pollingRunStartedAt = 0
  pollingElapsedBeforeRun = 0
  pollingLastSampleAt = 0
  pollingTotalSamples = 0
  pollingIntervalTotalMs = 0
  pollingIntervalCount = 0
}

const startPollingTest = () => {
  if (pollingState.value === 'idle') resetPollingTest()
  pollingState.value = 'running'
  pollingRunStartedAt = performance.now()
}

const pausePollingTest = () => {
  if (pollingState.value !== 'running') return
  pollingElapsedBeforeRun += performance.now() - pollingRunStartedAt
  pollingElapsedMs.value = pollingElapsedBeforeRun
  pollingState.value = 'paused'
  pollingPointerActive.value = false
  pollingLastSampleAt = 0
}

const cancelReactionRound = () => {
  clearTimeout(reactionTimer)
  cancelAnimationFrame(reactionFrame)
  reactionCueStartedAt = 0
  reaction.phase = reaction.results.length ? 'result' : 'idle'
}

const resetReactionTest = () => {
  clearTimeout(reactionTimer)
  cancelAnimationFrame(reactionFrame)
  reaction.phase = 'idle'
  reaction.results = []
  reaction.lastTime = 0
  reactionCueStartedAt = 0
}

const startReactionRound = () => {
  if (reaction.phase === 'complete') resetReactionTest()
  clearTimeout(reactionTimer)
  cancelAnimationFrame(reactionFrame)
  reaction.phase = 'waiting'
  reactionCueStartedAt = 0
  const delay = 1200 + Math.random() * 1800
  reactionTimer = window.setTimeout(() => {
    reactionFrame = requestAnimationFrame(timestamp => {
      reactionCueStartedAt = timestamp
      reaction.phase = 'ready'
    })
  }, delay)
}

const recordReaction = () => {
  if (reaction.phase === 'waiting') {
    clearTimeout(reactionTimer)
    cancelAnimationFrame(reactionFrame)
    reaction.phase = 'falseStart'
    return
  }
  if (reaction.phase !== 'ready') return

  const elapsed = Math.max(1, Math.round(performance.now() - reactionCueStartedAt))
  reaction.results.push(elapsed)
  reaction.lastTime = elapsed
  reactionCueStartedAt = 0
  reaction.phase = reaction.results.length >= reactionRoundCount ? 'complete' : 'result'
}

const selectTool = tool => {
  if (tool === activeTool.value) return
  if (tool !== 'polling') pausePollingTest()
  if (tool !== 'reaction' && ['waiting', 'ready'].includes(reaction.phase)) cancelReactionRound()
  activeTool.value = tool
}

const focusToolTab = tool => {
  selectTool(tool)
  requestAnimationFrame(() => document.getElementById(`mouse-${tool}-tab`)?.focus())
}

onMounted(() => {
  window.addEventListener('pointerdown', handleHardwarePointer)
  window.addEventListener('pointermove', handlePollingPointer, { passive: true })
  window.addEventListener('wheel', handleHardwareWheel, { passive: true })
  window.addEventListener('contextmenu', preventHardwareDefault)
  window.addEventListener('auxclick', preventHardwareDefault)
  pollingTimer = window.setInterval(refreshPollingStats, 120)
})

onBeforeUnmount(() => {
  clearTimeout(activeTimer)
  clearTimeout(reactionTimer)
  cancelAnimationFrame(reactionFrame)
  clearInterval(pollingTimer)
  window.removeEventListener('pointerdown', handleHardwarePointer)
  window.removeEventListener('pointermove', handlePollingPointer)
  window.removeEventListener('wheel', handleHardwareWheel)
  window.removeEventListener('contextmenu', preventHardwareDefault)
  window.removeEventListener('auxclick', preventHardwareDefault)
})
</script>

<template>
  <main class="mouse-test-page section-shell">
    <header class="mouse-test-heading">
      <div>
        <p class="page-kicker">输入诊断工具</p>
        <h1>鼠标测试</h1>
        <p>检查按键输入、USB 回报率和视觉点击反应时间。</p>
      </div>
      <div v-if="activeTool === 'buttons'" class="test-progress" aria-live="polite">
        <span>{{ completionLabel }}</span>
        <progress :value="completedCount" :max="testInputs.length">{{ completedCount }} / {{ testInputs.length }}</progress>
      </div>
      <div v-else-if="activeTool === 'polling'" class="polling-heading-status" :class="`is-${pollingState}`" aria-live="polite">
        <span aria-hidden="true"></span>
        {{ pollingStatusLabel }}
      </div>
      <div v-else class="polling-heading-status" :class="`is-${reaction.phase}`" aria-live="polite">
        <span aria-hidden="true"></span>
        {{ reactionStatusLabel }}
      </div>
    </header>

    <div class="mouse-test-workspace">
    <div class="mouse-test-mode-deck" data-testid="mouse-test-mode-switcher">
      <div class="mouse-test-modes" role="tablist" aria-label="选择鼠标测试类型">
        <button
          id="mouse-buttons-tab"
          type="button"
          role="tab"
          aria-label="按键测试"
          :aria-selected="activeTool === 'buttons'"
          :tabindex="activeTool === 'buttons' ? 0 : -1"
          aria-controls="mouse-buttons-panel"
          @click="selectTool('buttons')"
          @keydown.down.prevent="focusToolTab('polling')"
          @keydown.right.prevent="focusToolTab('polling')"
          @keydown.end.prevent="focusToolTab('reaction')"
        >
          <span class="mode-track-info"><strong>按键测试</strong><small>检测七项按键输入</small></span>
          <i class="mode-code" aria-hidden="true">BUTTONS</i>
        </button>
        <button
          id="mouse-polling-tab"
          type="button"
          role="tab"
          aria-label="回报率测试"
          :aria-selected="activeTool === 'polling'"
          :tabindex="activeTool === 'polling' ? 0 : -1"
          aria-controls="mouse-polling-panel"
          @click="selectTool('polling')"
          @keydown.up.prevent="focusToolTab('buttons')"
          @keydown.left.prevent="focusToolTab('buttons')"
          @keydown.down.prevent="focusToolTab('reaction')"
          @keydown.right.prevent="focusToolTab('reaction')"
          @keydown.home.prevent="focusToolTab('buttons')"
          @keydown.end.prevent="focusToolTab('reaction')"
        >
          <span class="mode-track-info"><strong>回报率测试</strong><small>估算 USB 实时回报率</small></span>
          <i class="mode-code" aria-hidden="true">POLLING</i>
        </button>
        <button
          id="mouse-reaction-tab"
          type="button"
          role="tab"
          aria-label="反应测试"
          :aria-selected="activeTool === 'reaction'"
          :tabindex="activeTool === 'reaction' ? 0 : -1"
          aria-controls="mouse-reaction-panel"
          @click="selectTool('reaction')"
          @keydown.up.prevent="focusToolTab('polling')"
          @keydown.left.prevent="focusToolTab('polling')"
          @keydown.home.prevent="focusToolTab('buttons')"
        >
          <span class="mode-track-info"><strong>反应测试</strong><small>完成五轮视觉点击测试</small></span>
          <i class="mode-code" aria-hidden="true">REACTION</i>
        </button>
        <span class="mode-glider-container" aria-hidden="true"><span class="mode-glider" data-testid="mouse-mode-glider"></span></span>
      </div>
    </div>

    <section
      v-if="activeTool === 'buttons'"
      id="mouse-buttons-panel"
      class="mouse-test-console"
      role="tabpanel"
      aria-labelledby="mouse-buttons-tab"
    >
      <div class="mouse-visual-panel">
        <div class="mouse-visual-meta" aria-hidden="true">
          <span>TOP VIEW / 5 INPUTS</span>
          <span>GPW-CLASS SHELL</span>
        </div>

        <svg
          class="mouse-outline"
          data-testid="mouse-outline"
          viewBox="0 0 440 620"
          role="img"
          aria-labelledby="mouse-outline-title mouse-outline-desc"
          @contextmenu.prevent
        >
          <title id="mouse-outline-title">五键鼠标俯视轮廓</title>
          <desc id="mouse-outline-desc">对称式鼠标轮廓，包含左键、右键、滚轮键、前侧键和后侧键五个可操作区域。</desc>

          <path
            class="mouse-shell-shadow"
            d="M220 18C143 18 93 58 73 127C60 173 59 252 63 335C67 425 82 508 132 563C156 590 185 604 220 604C255 604 284 590 308 563C358 508 373 425 377 335C381 252 380 173 367 127C347 58 297 18 220 18Z"
          />
          <path
            class="mouse-shell"
            d="M220 25C148 25 101 63 82 131C70 176 69 252 73 333C77 419 91 497 138 549C160 574 187 588 220 588C253 588 280 574 302 549C349 497 363 419 367 333C371 252 370 176 358 131C339 63 292 25 220 25Z"
          />
          <path class="mouse-shell-detail" d="M83 245C106 263 143 274 220 274C297 274 334 263 357 245" />
          <path class="mouse-shell-detail faint" d="M112 430C139 449 175 458 220 458C265 458 301 449 328 430" />

          <g
            class="mouse-region mouse-region-left"
            :class="{ 'is-active': activeButton === 'left', 'is-tested': counts.left > 0 }"
            role="button"
            tabindex="0"
            aria-label="测试左键"
            :aria-pressed="activeButton === 'left'"
            data-testid="mouse-region-left"
            @pointerdown.stop.prevent="activateButton('left')"
            @keydown.enter.prevent="activateButton('left', '键盘')"
            @keydown.space.prevent="activateButton('left', '键盘')"
          >
            <path d="M211 42C151 44 111 75 94 133C85 165 82 205 87 244C116 258 154 264 211 265V42Z" />
            <text x="143" y="153">L</text>
          </g>

          <g
            class="mouse-region mouse-region-right"
            :class="{ 'is-active': activeButton === 'right', 'is-tested': counts.right > 0 }"
            role="button"
            tabindex="0"
            aria-label="测试右键"
            :aria-pressed="activeButton === 'right'"
            data-testid="mouse-region-right"
            @pointerdown.stop.prevent="activateButton('right')"
            @keydown.enter.prevent="activateButton('right', '键盘')"
            @keydown.space.prevent="activateButton('right', '键盘')"
          >
            <path d="M229 42C289 44 329 75 346 133C355 165 358 205 353 244C324 258 286 264 229 265V42Z" />
            <text x="297" y="153">R</text>
          </g>

          <g
            class="mouse-region mouse-region-wheel"
            :class="{
              'is-active': ['wheel', 'wheel-up', 'wheel-down'].includes(activeButton),
              'is-tested': counts.wheel > 0 || counts['wheel-up'] > 0 || counts['wheel-down'] > 0,
              'is-scroll-up': activeButton === 'wheel-up',
              'is-scroll-down': activeButton === 'wheel-down',
            }"
            role="button"
            tabindex="0"
            aria-label="测试滚轮键"
            :aria-pressed="activeButton === 'wheel'"
            data-testid="mouse-region-wheel"
            @pointerdown.stop.prevent="activateButton('wheel')"
            @keydown.enter.prevent="activateButton('wheel', '键盘')"
            @keydown.space.prevent="activateButton('wheel', '键盘')"
          >
            <rect x="199" y="77" width="42" height="116" rx="20" />
            <path class="wheel-scroll-arrow wheel-scroll-arrow-up" d="M211 98L220 89L229 98" />
            <path class="wheel-rib" d="M207 110H233M207 122H233M207 134H233M207 146H233M207 158H233" />
            <path class="wheel-scroll-arrow wheel-scroll-arrow-down" d="M211 173L220 182L229 173" />
            <text x="220" y="218">M</text>
          </g>

          <g
            class="mouse-region mouse-region-side mouse-region-side-forward"
            :class="{ 'is-active': activeButton === 'side-forward', 'is-tested': counts['side-forward'] > 0 }"
            role="button"
            tabindex="0"
            aria-label="测试前侧键"
            :aria-pressed="activeButton === 'side-forward'"
            data-testid="mouse-region-side-forward"
            @pointerdown.stop.prevent="activateButton('side-forward')"
            @keydown.enter.prevent="activateButton('side-forward', '键盘')"
            @keydown.space.prevent="activateButton('side-forward', '键盘')"
          >
            <path d="M72 241C60 244 53 255 54 270L57 290C59 300 66 305 77 302L89 297L87 245L72 241Z" />
            <text x="70" y="276">F</text>
          </g>

          <g
            class="mouse-region mouse-region-side mouse-region-side-back"
            :class="{ 'is-active': activeButton === 'side-back', 'is-tested': counts['side-back'] > 0 }"
            role="button"
            tabindex="0"
            aria-label="测试后侧键"
            :aria-pressed="activeButton === 'side-back'"
            data-testid="mouse-region-side-back"
            @pointerdown.stop.prevent="activateButton('side-back')"
            @keydown.enter.prevent="activateButton('side-back', '键盘')"
            @keydown.space.prevent="activateButton('side-back', '键盘')"
          >
            <path d="M76 309C64 311 58 321 59 335L61 355C63 366 70 371 81 367L94 361L90 313L76 309Z" />
            <text x="75" y="344">B</text>
          </g>

          <path class="mouse-center-line" d="M220 28V67M220 204V264" />
          <circle class="mouse-sensor-mark" cx="220" cy="410" r="20" />
          <circle class="mouse-sensor-dot" cx="220" cy="410" r="4" />
        </svg>

        <p class="mouse-visual-caption">点击轮廓中的任意按键区域</p>
      </div>

      <div class="mouse-diagnostics-panel">
        <div class="diagnostic-heading">
          <div>
            <p>INPUT MONITOR</p>
            <h2>实时响应</h2>
          </div>
          <button class="mouse-test-reset" type="button" @click="resetTest">
            <span aria-hidden="true">↺</span>
            重置
          </button>
        </div>

        <output class="mouse-input-status" data-testid="mouse-test-status" aria-live="polite">
          <span class="status-indicator" :class="{ 'is-active': activeButton }" aria-hidden="true"></span>
          <span>
            <small>{{ lastInput ? lastInput.source : '等待输入' }}</small>
            <strong>{{ lastInput ? `${lastInput.label}已触发` : '按下任意测试键' }}</strong>
          </span>
          <b>{{ lastInput ? `× ${lastInput.count}` : 'READY' }}</b>
        </output>

        <div class="mouse-button-rows" aria-label="按键测试结果">
          <div
            v-for="input in testInputs"
            :key="input.id"
            class="mouse-button-row"
            :class="{ 'is-active': activeButton === input.id, 'is-tested': counts[input.id] > 0 }"
            :data-testid="`mouse-count-${input.id}`"
          >
            <span class="button-code">{{ input.shortLabel }}</span>
            <strong>{{ input.label }}</strong>
            <span>{{ counts[input.id] }}</span>
          </div>
        </div>

        <div
          class="hardware-capture-status"
          role="note"
          aria-label="实体输入捕获范围"
        >
          <strong>全页面实体输入捕获已开启</strong>
          <p>页眉和页脚之间任意位置均可测试；滚轮上滚与下滚分别记录。</p>
        </div>

        <div class="input-history" aria-label="最近输入记录">
          <span>最近输入</span>
          <ol v-if="history.length">
            <li v-for="entry in history" :key="entry.id">
              <b>{{ entry.label }}</b>
              <small>{{ entry.source }}</small>
            </li>
          </ol>
          <p v-else>暂无记录</p>
        </div>
      </div>
    </section>

    <section
      v-else-if="activeTool === 'polling'"
      id="mouse-polling-panel"
      class="polling-test-console"
      role="tabpanel"
      aria-labelledby="mouse-polling-tab"
    >
      <div class="polling-capture-panel">
        <div class="mouse-visual-meta" aria-hidden="true">
          <span>POINTER EVENT CAPTURE</span>
          <span>{{ pollingElapsedLabel }}</span>
        </div>

        <div
          class="polling-capture-surface"
          :class="{ 'is-running': pollingState === 'running', 'is-active': pollingPointerActive }"
          data-testid="polling-capture-surface"
          aria-label="鼠标回报率全页面采样状态"
        >
          <span class="polling-ring polling-ring-outer" aria-hidden="true"></span>
          <span class="polling-ring polling-ring-inner" aria-hidden="true"></span>
          <span class="polling-crosshair" aria-hidden="true"></span>
          <output class="polling-capture-state" aria-live="polite">
            <small>{{ pollingState === 'running' ? 'CAPTURE AREA' : 'POLLING RATE' }}</small>
            <strong>{{ pollingStatusLabel }}</strong>
            <span>{{ pollingState === 'running' ? '在页面内容区内持续移动鼠标' : '开始后全页面内容区均可采样' }}</span>
          </output>
        </div>

        <div class="polling-controls">
          <button
            v-if="pollingState !== 'running'"
            class="polling-primary-action"
            type="button"
            @click="startPollingTest"
          >{{ pollingActionLabel }}</button>
          <button
            v-else
            class="polling-primary-action"
            type="button"
            @click="pausePollingTest"
          >暂停测试</button>
          <button class="mouse-test-reset" type="button" @click="resetPollingTest">
            <span aria-hidden="true">↺</span>
            重测
          </button>
        </div>
      </div>

      <div class="polling-diagnostics-panel">
        <div class="diagnostic-heading">
          <div>
            <p>POLLING MONITOR</p>
            <h2>实时回报率</h2>
          </div>
          <span class="polling-estimate">
            <small>估算档位</small>
            <strong>{{ pollingEstimatedRate ? `${pollingEstimatedRate} Hz` : '--' }}</strong>
          </span>
        </div>

        <output class="polling-rate-hero" data-testid="polling-current-rate" aria-live="polite">
          <strong>{{ pollingCurrentRate }}</strong>
          <span>Hz</span>
          <small>当前</small>
        </output>

        <dl class="polling-metrics">
          <div>
            <dt>平均</dt>
            <dd data-testid="polling-average-rate">{{ pollingAverageRate }} <small>Hz</small></dd>
          </div>
          <div>
            <dt>峰值</dt>
            <dd>{{ pollingPeakRate }} <small>Hz</small></dd>
          </div>
          <div>
            <dt>采样数</dt>
            <dd data-testid="polling-sample-count">{{ pollingSampleCount }}</dd>
          </div>
          <div>
            <dt>时长</dt>
            <dd>{{ pollingElapsedLabel }}</dd>
          </div>
        </dl>

        <div class="polling-chart" role="img" :aria-label="pollingChartSummary">
          <div class="polling-chart-heading">
            <span>最近趋势</span>
            <small>0 - {{ pollingChartCeiling }} Hz</small>
          </div>
          <div class="polling-bars" aria-hidden="true">
            <i
              v-for="(rate, index) in pollingHistory"
              :key="index"
              :class="{ 'has-sample': rate > 0 }"
              :style="{ height: `${rate ? Math.max(5, Math.min(100, rate / pollingChartCeiling * 100)) : 2}%` }"
            ></i>
          </div>
        </div>

        <p class="polling-caveat" role="note">
          浏览器事件合并、系统调度和屏幕刷新率会影响结果；请持续快速移动鼠标，并关闭其他高负载程序。
        </p>
      </div>
    </section>

    <section
      v-else
      id="mouse-reaction-panel"
      class="reaction-test-console"
      role="tabpanel"
      aria-labelledby="mouse-reaction-tab"
    >
      <div class="reaction-stage-panel">
        <div class="mouse-visual-meta" aria-hidden="true">
          <span>VISUAL RESPONSE</span>
          <span>ROUND {{ reactionRoundLabel }} / {{ reactionRoundCount }}</span>
        </div>

        <div
          class="reaction-surface"
          :class="`is-${reaction.phase}`"
          data-testid="reaction-surface"
        >
          <button
            v-if="['waiting', 'ready'].includes(reaction.phase)"
            class="reaction-hit-target"
            type="button"
            :aria-label="reaction.phase === 'ready' ? '立即点击记录反应时间' : '等待反应测试信号'"
            @pointerdown.prevent="recordReaction"
            @keydown.enter.prevent="recordReaction"
            @keydown.space.prevent="recordReaction"
          ></button>
          <span class="reaction-signal" aria-hidden="true">
            <i></i>
            <b v-if="reaction.phase === 'waiting'">等待</b>
            <b v-else-if="reaction.phase === 'ready'">点击</b>
          </span>
          <button
            v-if="reaction.phase === 'result'"
            class="reaction-center-action"
            type="button"
            @click="startReactionRound"
          >下一轮</button>
          <output class="reaction-stage-status" aria-live="assertive">
            <small>REACTION TEST</small>
            <strong>{{ reactionStatusLabel }}</strong>
            <span>{{ reactionHint }}</span>
          </output>
        </div>

        <div class="reaction-controls">
          <button
            v-if="reactionActionLabel && reaction.phase !== 'result'"
            class="reaction-primary-action"
            type="button"
            @click="startReactionRound"
          >{{ reactionActionLabel }}</button>
          <button
            v-else-if="['waiting', 'ready'].includes(reaction.phase)"
            class="mouse-test-reset"
            type="button"
            @click="cancelReactionRound"
          >取消本轮</button>
          <button
            v-if="reaction.results.length || reaction.phase === 'falseStart'"
            class="mouse-test-reset"
            type="button"
            @click="resetReactionTest"
          >重置成绩</button>
        </div>
      </div>

      <div class="reaction-results-panel">
        <div class="diagnostic-heading">
          <div>
            <p>REACTION MONITOR</p>
            <h2>反应结果</h2>
          </div>
          <span class="reaction-progress">
            <small>已完成</small>
            <strong>{{ reactionProgressLabel }}</strong>
          </span>
        </div>

        <output class="reaction-time-hero" data-testid="reaction-latest-time" aria-live="polite">
          <strong>{{ reaction.lastTime || '--' }}</strong>
          <span>ms</span>
          <small>最近一次</small>
        </output>

        <dl class="reaction-metrics">
          <div>
            <dt>平均</dt>
            <dd data-testid="reaction-average-time">{{ reactionAverage || '--' }} <small>ms</small></dd>
          </div>
          <div>
            <dt>最佳</dt>
            <dd>{{ reactionBest || '--' }} <small>ms</small></dd>
          </div>
        </dl>

        <div class="reaction-rounds" aria-label="五轮反应成绩">
          <div v-for="round in reactionRoundCount" :key="round" :class="{ 'has-result': reaction.results[round - 1] }">
            <span>R{{ round }}</span>
            <strong>{{ reaction.results[round - 1] ? `${reaction.results[round - 1]} ms` : '--' }}</strong>
          </div>
        </div>

        <p class="polling-caveat" role="note">
          结果包含人体反应、鼠标、系统、浏览器和显示器延迟，只适合在相同设备与环境下比较。
        </p>
      </div>
    </section>
    </div>
  </main>
</template>

<style scoped>
.mouse-test-page {
  padding-block: 58px 72px;
}

.mouse-test-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 22px;
}

.mouse-test-heading h1 {
  margin: 0;
  font-size: 2.55rem;
  font-weight: 720;
  line-height: 1.08;
}

.mouse-test-heading > div:first-child > p:last-child {
  max-width: 660px;
  margin: 14px 0 0;
  color: var(--gear-text-soft);
  line-height: 1.65;
}

.test-progress {
  display: grid;
  width: min(280px, 28vw);
  gap: 9px;
  color: var(--gear-text-soft);
  font-size: 0.78rem;
  font-weight: 650;
}

.test-progress progress {
  width: 100%;
  height: 5px;
  overflow: hidden;
  border: 0;
  border-radius: 3px;
  appearance: none;
  background: var(--gear-line);
}

.test-progress progress::-webkit-progress-bar { background: var(--gear-line); }
.test-progress progress::-webkit-progress-value { background: var(--gear-accent); }
.test-progress progress::-moz-progress-bar { background: var(--gear-accent); }

.polling-heading-status {
  display: inline-flex;
  min-height: 32px;
  align-items: center;
  gap: 9px;
  color: var(--gear-text-soft);
  font-size: 0.78rem;
  font-weight: 650;
}

.polling-heading-status > span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gear-line-strong);
}

.polling-heading-status.is-running > span {
  background: var(--gear-accent);
  box-shadow: 0 0 0 5px var(--gear-accent-soft), 0 0 16px var(--gear-accent);
}

.polling-heading-status.is-ready > span,
.polling-heading-status.is-complete > span {
  background: var(--gear-success);
  box-shadow: 0 0 0 5px rgb(120 223 159 / 12%), 0 0 16px rgb(120 223 159 / 45%);
}

.polling-heading-status.is-waiting > span { background: var(--gear-warning); }
.polling-heading-status.is-falseStart > span { background: var(--gear-danger); }

.mouse-test-workspace {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}

.mouse-test-mode-deck {
  position: sticky;
  top: 92px;
  width: 100%;
  min-width: 0;
}

.mouse-test-modes {
  position: relative;
  display: flex;
  flex-direction: column;
  padding-left: 12px;
}

.mouse-test-modes button {
  position: relative;
  z-index: 1;
  display: grid;
  min-width: 0;
  min-height: 82px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--gear-muted);
  cursor: pointer;
  text-align: left;
  touch-action: manipulation;
  transition: color 180ms ease, background-color 180ms ease;
}

.mouse-test-modes button:hover {
  background: rgb(255 255 255 / 3%);
  color: var(--gear-text-soft);
}

.mouse-test-modes button:active {
  background: rgb(120 223 92 / 7%);
}

.mouse-test-modes button:focus-visible {
  z-index: 2;
  outline: 2px solid var(--gear-accent);
  outline-offset: -2px;
}

.mode-track-info {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.mode-track-info strong {
  color: currentColor;
  font: 750 0.98rem/1.15 var(--gear-font);
}

.mode-track-info small {
  color: color-mix(in srgb, currentColor 72%, var(--gear-bg));
  font: 0.72rem/1.45 var(--gear-font);
}

.mode-code {
  color: color-mix(in srgb, currentColor 42%, transparent);
  font: normal 0.68rem/1 var(--gear-mono);
}

.mouse-test-modes button[aria-selected='true'] {
  background: linear-gradient(90deg, rgb(120 223 92 / 12%) 0%, transparent 100%);
  color: var(--gear-accent);
}

.mode-glider-container {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 1px;
  background: linear-gradient(0deg, transparent 0%, var(--gear-line-strong) 50%, transparent 100%);
  pointer-events: none;
}

.mode-glider {
  position: relative;
  display: block;
  width: 100%;
  height: calc(100% / 3);
  background: linear-gradient(0deg, transparent 0%, var(--gear-accent) 50%, transparent 100%);
  transition: transform 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.mode-glider::before {
  position: absolute;
  top: 50%;
  width: 3px;
  height: 60%;
  background: var(--gear-accent);
  content: '';
  filter: blur(6px);
  transform: translateY(-50%);
}

.mode-glider::after {
  position: absolute;
  left: 0;
  width: min(180px, 70vw);
  height: 100%;
  background: linear-gradient(90deg, rgb(120 223 92 / 8%) 0%, transparent 100%);
  content: '';
}

.mouse-test-modes button:nth-of-type(1)[aria-selected='true'] ~ .mode-glider-container .mode-glider { transform: translateY(0); }
.mouse-test-modes button:nth-of-type(2)[aria-selected='true'] ~ .mode-glider-container .mode-glider { transform: translateY(100%); }
.mouse-test-modes button:nth-of-type(3)[aria-selected='true'] ~ .mode-glider-container .mode-glider { transform: translateY(200%); }

.mouse-test-console {
  display: grid;
  min-height: 680px;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);
  overflow: hidden;
  border: 1px solid var(--gear-line-strong);
  border-radius: 8px;
  background: var(--gear-bg-soft);
}

.polling-test-console {
  display: grid;
  min-height: 680px;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);
  overflow: hidden;
  border: 1px solid var(--gear-line-strong);
  border-radius: 8px;
  background: var(--gear-bg-soft);
}

.polling-capture-panel {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
  padding: 58px 34px 30px;
  border-right: 1px solid var(--gear-line);
  background-color: #0a0d0b;
  background-image:
    linear-gradient(rgb(120 223 92 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(120 223 92 / 4%) 1px, transparent 1px);
  background-size: 24px 24px;
}

.polling-capture-surface {
  position: relative;
  display: grid;
  min-height: 480px;
  flex: 1;
  place-items: center;
  overflow: hidden;
  border: 1px solid #303b33;
  border-radius: 7px;
  background: rgb(5 8 6 / 76%);
  cursor: crosshair;
  isolation: isolate;
  touch-action: none;
}

.polling-capture-surface::before,
.polling-capture-surface::after {
  content: '';
  position: absolute;
  z-index: -1;
  background: rgb(120 223 92 / 8%);
}

.polling-capture-surface::before { width: 1px; height: 100%; }
.polling-capture-surface::after { width: 100%; height: 1px; }

.polling-capture-surface.is-running {
  border-color: var(--gear-accent-line);
}

.polling-ring {
  position: absolute;
  border: 1px solid rgb(120 223 92 / 18%);
  border-radius: 50%;
  pointer-events: none;
  transition: border-color 120ms ease, transform 120ms ease;
}

.polling-ring-outer { width: min(66%, 360px); aspect-ratio: 1; }
.polling-ring-inner { width: min(38%, 210px); aspect-ratio: 1; }

.polling-crosshair {
  width: 11px;
  height: 11px;
  border: 2px solid #566159;
  border-radius: 50%;
  background: #0a0d0b;
  box-shadow: 0 0 0 7px rgb(120 223 92 / 4%);
  pointer-events: none;
  transition: background-color 120ms ease, border-color 120ms ease, box-shadow 120ms ease;
}

.polling-capture-surface.is-active .polling-ring {
  border-color: rgb(120 223 92 / 48%);
  transform: scale(1.02);
}

.polling-capture-surface.is-active .polling-crosshair {
  border-color: var(--gear-accent-strong);
  background: var(--gear-accent);
  box-shadow: 0 0 0 7px var(--gear-accent-soft), 0 0 24px rgb(120 223 92 / 52%);
}

.polling-capture-state {
  position: absolute;
  bottom: 32px;
  display: grid;
  justify-items: center;
  gap: 5px;
  padding-inline: 20px;
  text-align: center;
  pointer-events: none;
}

.polling-capture-state small {
  color: var(--gear-accent);
  font: 0.65rem/1 var(--gear-mono);
}

.polling-capture-state strong { color: var(--gear-text); font-size: 1rem; }
.polling-capture-state span { color: var(--gear-muted); font-size: 0.72rem; }

.polling-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.polling-primary-action {
  min-height: 46px;
  padding: 0 18px;
  border: 1px solid var(--gear-accent);
  border-radius: 7px;
  background: var(--gear-accent);
  color: #071006;
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 750;
  transition: background-color 150ms ease, border-color 150ms ease, transform 120ms ease;
}

.polling-primary-action:hover {
  border-color: var(--gear-accent-strong);
  background: var(--gear-accent-strong);
}

.polling-primary-action:active { transform: scale(0.985); }

.polling-controls .mouse-test-reset {
  min-height: 46px;
}

.polling-diagnostics-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 28px;
  background: var(--gear-surface);
}

.polling-estimate {
  display: grid;
  justify-items: end;
  gap: 4px;
}

.polling-estimate small { color: var(--gear-muted); font-size: 0.65rem; }
.polling-estimate strong { color: var(--gear-accent-strong); font: 700 0.8rem/1 var(--gear-mono); }

.polling-rate-hero {
  display: grid;
  min-height: 152px;
  grid-template-columns: auto 1fr;
  align-content: center;
  align-items: baseline;
  column-gap: 8px;
  padding-block: 20px;
  border-bottom: 1px solid var(--gear-line);
}

.polling-rate-hero strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--gear-text);
  font: 750 clamp(3rem, 5vw, 4.8rem)/0.9 var(--gear-mono);
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
}

.polling-rate-hero > span { color: var(--gear-accent); font: 700 0.9rem/1 var(--gear-mono); }
.polling-rate-hero > small { grid-column: 1 / -1; margin-top: 9px; color: var(--gear-muted); font-size: 0.68rem; }

.polling-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid var(--gear-line);
}

.polling-metrics > div { padding: 16px 0; }
.polling-metrics > div:nth-child(odd) { border-right: 1px solid var(--gear-line); }
.polling-metrics > div:nth-child(even) { padding-left: 18px; }
.polling-metrics > div:nth-child(-n + 2) { border-bottom: 1px solid var(--gear-line); }
.polling-metrics dt { color: var(--gear-muted); font-size: 0.65rem; }
.polling-metrics dd { margin: 7px 0 0; color: var(--gear-text); font: 700 1rem/1 var(--gear-mono); font-variant-numeric: tabular-nums; }
.polling-metrics dd small { color: var(--gear-muted); font-size: 0.62rem; }

.polling-chart { padding-block: 20px; border-bottom: 1px solid var(--gear-line); }
.polling-chart-heading { display: flex; justify-content: space-between; gap: 12px; color: var(--gear-muted); font-size: 0.66rem; }
.polling-chart-heading small { font: 0.62rem/1 var(--gear-mono); }

.polling-bars {
  display: grid;
  height: 92px;
  grid-template-columns: repeat(32, minmax(2px, 1fr));
  align-items: end;
  gap: 3px;
  margin-top: 14px;
  border-bottom: 1px solid var(--gear-line-strong);
  background-image: linear-gradient(rgb(255 255 255 / 4%) 1px, transparent 1px);
  background-size: 100% 25%;
}

.polling-bars i {
  min-height: 2px;
  border-radius: 2px 2px 0 0;
  background: var(--gear-line-strong);
  transition: height 120ms linear, background-color 120ms ease;
}

.polling-bars i.has-sample { background: var(--gear-accent); }

.polling-caveat {
  margin: auto 0 0;
  padding-top: 18px;
  color: var(--gear-muted);
  font-size: 0.68rem;
  line-height: 1.55;
}

.reaction-test-console {
  display: grid;
  min-height: 680px;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);
  overflow: hidden;
  border: 1px solid var(--gear-line-strong);
  border-radius: 8px;
  background: var(--gear-bg-soft);
}

.reaction-stage-panel {
  position: relative;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 20px;
  padding: 58px 34px 30px;
  border-right: 1px solid var(--gear-line);
  background-color: #0a0d0b;
  background-image:
    linear-gradient(rgb(120 223 92 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(120 223 92 / 4%) 1px, transparent 1px);
  background-size: 24px 24px;
}

.reaction-surface {
  position: relative;
  display: flex;
  min-height: 480px;
  flex: 1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 28px;
  border: 1px solid #303b33;
  border-radius: 7px;
  background: rgb(5 8 6 / 76%);
  color: var(--gear-text);
  isolation: isolate;
  transition: border-color 150ms ease, background-color 150ms ease, box-shadow 150ms ease;
}

.reaction-hit-target {
  position: absolute;
  z-index: 5;
  inset: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.reaction-hit-target:focus-visible {
  outline: 2px solid var(--gear-accent);
  outline-offset: -4px;
}

.reaction-surface.is-waiting { border-color: rgb(230 189 104 / 62%); background: rgb(230 189 104 / 5%); }
.reaction-surface.is-ready {
  border-color: var(--gear-accent-strong);
  background: rgb(120 223 92 / 22%);
  box-shadow: inset 0 0 0 2px rgb(144 235 117 / 34%), inset 0 0 90px rgb(120 223 92 / 18%);
}
.reaction-surface.is-falseStart { border-color: rgb(240 127 120 / 72%); background: rgb(240 127 120 / 12%); }
.reaction-surface.is-complete { border-color: rgb(120 223 159 / 48%); }

.reaction-signal {
  position: relative;
  z-index: 1;
  display: grid;
  width: 220px;
  height: 220px;
  place-items: center;
  border: 2px solid var(--gear-line-strong);
  border-radius: 7px;
  background: var(--gear-surface);
  box-shadow: 0 0 0 24px rgb(255 255 255 / 1.5%);
  transition: border-color 150ms ease, background-color 150ms ease, box-shadow 150ms ease, transform 150ms ease;
}

.reaction-signal::before,
.reaction-signal::after {
  content: '';
  position: absolute;
  background: var(--gear-line-strong);
}

.reaction-signal::before { width: 76px; height: 1px; }
.reaction-signal::after { width: 1px; height: 76px; }

.reaction-signal i {
  width: 12px;
  height: 12px;
  border: 2px solid var(--gear-muted);
  border-radius: 50%;
  background: var(--gear-bg);
  transition: border-color 150ms ease, background-color 150ms ease, box-shadow 150ms ease;
}

.reaction-signal b {
  position: absolute;
  bottom: 28px;
  color: var(--gear-text-soft);
  font: 800 1.05rem/1 var(--gear-font);
}

.reaction-surface.is-waiting .reaction-signal { border-color: var(--gear-warning); }
.reaction-surface.is-ready .reaction-signal {
  border-color: var(--gear-accent-strong);
  background: rgb(120 223 92 / 32%);
  box-shadow: 0 0 0 26px rgb(120 223 92 / 16%), 0 0 72px rgb(120 223 92 / 52%);
  animation: reaction-ready-pulse 520ms ease-in-out infinite alternate;
}
.reaction-surface.is-ready .reaction-signal i { border-color: var(--gear-accent-strong); background: var(--gear-accent); box-shadow: 0 0 16px var(--gear-accent); }
.reaction-surface.is-ready .reaction-signal b { color: #fff; font-size: 1.45rem; }
.reaction-surface.is-falseStart .reaction-signal { border-color: var(--gear-danger); }
.reaction-surface.is-falseStart .reaction-signal i { border-color: var(--gear-danger); background: var(--gear-danger); }
.reaction-surface.is-complete .reaction-signal { border-color: var(--gear-success); }

@keyframes reaction-ready-pulse {
  from { transform: scale(1); }
  to { transform: scale(1.035); }
}

.reaction-center-action {
  position: absolute;
  z-index: 6;
  inset: 0;
  width: 166px;
  height: 62px;
  margin: auto;
  padding: 0 24px;
  border: 1px solid var(--gear-accent-strong);
  border-radius: 7px;
  background: var(--gear-accent);
  color: #071006;
  cursor: pointer;
  font-size: 0.92rem;
  font-weight: 800;
  box-shadow: 0 0 0 16px var(--gear-accent-soft), 0 0 42px rgb(120 223 92 / 28%);
  transition: background-color 150ms ease, transform 120ms ease, box-shadow 150ms ease;
}

.reaction-center-action:hover { background: var(--gear-accent-strong); box-shadow: 0 0 0 18px var(--gear-accent-soft), 0 0 52px rgb(120 223 92 / 38%); }
.reaction-center-action:active { transform: scale(0.98); }
.reaction-center-action:focus-visible { outline: 2px solid #fff; outline-offset: 3px; }

.reaction-stage-status {
  position: absolute;
  z-index: 3;
  bottom: 34px;
  display: grid;
  justify-items: center;
  gap: 6px;
  text-align: center;
}

.reaction-stage-status small { color: var(--gear-accent); font: 0.65rem/1 var(--gear-mono); }
.reaction-stage-status strong { color: var(--gear-text); font-size: 1.1rem; }
.reaction-stage-status span { color: var(--gear-muted); font-size: 0.72rem; }
.reaction-surface.is-ready .reaction-stage-status strong { color: var(--gear-accent-strong); }
.reaction-surface.is-ready .reaction-stage-status strong { font-size: 1.5rem; }
.reaction-surface.is-falseStart .reaction-stage-status strong { color: var(--gear-danger); }

.reaction-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.reaction-controls > button:only-child { grid-column: 1 / -1; }

.reaction-primary-action {
  min-height: 46px;
  padding: 0 18px;
  border: 1px solid var(--gear-accent);
  border-radius: 7px;
  background: var(--gear-accent);
  color: #071006;
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 750;
  transition: background-color 150ms ease, border-color 150ms ease, transform 120ms ease;
}

.reaction-primary-action:hover { border-color: var(--gear-accent-strong); background: var(--gear-accent-strong); }
.reaction-primary-action:active { transform: scale(0.985); }
.reaction-controls .mouse-test-reset { min-height: 46px; }

.reaction-results-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 28px;
  background: var(--gear-surface);
}

.reaction-progress {
  display: grid;
  justify-items: end;
  gap: 4px;
}

.reaction-progress small { color: var(--gear-muted); font-size: 0.65rem; }
.reaction-progress strong { color: var(--gear-accent-strong); font: 700 0.8rem/1 var(--gear-mono); }

.reaction-time-hero {
  display: grid;
  min-height: 152px;
  grid-template-columns: auto 1fr;
  align-content: center;
  align-items: baseline;
  column-gap: 8px;
  padding-block: 20px;
  border-bottom: 1px solid var(--gear-line);
}

.reaction-time-hero strong { color: var(--gear-text); font: 750 clamp(3rem, 5vw, 4.8rem)/0.9 var(--gear-mono); font-variant-numeric: tabular-nums; }
.reaction-time-hero > span { color: var(--gear-accent); font: 700 0.9rem/1 var(--gear-mono); }
.reaction-time-hero > small { grid-column: 1 / -1; margin-top: 9px; color: var(--gear-muted); font-size: 0.68rem; }

.reaction-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
  border-bottom: 1px solid var(--gear-line);
}

.reaction-metrics > div { padding: 16px 0; }
.reaction-metrics > div + div { padding-left: 18px; border-left: 1px solid var(--gear-line); }
.reaction-metrics dt { color: var(--gear-muted); font-size: 0.65rem; }
.reaction-metrics dd { margin: 7px 0 0; color: var(--gear-text); font: 700 1rem/1 var(--gear-mono); font-variant-numeric: tabular-nums; }
.reaction-metrics dd small { color: var(--gear-muted); font-size: 0.62rem; }

.reaction-rounds { display: grid; padding-block: 10px; }

.reaction-rounds > div {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid rgb(255 255 255 / 4%);
  color: var(--gear-muted);
}

.reaction-rounds span { font: 0.68rem/1 var(--gear-mono); }
.reaction-rounds strong { font: 700 0.76rem/1 var(--gear-mono); }
.reaction-rounds > div.has-result { color: var(--gear-text-soft); }

.mouse-visual-panel {
  position: relative;
  display: grid;
  min-width: 0;
  place-items: center;
  padding: 42px 34px 30px;
  border-right: 1px solid var(--gear-line);
  background-color: #0a0d0b;
  background-image:
    linear-gradient(rgb(120 223 92 / 4%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(120 223 92 / 4%) 1px, transparent 1px);
  background-size: 24px 24px;
}

.mouse-visual-meta {
  position: absolute;
  top: 22px;
  right: 24px;
  left: 24px;
  display: flex;
  justify-content: space-between;
  color: var(--gear-muted);
  font: 0.66rem/1 var(--gear-mono);
}

.mouse-outline {
  width: min(100%, 430px);
  height: min(70vh, 570px);
  overflow: visible;
  filter: drop-shadow(0 24px 34px rgb(0 0 0 / 46%));
}

.mouse-shell-shadow {
  fill: #050706;
  stroke: rgb(120 223 92 / 16%);
  stroke-width: 12;
}

.mouse-shell {
  fill: #141a16;
  stroke: #4a574d;
  stroke-width: 2;
}

.mouse-shell-detail,
.mouse-center-line {
  fill: none;
  stroke: #303b33;
  stroke-width: 2;
}

.mouse-shell-detail.faint { opacity: 0.5; }

.mouse-region {
  cursor: pointer;
  outline: none;
}

.mouse-region path,
.mouse-region rect {
  fill: #1b231d;
  stroke: #455348;
  stroke-width: 2;
  transition: fill 180ms cubic-bezier(0.23, 1, 0.32, 1), stroke 180ms cubic-bezier(0.23, 1, 0.32, 1), filter 180ms cubic-bezier(0.23, 1, 0.32, 1), transform 140ms cubic-bezier(0.23, 1, 0.32, 1);
  transform-box: fill-box;
  transform-origin: center;
}

.mouse-region text {
  fill: #768078;
  font: 700 13px var(--gear-mono);
  text-anchor: middle;
  pointer-events: none;
  transition: fill 180ms cubic-bezier(0.23, 1, 0.32, 1);
}

.mouse-region .wheel-rib {
  fill: none;
  stroke: #69756c;
  stroke-width: 2;
  pointer-events: none;
  transform-box: fill-box;
  transform-origin: center;
  transition: stroke 120ms ease, transform 140ms cubic-bezier(0.77, 0, 0.175, 1);
}

.mouse-region .wheel-scroll-arrow {
  fill: none;
  stroke: var(--gear-accent-strong);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.5;
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease, transform 140ms cubic-bezier(0.23, 1, 0.32, 1);
}

.wheel-scroll-arrow-up { transform: translateY(5px); }
.wheel-scroll-arrow-down { transform: translateY(-5px); }

.mouse-region-wheel.is-scroll-up .wheel-rib {
  stroke: var(--gear-accent-strong);
  transform: translateY(-6px);
}

.mouse-region-wheel.is-scroll-down .wheel-rib {
  stroke: var(--gear-accent-strong);
  transform: translateY(6px);
}

.mouse-region-wheel.is-scroll-up .wheel-scroll-arrow-up,
.mouse-region-wheel.is-scroll-down .wheel-scroll-arrow-down {
  opacity: 1;
  transform: translateY(0);
}

.mouse-region.is-tested path,
.mouse-region.is-tested rect {
  stroke: var(--gear-accent-line);
}

.mouse-region.is-tested text { fill: var(--gear-accent); }

.mouse-region.is-active path,
.mouse-region.is-active rect {
  fill: rgb(120 223 92 / 26%);
  stroke: var(--gear-accent);
  filter: drop-shadow(0 0 12px rgb(120 223 92 / 36%));
  transform: scale(0.985);
}

.mouse-region.is-active text { fill: var(--gear-accent-strong); }

.mouse-region:focus-visible path,
.mouse-region:focus-visible rect {
  stroke: var(--gear-accent);
  stroke-width: 4;
}

.mouse-sensor-mark {
  fill: none;
  stroke: #303b33;
  stroke-width: 2;
}

.mouse-sensor-dot { fill: #303b33; }

.mouse-visual-caption {
  position: absolute;
  bottom: 21px;
  left: 24px;
  margin: 0;
  color: var(--gear-muted);
  font-size: 0.72rem;
}

.mouse-diagnostics-panel {
  display: flex;
  min-width: 0;
  flex-direction: column;
  padding: 28px;
  background: var(--gear-surface);
}

.diagnostic-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--gear-line);
}

.diagnostic-heading p {
  margin: 0 0 5px;
  color: var(--gear-accent);
  font: 0.66rem/1 var(--gear-mono);
}

.diagnostic-heading h2 {
  margin: 0;
  font-size: 1.1rem;
}

.mouse-test-reset {
  display: inline-flex;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  border: 1px solid var(--gear-line-strong);
  border-radius: 7px;
  background: transparent;
  color: var(--gear-text-soft);
  cursor: pointer;
  font-size: 0.78rem;
  font-weight: 650;
  transition: border-color 160ms ease, color 160ms ease, background-color 160ms ease, transform 140ms cubic-bezier(0.23, 1, 0.32, 1);
}

.mouse-test-reset:hover {
  border-color: var(--gear-accent-line);
  background: var(--gear-accent-soft);
  color: var(--gear-accent-strong);
}

.mouse-test-reset:active { transform: scale(0.97); }

.mouse-input-status {
  display: grid;
  min-height: 96px;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 18px 0;
  border-bottom: 1px solid var(--gear-line);
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gear-line-strong);
  box-shadow: 0 0 0 5px rgb(255 255 255 / 2%);
  transition: background-color 140ms ease, box-shadow 140ms ease;
}

.status-indicator.is-active {
  background: var(--gear-accent);
  box-shadow: 0 0 0 5px var(--gear-accent-soft), 0 0 18px var(--gear-accent);
}

.mouse-input-status > span:nth-child(2) {
  display: grid;
  gap: 4px;
}

.mouse-input-status small {
  color: var(--gear-muted);
  font-size: 0.68rem;
}

.mouse-input-status strong {
  color: var(--gear-text);
  font-size: 1rem;
}

.mouse-input-status > b {
  color: var(--gear-accent);
  font: 700 0.75rem/1 var(--gear-mono);
}

.mouse-button-rows {
  display: grid;
  padding-block: 10px;
}

.mouse-button-row {
  display: grid;
  min-height: 48px;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid rgb(255 255 255 / 4%);
  color: var(--gear-muted);
  transition: color 140ms ease, background-color 140ms ease;
}

.mouse-button-row.is-tested { color: var(--gear-text-soft); }
.mouse-button-row.is-active { background: var(--gear-accent-soft); color: var(--gear-accent-strong); }

.button-code {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border: 1px solid var(--gear-line-strong);
  border-radius: 5px;
  font: 700 0.65rem/1 var(--gear-mono);
}

.mouse-button-row strong { font-size: 0.8rem; }
.mouse-button-row > span:last-child { font: 700 0.75rem/1 var(--gear-mono); }

.hardware-capture-status {
  position: relative;
  display: grid;
  min-height: 88px;
  align-content: center;
  gap: 5px;
  overflow: hidden;
  margin-top: 16px;
  padding: 18px 18px 18px 40px;
  border: 1px solid var(--gear-accent-line);
  border-radius: 7px;
  background: var(--gear-accent-soft);
  user-select: none;
}

.hardware-capture-status::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 18px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--gear-accent);
  box-shadow: 0 0 0 5px var(--gear-accent-soft), 0 0 16px var(--gear-accent);
  transform: translateY(-50%);
}

.hardware-capture-status strong {
  color: var(--gear-accent-strong);
  font-size: 0.82rem;
}

.hardware-capture-status p {
  max-width: 280px;
  margin: 0;
  color: var(--gear-text-soft);
  font-size: 0.7rem;
  line-height: 1.45;
}

.input-history {
  display: grid;
  gap: 8px;
  margin-top: 18px;
}

.input-history > span {
  color: var(--gear-muted);
  font-size: 0.68rem;
}

.input-history ol {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.input-history li {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 7px;
  border: 1px solid var(--gear-line);
  border-radius: 5px;
  color: var(--gear-text-soft);
  font-size: 0.66rem;
}

.input-history li small { color: var(--gear-muted); }
.input-history > p { margin: 0; color: var(--gear-muted); font-size: 0.72rem; }

@media (hover: hover) and (pointer: fine) {
  .mouse-region:hover path,
  .mouse-region:hover rect {
    fill: #222c25;
    stroke: var(--gear-accent-line);
  }
}

@media (max-width: 1100px) {
  .mouse-test-workspace { grid-template-columns: 1fr; }
  .mouse-test-mode-deck {
    position: static;
    width: min(100%, 560px);
  }
}

@media (max-width: 900px) {
  .mouse-test-page { padding-block: 40px 88px; }
  .mouse-test-heading { align-items: start; flex-direction: column; gap: 18px; }
  .test-progress { width: 100%; }
  .mouse-test-console,
  .polling-test-console,
  .reaction-test-console { grid-template-columns: 1fr; }
  .mouse-visual-panel { min-height: 620px; border-right: 0; border-bottom: 1px solid var(--gear-line); }
  .mouse-outline { height: 540px; }
  .polling-capture-panel { min-height: 620px; border-right: 0; border-bottom: 1px solid var(--gear-line); }
  .polling-capture-surface { min-height: 460px; }
  .reaction-stage-panel { min-height: 620px; border-right: 0; border-bottom: 1px solid var(--gear-line); }
  .reaction-surface { min-height: 460px; }
}

@media (min-width: 1200px) and (min-height: 1200px) {
  .mouse-test-console { min-height: 570px; }
  .mouse-outline { height: 500px; }
  .mouse-diagnostics-panel { padding: 20px; }
  .diagnostic-heading { padding-bottom: 14px; }
  .mouse-input-status { min-height: 76px; padding-block: 12px; }
  .mouse-button-rows { padding-block: 4px; }
  .mouse-button-row { min-height: 38px; }
  .hardware-capture-status { min-height: 72px; margin-top: 10px; padding-block: 12px; }
  .input-history { gap: 5px; margin-top: 10px; }
}

@media (max-width: 520px) {
  .mouse-test-page { padding-top: 30px; }
  .mouse-test-heading h1 { font-size: 2rem; }
  .mouse-test-mode-deck { width: 100%; }
  .mouse-test-console,
  .polling-test-console,
  .reaction-test-console { min-height: 0; }
  .mouse-visual-panel { min-height: 520px; padding: 44px 12px 30px; }
  .mouse-outline { width: min(100%, 340px); height: 450px; }
  .mouse-visual-meta { right: 14px; left: 14px; }
  .mouse-diagnostics-panel { padding: 22px 18px; }
  .hardware-capture-status { min-height: 100px; }
  .polling-capture-panel { min-height: 520px; padding: 50px 12px 20px; }
  .polling-capture-surface { min-height: 360px; }
  .polling-capture-state { bottom: 22px; }
  .polling-controls { grid-template-columns: minmax(0, 1fr) auto; }
  .polling-diagnostics-panel { padding: 22px 18px; }
  .polling-rate-hero strong { font-size: 3.25rem; }
  .reaction-stage-panel { min-height: 520px; padding: 50px 12px 20px; }
  .reaction-controls { order: 1; }
  .reaction-surface { min-height: 340px; order: 2; padding-inline: 12px; }
  .reaction-signal { width: 176px; height: 176px; }
  .reaction-signal b { bottom: 22px; }
  .reaction-center-action { width: 150px; height: 58px; }
  .reaction-stage-status { bottom: 22px; }
  .reaction-results-panel { padding: 22px 18px; }
  .reaction-time-hero strong { font-size: 3.25rem; }
}

@media (prefers-reduced-motion: reduce) {
  .mouse-region path,
  .mouse-region rect,
  .mouse-region text,
  .mouse-test-reset,
  .status-indicator,
  .mouse-button-row,
  .polling-ring,
  .polling-crosshair,
  .polling-bars i,
  .polling-primary-action,
  .reaction-surface,
  .reaction-signal,
  .reaction-signal i,
  .reaction-primary-action,
  .reaction-center-action,
  .mouse-test-modes button,
  .mode-glider {
    transition-duration: 1ms;
  }

  .mouse-region.is-active path,
  .mouse-region.is-active rect,
  .mouse-test-reset:active {
    transform: none;
  }

  .polling-capture-surface.is-active .polling-ring,
  .polling-primary-action:active,
  .reaction-primary-action:active,
  .reaction-center-action:active {
    transform: none;
  }

  .reaction-surface.is-ready .reaction-signal { animation: none; }

  .mouse-region-wheel.is-scroll-up .wheel-rib,
  .mouse-region-wheel.is-scroll-down .wheel-rib,
  .wheel-scroll-arrow-up,
  .wheel-scroll-arrow-down {
    transform: none;
  }
}
</style>
