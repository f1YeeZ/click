<script setup>
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  size: { type: String, default: 'default', validator: value => ['compact', 'default', 'wide'].includes(value) },
  busy: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const panel = ref(null)
const titleId = `admin-floating-title-${useId().replaceAll(':', '')}`
let previousFocus = null

const focusableSelector = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

const requestClose = () => {
  if (!props.busy) emit('close')
}

const handleKeydown = (event) => {
  if (event.key === 'Escape') {
    event.preventDefault()
    event.stopPropagation()
    requestClose()
    return
  }
  if (event.key !== 'Tab' || !panel.value) return
  const focusable = [...panel.value.querySelectorAll(focusableSelector)]
  if (!focusable.length) {
    event.preventDefault()
    panel.value.focus()
    return
  }
  const first = focusable[0]
  const last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

watch(() => props.open, async open => {
  if (open) {
    previousFocus = document.activeElement
    await nextTick()
    const preferred = panel.value?.querySelector('[autofocus]')
      || panel.value?.querySelector('input:not([disabled]), select:not([disabled]), textarea:not([disabled])')
      || panel.value?.querySelector('button:not([disabled])')
    ;(preferred || panel.value)?.focus()
  } else {
    previousFocus?.focus?.()
    previousFocus = null
  }
})

onBeforeUnmount(() => previousFocus?.focus?.())
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="admin-floating-overlay" @click.self="requestClose" @keydown="handleKeydown">
      <section
        ref="panel"
        class="admin-floating-panel"
        :class="`size-${size}`"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        tabindex="-1"
      >
        <header class="admin-floating-header">
          <div>
            <h2 :id="titleId">{{ title }}</h2>
            <p v-if="subtitle">{{ subtitle }}</p>
          </div>
          <button type="button" :disabled="busy" aria-label="关闭悬浮窗" @click="requestClose">×</button>
        </header>
        <div class="admin-floating-body"><slot /></div>
        <footer v-if="$slots.footer" class="admin-floating-footer"><slot name="footer" /></footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.admin-floating-overlay {
  position: fixed;
  inset: 0;
  z-index: 145;
  display: grid;
  place-items: center;
  padding: 18px;
  background: rgba(5, 5, 5, 0.8);
  backdrop-filter: blur(10px);
  overscroll-behavior: contain;
  animation: admin-floating-fade 180ms ease-out both;
}
.admin-floating-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: min(760px, 100%);
  max-height: min(90dvh, 860px);
  overflow: hidden;
  border: 1px solid #444;
  border-radius: 16px;
  outline: none;
  background: #121212;
  color: #ededed;
  animation: admin-floating-rise 200ms cubic-bezier(0.2, 0.8, 0.25, 1) both;
}
.admin-floating-panel.size-compact { width: min(560px, 100%); }
.admin-floating-panel.size-wide { width: min(1120px, 100%); }
.admin-floating-header,
.admin-floating-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 17px 20px;
  background: #171717;
}
.admin-floating-header { border-bottom: 1px solid #383838; }
.admin-floating-footer { border-top: 1px solid #383838; }
.admin-floating-header h2 {
  margin: 0;
  color: #f1f1f1;
  font: 700 1.05rem/1.2 var(--sans, sans-serif);
  letter-spacing: -0.025em;
}
.admin-floating-header p {
  margin: 5px 0 0;
  color: #a5a5a5;
  font-size: .75rem;
}
.admin-floating-header > button {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border: 1px solid #4c4c4c;
  border-radius: 9px;
  background: #202020;
  color: #dedede;
  font-size: 1.2rem;
  cursor: pointer;
}
.admin-floating-header > button:hover { border-color: #777; background: #292929; color: #fff; }
.admin-floating-header > button:focus-visible { outline: 2px solid #ddd; outline-offset: 2px; }
.admin-floating-header > button:disabled { opacity: 0.5; cursor: not-allowed; }
.admin-floating-body {
  min-height: 0;
  overflow: auto;
  padding: 20px;
  scrollbar-color: #696969 transparent;
  scrollbar-width: thin;
}
@keyframes admin-floating-fade { from { opacity: 0; } }
@keyframes admin-floating-rise { from { opacity: 0; transform: translateY(12px) scale(0.99); } }
@media (max-width: 620px) {
  .admin-floating-overlay { place-items: end center; padding: max(8px, env(safe-area-inset-top)) 8px max(8px, env(safe-area-inset-bottom)); }
  .admin-floating-panel,
  .admin-floating-panel.size-compact,
  .admin-floating-panel.size-wide { width: 100%; max-height: calc(100dvh - 16px - env(safe-area-inset-top) - env(safe-area-inset-bottom)); border-radius: 14px; }
  .admin-floating-header { padding: 15px 16px; }
  .admin-floating-body { padding: 16px; }
  .admin-floating-footer { align-items: stretch; padding: 12px 16px; }
}
@media (prefers-reduced-motion: reduce) {
  .admin-floating-overlay,
  .admin-floating-panel { animation: none; }
}
</style>
