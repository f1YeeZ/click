<script setup>
import { computed, reactive, ref, watch } from 'vue'
import AdminFloatingPanel from './AdminFloatingPanel.vue'

const props = defineProps({
  config: { type: Object, default: null },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['confirm', 'close'])
const draft = reactive({})
const validationMessage = ref('')
const fields = computed(() => props.config?.fields || [])

watch(() => props.config, config => {
  for (const key of Object.keys(draft)) delete draft[key]
  for (const field of config?.fields || []) draft[field.key] = field.value ?? ''
  validationMessage.value = ''
}, { immediate: true })

const submit = () => {
  const missing = fields.value.find(field => field.required && !String(draft[field.key] || '').trim())
  if (missing) {
    validationMessage.value = `请填写${missing.label}`
    return
  }
  validationMessage.value = ''
  emit('confirm', { ...draft })
}
</script>

<template>
  <AdminFloatingPanel
    :open="Boolean(config)"
    :title="config?.title || '确认操作'"
    :subtitle="config?.subtitle || ''"
    size="compact"
    :busy="busy"
    @close="emit('close')"
  >
    <form id="admin-action-dialog-form" class="admin-action-dialog" @submit.prevent="submit">
      <p v-if="config?.message" class="admin-action-message">{{ config.message }}</p>
      <div v-if="fields.length" class="admin-action-fields">
        <label v-for="field in fields" :key="field.key">
          <span>{{ field.label }}<em v-if="field.required">必填</em></span>
          <textarea
            v-if="field.type === 'textarea'"
            v-model="draft[field.key]"
            :placeholder="field.placeholder || ''"
            :maxlength="field.maxlength"
            :required="field.required"
            :autofocus="field.autofocus"
            rows="4"
            @input="validationMessage = ''"
          />
          <input
            v-else
            v-model="draft[field.key]"
            :type="field.type"
            :placeholder="field.placeholder || ''"
            :maxlength="field.maxlength"
            :required="field.required"
            :autofocus="field.autofocus"
            @input="validationMessage = ''"
          >
          <small v-if="field.hint">{{ field.hint }}</small>
        </label>
      </div>
      <p v-if="validationMessage" class="admin-action-error" role="alert">{{ validationMessage }}</p>
    </form>
    <template #footer>
      <div class="admin-action-buttons">
        <button type="button" class="button button-ghost" :disabled="busy" @click="emit('close')">{{ config?.cancelLabel || '取消' }}</button>
        <button
          form="admin-action-dialog-form"
          class="button"
          :class="{ 'danger-button': config?.tone === 'danger' }"
          :disabled="busy"
        >{{ busy ? '正在处理…' : config?.confirmLabel || '确认操作' }}</button>
      </div>
    </template>
  </AdminFloatingPanel>
</template>

<style scoped>
.admin-action-dialog { display: grid; gap: 18px; }
.admin-action-message { margin: 0; color: #d4d4d4; font-size: .88rem; line-height: 1.65; white-space: pre-line; }
.admin-action-fields { display: grid; gap: 14px; }
.admin-action-fields label { display: grid; gap: 7px; color: #d8d8d8; font-size: .78rem; font-weight: 700; }
.admin-action-fields label > span { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.admin-action-fields em { color: #aaa; font-size: .65rem; font-style: normal; font-weight: 500; }
.admin-action-fields input,
.admin-action-fields textarea { width: 100%; border: 1px solid #4a4a4a; border-radius: 9px; background: #1b1b1b; color: #f1f1f1; font: inherit; font-weight: 500; line-height: 1.5; }
.admin-action-fields input { min-height: 42px; padding: 0 12px; }
.admin-action-fields textarea { min-height: 108px; resize: vertical; padding: 11px 12px; }
.admin-action-fields input:hover,
.admin-action-fields textarea:hover { border-color: #666; }
.admin-action-fields input:focus,
.admin-action-fields textarea:focus { border-color: #aaa; outline: 2px solid rgba(220, 220, 220, .16); outline-offset: 1px; }
.admin-action-fields small { color: #929292; font-size: .68rem; font-weight: 500; line-height: 1.5; }
.admin-action-error { margin: -6px 0 0; color: #f0b4b4; font-size: .74rem; }
.admin-action-buttons { display: flex; justify-content: flex-end; gap: 8px; width: 100%; }
.admin-action-buttons .danger-button { background: #633236; color: #fff; }
.admin-action-buttons .danger-button:hover { background: #754047; }
@media (max-width: 620px) {
  .admin-action-buttons { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>
