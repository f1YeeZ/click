<script setup>
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { errorMessage } from '../api/client'
import MouseCard from '../components/MouseCard.vue'
import RangeSlider from '../components/RangeSlider.vue'
import FilterCheckGroup from '../components/FilterCheckGroup.vue'
import { onRealtime } from '../services/realtime'
import { clearCatalogFilterKeys, compactCatalogFilters, createCatalogFilters } from '../utils/catalogFilters'

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
const filters = reactive(createCatalogFilters(defaults, route.query, multiKeys))
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
const mobileFiltersOpen = ref(false)
const advancedFiltersOpen = ref(false)
const resultsSection = ref(null)
const activeFilterSection = ref('essentials')
const filterTabs = [
  { key: 'essentials', label: '快速定位', hint: '品牌、尺寸与连接' },
  { key: 'geometry', label: '外形尺寸', hint: '三围与握持结构' },
  { key: 'performance', label: '传感性能', hint: '传感器与回报率' },
  { key: 'components', label: '按键微动', hint: '按键数量与微动' },
  { key: 'build', label: '滚轮材质', hint: '编码器与购买渠道' },
]
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
const filterLabels = { q: '鼠标型号', brand: '品牌', size: '尺寸', shape: '外形', hand: '适用手', connection: '连接', humpPlacement: '隆起位置', frontFlare: '前端外扩', sideCurvature: '侧面曲率', thumbRest: '拇指托', ringFingerRest: '无名指托', sensorType: '传感器类型', sensorName: '传感器型号', adjustableSensorPosition: '可调传感器位置', hotSwap: '热插拔微动', switchType: '微动类型', switchName: '微动型号', encoderType: '编码器类型', encoderName: '编码器型号', material: '材质', purchaseChannel: '购买渠道' }
const choiceLabels = Object.values(choices).flat().reduce((labels, option) => ({ ...labels, [option.value]: option.label }), {})
const formatFilterValue = (value) => {
  const format = (item) => choiceLabels[item] || item
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
let syncingFromRoute = false

const load = async () => {
  loading.value = true; error.value = ''
  try {
    const { data } = await api.get('/mice', { params: compactCatalogFilters(filters) })
    result.value = data
    const query = compactCatalogFilters(filters)
    if (query.sort === 'newest') delete query.sort
    if (Number(query.page) === 1) delete query.page
    if (Number(query.pageSize) === 12) delete query.pageSize
    await router.replace({ query })
  } catch (e) { error.value = errorMessage(e) } finally { loading.value = false }
}
const toggleListValue = (key, value) => {
  const current = Array.isArray(filters[key]) ? filters[key] : []
  filters[key] = current.includes(value) ? current.filter(item => item !== value) : [...current, value]
}
const toggleQuickFilter = key => {
  if (key === 'lightweight') filters.weightMax = Number(filters.weightMax) === 60 ? '' : 60
  if (key === 'wireless') toggleListValue('connection', 'wireless_2_4g')
  if (key === 'symmetrical') toggleListValue('shape', 'SYMMETRICAL')
  if (key === 'polling8k') filters.pollingMin = Number(filters.pollingMin) === 8000 ? '' : 8000
}
const reset = () => { Object.assign(filters, defaults); activeFilterSection.value = 'essentials'; advancedFiltersOpen.value = false; load() }
const submit = () => { filters.page = 1; load() }
const move = (page) => { filters.page = page; load(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
const clearChip = (chip) => { clearCatalogFilterKeys(filters, chip.keys, multiKeys) }
const showMobileResults = () => {
  mobileFiltersOpen.value = false
  requestAnimationFrame(() => resultsSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}
watch(filterSignature, () => {
  if (syncingFromRoute) return
  filters.page = 1
  clearTimeout(filterTimer)
  filterTimer = setTimeout(load, 280)
}, { flush: 'sync' })
const syncFiltersFromRoute = () => {
  syncingFromRoute = true
  Object.assign(filters, createCatalogFilters(defaults, route.query, multiKeys))
  syncingFromRoute = false
}
const startViewRealtime = () => {
  stopRealtime()
  stopRealtime = onRealtime((event) => {
    if (event.type !== 'mouse.changed' && event.type !== 'sync.required') return
    clearTimeout(realtimeTimer)
    realtimeTimer = setTimeout(async () => {
      try { brands.value = (await api.get('/mice/brands')).data } catch (e) { error.value = errorMessage(e) }
      await load()
    }, 250)
  })
}
onActivated(() => {
  syncFiltersFromRoute()
  load()
  startViewRealtime()
})
onMounted(async () => {
  try { brands.value = (await api.get('/mice/brands')).data } catch (e) { error.value = errorMessage(e) }
})
onDeactivated(() => { stopRealtime(); clearTimeout(realtimeTimer) })
onBeforeUnmount(() => { stopRealtime(); clearTimeout(filterTimer); clearTimeout(realtimeTimer) })
</script>

<template>
  <main class="section-shell database-page">
    <div class="page-kicker"><span>DATABASE / MICE</span><span>{{ result.page.totalItems }} MATCHES</span></div>

    <button class="mobile-filter-trigger" type="button" :aria-expanded="mobileFiltersOpen" aria-controls="catalog-filter-panel" @click="mobileFiltersOpen = !mobileFiltersOpen">
      <span><b>筛选与排序</b><small>{{ activeFilterCount ? `${activeFilterCount} 个条件已启用` : '按参数缩小范围' }}</small></span>
      <i>{{ mobileFiltersOpen ? '收起' : '展开' }}</i>
    </button>
    <div class="database-content-layout">
      <aside id="catalog-filter-panel" class="database-filter-rail" :class="{ 'mobile-open': mobileFiltersOpen }">
        <form class="filter-studio filter-workbench" @submit.prevent="submit" @keydown.esc="advancedFiltersOpen = false">
          <div class="filter-compact-bar">
            <label class="filter-search"><span>型号</span><input v-model.trim="filters.q" type="search" placeholder="搜索鼠标" autocomplete="off"></label>
            <div class="filter-quick-row" aria-label="快捷筛选">
              <button type="button" :aria-pressed="Number(filters.weightMax) === 60" @click="toggleQuickFilter('lightweight')">≤ 60g</button>
              <button type="button" :aria-pressed="filters.connection.includes('wireless_2_4g')" @click="toggleQuickFilter('wireless')">2.4G</button>
              <button type="button" :aria-pressed="filters.shape.includes('SYMMETRICAL')" @click="toggleQuickFilter('symmetrical')">对称</button>
              <button type="button" :aria-pressed="Number(filters.pollingMin) === 8000" @click="toggleQuickFilter('polling8k')">8KHz</button>
            </div>
            <div class="filter-compact-actions">
              <span aria-live="polite">{{ result.page.totalItems }} 款</span>
              <button class="filter-more-button" type="button" :aria-expanded="advancedFiltersOpen" aria-controls="filter-advanced-panel" @click="advancedFiltersOpen = !advancedFiltersOpen">{{ advancedFiltersOpen ? '收起' : '更多筛选' }}<b v-if="activeFilterCount">{{ activeFilterCount }}</b></button>
              <button v-if="activeFilterCount" class="filter-clear-button" type="button" @click="reset">清空</button>
            </div>
          </div>

          <Transition name="filter-panel">
            <div v-if="advancedFiltersOpen" id="filter-advanced-panel" class="filter-advanced-panel">
              <nav class="filter-domain-tabs" aria-label="筛选参数分类">
                <button v-for="tab in filterTabs" :key="tab.key" type="button" :class="{ active: activeFilterSection === tab.key }" :aria-pressed="activeFilterSection === tab.key" :aria-controls="`filter-domain-${tab.key}`" @click="activeFilterSection = tab.key"><strong>{{ tab.label }}</strong><small>{{ tab.hint }}</small></button>
              </nav>

              <section :id="`filter-domain-${activeFilterSection}`" :key="activeFilterSection" class="filter-domain-panel" aria-live="polite">
              <template v-if="activeFilterSection === 'essentials'">
                <FilterCheckGroup class="filter-span-6 filter-brand-control" label="品牌" v-model="filters.brand" :options="brandOptions" searchable />
                <FilterCheckGroup class="filter-span-3" label="尺寸分类" v-model="filters.size" :options="choices.size" />
                <RangeSlider class="filter-span-3" label="重量" unit="g" :min="20" :max="200" :step="1" :min-value="filters.weightMin" :max-value="filters.weightMax" @update:min-value="filters.weightMin = $event" @update:max-value="filters.weightMax = $event" />
                <FilterCheckGroup class="filter-span-4" label="外形类型" v-model="filters.shape" :options="choices.shape" />
                <FilterCheckGroup class="filter-span-3" label="适用手" v-model="filters.hand" :options="choices.hand" />
                <FilterCheckGroup class="filter-span-5 filter-tablet-wide" label="连接模式" v-model="filters.connection" :options="choices.connection" />
              </template>

              <template v-else-if="activeFilterSection === 'geometry'">
                <RangeSlider class="filter-span-3" label="长度" unit="mm" :min="70" :max="160" :step=".1" :min-value="filters.lengthMin" :max-value="filters.lengthMax" @update:min-value="filters.lengthMin = $event" @update:max-value="filters.lengthMax = $event" />
                <RangeSlider class="filter-span-3" label="宽度" unit="mm" :min="40" :max="100" :step=".1" :min-value="filters.widthMin" :max-value="filters.widthMax" @update:min-value="filters.widthMin = $event" @update:max-value="filters.widthMax = $event" />
                <RangeSlider class="filter-span-3" label="高度" unit="mm" :min="20" :max="80" :step=".1" :min-value="filters.heightMin" :max-value="filters.heightMax" @update:min-value="filters.heightMin = $event" @update:max-value="filters.heightMax = $event" />
                <FilterCheckGroup class="filter-span-3" label="隆起位置" v-model="filters.humpPlacement" :options="choices.humpPlacement" />
                <FilterCheckGroup class="filter-span-3" label="前端外扩" v-model="filters.frontFlare" :options="choices.frontFlare" />
                <FilterCheckGroup class="filter-span-3" label="侧面曲率" v-model="filters.sideCurvature" :options="choices.sideCurvature" />
                <FilterCheckGroup class="filter-span-3" label="拇指托" v-model="filters.thumbRest" :options="choices.yesNo" />
                <FilterCheckGroup class="filter-span-3" label="无名指托" v-model="filters.ringFingerRest" :options="choices.yesNo" />
              </template>

              <template v-else-if="activeFilterSection === 'performance'">
                <FilterCheckGroup class="filter-span-4" label="传感器类型" v-model="filters.sensorType" :options="choices.sensorType" />
                <label class="filter-text-field filter-span-4"><span>传感器型号</span><input v-model.trim="filters.sensorName" placeholder="例如 PAW3950"></label>
                <FilterCheckGroup class="filter-span-4" label="可调传感器位置" v-model="filters.adjustableSensorPosition" :options="choices.yesNo" />
                <RangeSlider class="filter-span-3" label="DPI" :min="100" :max="50000" :step="100" :min-value="filters.dpiMin" :max-value="filters.dpiMax" @update:min-value="filters.dpiMin = $event" @update:max-value="filters.dpiMax = $event" />
                <RangeSlider class="filter-span-3" label="回报率" unit="Hz" :min="125" :max="8000" :step="125" :min-value="filters.pollingMin" :max-value="filters.pollingMax" @update:min-value="filters.pollingMin = $event" @update:max-value="filters.pollingMax = $event" />
                <RangeSlider class="filter-span-3" label="追踪速度" unit="IPS" :min="100" :max="1000" :step="10" :min-value="filters.trackingMin" :max-value="filters.trackingMax" @update:min-value="filters.trackingMin = $event" @update:max-value="filters.trackingMax = $event" />
                <RangeSlider class="filter-span-3 filter-tablet-wide" label="加速度" unit="G" :min="10" :max="100" :step="1" :min-value="filters.accelerationMin" :max-value="filters.accelerationMax" @update:min-value="filters.accelerationMin = $event" @update:max-value="filters.accelerationMax = $event" />
              </template>

              <template v-else-if="activeFilterSection === 'components'">
                <RangeSlider class="filter-span-4" label="总按键数" :min="1" :max="20" :min-value="filters.buttonsMin" :max-value="filters.buttonsMax" @update:min-value="filters.buttonsMin = $event" @update:max-value="filters.buttonsMax = $event" />
                <RangeSlider class="filter-span-4" label="侧键数" :min="0" :max="8" :min-value="filters.sideButtonsMin" :max-value="filters.sideButtonsMax" @update:min-value="filters.sideButtonsMin = $event" @update:max-value="filters.sideButtonsMax = $event" />
                <FilterCheckGroup class="filter-span-4" label="热插拔微动" v-model="filters.hotSwap" :options="choices.yesNo" />
                <FilterCheckGroup class="filter-span-6" label="微动类型" v-model="filters.switchType" :options="choices.switchType" />
                <label class="filter-text-field filter-span-6 filter-tablet-wide"><span>微动型号</span><input v-model.trim="filters.switchName" placeholder="输入型号关键词"></label>
              </template>

              <template v-else>
                <FilterCheckGroup class="filter-span-4" label="编码器类型" v-model="filters.encoderType" :options="choices.encoderType" />
                <label class="filter-text-field filter-span-4"><span>编码器型号</span><input v-model.trim="filters.encoderName" placeholder="输入型号关键词"></label>
                <RangeSlider class="filter-span-4" label="滚轮步数" :min="1" :max="100" :min-value="filters.encoderStepsMin" :max-value="filters.encoderStepsMax" @update:min-value="filters.encoderStepsMin = $event" @update:max-value="filters.encoderStepsMax = $event" />
                <label class="filter-text-field filter-span-6"><span>外壳材质</span><input v-model.trim="filters.material" placeholder="例如 ABS、镁合金"></label>
                <label class="filter-text-field filter-span-6 filter-tablet-wide"><span>购买渠道</span><input v-model.trim="filters.purchaseChannel" placeholder="例如官网、京东"></label>
              </template>
              </section>

              <div class="filter-submitbar"><label>每页<select v-model.number="filters.pageSize"><option :value="12">12</option><option :value="24">24</option><option :value="48">48</option></select></label><label>排序<select v-model="filters.sort"><option value="newest">最近录入</option><option value="brand_asc">品牌 A—Z</option><option value="weight_asc">从轻到重</option><option value="weight_desc">从重到轻</option><option value="rating_desc">握姿舒适度最高</option><option value="review_count_desc">评价最多</option></select></label><button class="mobile-filter-done primary-action-button" type="button" @click="showMobileResults">查看 {{ result.page.totalItems }} 款结果</button></div>
            </div>
          </Transition>

          <div v-if="mobileFiltersOpen && !advancedFiltersOpen" class="filter-submitbar filter-submitbar-mobile-only"><button class="mobile-filter-done primary-action-button" type="button" @click="showMobileResults">查看 {{ result.page.totalItems }} 款结果</button></div>
        </form>
      </aside>

      <section ref="resultsSection" class="database-results">
        <header class="database-results-head">
          <div><p class="page-label">MOUSE DATABASE</p><h1>鼠标数据库</h1><span>找到 {{ result.page.totalItems }} 款符合当前条件的鼠标</span></div>
          <label>排序方式<select v-model="filters.sort"><option value="newest">最近录入</option><option value="brand_asc">品牌 A—Z</option><option value="weight_asc">从轻到重</option><option value="weight_desc">从重到轻</option><option value="rating_desc">握姿舒适度最高</option><option value="review_count_desc">评价最多</option></select></label>
        </header>
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
