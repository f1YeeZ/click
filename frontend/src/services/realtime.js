const EVENT_NAME = 'clicker:realtime'
let source = null

export const startRealtime = () => {
  if (source || typeof window === 'undefined' || !window.EventSource) return
  source = new window.EventSource('/api/v1/events')
  source.addEventListener('resource-update', (message) => {
    try {
      const event = JSON.parse(message.data)
      if (event?.type && event?.occurredAt) {
        window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: event }))
      }
    } catch {
      // Ignore malformed events; EventSource keeps the valid connection alive.
    }
  })
}

export const stopRealtime = () => {
  source?.close()
  source = null
}

export const onRealtime = (handler) => {
  const listener = (event) => handler(event.detail)
  window.addEventListener(EVENT_NAME, listener)
  return () => window.removeEventListener(EVENT_NAME, listener)
}
