<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  unit: { type: String, default: '' },
  min: { type: Number, required: true },
  max: { type: Number, required: true },
  step: { type: Number, default: 1 },
  minValue: { type: [String, Number], default: '' },
  maxValue: { type: [String, Number], default: '' }
})
const emit = defineEmits(['update:minValue', 'update:maxValue'])
const lower = computed(() => props.minValue === '' || props.minValue == null ? props.min : Number(props.minValue))
const upper = computed(() => props.maxValue === '' || props.maxValue == null ? props.max : Number(props.maxValue))
const lowerPercent = computed(() => ((lower.value - props.min) / (props.max - props.min)) * 100)
const upperPercent = computed(() => ((upper.value - props.min) / (props.max - props.min)) * 100)
const setLower = (event) => emit('update:minValue', Math.min(Number(event.target.value), upper.value))
const setUpper = (event) => emit('update:maxValue', Math.max(Number(event.target.value), lower.value))
const setLowerFromInput = (event) => {
  if (event.target.value === '') return emit('update:minValue', '')
  const value = Math.min(Math.max(Number(event.target.value), props.min), upper.value)
  emit('update:minValue', value)
}
const setUpperFromInput = (event) => {
  if (event.target.value === '') return emit('update:maxValue', '')
  const value = Math.max(Math.min(Number(event.target.value), props.max), lower.value)
  emit('update:maxValue', value)
}
</script>

<template>
  <div class="range-slider">
    <div class="range-slider-heading"><span>{{ label }}</span><small v-if="unit">{{ unit }}</small></div>
    <div class="range-slider-values"><input type="number" :style="{ width: `${String(lower).length + 0.25}ch` }" :min="min" :max="upper" :step="step" :value="lower" :aria-label="`${label}最小值`" @input="setLowerFromInput"><input type="number" :style="{ width: `${String(upper).length + 0.25}ch` }" :min="lower" :max="max" :step="step" :value="upper" :aria-label="`${label}最大值`" @input="setUpperFromInput"></div>
    <div class="range-slider-control">
      <span class="range-slider-fill" :style="{ left: `${lowerPercent}%`, right: `${100 - upperPercent}%` }"></span>
      <input class="range-slider-input range-slider-input-lower" type="range" :min="min" :max="max" :step="step" :value="lower" :aria-label="`${label}最小值`" @input="setLower">
      <input class="range-slider-input range-slider-input-upper" type="range" :min="min" :max="max" :step="step" :value="upper" :aria-label="`${label}最大值`" @input="setUpper">
    </div>
  </div>
</template>
