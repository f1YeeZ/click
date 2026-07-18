<script setup>
import { computed, ref } from 'vue'
import { useCompareStore } from '../stores/compare'

const props = defineProps({ mouse: { type: Object, required: true }, index: { type: Number, default: 0 } })
const compare = useCompareStore()
const error = ref('')
const labels = { FINGERTIP: 'Fingertip', EXTRA_SMALL: '超小', SMALL: '小', MEDIUM: '中', LARGE: '大', SYMMETRICAL: '对称', ERGONOMIC: '人体工学', HYBRID: '混合' }
const connection = computed(() => props.mouse.connectionModes?.length >= 3 ? '三模' : props.mouse.connectionModes?.length === 2 ? '双模' : props.mouse.connectionModes?.includes('wired') ? '有线' : '无线')
const sensorShort = computed(() => String(props.mouse.sensorName || '—').replace('PixArt ', ''))
const signal = computed(() => Math.min(100, Math.max(18, Number(props.mouse.maxPollingRateHz || 1000) / 80)))
const toggle = () => { try { compare.toggle(props.mouse); error.value = '' } catch (e) { error.value = e.message } }
</script>

<template>
  <article class="mouse-card">
    <RouterLink class="card-visual" :to="`/mice/${mouse.slug}`" :style="{ '--signal': `${signal}%` }" aria-label="查看鼠标详情">
      <span class="visual-code">UNIT // {{ String(index + 1).padStart(2, '0') }}</span>
      <span class="visual-axis visual-axis-x"></span><span class="visual-axis visual-axis-y"></span>
      <span class="visual-pulse"></span>
      <span class="verified-badge">VERIFIED</span>
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
