<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

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
let activeTimer

const completedCount = computed(() => testInputs.filter(input => counts[input.id] > 0).length)
const completionLabel = computed(() => completedCount.value === testInputs.length
  ? '七项输入均已响应'
  : `已检测 ${completedCount.value} / ${testInputs.length}`)

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
  if (event.pointerType !== 'mouse' || !isInsideCaptureBand(event)) return
  const id = hardwareButtonMap[event.button]
  if (!id) return
  if (event.button !== 0) event.preventDefault()
  activateButton(id, '实体按键')
}

const handleHardwareWheel = event => {
  if (!isInsideCaptureBand(event) || event.deltaY === 0) return
  activateButton(event.deltaY < 0 ? 'wheel-up' : 'wheel-down', '实体滚轮')
}

const preventHardwareDefault = event => {
  if (isInsideCaptureBand(event)) event.preventDefault()
}

const resetTest = () => {
  testInputs.forEach(input => { counts[input.id] = 0 })
  activeButton.value = ''
  lastInput.value = null
  history.value = []
  clearTimeout(activeTimer)
}

onMounted(() => {
  window.addEventListener('pointerdown', handleHardwarePointer)
  window.addEventListener('wheel', handleHardwareWheel, { passive: true })
  window.addEventListener('contextmenu', preventHardwareDefault)
  window.addEventListener('auxclick', preventHardwareDefault)
})

onBeforeUnmount(() => {
  clearTimeout(activeTimer)
  window.removeEventListener('pointerdown', handleHardwarePointer)
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
        <h1>鼠标按键测试</h1>
        <p>直接点击轮廓上的按键，或在页眉与页脚之间测试实体按键和滚轮方向。</p>
      </div>
      <div class="test-progress" aria-live="polite">
        <span>{{ completionLabel }}</span>
        <progress :value="completedCount" :max="testInputs.length">{{ completedCount }} / {{ testInputs.length }}</progress>
      </div>
    </header>

    <section class="mouse-test-console" aria-label="鼠标按键与滚轮测试台">
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
  margin-bottom: 30px;
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

.mouse-test-console {
  display: grid;
  min-height: 680px;
  grid-template-columns: minmax(0, 1.35fr) minmax(340px, 0.65fr);
  overflow: hidden;
  border: 1px solid var(--gear-line-strong);
  border-radius: 8px;
  background: var(--gear-bg-soft);
}

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

@media (max-width: 900px) {
  .mouse-test-page { padding-block: 40px 88px; }
  .mouse-test-heading { align-items: start; flex-direction: column; gap: 18px; }
  .test-progress { width: 100%; }
  .mouse-test-console { grid-template-columns: 1fr; }
  .mouse-visual-panel { min-height: 620px; border-right: 0; border-bottom: 1px solid var(--gear-line); }
  .mouse-outline { height: 540px; }
}

@media (max-width: 520px) {
  .mouse-test-page { padding-top: 30px; }
  .mouse-test-heading h1 { font-size: 2rem; }
  .mouse-test-console { min-height: 0; }
  .mouse-visual-panel { min-height: 520px; padding: 44px 12px 30px; }
  .mouse-outline { width: min(100%, 340px); height: 450px; }
  .mouse-visual-meta { right: 14px; left: 14px; }
  .mouse-diagnostics-panel { padding: 22px 18px; }
  .hardware-capture-status { min-height: 100px; }
}

@media (prefers-reduced-motion: reduce) {
  .mouse-region path,
  .mouse-region rect,
  .mouse-region text,
  .mouse-test-reset,
  .status-indicator,
  .mouse-button-row {
    transition-duration: 1ms;
  }

  .mouse-region.is-active path,
  .mouse-region.is-active rect,
  .mouse-test-reset:active {
    transform: none;
  }

  .mouse-region-wheel.is-scroll-up .wheel-rib,
  .mouse-region-wheel.is-scroll-down .wheel-rib,
  .wheel-scroll-arrow-up,
  .wheel-scroll-arrow-down {
    transform: none;
  }
}
</style>
