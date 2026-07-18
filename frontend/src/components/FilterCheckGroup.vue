<script setup>
import { computed, ref } from 'vue'
const props = defineProps({
  label: { type: String, required: true },
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, required: true },
  searchable: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue'])
const search = ref('')
const visibleOptions = computed(() => {
  const query = search.value.trim().toLowerCase()
  return query ? props.options.filter((option) => option.label.toLowerCase().includes(query)) : props.options
})
const choose = (value) => {
  const current = Array.isArray(props.modelValue) ? props.modelValue : []
  emit('update:modelValue', current.includes(value) ? current.filter((item) => item !== value) : [...current, value])
}
</script>

<template>
  <fieldset class="filter-check-group" :class="{ searchable }">
    <legend>{{ label }}</legend>
    <input v-if="searchable" v-model="search" class="filter-check-search" type="search" :placeholder="`搜索${label}`" :aria-label="`搜索${label}`">
    <div class="filter-check-options">
      <label v-for="option in visibleOptions" :key="option.value" class="filter-check-option" :class="{ selected: modelValue.includes(option.value) }">
        <input type="checkbox" :checked="modelValue.includes(option.value)" @change="choose(option.value)">
        <span>{{ option.label }}</span>
      </label>
    </div>
  </fieldset>
</template>
