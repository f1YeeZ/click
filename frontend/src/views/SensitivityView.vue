<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import {
  FPS_GAMES,
  calculateCmPer360,
  calculateEdpi,
  convertSensitivity,
  getFpsGame,
  toPositiveNumber,
} from '../utils/sensitivity'

const form = reactive({
  sourceGameId: 'cs2',
  targetGameId: 'valorant',
  sourceSensitivity: '1',
  sourceDpi: '800',
  targetDpi: '800',
})

const copyState = ref('idle')
const isSwapping = ref(false)
let copyTimer
let swapTimer

const sourceGame = computed(() => getFpsGame(form.sourceGameId))
const targetGame = computed(() => getFpsGame(form.targetGameId))
const result = computed(() => convertSensitivity(form))
const cmPer360 = computed(() => calculateCmPer360({
  gameId: form.sourceGameId,
  sensitivity: form.sourceSensitivity,
  dpi: form.sourceDpi,
}))
const sourceEdpi = computed(() => calculateEdpi({ sensitivity: form.sourceSensitivity, dpi: form.sourceDpi }))
const targetEdpi = computed(() => calculateEdpi({ sensitivity: result.value, dpi: form.targetDpi }))
const hasR6 = computed(() => form.sourceGameId === 'r6' || form.targetGameId === 'r6')
const isValid = computed(() => result.value !== null)

const inputErrors = computed(() => ({
  sourceSensitivity: toPositiveNumber(form.sourceSensitivity) ? '' : '请输入大于 0 的游戏灵敏度',
  sourceDpi: toPositiveNumber(form.sourceDpi) ? '' : '请输入大于 0 的源 DPI',
  targetDpi: toPositiveNumber(form.targetDpi) ? '' : '请输入大于 0 的目标 DPI',
}))

const trimFixed = (value, precision) => {
  if (!Number.isFinite(value)) return '等待输入'
  return value.toFixed(precision).replace(/(\.\d*?[1-9])0+$|\.0+$/, '$1')
}
const formattedResult = computed(() => result.value === null
  ? '等待输入'
  : trimFixed(result.value, targetGame.value?.precision ?? 4))
const formattedDistance = computed(() => cmPer360.value === null ? '等待输入' : `${cmPer360.value.toFixed(2)} cm`)
const formattedSourceEdpi = computed(() => sourceEdpi.value === null ? '等待输入' : trimFixed(sourceEdpi.value, 2))
const formattedTargetEdpi = computed(() => targetEdpi.value === null ? '等待输入' : trimFixed(targetEdpi.value, 2))
const conversionSummary = computed(() => {
  if (!isValid.value) return '请先完成左侧有效输入'
  return `${sourceGame.value.shortName || sourceGame.value.name} ${form.sourceSensitivity} @ ${form.sourceDpi} DPI → ${targetGame.value.shortName || targetGame.value.name} ${formattedResult.value} @ ${form.targetDpi} DPI`
})

const swapGames = () => {
  if (!isValid.value) return
  const previousSourceGame = form.sourceGameId
  const previousSourceDpi = form.sourceDpi
  const convertedSensitivity = String(Number(result.value.toPrecision(12)))

  form.sourceGameId = form.targetGameId
  form.targetGameId = previousSourceGame
  form.sourceDpi = form.targetDpi
  form.targetDpi = previousSourceDpi
  form.sourceSensitivity = convertedSensitivity

  isSwapping.value = false
  window.requestAnimationFrame(() => { isSwapping.value = true })
  clearTimeout(swapTimer)
  swapTimer = window.setTimeout(() => { isSwapping.value = false }, 420)
}

const fallbackCopy = text => {
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  textarea.remove()
  if (!copied) throw new Error('copy failed')
}

const copyResult = async () => {
  if (!isValid.value) return
  try {
    if (navigator.clipboard?.writeText) await navigator.clipboard.writeText(formattedResult.value)
    else fallbackCopy(formattedResult.value)
    copyState.value = 'copied'
  } catch {
    try {
      fallbackCopy(formattedResult.value)
      copyState.value = 'copied'
    } catch {
      copyState.value = 'error'
    }
  }
  clearTimeout(copyTimer)
  copyTimer = window.setTimeout(() => { copyState.value = 'idle' }, 1800)
}

watch(result, () => {
  if (copyState.value !== 'idle') copyState.value = 'idle'
})

onBeforeUnmount(() => {
  clearTimeout(copyTimer)
  clearTimeout(swapTimer)
})
</script>

<template>
  <main class="sensitivity-page section-shell">
    <header class="sensitivity-heading">
      <p class="page-kicker">FPS 灵敏度工具</p>
      <h1>换游戏，不换手感</h1>
      <p>按相同的 360° 转身距离换算腰射灵敏度，让肌肉记忆直接跟进下一款游戏。</p>
    </header>

    <section class="sensitivity-workbench" aria-labelledby="converter-title">
      <form class="sensitivity-input-panel" novalidate @submit.prevent>
        <div class="panel-heading">
          <div>
            <h2 id="converter-title">输入校准参数</h2>
            <p>数值修改后，右侧结果会立即更新。</p>
          </div>
          <span class="hipfire-label">腰射</span>
        </div>

        <fieldset class="game-fieldset">
          <legend>源游戏</legend>
          <div class="field-grid source-fields">
            <label class="field field-wide">
              <span>游戏</span>
              <select v-model="form.sourceGameId" aria-label="源游戏">
                <option v-for="game in FPS_GAMES" :key="game.id" :value="game.id">{{ game.name }}</option>
              </select>
            </label>
            <label class="field">
              <span>游戏灵敏度</span>
              <input
                v-model="form.sourceSensitivity"
                data-testid="source-sensitivity"
                type="text"
                inputmode="decimal"
                :aria-invalid="Boolean(inputErrors.sourceSensitivity)"
                aria-describedby="source-sensitivity-error"
              >
              <small v-if="inputErrors.sourceSensitivity" id="source-sensitivity-error" class="field-error">{{ inputErrors.sourceSensitivity }}</small>
            </label>
            <label class="field">
              <span>源 DPI</span>
              <input
                v-model="form.sourceDpi"
                data-testid="source-dpi"
                type="text"
                inputmode="numeric"
                :aria-invalid="Boolean(inputErrors.sourceDpi)"
                aria-describedby="source-dpi-error"
              >
              <small v-if="inputErrors.sourceDpi" id="source-dpi-error" class="field-error">{{ inputErrors.sourceDpi }}</small>
            </label>
          </div>
        </fieldset>

        <div class="swap-row">
          <span aria-hidden="true"></span>
          <button class="swap-button" type="button" :disabled="!isValid" @click="swapGames">
            <span aria-hidden="true">↕</span>
            交换方向
          </button>
          <span aria-hidden="true"></span>
        </div>

        <fieldset class="game-fieldset target-fieldset">
          <legend>目标游戏</legend>
          <div class="field-grid target-fields">
            <label class="field field-wide">
              <span>游戏</span>
              <select v-model="form.targetGameId" aria-label="目标游戏">
                <option v-for="game in FPS_GAMES" :key="game.id" :value="game.id">{{ game.name }}</option>
              </select>
            </label>
            <label class="field">
              <span>目标 DPI</span>
              <input
                v-model="form.targetDpi"
                data-testid="target-dpi"
                type="text"
                inputmode="numeric"
                :aria-invalid="Boolean(inputErrors.targetDpi)"
                aria-describedby="target-dpi-error"
              >
              <small v-if="inputErrors.targetDpi" id="target-dpi-error" class="field-error">{{ inputErrors.targetDpi }}</small>
            </label>
          </div>
        </fieldset>
      </form>

      <aside class="sensitivity-result-panel" :class="{ 'is-swapping': isSwapping }" aria-live="polite">
        <div class="result-heading">
          <p>目标灵敏度</p>
          <span>{{ targetGame?.shortName || targetGame?.name }}</span>
        </div>

        <output class="result-value" data-testid="sensitivity-result" :aria-label="`目标灵敏度 ${formattedResult}`">
          {{ formattedResult }}
        </output>

        <button class="copy-button" type="button" :disabled="!isValid" @click="copyResult">
          <template v-if="copyState === 'copied'">已复制</template>
          <template v-else-if="copyState === 'error'">复制失败</template>
          <template v-else>复制数值</template>
        </button>

        <div class="measurement-strip">
          <div>
            <span>360° 转身距离</span>
            <strong data-testid="cm-per-360">{{ formattedDistance }}</strong>
          </div>
          <div>
            <span>源游戏 eDPI</span>
            <strong>{{ formattedSourceEdpi }}</strong>
          </div>
          <div>
            <span>目标游戏 eDPI</span>
            <strong>{{ formattedTargetEdpi }}</strong>
          </div>
        </div>

        <p class="conversion-summary">{{ conversionSummary }}</p>
      </aside>
    </section>

    <section class="sensitivity-notes" aria-labelledby="method-title">
      <div>
        <h2 id="method-title">换算边界</h2>
        <p>当前版本只换算腰射灵敏度。ADS、倍镜倍率和视野范围会改变视觉感受，不混入基础结果。</p>
      </div>
      <dl>
        <div>
          <dt>计算基准</dt>
          <dd>每款游戏的视角系数与物理 360° 转身距离</dd>
        </div>
        <div>
          <dt>彩虹六号设置</dt>
          <dd :class="{ 'is-relevant': hasR6 }">默认 MouseSensitivityMultiplierUnit = 0.02</dd>
        </div>
      </dl>
    </section>
  </main>
</template>

<style scoped>
.sensitivity-page {
  position: relative;
  min-height: 0;
  padding-block: clamp(44px, 6vw, 82px) 88px;
}

.sensitivity-page::before {
  position: absolute;
  z-index: -1;
  top: 20px;
  right: 4%;
  width: min(42vw, 560px);
  height: 270px;
  border-radius: 50%;
  background: radial-gradient(circle, rgb(120 223 92 / 7%), transparent 68%);
  content: '';
  filter: blur(8px);
  pointer-events: none;
}

.sensitivity-heading {
  max-width: 760px;
  margin-bottom: clamp(30px, 4vw, 50px);
}

.sensitivity-heading h1 {
  max-width: 680px;
  margin: 0;
  font-size: clamp(2.45rem, 5vw, 4.75rem);
  font-weight: 760;
  letter-spacing: -0.055em !important;
  line-height: 0.98;
}

.sensitivity-heading > p:last-child {
  max-width: 600px;
  margin: 18px 0 0;
  color: var(--gear-text-soft);
  font-size: clamp(0.98rem, 1.4vw, 1.1rem);
  line-height: 1.7;
}

.sensitivity-workbench {
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(390px, 1.08fr);
  align-items: stretch;
  gap: clamp(18px, 2.3vw, 34px);
}

.sensitivity-input-panel,
.sensitivity-result-panel {
  min-width: 0;
  border: 1px solid var(--gear-line);
  border-radius: var(--gear-radius);
}

.sensitivity-input-panel {
  padding: clamp(20px, 2.4vw, 30px);
  background: var(--gear-surface);
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 28px;
}

.panel-heading h2,
.sensitivity-notes h2 {
  margin: 0;
  font-size: 1.12rem;
  font-weight: 700;
}

.panel-heading p {
  margin: 6px 0 0;
  color: var(--gear-muted);
  font-size: 0.84rem;
  line-height: 1.55;
}

.hipfire-label {
  flex: 0 0 auto;
  padding: 5px 8px;
  border: 1px solid var(--gear-accent-line);
  border-radius: 6px;
  background: var(--gear-accent-soft);
  color: var(--gear-accent-strong);
  font-size: 0.72rem;
  font-weight: 700;
}

.game-fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.game-fieldset legend {
  width: 100%;
  margin-bottom: 14px;
  color: var(--gear-text-soft);
  font-size: 0.78rem;
  font-weight: 700;
}

.field-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(112px, 0.45fr);
  gap: 14px;
}

.source-fields .field-wide {
  grid-column: 1 / -1;
}

.target-fields {
  grid-template-columns: minmax(0, 1fr) minmax(130px, 0.55fr);
}

.field {
  display: grid;
  min-width: 0;
  gap: 8px;
  color: var(--gear-text-soft);
  font-size: 0.8rem;
  font-weight: 650;
}

.field input,
.field select {
  width: 100%;
  padding-inline: 12px;
  font-family: var(--gear-mono);
  font-size: 0.95rem !important;
  font-variant-numeric: tabular-nums;
}

.field select {
  font-family: var(--gear-font);
}

.field [aria-invalid='true'] {
  border-color: var(--gear-danger) !important;
  box-shadow: 0 0 0 3px rgb(240 127 120 / 10%) !important;
}

.field-error {
  color: #ffaaa4;
  font-size: 0.72rem;
  font-weight: 550;
  line-height: 1.45;
}

.swap-row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  margin: 22px 0;
}

.swap-row > span {
  height: 1px;
  background: var(--gear-line);
}

.swap-button,
.copy-button {
  display: inline-flex;
  min-height: 40px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border: 1px solid var(--gear-line-strong);
  border-radius: var(--gear-control-radius);
  background: var(--gear-surface-raised);
  color: var(--gear-text-soft);
  font-size: 0.82rem;
  font-weight: 700;
  white-space: nowrap;
  transition: transform 140ms ease, border-color 160ms ease, background-color 160ms ease, color 160ms ease;
}

.swap-button {
  padding: 0 13px;
}

.swap-button span {
  color: var(--gear-accent);
  font-family: var(--gear-mono);
  font-size: 1rem;
}

.swap-button:hover,
.copy-button:hover {
  border-color: var(--gear-accent-line);
  background: var(--gear-accent-soft);
  color: var(--gear-accent-strong);
}

.swap-button:active,
.copy-button:active {
  transform: scale(0.98);
}

.swap-button:disabled,
.copy-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.sensitivity-result-panel {
  position: relative;
  overflow: hidden;
  padding: clamp(24px, 3.6vw, 48px);
  background:
    linear-gradient(145deg, rgb(120 223 92 / 8%), transparent 48%),
    var(--gear-bg-soft);
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%);
}

.sensitivity-result-panel::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: var(--gear-accent);
  content: '';
}

.result-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.result-heading p,
.result-heading span {
  margin: 0;
  color: var(--gear-muted);
  font-size: 0.78rem;
  font-weight: 700;
}

.result-heading span {
  overflow: hidden;
  max-width: 60%;
  color: var(--gear-text-soft);
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-value {
  display: block;
  overflow: hidden;
  margin: clamp(28px, 5vw, 62px) 0 16px;
  color: var(--gear-text);
  font-family: var(--gear-mono);
  font-size: clamp(3rem, 7.5vw, 7rem);
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  letter-spacing: -0.07em;
  line-height: 0.88;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-button {
  min-width: 108px;
  padding: 0 14px;
}

.measurement-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1px;
  margin-top: clamp(34px, 5vw, 62px);
  overflow: hidden;
  border: 1px solid var(--gear-line);
  border-radius: var(--gear-control-radius);
  background: var(--gear-line);
}

.measurement-strip > div {
  display: grid;
  min-width: 0;
  gap: 8px;
  padding: 15px;
  background: var(--gear-surface);
}

.measurement-strip span {
  color: var(--gear-muted);
  font-size: 0.7rem;
  line-height: 1.35;
}

.measurement-strip strong {
  overflow: hidden;
  color: var(--gear-text);
  font-family: var(--gear-mono);
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversion-summary {
  margin: 18px 0 0;
  color: var(--gear-muted);
  font-family: var(--gear-mono);
  font-size: 0.72rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.sensitivity-notes {
  display: grid;
  grid-template-columns: minmax(220px, 0.58fr) minmax(0, 1.42fr);
  gap: clamp(30px, 6vw, 92px);
  margin-top: clamp(42px, 6vw, 72px);
  padding-top: 28px;
  border-top: 1px solid var(--gear-line);
}

.sensitivity-notes > div > p {
  max-width: 390px;
  margin: 10px 0 0;
  color: var(--gear-muted);
  font-size: 0.84rem;
  line-height: 1.65;
}

.sensitivity-notes dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin: 0;
}

.sensitivity-notes dl > div {
  min-width: 0;
}

.sensitivity-notes dt {
  margin-bottom: 8px;
  color: var(--gear-text-soft);
  font-size: 0.76rem;
  font-weight: 700;
}

.sensitivity-notes dd {
  margin: 0;
  color: var(--gear-muted);
  font-size: 0.8rem;
  line-height: 1.6;
}

.sensitivity-notes dd.is-relevant {
  color: var(--gear-accent-strong);
}

@media (prefers-reduced-motion: no-preference) {
  .sensitivity-heading,
  .sensitivity-workbench,
  .sensitivity-notes {
    animation: sensitivity-enter 480ms cubic-bezier(0.16, 1, 0.3, 1) both;
  }

  .sensitivity-workbench { animation-delay: 70ms; }
  .sensitivity-notes { animation-delay: 130ms; }

  .sensitivity-result-panel.is-swapping .result-value {
    animation: result-swap 400ms cubic-bezier(0.16, 1, 0.3, 1);
  }
}

@keyframes sensitivity-enter {
  from { opacity: 0; transform: translateY(14px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes result-swap {
  0% { opacity: 0.35; transform: translateY(7px); }
  100% { opacity: 1; transform: translateY(0); }
}

@media (max-width: 980px) {
  .sensitivity-workbench {
    grid-template-columns: 1fr;
  }

  .sensitivity-result-panel {
    min-height: 430px;
  }

  .result-value {
    font-size: clamp(3.7rem, 14vw, 7rem);
  }
}

@media (max-width: 600px) {
  .sensitivity-page {
    padding-block: 38px 92px;
  }

  .sensitivity-heading h1 {
    font-size: clamp(2.35rem, 12vw, 3.5rem);
  }

  .sensitivity-heading > p:last-child {
    font-size: 0.92rem;
  }

  .sensitivity-input-panel,
  .sensitivity-result-panel {
    padding: 20px 16px;
  }

  .field-grid,
  .target-fields {
    grid-template-columns: 1fr;
  }

  .source-fields .field-wide {
    grid-column: auto;
  }

  .sensitivity-result-panel {
    min-height: 0;
  }

  .result-value {
    margin-top: 38px;
    font-size: clamp(2.75rem, 16vw, 4.5rem);
  }

  .measurement-strip {
    grid-template-columns: 1fr;
    margin-top: 38px;
  }

  .measurement-strip > div {
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
  }

  .sensitivity-notes,
  .sensitivity-notes dl {
    grid-template-columns: 1fr;
  }

  .sensitivity-notes dl {
    gap: 18px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .sensitivity-page *,
  .sensitivity-page *::before,
  .sensitivity-page *::after {
    scroll-behavior: auto !important;
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
