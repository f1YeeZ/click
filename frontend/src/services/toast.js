import { reactive } from 'vue'

let nextToastId = 0
const timers = new Map()

export const toastState = reactive({ items: [] })

const dismissToast = (id) => {
  const timer = timers.get(id)
  if (timer) clearTimeout(timer)
  timers.delete(id)
  const index = toastState.items.findIndex((item) => item.id === id)
  if (index >= 0) toastState.items.splice(index, 1)
}

export const showToast = (message, { type = 'success', title, duration = 3200 } = {}) => {
  if (!message) return
  const id = ++nextToastId
  toastState.items.push({
    id,
    message,
    type,
    title: title || (type === 'error' ? '操作未完成' : type === 'info' ? '提示' : '操作成功')
  })
  timers.set(id, setTimeout(() => dismissToast(id), duration))
}

export { dismissToast }
