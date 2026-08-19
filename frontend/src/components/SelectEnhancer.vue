<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeSelect = ref(null)
const adminTheme = ref(false)
const popup = ref(null)
const visible = ref(false)
const optionsVersion = ref(0)
const position = reactive({ left: 0, top: 0, width: 0, maxHeight: 360 })
const highlighted = ref(-1)
let optionObserver
let pageObserver
let positionFrame
let positionRequest = 0
const options = computed(() => {
  optionsVersion.value
  return activeSelect.value ? [...activeSelect.value.options].map((option, index) => ({
    index,
    label: option.textContent || '',
    value: option.value,
    disabled: option.disabled,
    selected: option.selected,
  })) : []
})

const selectableIndexes = () => options.value.filter(option => !option.disabled).map(option => option.index)
const firstSelectable = () => selectableIndexes()[0] ?? -1
const selectedOrFirst = () => {
  const selected = options.value.find(option => option.selected && !option.disabled)
  return selected?.index ?? firstSelectable()
}
const close = ({ restoreFocus = true } = {}) => {
  if (!activeSelect.value) return
  const select = activeSelect.value
  positionRequest += 1
  if (positionFrame) cancelAnimationFrame(positionFrame)
  optionObserver?.disconnect()
  optionObserver = undefined
  activeSelect.value = null
  visible.value = false
  select.removeAttribute('aria-expanded')
  if (restoreFocus && select.isConnected) select.focus({ preventScroll: true })
}
const updatePosition = async () => {
  const request = ++positionRequest
  await nextTick()
  const select = activeSelect.value
  if (!select || !select.isConnected || !popup.value || request !== positionRequest) {
    if (select && !select.isConnected) close({ restoreFocus: false })
    return
  }
  const rect = select.getBoundingClientRect()
  const viewportPadding = 8
  const gap = 6
  const availableWidth = Math.max(0, window.innerWidth - viewportPadding * 2)
  const width = Math.min(rect.width, availableWidth)
  const availableBelow = Math.max(0, window.innerHeight - rect.bottom - viewportPadding - gap)
  const availableAbove = Math.max(0, rect.top - viewportPadding - gap)
  const naturalHeight = popup.value.scrollHeight
  const maxHeight = Math.min(360, Math.max(availableBelow, availableAbove))
  const opensUp = availableBelow < Math.min(naturalHeight, 360) && availableAbove > availableBelow
  const height = Math.min(naturalHeight, maxHeight)
  const viewportLeft = Math.min(Math.max(viewportPadding, rect.left), window.innerWidth - width - viewportPadding)
  // `scrollbar-gutter: stable both-edges` shifts fixed-position descendants even
  // though getBoundingClientRect() remains viewport-relative. Compensate for the
  // reserved root gutters so the popup and its trigger share the same left edge.
  const fixedOriginLeft = Math.max(0, (window.innerWidth - document.documentElement.getBoundingClientRect().width) / 2)
  const left = viewportLeft - fixedOriginLeft
  const top = opensUp ? Math.max(viewportPadding, rect.top - height - gap) : Math.min(window.innerHeight - height - viewportPadding, rect.bottom + gap)
  Object.assign(position, { left, top, width, maxHeight })
  await nextTick()
  if (request !== positionRequest || activeSelect.value !== select || !select.isConnected) return
  const bounds = popup.value?.getBoundingClientRect()
  if (bounds) {
    const correctedLeft = bounds.left < viewportPadding
      ? position.left + viewportPadding - bounds.left
      : bounds.right > window.innerWidth - viewportPadding
        ? position.left - (bounds.right - window.innerWidth + viewportPadding)
        : position.left
    const correctedTop = bounds.top < viewportPadding
      ? position.top + viewportPadding - bounds.top
      : bounds.bottom > window.innerHeight - viewportPadding
        ? position.top - (bounds.bottom - window.innerHeight + viewportPadding)
        : position.top
    Object.assign(position, { left: correctedLeft, top: correctedTop })
  }
  positionFrame = requestAnimationFrame(() => {
    if (request === positionRequest && activeSelect.value === select) visible.value = true
  })
}
const open = async select => {
  if (select.disabled || select.multiple || !select.options.length) return
  if (activeSelect.value === select) { close(); return }
  if (activeSelect.value) close({ restoreFocus: false })
  visible.value = false
  activeSelect.value = select
  adminTheme.value = Boolean(select.closest('.admin-saas, .admin-floating-panel, .editor-modal, .user-management-modal'))
  highlighted.value = selectedOrFirst()
  optionObserver = new MutationObserver(() => {
    optionsVersion.value += 1
    highlighted.value = selectedOrFirst()
    updatePosition()
  })
  optionObserver.observe(select, { attributes: true, characterData: true, childList: true, subtree: true })
  select.focus({ preventScroll: true })
  select.setAttribute('aria-expanded', 'true')
  await updatePosition()
}
const moveHighlight = direction => {
  const indexes = selectableIndexes()
  if (!indexes.length) return
  const current = indexes.indexOf(highlighted.value)
  highlighted.value = indexes[(current + direction + indexes.length) % indexes.length]
}
const commit = index => {
  const select = activeSelect.value
  const option = select?.options[index]
  if (!select || !option || option.disabled) return
  select.value = option.value
  select.dispatchEvent(new Event('input', { bubbles: true }))
  select.dispatchEvent(new Event('change', { bubbles: true }))
  close()
}
const handlePointerDown = event => {
  const select = event.target.closest?.('select:not([multiple])')
  if (!select) return
  event.preventDefault()
  open(select)
}
const handleClickOutside = event => {
  const select = event.target.closest?.('select:not([multiple])')
  if (select) {
    event.preventDefault()
    return
  }
  if (activeSelect.value && !event.target.closest?.('.select-enhancer-popup')) close()
}
const handleKeydown = event => {
  const select = event.target.closest?.('select:not([multiple])')
  if (activeSelect.value && event.key === 'Escape') { event.preventDefault(); close(); return }
  if (activeSelect.value && event.key === 'Tab') { close({ restoreFocus: false }); return }
  if (activeSelect.value && event.target === activeSelect.value) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') { event.preventDefault(); moveHighlight(1); return }
    if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') { event.preventDefault(); moveHighlight(-1); return }
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); commit(highlighted.value); return }
  }
  if (!activeSelect.value && select && ['Enter', ' ', 'ArrowDown', 'ArrowUp'].includes(event.key)) { event.preventDefault(); open(select) }
}
const handleViewportChange = () => { if (activeSelect.value) updatePosition() }
const handleSelectChange = event => { if (event.target === activeSelect.value) close({ restoreFocus: false }) }
watch(() => route.fullPath, () => close({ restoreFocus: false }))
onMounted(() => {
  document.addEventListener('pointerdown', handlePointerDown, true)
  document.addEventListener('click', handleClickOutside, true)
  document.addEventListener('keydown', handleKeydown, true)
  document.addEventListener('change', handleSelectChange, true)
  window.addEventListener('resize', handleViewportChange)
  window.addEventListener('scroll', handleViewportChange, true)
  window.visualViewport?.addEventListener('resize', handleViewportChange)
  window.visualViewport?.addEventListener('scroll', handleViewportChange)
  pageObserver = new MutationObserver(() => {
    if (activeSelect.value && !activeSelect.value.isConnected) close({ restoreFocus: false })
  })
  pageObserver.observe(document.body, { childList: true, subtree: true })
})
onBeforeUnmount(() => {
  close({ restoreFocus: false })
  pageObserver?.disconnect()
  document.removeEventListener('pointerdown', handlePointerDown, true)
  document.removeEventListener('click', handleClickOutside, true)
  document.removeEventListener('keydown', handleKeydown, true)
  document.removeEventListener('change', handleSelectChange, true)
  window.removeEventListener('resize', handleViewportChange)
  window.removeEventListener('scroll', handleViewportChange, true)
  window.visualViewport?.removeEventListener('resize', handleViewportChange)
  window.visualViewport?.removeEventListener('scroll', handleViewportChange)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="activeSelect"
      ref="popup"
      class="select-enhancer-popup"
      :class="{ 'is-visible': visible, 'is-admin': adminTheme }"
      role="listbox"
      :aria-label="activeSelect.getAttribute('aria-label') || activeSelect.previousElementSibling?.textContent || '选择项目'"
      :style="{ left: `${position.left}px`, top: `${position.top}px`, width: `${position.width}px`, maxHeight: `${position.maxHeight}px` }"
    >
      <button
        v-for="option in options"
        :key="`${option.value}-${option.index}`"
        type="button"
        class="select-enhancer-option"
        :class="{ 'is-selected': option.selected, 'is-highlighted': option.index === highlighted }"
        role="option"
        :aria-selected="option.selected"
        :disabled="option.disabled"
        @pointerenter="highlighted = option.index"
        @click.stop="commit(option.index)"
      >
        <span>{{ option.label }}</span>
        <strong v-if="option.selected" aria-hidden="true">✓</strong>
      </button>
    </div>
  </Teleport>
</template>
