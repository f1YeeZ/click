<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, useAttrs, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import handModelUrl from '../assets/models/hand-support.glb?url'
import handPreviewUrl from '../assets/models/hand-support-preview.png'
import { fitPerspectiveBounds, scaleAndCenterObject3D } from '../utils/threeCameraFit'
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

defineOptions({ inheritAttrs: false })
const attrs = useAttrs()

const props = defineProps({
  summaryCells: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 0 },
  gridColumns: { type: Number, default: SUPPORT_GRID_COLUMNS },
  gridRows: { type: Number, default: SUPPORT_GRID_ROWS },
  dabs: { type: Array, default: () => [] },
  brushSize: { type: Number, default: 12 },
  tool: { type: String, default: 'paint' },
  editable: { type: Boolean, default: false }
})
const emit = defineEmits(['update:dabs', 'error'])

const host = ref(null)
const canvas = ref(null)
const brushCursor = ref(null)
const state = ref('loading')

let renderer
let scene
let camera
let controls
let resizeObserver
let visibilityObserver
let heatCanvas
let heatTexture
let summaryCanvas
let strokeCanvas
let modelBounds
let paintTargets = []
let heatMaterials = []
let painting = false
let paintPointerId = null
let previousPaintPoint = null
let localDabs = []
let visible = true

const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const palmNormal = new THREE.Vector3(0, 0, 1)
const normalMatrix = new THREE.Matrix3()

const renderHeatTexture = () => {
  if (!heatCanvas || !heatTexture) return
  const context = heatCanvas.getContext('2d')
  context.clearRect(0, 0, heatCanvas.width, heatCanvas.height)

  const columns = Math.max(1, props.gridColumns || SUPPORT_GRID_COLUMNS)
  const rows = Math.max(1, props.gridRows || SUPPORT_GRID_ROWS)
  if (!summaryCanvas || summaryCanvas.width !== columns || summaryCanvas.height !== rows) {
    summaryCanvas = document.createElement('canvas')
    summaryCanvas.width = columns
    summaryCanvas.height = rows
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
  context.filter = 'blur(7px) saturate(1.08)'
  context.drawImage(summaryCanvas, 0, 0, heatCanvas.width, heatCanvas.height)
  context.restore()

  if (!strokeCanvas) {
    strokeCanvas = document.createElement('canvas')
    strokeCanvas.width = heatCanvas.width
    strokeCanvas.height = heatCanvas.height
  }
  const strokeContext = strokeCanvas.getContext('2d')
  strokeContext.clearRect(0, 0, strokeCanvas.width, strokeCanvas.height)
  for (const rawDab of localDabs) {
    const dab = normalizeSupportDab(rawDab)
    strokeContext.globalCompositeOperation = dab.mode === 'ERASE' ? 'destination-out' : 'source-over'
  strokeContext.fillStyle = 'rgba(242, 242, 242, 0.9)'
    strokeContext.beginPath()
    strokeContext.arc(
      mirrorSupportX(dab.x) / SUPPORT_VIEWBOX_WIDTH * strokeCanvas.width,
      dab.y / SUPPORT_VIEWBOX_HEIGHT * strokeCanvas.height,
      dab.radius / 1000 * Math.min(strokeCanvas.width, strokeCanvas.height),
      0,
      Math.PI * 2
    )
    strokeContext.fill()
  }
  strokeContext.globalCompositeOperation = 'source-over'
  context.save()
  context.filter = 'blur(1.2px)'
  context.shadowColor = 'rgba(255, 255, 255, 0.72)'
  context.shadowBlur = 5
  context.drawImage(strokeCanvas, 0, 0)
  context.restore()
  heatTexture.needsUpdate = true
}

const renderFrame = () => {
  if (!renderer || !scene || !camera) return
  controls?.update()
  renderer.render(scene, camera)
}

const resize = () => {
  if (!renderer || !camera || !host.value) return
  const width = Math.max(1, host.value.clientWidth)
  const height = Math.max(1, host.value.clientHeight)
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  if (visible) renderFrame()
}

const setControlMode = () => {
  if (!controls || !canvas.value) return
  const editingSurface = props.editable && ['paint', 'erase'].includes(props.tool)
  controls.enabled = true
  controls.mouseButtons.LEFT = editingSurface ? -1 : THREE.MOUSE.ROTATE
  controls.mouseButtons.MIDDLE = THREE.MOUSE.DOLLY
  controls.mouseButtons.RIGHT = -1
  controls.touches.ONE = editingSurface ? -1 : THREE.TOUCH.ROTATE
  controls.touches.TWO = THREE.TOUCH.DOLLY_PAN
  canvas.value.style.cursor = editingSurface ? 'none' : 'grab'
  if (!editingSurface && brushCursor.value) brushCursor.value.hidden = true
}

const supportPointFromWorld = (worldPoint) => {
  if (!modelBounds) return null
  const size = modelBounds.getSize(new THREE.Vector3())
  const xRatio = (worldPoint.x - modelBounds.min.x) / size.x
  const yRatio = (modelBounds.max.y - worldPoint.y) / size.y
  if (xRatio < 0 || xRatio > 1 || yRatio < 0 || yRatio > 1) return null
  return {
    x: mirrorSupportX(xRatio * SUPPORT_VIEWBOX_WIDTH),
    y: Math.round(yRatio * SUPPORT_VIEWBOX_HEIGHT)
  }
}

const updateBrushCursor = (event, intersection) => {
  if (!brushCursor.value || !canvas.value) return
  const active = props.editable && ['paint', 'erase'].includes(props.tool) && Boolean(intersection)
  brushCursor.value.hidden = !active
  if (!active) return
  const rect = canvas.value.getBoundingClientRect()
  const diameter = Math.max(12, rect.width * props.brushSize / 100)
  brushCursor.value.style.width = `${diameter}px`
  brushCursor.value.style.height = `${diameter}px`
  brushCursor.value.style.left = `${event.clientX - rect.left}px`
  brushCursor.value.style.top = `${event.clientY - rect.top}px`
}

const intersectionFromEvent = (event) => {
  if (!renderer || !camera || !canvas.value) return null
  const rect = canvas.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const intersections = raycaster.intersectObjects(paintTargets, false)
  return intersections.find((intersection) => {
    if (!intersection.face) return false
    normalMatrix.getNormalMatrix(intersection.object.matrixWorld)
    const worldNormal = intersection.face.normal.clone().applyMatrix3(normalMatrix).normalize()
    return worldNormal.dot(palmNormal) > 0.12
  }) || null
}

const applyPaintPoint = (point) => {
  if (!point) return
  const radius = Math.round(props.brushSize * 5)
  const mode = props.tool === 'erase' ? 'ERASE' : 'PAINT'
  const nextDabs = interpolateSupportDabs(previousPaintPoint, point, radius, mode)
  localDabs = appendSupportDabs(localDabs, nextDabs)
  emit('update:dabs', [...localDabs])
  renderHeatTexture()
  previousPaintPoint = point
}

const beginPaint = (event) => {
  if (event.pointerType !== 'touch' && event.button === 2) {
    event.preventDefault()
    if (brushCursor.value) brushCursor.value.hidden = true
    return
  }
  if (!props.editable || !['paint', 'erase'].includes(props.tool)) return
  const primaryPaintPointer = event.pointerType === 'touch' || event.button === 0
  if (!primaryPaintPointer) {
    if (brushCursor.value) brushCursor.value.hidden = true
    return
  }
  const intersection = intersectionFromEvent(event)
  updateBrushCursor(event, intersection)
  event.preventDefault()
  event.stopImmediatePropagation()
  canvas.value?.setPointerCapture(event.pointerId)
  painting = true
  paintPointerId = event.pointerId
  previousPaintPoint = null
  if (intersection) applyPaintPoint(supportPointFromWorld(intersection.point))
}

const continuePaint = (event) => {
  if (!painting && event.pointerType !== 'touch' && event.buttons !== 0) {
    if (brushCursor.value) brushCursor.value.hidden = true
    return
  }
  const intersection = intersectionFromEvent(event)
  updateBrushCursor(event, intersection)
  if (!painting || event.pointerId !== paintPointerId) return
  if (!intersection) {
    previousPaintPoint = null
    return
  }
  event.preventDefault()
  applyPaintPoint(supportPointFromWorld(intersection.point))
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

const hideBrushCursor = () => {
  if (!painting && brushCursor.value) brushCursor.value.hidden = true
}

const dispose = () => {
  window.removeEventListener('blur', finishPaint)
  finishPaint()
  resizeObserver?.disconnect()
  visibilityObserver?.disconnect()
  renderer?.setAnimationLoop(null)
  controls?.dispose()
  heatTexture?.dispose()
  heatMaterials.forEach((material) => material.dispose())
  paintTargets.forEach((mesh) => {
    mesh.geometry?.dispose()
    const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
    materials.forEach((material) => {
      for (const value of Object.values(material || {})) {
        if (value?.isTexture) value.dispose()
      }
      material?.dispose?.()
    })
  })
  renderer?.dispose()
  paintTargets = []
  heatMaterials = []
}

const initialize = async () => {
  try {
    await nextTick()
    renderer = new THREE.WebGLRenderer({ canvas: canvas.value, antialias: true, alpha: true, powerPreference: 'high-performance' })
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
    renderer.outputColorSpace = THREE.SRGBColorSpace
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.05

    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(34, 1, 0.01, 20)
    scene.add(new THREE.HemisphereLight(0xdceaff, 0x18243b, 2.1))
    const keyLight = new THREE.DirectionalLight(0xffe5d1, 2.6)
    keyLight.position.set(-1.8, 2.4, 3.2)
    scene.add(keyLight)
    const fillLight = new THREE.DirectionalLight(0x8dbbff, 1.35)
    fillLight.position.set(2.4, -0.4, 2.1)
    scene.add(fillLight)

    heatCanvas = document.createElement('canvas')
    heatCanvas.width = 768
    heatCanvas.height = 768
    heatTexture = new THREE.CanvasTexture(heatCanvas)
    heatTexture.colorSpace = THREE.SRGBColorSpace
    heatTexture.minFilter = THREE.LinearFilter
    heatTexture.magFilter = THREE.LinearFilter

    const gltf = await new GLTFLoader().loadAsync(handModelUrl)
    const model = gltf.scene
    scaleAndCenterObject3D(model, 1.58)
    const presentationRoot = new THREE.Group()
    presentationRoot.add(model)
    scene.add(presentationRoot)
    presentationRoot.updateMatrixWorld(true)
    modelBounds = new THREE.Box3().setFromObject(presentationRoot)
    const size = modelBounds.getSize(new THREE.Vector3())
    const discardedSurfaceTextures = new Set()

    model.traverse((object) => {
      if (!object.isMesh) return
      paintTargets.push(object)
      const materials = Array.isArray(object.material) ? object.material : [object.material]
      materials.forEach((material) => {
        if (material.map) discardedSurfaceTextures.add(material.map)
        if (material.normalMap) discardedSurfaceTextures.add(material.normalMap)
        if (material.roughnessMap) discardedSurfaceTextures.add(material.roughnessMap)
        if (material.metalnessMap) discardedSurfaceTextures.add(material.metalnessMap)
        material.map = null
        material.normalMap = null
        material.roughnessMap = null
        material.color?.set(0x85817b)
        material.emissive?.set(0x000000)
        material.metalness = 0
        material.metalnessMap = null
        material.roughness = 0.82
        material.flatShading = false
        material.side = THREE.FrontSide
        material.needsUpdate = true
      })
    })
    const retainedTextures = new Set()
    paintTargets.forEach((mesh) => {
      const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
      materials.forEach((material) => Object.values(material || {}).forEach((value) => {
        if (value?.isTexture) retainedTextures.add(value)
      }))
    })
    discardedSurfaceTextures.forEach((texture) => {
      if (!retainedTextures.has(texture)) texture.dispose()
    })
    presentationRoot.updateMatrixWorld(true)
    modelBounds.setFromObject(presentationRoot)
    modelBounds.getSize(size)

    const heatMin = new THREE.Vector2(modelBounds.min.x, modelBounds.min.y)
    const heatSize = new THREE.Vector2(size.x, size.y)
    for (const mesh of paintTargets) {
      const material = new THREE.ShaderMaterial({
        uniforms: {
          heatMap: { value: heatTexture },
          heatMin: { value: heatMin },
          heatSize: { value: heatSize },
          palmDirection: { value: palmNormal.clone() },
          palmFadeStart: { value: 0.08 },
          palmFadeEnd: { value: 0.32 }
        },
        vertexShader: `
          varying vec2 vHeatUv;
          varying vec3 vWorldNormal;
          uniform vec2 heatMin;
          uniform vec2 heatSize;
          void main() {
            vec4 worldPosition = modelMatrix * vec4(position, 1.0);
            vHeatUv = (worldPosition.xy - heatMin) / heatSize;
            vWorldNormal = normalize(mat3(modelMatrix) * normal);
            gl_Position = projectionMatrix * viewMatrix * worldPosition;
          }
        `,
        fragmentShader: `
          varying vec2 vHeatUv;
          varying vec3 vWorldNormal;
          uniform sampler2D heatMap;
          uniform vec3 palmDirection;
          uniform float palmFadeStart;
          uniform float palmFadeEnd;
          void main() {
            float palmVisibility = smoothstep(
              palmFadeStart,
              palmFadeEnd,
              dot(normalize(vWorldNormal), palmDirection)
            );
            if (palmVisibility < 0.01) discard;
            vec4 heat = texture2D(heatMap, vHeatUv);
            heat.a *= palmVisibility;
            if (heat.a < 0.015) discard;
            gl_FragColor = heat;
          }
        `,
        transparent: true,
        depthWrite: false,
        depthTest: true,
        polygonOffset: true,
        polygonOffsetFactor: -2,
        polygonOffsetUnits: -2,
        toneMapped: false
      })
      const overlay = new THREE.Mesh(mesh.geometry, material)
      overlay.renderOrder = 2
      mesh.add(overlay)
      heatMaterials.push(material)
    }

    resize()
    const { distance } = fitPerspectiveBounds(modelBounds, camera.fov, camera.aspect)
    camera.position.set(0, 0, distance)
    camera.lookAt(0, 0, 0)
    camera.near = Math.max(0.01, distance / 100)
    camera.far = distance * 10
    camera.updateProjectionMatrix()

    controls = new OrbitControls(camera, canvas.value)
    controls.enableDamping = true
    controls.dampingFactor = 0.08
    controls.enablePan = false
    controls.minDistance = distance * 0.72
    controls.maxDistance = distance * 1.45
    controls.target.set(0, 0, 0)
    controls.update()
    setControlMode()
    renderHeatTexture()

    resizeObserver = new ResizeObserver(resize)
    resizeObserver.observe(host.value)
    visibilityObserver = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting
      if (visible) {
        resize()
        renderFrame()
      }
    }, { threshold: 0.05 })
    visibilityObserver.observe(host.value)
    resize()
    renderer.setAnimationLoop(() => {
      if (!visible) return
      renderFrame()
    })
    state.value = 'ready'
  } catch (error) {
    state.value = 'error'
    emit('error', error)
  }
}

watch(() => props.dabs, (dabs) => {
  if (!painting) localDabs = (dabs || []).map(normalizeSupportDab)
  renderHeatTexture()
}, { deep: true, immediate: true })
watch(() => [props.summaryCells, props.maxCount, props.gridColumns, props.gridRows], renderHeatTexture, { deep: true })
watch(() => [props.tool, props.editable], () => {
  if (!props.editable || !['paint', 'erase'].includes(props.tool)) finishPaint()
  setControlMode()
})
onMounted(() => {
  window.addEventListener('blur', finishPaint)
  initialize()
})
onBeforeUnmount(dispose)
</script>

<template>
  <div ref="host" class="hand-support-3d" :class="[`is-${state}`, `tool-${tool}`]">
    <canvas
      ref="canvas"
      :aria-label="attrs['aria-label'] || '可旋转的三维右手支撑位置模型'"
      @pointerdown="beginPaint"
      @pointermove="continuePaint"
      @pointerup="finishPaint"
      @pointercancel="finishPaint"
      @lostpointercapture="finishPaint"
      @pointerleave="hideBrushCursor"
      @mousedown.right.prevent
      @auxclick.prevent
      @contextmenu.prevent
    />
    <span ref="brushCursor" class="support-brush-cursor" :class="{ erase: tool === 'erase' }" hidden></span>
    <div v-if="state === 'loading'" class="hand-model-loading" aria-live="polite"><span></span><em>正在加载三维手掌</em></div>
    <div v-else-if="state === 'error'" class="hand-model-fallback">
      <img :src="handPreviewUrl" alt="写实成年人右手掌心模型预览">
      <span>当前设备无法显示三维模型</span>
    </div>
  </div>
</template>
