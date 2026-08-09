<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCompareStore } from '../stores/compare'

const props = defineProps({ mouse: { type: Object, required: true }, index: { type: Number, default: 0 } })
const compare = useCompareStore()
const router = useRouter()
const error = ref('')
const imageFailed = ref(false)
const labels = { FINGERTIP: 'Fingertip', EXTRA_SMALL: '超小', SMALL: '小', MEDIUM: '中', LARGE: '大', SYMMETRICAL: '对称', ERGONOMIC: '人体工学', HYBRID: '混合' }
const connection = computed(() => props.mouse.connectionModes?.length >= 3 ? '三模' : props.mouse.connectionModes?.length === 2 ? '双模' : props.mouse.connectionModes?.includes('wired') ? '有线' : '无线')
const sensorShort = computed(() => String(props.mouse.sensorName || '—').replace('PixArt ', ''))
const hasImage = computed(() => Boolean(props.mouse.imageUrl) && !imageFailed.value)
const openDetail = () => router.push(`/mice/${props.mouse.id}`)
const toggle = () => { try { compare.toggle(props.mouse); error.value = '' } catch (e) { error.value = e.message } }
</script>

<template>
  <article class="mouse-card" role="link" tabindex="0" :aria-label="`查看 ${mouse.brand} ${mouse.model} 详情`" @click="openDetail" @keydown.enter.self="openDetail" @keydown.space.self.prevent="openDetail">
    <div class="card-visual" :class="{ 'image-empty': !hasImage }">
      <img v-if="hasImage" class="card-product-image" :src="mouse.imageUrl" :alt="`${mouse.brand} ${mouse.model}`" loading="lazy" @error="imageFailed = true">
      <span v-else class="card-image-placeholder"><small>IMAGE PENDING</small><strong>{{ mouse.brand }} {{ mouse.model }}</strong><em>暂无产品图片</em></span>
    </div>
    <div class="card-body">
      <div class="card-topline"><span>{{ mouse.brand }}</span><span>{{ connection }} · {{ labels[mouse.shapeType] || mouse.shapeType }}</span></div>
      <h3>{{ mouse.model }}</h3>
      <p class="variant">{{ mouse.variant || 'STANDARD EDITION' }}</p>
      <div class="card-rating" :class="{ low: mouse.lowReviewSample, empty: !mouse.reviewCount }"><strong>{{ mouse.reviewCount ? mouse.averageScore : '—' }}</strong><span>{{ mouse.reviewCount ? `${mouse.reviewCount} 份舒适评分` : '暂无舒适评分' }}<small v-if="mouse.reviewCount && mouse.lowReviewSample">样本较少</small></span></div>
      <div class="card-metrics">
        <div><span>重量</span><strong>{{ mouse.weightG ?? '—' }}g</strong></div>
        <div><span>传感器</span><strong>{{ sensorShort }}</strong></div>
        <div><span>回报率</span><strong>{{ mouse.maxPollingRateHz ?? '—' }}Hz</strong></div>
      </div>
      <button class="compare-add" type="button" :class="{ selected: compare.contains(mouse.id) }" @click.stop="toggle">{{ compare.contains(mouse.id) ? '✓ 已加入对比' : '加入对比' }}</button>
    </div>
    <small class="inline-error" v-if="error">{{ error }}</small>
  </article>
</template>
