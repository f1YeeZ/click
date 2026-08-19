<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import handPalmUrl from '../assets/images/hand-palm-model-projection.png'
import {
  appendSupportDabs,
  interpolateSupportDabs,
  mirrorSupportGridX,
  mirrorSupportX,
  normalizeSupportDab,
  SUPPORT_GRID_COLUMNS,
  SUPPORT_GRID_ROWS,
  SUPPORT_VIEWBOX_HEIGHT,
  SUPPORT_VIEWBOX_WIDTH
} from '../utils/supportHeatmap'

const props = defineProps({
  summaryCells: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 0 },
  gridColumns: { type: Number, default: SUPPORT_GRID_COLUMNS },
  gridRows: { type: Number, default: SUPPORT_GRID_ROWS },
  dabs: { type: Array, default: () => [] },
  brushSize: { type: Number, default: 12 },
  tool: { type: String, default: 'paint' },
  editable: { type: Boolean, default: false },
  ariaLabel: { type: String, default: '' }
})

const emit = defineEmits(['update:dabs', 'error'])

const host = ref(null)
const image = ref(null)
const canvas = ref(null)
const brushCursor = ref(null)
const state = ref('loading')
const cursorVisible = ref(false)

let maskCanvas
let summaryCanvas
let strokeCanvas
let localDabs = []
let painting = false
let paintPointerId = null
let previousPaintPoint = null

const makeCanvas = (width, height) => {
  const element = document.createElement('canvas')
  element.width = width
  element.height = height
  return element
}

const ensureLayers = () => {
  if (!canvas.value) return false
  const width = canvas.value.width
  const height = canvas.value.height
  if (!maskCanvas || maskCanvas.width !== width || maskCanvas.height !== height) maskCanvas = makeCanvas(width, height)
  if (!strokeCanvas || strokeCanvas.width !== width || strokeCanvas.height !== height) strokeCanvas = makeCanvas(width, height)
  return true
}

const renderHeatmap = () => {
  if (!ensureLayers() || state.value !== 'ready') return
  const width = canvas.value.width
  const height = canvas.value.height
  const context = canvas.value.getContext('2d')
  context.clearRect(0, 0, width, height)

  const columns = Math.max(1, props.gridColumns || SUPPORT_GRID_COLUMNS)
  const rows = Math.max(1, props.gridRows || SUPPORT_GRID_ROWS)
  if (!summaryCanvas || summaryCanvas.width !== columns || summaryCanvas.height !== rows) {
    summaryCanvas = makeCanvas(columns, rows)
  }
  const summaryContext = summaryCanvas.getContext('2d')
  summaryContext.clearRect(0, 0, columns, rows)
  for (const cell of props.summaryCells || []) {
    const intensity = Math.max(0, Math.min(1, cell.count / Math.max(1, props.maxCount)))
    const hue = Math.round(42 - intensity * 37)
    summaryContext.fillStyle = `hsla(${hue}, 94%, ${60 - intensity * 10}%, ${0.2 + intensity * 0.76})`
    summaryContext.fillRect(mirrorSupportGridX(cell.x, columns), cell.y, 1, 1)
  }
  context.save()
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.filter = 'blur(10px) saturate(1.12)'
  context.drawImage(summaryCanvas, 0, 0, width, height)
  context.restore()

  const strokeContext = strokeCanvas.getContext('2d')
  strokeContext.clearRect(0, 0, width, height)
  for (const rawDab of localDabs) {
    const dab = normalizeSupportDab(rawDab)
    const displayX = mirrorSupportX(dab.x) / SUPPORT_VIEWBOX_WIDTH * width
    const displayY = dab.y / SUPPORT_VIEWBOX_HEIGHT * height
    const radius = dab.radius / 1000 * Math.min(width, height)
    strokeContext.globalCompositeOperation = dab.mode === 'ERASE' ? 'destination-out' : 'source-over'
  strokeContext.fillStyle = 'rgba(242, 242, 242, 0.9)'
    strokeContext.beginPath()
    strokeContext.arc(displayX, displayY, radius, 0, Math.PI * 2)
    strokeContext.fill()
  }
  strokeContext.globalCompositeOperation = 'source-over'
  context.save()
  context.filter = 'blur(1.4px)'
  context.shadowColor = 'rgba(255, 255, 255, 0.72)'
  context.shadowBlur = 7
  context.drawImage(strokeCanvas, 0, 0)
  context.restore()

  context.globalCompositeOperation = 'destination-in'
  context.drawImage(maskCanvas, 0, 0)
  context.globalCompositeOperation = 'source-over'
}

const initializeImage = async () => {
  try {
    await nextTick()
    if (!image.value || !canvas.value) return
    canvas.value.width = 900
    canvas.value.height = 1200
    ensureLayers()
    const maskContext = maskCanvas.getContext('2d', { willReadFrequently: true })
    maskContext.clearRect(0, 0, maskCanvas.width, maskCanvas.height)
    maskContext.drawImage(image.value, 0, 0, maskCanvas.width, maskCanvas.height)
    state.value = 'ready'
    renderHeatmap()
  } catch (error) {
    state.value = 'error'
    emit('error', error)
  }
}

const failImage = (error) => {
  state.value = 'error'
  emit('error', error)
}

const pointFromEvent = (event) => {
  if (!canvas.value || !maskCanvas || state.value !== 'ready') return null
  const rect = canvas.value.getBoundingClientRect()
  if (!rect.width || !rect.height) return null
  const displayX = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width))
  const displayY = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height))
  const pixelX = Math.min(maskCanvas.width - 1, Math.round(displayX * maskCanvas.width))
  const pixelY = Math.min(maskCanvas.height - 1, Math.round(displayY * maskCanvas.height))
  const alpha = maskCanvas.getContext('2d', { willReadFrequently: true }).getImageData(pixelX, pixelY, 1, 1).data[3]
  if (alpha < 24) return null
  return {
    x: mirrorSupportX(displayX * SUPPORT_VIEWBOX_WIDTH),
    y: Math.round(displayY * SUPPORT_VIEWBOX_HEIGHT)
  }
}

const updateBrushCursor = (event) => {
  if (!brushCursor.value || !canvas.value) return
  const active = props.editable && ['paint', 'erase'].includes(props.tool)
  cursorVisible.value = active
  if (!active) return
  const rect = canvas.value.getBoundingClientRect()
  const diameter = Math.max(12, rect.width * props.brushSize / 100)
  brushCursor.value.style.width = `${diameter}px`
  brushCursor.value.style.height = `${diameter}px`
  brushCursor.value.style.left = `${event.clientX - rect.left}px`
  brushCursor.value.style.top = `${event.clientY - rect.top}px`
}

const applyPaintPoint = (point) => {
  if (!point) return
  const radius = Math.round(props.brushSize * 5)
  const mode = props.tool === 'erase' ? 'ERASE' : 'PAINT'
  const nextDabs = interpolateSupportDabs(previousPaintPoint, point, radius, mode)
  localDabs = appendSupportDabs(localDabs, nextDabs)
  previousPaintPoint = point
  emit('update:dabs', [...localDabs])
  renderHeatmap()
}

const beginPaint = (event) => {
  if (!props.editable || !['paint', 'erase'].includes(props.tool)) return
  const primaryPaintPointer = event.pointerType === 'touch' || event.button === 0
  if (!primaryPaintPointer) return
  const point = pointFromEvent(event)
  updateBrushCursor(event)
  event.preventDefault()
  canvas.value?.setPointerCapture(event.pointerId)
  painting = true
  paintPointerId = event.pointerId
  previousPaintPoint = null
  if (point) applyPaintPoint(point)
}

const continuePaint = (event) => {
  const point = pointFromEvent(event)
  updateBrushCursor(event)
  if (!painting || event.pointerId !== paintPointerId) return
  if (!point) {
    previousPaintPoint = null
    return
  }
  event.preventDefault()
  applyPaintPoint(point)
}

const finishPaint = (event) => {
  if (!painting || (event?.pointerId != null && event.pointerId !== paintPointerId)) return
  painting = false
  paintPointerId = null
  previousPaintPoint = null
  if (event?.pointerId != null && canvas.value?.hasPointerCapture(event.pointerId)) {
    canvas.value.releasePointerCapture(event.pointerId)
  }
}

watch(() => props.dabs, (dabs) => {
  if (!painting) localDabs = (dabs || []).map(normalizeSupportDab)
  renderHeatmap()
}, { deep: true, immediate: true })
watch(() => [props.summaryCells, props.maxCount, props.gridColumns, props.gridRows], renderHeatmap, { deep: true })
watch(() => [props.tool, props.editable], () => {
  if (!props.editable || !['paint', 'erase'].includes(props.tool)) {
    cursorVisible.value = false
    finishPaint()
  }
})

const handleWindowPointerMove = (event) => {
  if (!cursorVisible.value || painting || !host.value) return
  const rect = host.value.getBoundingClientRect()
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) {
    cursorVisible.value = false
  }
}

onMounted(() => {
  window.addEventListener('pointermove', handleWindowPointerMove)
  window.addEventListener('blur', finishPaint)
  if (image.value?.complete && image.value.naturalWidth) initializeImage()
})
onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handleWindowPointerMove)
  window.removeEventListener('blur', finishPaint)
  finishPaint()
  maskCanvas = null
  summaryCanvas = null
  strokeCanvas = null
})
</script>

<template>
  <div ref="host" class="hand-support-2d" :class="[`is-${state}`, `tool-${tool}`]">
    <img
      ref="image"
      class="hand-palm-image"
      :src="handPalmUrl"
      alt="右手手心支撑位置图"
      draggable="false"
      @load="initializeImage"
      @error="failImage"
    >
    <canvas
      ref="canvas"
      :aria-label="ariaLabel || (editable ? '可涂抹的二维右手手心支撑位置图' : '只读的二维右手手心支撑位置热力图')"
      @pointerdown="beginPaint"
      @pointermove="continuePaint"
      @pointerup="finishPaint"
      @pointercancel="finishPaint"
      @lostpointercapture="finishPaint"
    />
    <span ref="brushCursor" class="support-brush-cursor" :class="{ erase: tool === 'erase' }" :hidden="!cursorVisible"></span>
    <div v-if="state === 'loading'" class="hand-model-loading" aria-live="polite"><span></span><em>正在加载二维手掌</em></div>
    <div v-else-if="state === 'error'" class="hand-model-fallback"><span>二维手掌图片加载失败</span></div>
  </div>
</template>
