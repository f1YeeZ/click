<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  side: { type: String, required: true, validator: value => ['left', 'right'].includes(value) },
  ad: { type: Object, required: true },
})

const imageFailed = ref(false)
const imageUrl = computed(() => String(props.ad?.imageUrl || '').trim())
const targetUrl = computed(() => String(props.ad?.targetUrl || '').trim())
const altText = computed(() => String(props.ad?.altText || '').trim() || `${props.side === 'left' ? '左侧' : '右侧'}广告`)

watch(imageUrl, () => { imageFailed.value = false })
</script>

<template>
  <aside class="ad-rail" :class="`ad-rail-${side}`" :data-ad-slot="`${side}-rail`" aria-label="广告">
    <a v-if="targetUrl" class="ad-rail-creative" :href="targetUrl" target="_blank" rel="noopener noreferrer sponsored">
      <img v-if="imageUrl && !imageFailed" :src="imageUrl" :alt="altText" width="220" height="506" @error="imageFailed = true">
      <span v-else class="ad-rail-placeholder"><small>ADVERTISEMENT</small><strong>广告位</strong><em>220 × 506</em></span>
    </a>
    <div v-else class="ad-rail-creative">
      <img v-if="imageUrl && !imageFailed" :src="imageUrl" :alt="altText" width="220" height="506" @error="imageFailed = true">
      <span v-else class="ad-rail-placeholder"><small>ADVERTISEMENT</small><strong>广告位</strong><em>220 × 506</em></span>
    </div>
  </aside>
</template>

<style scoped>
.ad-rail {
  position: fixed;
  z-index: 8;
  top: 14.7vh;
  display: none;
  width: var(--ad-rail-width, 13.07vw);
  aspect-ratio: 220 / 506;
}

.ad-rail-left { left: var(--ad-rail-edge, 0.4vw); }
.ad-rail-right { right: var(--ad-rail-edge, 0.4vw); }

.ad-rail-creative {
  display: block;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-radius: 7px;
  background: #0f131b;
  color: #c5cedb;
  text-decoration: none;
}

.ad-rail-creative:focus-visible {
  outline: 2px solid var(--figma-cyan);
  outline-offset: 3px;
}

.ad-rail-creative img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ad-rail-placeholder {
  display: grid;
  width: 100%;
  height: 100%;
  place-content: center;
  gap: 0.5rem;
  border: 1px solid #202735;
  border-radius: inherit;
  background: linear-gradient(145deg, #111620, #0a0d13);
  text-align: center;
}

.ad-rail-placeholder small {
  color: #8c96a8;
  font: 0.62rem var(--dv-mono);
  letter-spacing: 0.12em;
}

.ad-rail-placeholder strong { color: #f3f7fb; font-size: 1rem; }
.ad-rail-placeholder em { color: #8c96a8; font: normal 0.7rem var(--dv-mono); }

/* Wide CSS viewports get dedicated rails; the content shell reserves their columns. */
@media (min-width: 90rem) and (min-height: 38.75rem) {
  .ad-rail { display: block; }
}
</style>
