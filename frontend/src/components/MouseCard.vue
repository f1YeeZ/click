<script setup>
import { computed, ref } from 'vue'
import { useCompareStore } from '../stores/compare'

const props = defineProps({ mouse: { type: Object, required: true }, index: { type: Number, default: 0 } })
const compare = useCompareStore()
const error = ref('')
const imageFailed = ref(false)
const labels = { FINGERTIP: 'Fingertip', EXTRA_SMALL: '超小', SMALL: '小', MEDIUM: '中', LARGE: '大', SYMMETRICAL: '对称', ERGONOMIC: '人体工学', HYBRID: '混合' }
const connection = computed(() => props.mouse.connectionModes?.length >= 3 ? '三模' : props.mouse.connectionModes?.length === 2 ? '双模' : props.mouse.connectionModes?.includes('wired') ? '有线' : '无线')
const sensorShort = computed(() => String(props.mouse.sensorName || '—').replace('PixArt ', ''))
const hasImage = computed(() => Boolean(props.mouse.imageUrl) && !imageFailed.value)
const toggle = () => { try { compare.toggle(props.mouse); error.value = '' } catch (e) { error.value = e.message } }
</script>

<template>
  <article class="mouse-card">
    <RouterLink class="card-visual" :class="{ 'image-empty': !hasImage }" :to="`/mice/${mouse.slug}`" aria-label="查看鼠标详情">
      <img v-if="hasImage" class="card-product-image" :src="mouse.imageUrl" :alt="`${mouse.brand} ${mouse.model}`" loading="lazy" @error="imageFailed = true">
      <span v-else class="card-image-placeholder"><small>IMAGE PENDING</small><strong>{{ mouse.brand }} {{ mouse.model }}</strong><em>暂无产品图片</em></span>
    </RouterLink>
    <div class="card-body">
      <div class="card-topline"><span>{{ mouse.brand }}</span><span>{{ connection }} · {{ labels[mouse.shapeType] || mouse.shapeType }}</span></div>
      <h3><RouterLink :to="`/mice/${mouse.slug}`">{{ mouse.model }}</RouterLink></h3>
      <p class="variant">{{ mouse.variant || 'STANDARD EDITION' }}</p>
      <div class="card-metrics">
        <div><span>重量</span><strong>{{ mouse.weightG ?? '—' }}g</strong></div>
        <div><span>传感器</span><strong>{{ sensorShort }}</strong></div>
        <div><span>回报率</span><strong>{{ mouse.maxPollingRateHz ?? '—' }}Hz</strong></div>
      </div>
      <button class="compare-add" :class="{ selected: compare.contains(mouse.id) }" @click="toggle">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '加入对比' }}</button>
    </div>
    <small class="inline-error" v-if="error">{{ error }}</small>
  </article>
</template>
