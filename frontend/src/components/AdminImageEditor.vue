<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { clamp, coverScale, editedFilename, offsetLimits } from '../utils/imageEditor'

const props = defineProps({
  source: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  externalError: { type: String, default: '' },
  brand: { type: String, default: '' },
  model: { type: String, default: '' },
})

const emit = defineEmits(['cancel', 'save'])
const BASE_WIDTH = 1200
const BASE_HEIGHT = 675
const MAX_FILE_SIZE = 5 * 1024 * 1024
const stageCanvas = ref(null)
const previewCanvas = ref(null)
const dialog = ref(null)
const image = ref(null)
const loading = ref(false)
const processing = ref(false)
const localError = ref('')
const originalWidth = ref(0)
const originalHeight = ref(0)
const zoom = ref(1)
const rotation = ref(0)
const flipX = ref(false)
const flipY = ref(false)
const offsetX = ref(0)
const offsetY = ref(0)
const outputSize = ref('1200x675')
const outputType = ref('image/webp')
const outputQuality = ref(90)
const dragging = ref(false)
let pointerState = null
let objectUrl = ''
let loadSequence = 0

const error = computed(() => localError.value || props.externalError)
const outputDimensions = computed(() => {
  const [width, height] = outputSize.value.split('x').map(Number)
  return { width, height }
})
const sourceLabel = computed(() => props.source?.name || '当前图片')
const displayTitle = computed(() => [props.brand, props.model].filter(Boolean).join(' ') || '鼠标产品图')

const resetTransform = () => {
  zoom.value = 1
  rotation.value = 0
  flipX.value = false
  flipY.value = false
  offsetX.value = 0
  offsetY.value = 0
  localError.value = ''
}

const clampOffsets = () => {
  if (!image.value) return
  const limits = offsetLimits({
    imageWidth: originalWidth.value,
    imageHeight: originalHeight.value,
    outputWidth: BASE_WIDTH,
    outputHeight: BASE_HEIGHT,
    rotation: rotation.value,
    zoom: zoom.value,
  })
  offsetX.value = clamp(offsetX.value, -limits.x, limits.x)
  offsetY.value = clamp(offsetY.value, -limits.y, limits.y)
}

const paintCanvas = (canvas, width, height, fillBackground = false) => {
  if (!canvas || !image.value) return
  if (canvas.width !== width) canvas.width = width
  if (canvas.height !== height) canvas.height = height
  const context = canvas.getContext('2d')
  context.clearRect(0, 0, width, height)
  if (fillBackground) {
    context.fillStyle = '#171717'
    context.fillRect(0, 0, width, height)
  }
  context.save()
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  const scale = coverScale(
    originalWidth.value,
    originalHeight.value,
    width,
    height,
    rotation.value,
  ) * zoom.value
  context.translate(
    width / 2 + offsetX.value * width / BASE_WIDTH,
    height / 2 + offsetY.value * height / BASE_HEIGHT,
  )
  context.rotate(rotation.value * Math.PI / 180)
  context.scale(flipX.value ? -1 : 1, flipY.value ? -1 : 1)
  context.drawImage(
    image.value,
    -originalWidth.value * scale / 2,
    -originalHeight.value * scale / 2,
    originalWidth.value * scale,
    originalHeight.value * scale,
  )
  context.restore()
}

const render = () => {
  if (!image.value) return
  paintCanvas(stageCanvas.value, 960, 540)
  paintCanvas(previewCanvas.value, 480, 270)
}

const releaseObjectUrl = () => {
  if (!objectUrl) return
  URL.revokeObjectURL(objectUrl)
  objectUrl = ''
}

const loadSource = async (source) => {
  const sequence = ++loadSequence
  releaseObjectUrl()
  image.value = null
  originalWidth.value = 0
  originalHeight.value = 0
  localError.value = ''
  if (!source) return
  loading.value = true
  resetTransform()
  const nextImage = new Image()
  if (!source.file) nextImage.crossOrigin = 'anonymous'
  const url = source.file ? URL.createObjectURL(source.file) : source.url
  if (source.file) objectUrl = url
  try {
    await new Promise((resolve, reject) => {
      nextImage.onload = resolve
      nextImage.onerror = () => reject(new Error('图片无法读取，请确认文件未损坏且允许后台访问'))
      nextImage.src = url
    })
    if (sequence !== loadSequence) return
    image.value = nextImage
    originalWidth.value = nextImage.naturalWidth
    originalHeight.value = nextImage.naturalHeight
    await nextTick()
    render()
    dialog.value?.focus()
  } catch (loadError) {
    if (sequence === loadSequence) localError.value = loadError.message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const rotate = (degrees) => {
  rotation.value = (rotation.value + degrees + 360) % 360
}

const nudge = (x, y) => {
  offsetX.value += x
  offsetY.value += y
  clampOffsets()
}

const handleKeyboardPan = (event) => {
  const amount = event.shiftKey ? 60 : 20
  const actions = {
    ArrowLeft: () => nudge(-amount, 0),
    ArrowRight: () => nudge(amount, 0),
    ArrowUp: () => nudge(0, -amount),
    ArrowDown: () => nudge(0, amount),
  }
  if (!actions[event.key]) return
  event.preventDefault()
  actions[event.key]()
}

const startDrag = (event) => {
  if (!image.value) return
  dragging.value = true
  pointerState = { id: event.pointerId, x: event.clientX, y: event.clientY }
  event.currentTarget.setPointerCapture(event.pointerId)
}

const moveDrag = (event) => {
  if (!dragging.value || pointerState?.id !== event.pointerId || !stageCanvas.value) return
  const rect = stageCanvas.value.getBoundingClientRect()
  offsetX.value += (event.clientX - pointerState.x) * BASE_WIDTH / rect.width
  offsetY.value += (event.clientY - pointerState.y) * BASE_HEIGHT / rect.height
  pointerState.x = event.clientX
  pointerState.y = event.clientY
  clampOffsets()
}

const endDrag = (event) => {
  if (pointerState?.id !== event.pointerId) return
  dragging.value = false
  pointerState = null
}

const handleWheel = (event) => {
  zoom.value = clamp(zoom.value + (event.deltaY < 0 ? 0.08 : -0.08), 1, 3)
}

const canvasToBlob = (canvas, type, quality) => new Promise((resolve) => {
  canvas.toBlob(resolve, type, quality)
})

const saveImage = async () => {
  if (!image.value || props.saving || processing.value) return
  processing.value = true
  localError.value = ''
  const { width, height } = outputDimensions.value
  const canvas = document.createElement('canvas')
  paintCanvas(canvas, width, height, outputType.value === 'image/jpeg')
  try {
    const blob = await canvasToBlob(canvas, outputType.value, outputQuality.value / 100)
    if (!blob) throw new Error('浏览器未能生成图片，请更换输出格式后重试')
    if (blob.size > MAX_FILE_SIZE) {
      throw new Error('处理后的图片超过 5 MB，请降低尺寸或改用 WebP 格式')
    }
    emit('save', {
      blob,
      filename: editedFilename(sourceLabel.value, outputType.value),
      width,
      height,
    })
  } catch (saveError) {
    localError.value = saveError.message
  } finally {
    processing.value = false
  }
}

const requestClose = () => {
  if (!props.saving && !processing.value) emit('cancel')
}

watch(() => props.source, loadSource, { immediate: true })
watch([zoom, rotation, flipX, flipY, offsetX, offsetY], () => {
  clampOffsets()
  render()
})
onBeforeUnmount(() => {
  loadSequence += 1
  releaseObjectUrl()
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="source"
      class="image-editor-overlay"
      @click.self="requestClose"
      @keydown.esc.stop.prevent="requestClose"
    >
      <section
        ref="dialog"
        class="image-editor-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="image-editor-title"
        tabindex="-1"
      >
        <header class="image-editor-header">
          <div>
            <p>产品图片工作台</p>
            <h2 id="image-editor-title">截选前台卡片图片</h2>
          </div>
          <button type="button" :disabled="saving || processing" aria-label="关闭图片工作台" @click="requestClose">×</button>
        </header>

        <div class="image-editor-body">
          <main class="image-editor-workspace">
            <div class="image-stage-heading">
              <div>
                <strong>拖动图片调整截选区域</strong>
                <span>{{ sourceLabel }} · {{ originalWidth || '—' }} × {{ originalHeight || '—' }}</span>
              </div>
              <button type="button" :disabled="!image" @click="resetTransform">重置图片</button>
            </div>
            <div
              class="image-editor-canvas-wrap"
              :class="{ dragging, loading }"
              role="application"
              aria-label="图片截选区域，可拖动图片，或使用方向键微调"
              tabindex="0"
              @keydown="handleKeyboardPan"
              @pointerdown="startDrag"
              @pointermove="moveDrag"
              @pointerup="endDrag"
              @pointercancel="endDrag"
              @wheel.prevent="handleWheel"
            >
              <canvas ref="stageCanvas" width="960" height="540"></canvas>
              <div class="image-crop-grid" aria-hidden="true"><i></i><i></i><b></b><b></b></div>
              <div v-if="loading" class="image-editor-loading"><i></i>正在读取图片</div>
              <div v-else-if="!image" class="image-editor-loading error-state">图片加载失败</div>
            </div>
            <p class="image-stage-help">截选比例固定为 16:9，与鼠标列表卡片一致。滚轮缩放，方向键微调，按住 Shift 可加速移动。</p>
          </main>

          <aside class="image-editor-sidebar">
            <section class="card-effect-preview">
              <div class="preview-heading"><strong>前台效果预览</strong><span>16:9 · COVER</span></div>
              <div class="preview-image"><canvas ref="previewCanvas" width="480" height="270"></canvas></div>
              <div class="preview-copy"><small>{{ brand || '品牌' }}</small><strong>{{ displayTitle }}</strong><span>图片会铺满卡片区域</span></div>
            </section>

            <section class="image-control-section">
              <label class="zoom-control">
                <span><strong>缩放图片</strong><output>{{ Math.round(zoom * 100) }}%</output></span>
                <input v-model.number="zoom" type="range" min="1" max="3" step="0.01" aria-label="图片缩放比例" />
              </label>
              <div class="image-tool-grid" aria-label="图片变换工具">
                <button type="button" :disabled="!image" @click="rotate(-90)"><span>↶</span>向左旋转</button>
                <button type="button" :disabled="!image" @click="rotate(90)"><span>↷</span>向右旋转</button>
                <button type="button" :class="{ active: flipX }" :disabled="!image" @click="flipX = !flipX"><span>↔</span>水平翻转</button>
                <button type="button" :class="{ active: flipY }" :disabled="!image" @click="flipY = !flipY"><span>↕</span>垂直翻转</button>
              </div>
            </section>

            <section class="image-output-settings">
              <strong>导出设置</strong>
              <div>
                <label>图片尺寸<select v-model="outputSize"><option value="800x450">800 × 450</option><option value="1200x675">1200 × 675（推荐）</option><option value="1600x900">1600 × 900</option></select></label>
                <label>图片格式<select v-model="outputType"><option value="image/webp">WebP（推荐）</option><option value="image/jpeg">JPEG</option><option value="image/png">PNG</option></select></label>
              </div>
              <label class="quality-control" :class="{ disabled: outputType === 'image/png' }">
                <span>输出质量 <output>{{ outputType === 'image/png' ? '无损' : `${outputQuality}%` }}</output></span>
                <input v-model.number="outputQuality" type="range" min="60" max="100" step="1" :disabled="outputType === 'image/png'" aria-label="输出图片质量" />
              </label>
            </section>

            <p v-if="error" class="image-editor-error" role="alert">{{ error }}</p>
          </aside>
        </div>

        <footer class="image-editor-footer">
          <p>保存后会上传为新图片，原图片不会被覆盖。</p>
          <div><button type="button" :disabled="saving || processing" @click="requestClose">取消编辑</button><button type="button" class="primary" :disabled="!image || saving || processing" @click="saveImage">{{ saving ? '正在上传…' : processing ? '正在生成…' : '保存并使用图片' }}</button></div>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.image-editor-overlay {
  position: fixed;
  inset: 0;
  z-index: 160;
  display: grid;
  place-items: center;
  padding: 18px;
  background: rgba(5, 5, 5, 0.82);
  backdrop-filter: blur(10px);
}
.image-editor-dialog {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(1180px, 100%);
  height: min(94vh, 900px);
  overflow: hidden;
  border: 1px solid #414141;
  border-radius: 16px;
  outline: none;
  background: #111;
  color: #ededed;
  scrollbar-color: #666 transparent;
  scrollbar-width: thin;
}
.image-editor-header,
.image-editor-footer {
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: rgba(17, 17, 17, 0.96);
  backdrop-filter: blur(14px);
}
.image-editor-header {
  padding: 18px 22px;
  border-bottom: 1px solid #353535;
}
.image-editor-header p {
  margin: 0 0 3px;
  color: #9a9a9a;
  font: .75rem var(--mono, monospace);
}
.image-editor-header h2 {
  margin: 0;
  color: #f2f2f2;
  font: 700 1.18rem/1.2 var(--sans, sans-serif);
  letter-spacing: -0.025em;
}
.image-editor-header > button {
  width: 38px;
  height: 38px;
  border: 1px solid #444;
  border-radius: 9px;
  background: #1a1a1a;
  color: #d7d7d7;
  font-size: 1.25rem;
  cursor: pointer;
}
.image-editor-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  min-height: 0;
  overflow: hidden;
}
.image-editor-workspace {
  min-width: 0;
  overflow: auto;
  padding: 22px;
  border-right: 1px solid #353535;
}
.image-stage-heading,
.preview-heading,
.zoom-control > span,
.quality-control > span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.image-stage-heading { margin-bottom: 12px; }
.image-stage-heading strong,
.image-stage-heading span { display: block; }
.image-stage-heading strong { color: #e3e3e3; font-size: .85rem; }
.image-stage-heading span { margin-top: 4px; color: #969696; font: .75rem var(--mono, monospace); }
.image-stage-heading button {
  border: 0;
  background: transparent;
  color: #b9b9b9;
  font-size: .75rem;
  cursor: pointer;
}
.image-stage-heading button:hover { color: #fff; }
.image-editor-canvas-wrap {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border: 1px solid #505050;
  border-radius: 12px;
  outline: none;
  background: #080808;
  cursor: grab;
  touch-action: none;
}
.image-editor-canvas-wrap:focus-visible { border-color: #dedede; box-shadow: 0 0 0 2px rgba(222, 222, 222, 0.2); }
.image-editor-canvas-wrap.dragging { cursor: grabbing; }
.image-editor-canvas-wrap canvas { display: block; width: 100%; height: 100%; }
.image-crop-grid { position: absolute; inset: 0; pointer-events: none; }
.image-crop-grid::after { content: ''; position: absolute; inset: 10px; border: 1px solid rgba(255, 255, 255, 0.42); border-radius: 6px; }
.image-crop-grid i,
.image-crop-grid b { position: absolute; background: rgba(255, 255, 255, 0.28); }
.image-crop-grid i { top: 10px; bottom: 10px; width: 1px; }
.image-crop-grid i:first-child { left: 33.333%; }
.image-crop-grid i:nth-child(2) { left: 66.666%; }
.image-crop-grid b { right: 10px; left: 10px; height: 1px; }
.image-crop-grid b:nth-child(3) { top: 33.333%; }
.image-crop-grid b:nth-child(4) { top: 66.666%; }
.image-editor-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  background: rgba(10, 10, 10, 0.88);
  color: #d5d5d5;
  font-size: .75rem;
}
.image-editor-loading i { width: 16px; height: 16px; border: 2px solid #555; border-top-color: #ddd; border-radius: 50%; animation: image-editor-spin 0.7s linear infinite; }
.image-editor-loading.error-state { color: #b9b9b9; }
.image-stage-help { margin: 10px 0 0; color: #929292; font-size: .75rem; line-height: 1.55; }
.image-editor-sidebar { display: flex; flex-direction: column; gap: 16px; overflow: auto; padding: 18px; background: #151515; }
.card-effect-preview,
.image-control-section,
.image-output-settings { padding-bottom: 16px; border-bottom: 1px solid #393939; }
.preview-heading strong,
.image-output-settings > strong { color: #dedede; font-size: .75rem; }
.preview-heading span { color: #929292; font: .75rem var(--mono, monospace); }
.preview-image { margin-top: 10px; overflow: hidden; aspect-ratio: 16 / 9; border-radius: 9px 9px 0 0; background: radial-gradient(circle at 50% 42%, #303030, #181818 70%); }
.preview-image canvas { display: block; width: 100%; height: 100%; }
.preview-copy { padding: 10px 11px 11px; border: 1px solid #363636; border-top: 0; border-radius: 0 0 9px 9px; background: #101010; }
.preview-copy small,
.preview-copy strong,
.preview-copy span { display: block; }
.preview-copy small { color: #9e9e9e; font: .75rem var(--mono, monospace); }
.preview-copy strong { overflow: hidden; margin-top: 3px; color: #ededed; font-size: .85rem; text-overflow: ellipsis; white-space: nowrap; }
.preview-copy span { margin-top: 4px; color: #858585; font-size: .75rem; }
.zoom-control { display: block; }
.zoom-control strong { color: #d5d5d5; font-size: .75rem; }
.zoom-control output,
.quality-control output { color: #bcbcbc; font: .75rem var(--mono, monospace); }
.zoom-control input,
.quality-control input { width: 100%; margin: 10px 0 0; accent-color: #d7d7d7; }
.image-tool-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-top: 13px; }
.image-tool-grid button {
  min-height: 38px;
  border: 1px solid #444;
  border-radius: 8px;
  background: #1b1b1b;
  color: #c6c6c6;
  font-size: .75rem;
  cursor: pointer;
}
.image-tool-grid button:hover,
.image-tool-grid button.active { border-color: #838383; background: #292929; color: #fff; }
.image-tool-grid button span { margin-right: 4px; font-size: .85rem; }
.image-output-settings > div { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin-top: 10px; }
.image-output-settings label { color: #a8a8a8; font-size: .75rem; }
.image-output-settings select {
  width: 100%;
  min-height: 36px;
  margin-top: 5px;
  padding: 0 7px;
  border: 1px solid #444;
  border-radius: 7px;
  background: #1b1b1b;
  color: #dedede;
  font-size: .75rem;
}
.quality-control { display: block; margin-top: 11px; }
.quality-control.disabled { opacity: 0.55; }
.image-editor-error { margin: 0; padding: 10px; border: 1px solid #777777; border-radius: 8px; background: #1d1d1d; color: #f0f0f0; font-size: .75rem; line-height: 1.45; }
.image-editor-footer { padding: 14px 20px; border-top: 1px solid #353535; }
.image-editor-footer p { margin: 0; color: #959595; font-size: .75rem; }
.image-editor-footer > div { display: flex; gap: 8px; }
.image-editor-footer button { min-height: 39px; padding: 0 15px; border: 1px solid #4a4a4a; border-radius: 9px; background: #1b1b1b; color: #d0d0d0; font-size: .75rem; cursor: pointer; }
.image-editor-footer button.primary { border-color: #e0e0e0; background: #e0e0e0; color: #111; font-weight: 700; }
button:disabled { opacity: 0.45; cursor: not-allowed; }
@keyframes image-editor-spin { to { transform: rotate(360deg); } }
@media (max-width: 860px) {
  .image-editor-overlay { padding: 8px; }
  .image-editor-dialog { height: calc(100dvh - 16px); }
  .image-editor-body { grid-template-columns: 1fr; overflow: auto; }
  .image-editor-workspace { overflow: visible; padding: 16px; border-right: 0; border-bottom: 1px solid #353535; }
  .image-editor-sidebar { display: grid; grid-template-columns: 1fr 1fr; overflow: visible; }
  .image-output-settings { grid-column: 1 / -1; }
}
@media (max-width: 560px) {
  .image-editor-overlay { padding: 0; }
  .image-editor-dialog { width: 100%; height: 100dvh; min-height: 100dvh; border: 0; border-radius: 0; }
  .image-editor-header { padding: 14px 16px; }
  .image-editor-header h2 { font-size: 1rem; }
  .image-editor-workspace { padding: 14px; }
  .image-editor-sidebar { display: flex; padding: 14px; }
  .image-stage-heading { align-items: flex-end; }
  .image-stage-help { font-size: .75rem; }
  .image-editor-footer { align-items: stretch; flex-direction: column; padding: 12px 14px calc(12px + env(safe-area-inset-bottom)); }
  .image-editor-footer > div { display: grid; grid-template-columns: 1fr 1.35fr; }
  .image-editor-footer button { padding: 0 10px; }
}
@media (prefers-reduced-motion: reduce) {
  .image-editor-loading i { animation: none; }
}
</style>
