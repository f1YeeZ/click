import { defineStore } from 'pinia'

const saved = JSON.parse(localStorage.getItem('clicker.compare') || '[]')

export const useCompareStore = defineStore('compare', {
  state: () => ({ items: Array.isArray(saved) ? saved.slice(0, 4) : [] }),
  getters: {
    ids: (state) => state.items.map((item) => item.id),
    contains: (state) => (id) => state.items.some((item) => item.id === id)
  },
  actions: {
    toggle(mouse) {
      const index = this.items.findIndex((item) => item.id === mouse.id)
      if (index >= 0) this.items.splice(index, 1)
      else if (this.items.length < 4) this.items.push({ id: mouse.id, displayName: mouse.displayName })
      else throw new Error('单次最多对比 4 款鼠标')
      this.persist()
    },
    remove(id) { this.items = this.items.filter((item) => item.id !== id); this.persist() },
    replace(items) { this.items = items.slice(0, 4).map((item) => ({ id: item.id, displayName: item.displayName })); this.persist() },
    clear() { this.items = []; this.persist() },
    persist() { localStorage.setItem('clicker.compare', JSON.stringify(this.items)) }
  }
})
