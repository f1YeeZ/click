<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { errorMessage } from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import RangeSlider from '../components/RangeSlider.vue'
import FilterCheckGroup from '../components/FilterCheckGroup.vue'
import { onRealtime } from '../services/realtime'

const route = useRoute()
const router = useRouter()
const defaults = {
  q: '', brand: [], size: [], shape: [], hand: [], connection: [],
  lengthMin: '', lengthMax: '', widthMin: '', widthMax: '', heightMin: '', heightMax: '', weightMin: '', weightMax: '',
  humpPlacement: [], frontFlare: [], sideCurvature: [], thumbRest: [], ringFingerRest: [],
  sensorType: [], sensorName: '', adjustableSensorPosition: [], dpiMin: '', dpiMax: '', pollingMin: '', pollingMax: '',
  trackingMin: '', trackingMax: '', accelerationMin: '', accelerationMax: '',
  buttonsMin: '', buttonsMax: '', sideButtonsMin: '', sideButtonsMax: '', hotSwap: [], switchType: [], switchName: '',
  encoderType: [], encoderName: '', encoderStepsMin: '', encoderStepsMax: '', material: '', purchaseChannel: '',
  sort: 'newest', page: 1, pageSize: 12
}
const multiKeys = ['brand', 'size', 'shape', 'hand', 'connection', 'humpPlacement', 'frontFlare', 'sideCurvature', 'thumbRest', 'ringFingerRest', 'sensorType', 'adjustableSensorPosition', 'hotSwap', 'switchType', 'encoderType']
const initialQuery = Object.fromEntries(Object.keys(defaults).map((key) => [key, route.query[key] ?? defaults[key]]))
multiKeys.forEach((key) => { initialQuery[key] = initialQuery[key] ? String(initialQuery[key]).split(',').filter(Boolean) : [] })
const filters = reactive({ ...initialQuery, page: Number(route.query.page || 1), pageSize: Number(route.query.pageSize || 12) })
const result = ref({ items: [], page: { number: 1, totalPages: 0, totalItems: 0 } })
const brands = ref([])
const brandOptions = computed(() => brands.value.map((brand) => ({ value: brand, label: brand })))
const choices = {
  size: [{ value: 'FINGERTIP', label: 'Fingertip' }, { value: 'EXTRA_SMALL', label: '超小' }, { value: 'SMALL', label: '小' }, { value: 'MEDIUM', label: '中' }, { value: 'LARGE', label: '大' }],
  shape: [{ value: 'SYMMETRICAL', label: '对称' }, { value: 'ERGONOMIC', label: '人体工学' }, { value: 'HYBRID', label: '混合' }],
  connection: [{ value: 'wired', label: '有线' }, { value: 'wireless_2_4g', label: '2.4G 无线' }, { value: 'bluetooth', label: '蓝牙' }],
  hand: [{ value: 'RIGHT', label: '右手' }, { value: 'LEFT', label: '左手' }, { value: 'AMBIDEXTROUS', label: '双手' }],
  humpPlacement: [{ value: 'FRONT', label: '前部' }, { value: 'CENTER', label: '中部' }, { value: 'BACK', label: '后部' }],
  frontFlare: [{ value: 'NARROW', label: '内收' }, { value: 'NEUTRAL', label: '平直' }, { value: 'FLARED', label: '外扩' }],
  sideCurvature: [{ value: 'FLAT', label: '平直' }, { value: 'MILD', label: '轻微' }, { value: 'CURVED', label: '明显' }],
  yesNo: [{ value: 'true', label: '是' }, { value: 'false', label: '否' }],
  sensorType: [{ value: 'OPTICAL', label: '光学' }, { value: 'LASER', label: '激光' }],
  switchType: [{ value: 'MECHANICAL', label: '机械' }, { value: 'OPTICAL', label: '光学' }, { value: 'INDUCTIVE', label: '电感' }],
  encoderType: [{ value: 'MECHANICAL', label: '机械' }, { value: 'OPTICAL', label: '光学' }, { value: 'MAGNETIC', label: '磁性' }]
}
const loading = ref(false)
const error = ref('')
const filterSections = reactive({ brand: false, size: false, shape: false, connection: false, sensor: false, performance: false, buttons: false, switch: false, wheel: false, material: false })
const activeFilterCount = computed(() => Object.entries(filters).filter(([key, value]) => (Array.isArray(value) ? value.length : value !== '' && value != null) && !['sort', 'page', 'pageSize'].includes(key)).length)
const rangeDefinitions = [
  { label: '重量', min: 'weightMin', max: 'weightMax' },
  { label: '长度', min: 'lengthMin', max: 'lengthMax' },
  { label: '宽度', min: 'widthMin', max: 'widthMax' },
  { label: '高度', min: 'heightMin', max: 'heightMax' },
  { label: 'DPI', min: 'dpiMin', max: 'dpiMax' },
  { label: '回报率', min: 'pollingMin', max: 'pollingMax' },
  { label: '追踪速度', min: 'trackingMin', max: 'trackingMax' },
  { label: '加速度', min: 'accelerationMin', max: 'accelerationMax' },
  { label: '总按键数', min: 'buttonsMin', max: 'buttonsMax' },
  { label: '侧键数', min: 'sideButtonsMin', max: 'sideButtonsMax' },
  { label: '滚轮步数', min: 'encoderStepsMin', max: 'encoderStepsMax' }
]
const rangeKeys = new Set(rangeDefinitions.flatMap(({ min, max }) => [min, max]))
const filterLabels = { q: '关键词', brand: '品牌', size: '尺寸', shape: '外形', hand: '适用手', connection: '连接', humpPlacement: '隆起位置', frontFlare: '前端外扩', sideCurvature: '侧面曲率', thumbRest: '拇指托', ringFingerRest: '无名指托', sensorType: '传感器类型', sensorName: '传感器型号', adjustableSensorPosition: '可调传感器位置', hotSwap: '热插拔微动', switchType: '微动类型', switchName: '微动型号', encoderType: '编码器类型', encoderName: '编码器型号', material: '材质', purchaseChannel: '购买渠道' }
const formatFilterValue = (value) => {
  const format = (item) => ({ wired: '有线', wireless_2_4g: '2.4G 无线', bluetooth: '蓝牙', true: '是', false: '否', RIGHT: '右手', LEFT: '左手', AMBIDEXTROUS: '双手' }[item] || item)
  return Array.isArray(value) ? value.map(format).join('、') : format(value)
}
const activeFilterChips = computed(() => {
  const chips = []
  Object.entries(filters).forEach(([key, value]) => {
    if ((!value || (Array.isArray(value) && !value.length)) || rangeKeys.has(key) || ['sort', 'page', 'pageSize'].includes(key)) return
    chips.push({ label: filterLabels[key] || key, value: formatFilterValue(value), keys: [key] })
  })
  rangeDefinitions.forEach(({ label, min, max }) => {
    const values = [filters[min] && `≥ ${filters[min]}`, filters[max] && `≤ ${filters[max]}`].filter(Boolean)
    if (values.length) chips.push({ label, value: values.join('  '), keys: [min, max] })
  })
  return chips
})
const filterSignature = computed(() => Object.entries(filters).filter(([key]) => key !== 'page').map(([key, value]) => `${key}:${value}`).join('|'))
let filterTimer
let realtimeTimer
let stopRealtime = () => {}

const compact = (source) => Object.fromEntries(Object.entries(source).filter(([, value]) => (Array.isArray(value) ? value.length : value !== '' && value != null)).map(([key, value]) => [key, Array.isArray(value) ? value.join(',') : value]))
const load = async () => {
  loading.value = true; error.value = ''
  try {
    const { data } = await api.get('/mice', { params: compact(filters) })
    result.value = data
    const query = compact(filters)
    if (query.sort === 'newest') delete query.sort
    if (Number(query.page) === 1) delete query.page
    if (Number(query.pageSize) === 12) delete query.pageSize
    await router.replace({ query })
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
const reset = () => { Object.assign(filters, defaults); Object.keys(filterSections).forEach((key) => { filterSections[key] = false }); load() }
const submit = () => { filters.page = 1; load() }
const move = (page) => { filters.page = page; load(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
const clearChip = (chip) => { chip.keys.forEach((key) => { filters[key] = '' }) }
watch(filterSignature, () => {
  filters.page = 1
  clearTimeout(filterTimer)
  filterTimer = setTimeout(load, 280)
})
onMounted(async () => {
  try { brands.value = (await api.get('/mice/brands')).data } catch (e) { error.value = errorMessage(e) }
  load()
  stopRealtime = onRealtime((event) => {
    if (event.type !== 'mouse.changed') return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(async () => {
      try { brands.value = (await api.get('/mice/brands')).data } catch (e) { error.value = errorMessage(e) }
      await load()
    }, 250)
  })
})
onBeforeUnmount(() => { stopRealtime(); clearTimeout(filterTimer); clearTimeout(realtimeTimer) })
</script>

<template>
  <main class="section-shell database-page">
    <div class="page-kicker"><span>DATABASE / MICE</span><span>{{ result.page.totalItems }} MATCHES</span></div>

    <div class="database-content-layout">
      <aside class="database-filter-rail">
        <form class="filter-studio" @submit.prevent="submit">
      <div class="filter-studio-head">
        <div><span class="panel-kicker">PARAMETRIC SEARCH</span><h2>参数筛选器</h2></div>
        <div class="filter-head-actions"><span v-if="activeFilterCount">{{ activeFilterCount }} 个条件已启用</span><button type="button" @click="reset">清空条件</button></div>
      </div>
      <div class="filter-primary">
        <label class="filter-search">关键词<input v-model.trim="filters.q" type="search" placeholder="品牌 / 型号 / 传感器"></label>
      </div>

      <div class="advanced-filters filter-accordion">
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.brand" @click="filterSections.brand = !filterSections.brand"><span><b>01</b> 品牌 <small>BRAND</small></span><i>{{ filterSections.brand ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.brand"><div class="filter-fields"><FilterCheckGroup label="品牌" v-model="filters.brand" :options="brandOptions" searchable /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.size" @click="filterSections.size = !filterSections.size"><span><b>02</b> 尺寸 <small>SIZE</small></span><i>{{ filterSections.size ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.size"><div class="filter-fields"><RangeSlider label="重量" unit="g" :min="20" :max="200" :step="1" :min-value="filters.weightMin" :max-value="filters.weightMax" @update:min-value="filters.weightMin = $event" @update:max-value="filters.weightMax = $event" /><RangeSlider label="长度" unit="mm" :min="70" :max="160" :step=".1" :min-value="filters.lengthMin" :max-value="filters.lengthMax" @update:min-value="filters.lengthMin = $event" @update:max-value="filters.lengthMax = $event" /><RangeSlider label="宽度" unit="mm" :min="40" :max="100" :step=".1" :min-value="filters.widthMin" :max-value="filters.widthMax" @update:min-value="filters.widthMin = $event" @update:max-value="filters.widthMax = $event" /><RangeSlider label="高度" unit="mm" :min="20" :max="80" :step=".1" :min-value="filters.heightMin" :max-value="filters.heightMax" @update:min-value="filters.heightMin = $event" @update:max-value="filters.heightMax = $event" /><FilterCheckGroup label="尺寸分类" v-model="filters.size" :options="choices.size" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.shape" @click="filterSections.shape = !filterSections.shape"><span><b>03</b> 外形 <small>SHAPE</small></span><i>{{ filterSections.shape ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.shape"><div class="filter-fields"><FilterCheckGroup label="外形类型" v-model="filters.shape" :options="choices.shape" /><FilterCheckGroup label="适用手" v-model="filters.hand" :options="choices.hand" /><FilterCheckGroup label="隆起位置" v-model="filters.humpPlacement" :options="choices.humpPlacement" /><FilterCheckGroup label="前端外扩" v-model="filters.frontFlare" :options="choices.frontFlare" /><FilterCheckGroup label="侧面曲率" v-model="filters.sideCurvature" :options="choices.sideCurvature" /><FilterCheckGroup label="拇指托" v-model="filters.thumbRest" :options="choices.yesNo" /><FilterCheckGroup label="无名指托" v-model="filters.ringFingerRest" :options="choices.yesNo" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.connection" @click="filterSections.connection = !filterSections.connection"><span><b>04</b> 连接 <small>CONNECTION</small></span><i>{{ filterSections.connection ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.connection"><div class="filter-fields"><FilterCheckGroup label="连接模式" v-model="filters.connection" :options="choices.connection" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.sensor" @click="filterSections.sensor = !filterSections.sensor"><span><b>05</b> 传感器 <small>SENSOR</small></span><i>{{ filterSections.sensor ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.sensor"><div class="filter-fields"><FilterCheckGroup label="传感器类型" v-model="filters.sensorType" :options="choices.sensorType" /><label>传感器型号<input v-model.trim="filters.sensorName" placeholder="例如 PAW3950"></label><FilterCheckGroup label="可调传感器位置" v-model="filters.adjustableSensorPosition" :options="choices.yesNo" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.performance" @click="filterSections.performance = !filterSections.performance"><span><b>06</b> 性能 <small>PERFORMANCE</small></span><i>{{ filterSections.performance ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.performance"><div class="filter-fields"><RangeSlider label="DPI" :min="100" :max="50000" :step="100" :min-value="filters.dpiMin" :max-value="filters.dpiMax" @update:min-value="filters.dpiMin = $event" @update:max-value="filters.dpiMax = $event" /><RangeSlider label="回报率" unit="Hz" :min="125" :max="8000" :step="125" :min-value="filters.pollingMin" :max-value="filters.pollingMax" @update:min-value="filters.pollingMin = $event" @update:max-value="filters.pollingMax = $event" /><RangeSlider label="追踪速度" unit="IPS" :min="100" :max="1000" :step="10" :min-value="filters.trackingMin" :max-value="filters.trackingMax" @update:min-value="filters.trackingMin = $event" @update:max-value="filters.trackingMax = $event" /><RangeSlider label="加速度" unit="G" :min="10" :max="100" :step="1" :min-value="filters.accelerationMin" :max-value="filters.accelerationMax" @update:min-value="filters.accelerationMin = $event" @update:max-value="filters.accelerationMax = $event" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.buttons" @click="filterSections.buttons = !filterSections.buttons"><span><b>07</b> 按键 <small>BUTTONS</small></span><i>{{ filterSections.buttons ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.buttons"><div class="filter-fields"><RangeSlider label="总按键数" :min="1" :max="20" :min-value="filters.buttonsMin" :max-value="filters.buttonsMax" @update:min-value="filters.buttonsMin = $event" @update:max-value="filters.buttonsMax = $event" /><RangeSlider label="侧键数" :min="0" :max="8" :min-value="filters.sideButtonsMin" :max-value="filters.sideButtonsMax" @update:min-value="filters.sideButtonsMin = $event" @update:max-value="filters.sideButtonsMax = $event" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.switch" @click="filterSections.switch = !filterSections.switch"><span><b>08</b> 微动 <small>SWITCH</small></span><i>{{ filterSections.switch ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.switch"><div class="filter-fields"><FilterCheckGroup label="热插拔微动" v-model="filters.hotSwap" :options="choices.yesNo" /><FilterCheckGroup label="微动类型" v-model="filters.switchType" :options="choices.switchType" /><label>微动型号<input v-model.trim="filters.switchName" placeholder="型号关键词"></label></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.wheel" @click="filterSections.wheel = !filterSections.wheel"><span><b>09</b> 滚轮 <small>WHEEL</small></span><i>{{ filterSections.wheel ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.wheel"><div class="filter-fields"><FilterCheckGroup label="编码器类型" v-model="filters.encoderType" :options="choices.encoderType" /><label>编码器型号<input v-model.trim="filters.encoderName" placeholder="型号关键词"></label><RangeSlider label="滚轮步数" :min="1" :max="100" :min-value="filters.encoderStepsMin" :max-value="filters.encoderStepsMax" @update:min-value="filters.encoderStepsMin = $event" @update:max-value="filters.encoderStepsMax = $event" /></div></fieldset></Transition></div>
        <div class="filter-accordion-section"><button class="filter-accordion-toggle" type="button" :aria-expanded="filterSections.material" @click="filterSections.material = !filterSections.material"><span><b>10</b> 材质 <small>MATERIAL</small></span><i>{{ filterSections.material ? '−' : '+' }}</i></button><Transition name="filter-expand"><fieldset v-if="filterSections.material"><div class="filter-fields"><label>材质<input v-model.trim="filters.material" placeholder="ABS / 镁合金"></label><label>购买渠道<input v-model.trim="filters.purchaseChannel" placeholder="官网 / 京东"></label></div></fieldset></Transition></div>
      </div>
      <div class="filter-submitbar"><label>每页<select v-model.number="filters.pageSize"><option :value="12">12</option><option :value="24">24</option><option :value="48">48</option></select></label><label>排序<select v-model="filters.sort"><option value="newest">最近录入</option><option value="brand_asc">品牌 A—Z</option><option value="weight_asc">从轻到重</option><option value="weight_desc">从重到轻</option></select></label></div>
        </form>
      </aside>

      <section class="database-results">
        <div class="active-filter-strip" v-if="activeFilterChips.length"><span class="active-filter-title">当前筛选</span><button v-for="(chip, index) in activeFilterChips" :key="`${chip.label}-${index}`" type="button" class="active-filter-chip" @click="clearChip(chip)"><span>{{ chip.label }}</span><b>{{ chip.value }}</b><i>×</i></button><button class="active-filter-clear" type="button" @click="reset">清空全部</button></div>
        <div class="flash error" v-if="error">{{ error }}</div>
        <div class="loading-state" v-if="loading">QUERYING DATABASE...</div>
        <div class="empty-state" v-else-if="!result.items.length"><span>NO MATCH</span><h2>没有找到符合条件的鼠标</h2><button class="button" @click="reset">清空全部条件</button></div>
        <div class="mouse-grid catalog-grid" v-else><MouseCard v-for="(mouse, index) in result.items" :key="mouse.id" :mouse="mouse" :index="index" /></div>
        <nav class="pagination" v-if="result.page.totalPages > 1"><button :disabled="result.page.number <= 1" @click="move(result.page.number - 1)">← 上一页</button><span>PAGE {{ result.page.number }} / {{ result.page.totalPages }}</span><button :disabled="result.page.number >= result.page.totalPages" @click="move(result.page.number + 1)">下一页 →</button></nav>
      </section>
    </div>
  </main>
</template>
