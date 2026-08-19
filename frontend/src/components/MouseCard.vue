<script setup>
import { computed, ref } from 'vue'
import { useCompareStore } from '../stores/compare'

const props = defineProps({ mouse: { type: Object, required: true }, index: { type: Number, default: 0 } })
const compare = useCompareStore()
const error = ref('')
const imageFailed = ref(false)
const labels = { FINGERTIP: 'Fingertip', EXTRA_SMALL: '超小', SMALL: '小', MEDIUM: '中', LARGE: '大', SYMMETRICAL: '对称', ERGONOMIC: '人体工学', HYBRID: '混合' }
const connection = computed(() => props.mouse.connectionModes?.length >= 3 ? '三模' : props.mouse.connectionModes?.length === 2 ? '双模' : props.mouse.connectionModes?.includes('wired') ? '有线' : '无线')
const hasImage = computed(() => Boolean(props.mouse.imageUrl) && !imageFailed.value)
const toggle = () => { try { compare.toggle(props.mouse); error.value = '' } catch (e) { error.value = e.message } }
</script>

<template>
  <article class="mouse-card">
    <RouterLink class="card-detail-link" :to="`/mice/${mouse.id}`" :aria-label="`查看 ${mouse.brand} ${mouse.model}${mouse.variant ? ` ${mouse.variant}` : ''} 详情`">
      <div class="card-visual" :class="{ 'image-empty': !hasImage }">
        <img v-if="hasImage" class="card-product-image" :src="mouse.imageUrl" :alt="`${mouse.brand} ${mouse.model}`" loading="lazy" @error="imageFailed = true">
        <span v-else class="card-image-placeholder"><strong>{{ mouse.brand }} {{ mouse.model }}</strong><em>暂无产品图片</em></span>
      </div>
      <div class="card-body">
        <span class="card-brand">{{ mouse.brand }}</span>
        <h3 :title="[mouse.model, mouse.variant].filter(Boolean).join(' · ')">{{ mouse.model }}<small v-if="mouse.variant"> · {{ mouse.variant }}</small></h3>
      </div>
    </RouterLink>
    <div class="card-footer">
      <p class="card-facts">
        <span>{{ mouse.weightG ?? '—' }}g</span>
        <span>{{ labels[mouse.shapeType] || mouse.shapeType || '未知形状' }}</span>
        <span>{{ connection }}</span>
      </p>
      <button class="compare-add" type="button" :class="{ selected: compare.contains(mouse.id) }" :aria-pressed="compare.contains(mouse.id)" @click="toggle">{{ compare.contains(mouse.id) ? '已对比' : '对比' }}</button>
    </div>
    <small class="inline-error" v-if="error">{{ error }}</small>
  </article>
</template>
