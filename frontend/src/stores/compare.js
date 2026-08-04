import { defineStore } from 'pinia'
import { browserStorage, readStoredJson } from '../utils/storage'

export const normalizeCompareItems = (items) => Array.isArray(items)
  ? items.filter((item) => item?.id).slice(0, 4).map((item) => ({ id: item.id, displayName: item.displayName }))
  : []

export const toggleCompareItems = (items, mouse) => {
  const normalized = normalizeCompareItems(items)
  const index = normalized.findIndex((item) => item.id === mouse.id)
  if (index >= 0) return normalized.filter((item) => item.id !== mouse.id)
  if (normalized.length >= 4) throw new Error('单次最多对比 4 款鼠标')
  return [...normalized, { id: mouse.id, displayName: mouse.displayName }]
}

export const readCompareItems = (storage = browserStorage()) => normalizeCompareItems(
  readStoredJson(storage, 'clicker.compare', [])
)

export const useCompareStore = defineStore('compare', {
  state: () => ({ items: readCompareItems() }),
  getters: {
    ids: (state) => state.items.map((item) => item.id),
    contains: (state) => (id) => state.items.some((item) => item.id === id)
  },
  actions: {
    toggle(mouse) {
      this.items = toggleCompareItems(this.items, mouse)
      this.persist()
    },
    remove(id) { this.items = this.items.filter((item) => item.id !== id); this.persist() },
    replace(items) { this.items = normalizeCompareItems(items); this.persist() },
    clear() { this.items = []; this.persist() },
    persist() { browserStorage()?.setItem('clicker.compare', JSON.stringify(this.items)) }
  }
})
