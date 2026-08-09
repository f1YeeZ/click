const EVENT_NAME = 'clicker:realtime'
const IDLE_CLOSE_MS = 5000
const MAX_RECONNECT_MS = 30000
const REFRESH_JITTER_MS = 2000

let source = null
let started = false
let connectedOnce = false
let subscriberCount = 0
let reconnectAttempt = 0
let reconnectTimer
let idleTimer
let dispatchTimer
const pendingEvents = new Map()

const dispatchPending = () => {
  dispatchTimer = undefined
  const events = [...pendingEvents.values()]
  pendingEvents.clear()
  events.forEach((event) => window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: event })))
}

const queueEvent = (event) => {
  const key = `${event.type}:${event.mouseId || '*'}`
  pendingEvents.set(key, event)
  if (dispatchTimer) return
  // Per-browser jitter prevents every connected page from refetching in the same millisecond.
  dispatchTimer = window.setTimeout(dispatchPending, 100 + Math.floor(Math.random() * REFRESH_JITTER_MS))
}

const closeSource = () => {
  source?.close()
  source = null
}

const reconnectDelay = () => {
  const ceiling = Math.min(MAX_RECONNECT_MS, 1000 * (2 ** Math.min(reconnectAttempt, 5)))
  reconnectAttempt += 1
  return Math.round(ceiling * (0.5 + Math.random()))
}

const scheduleReconnect = () => {
  if (!started || subscriberCount === 0 || reconnectTimer) return
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = undefined
    connect()
  }, reconnectDelay())
}

const connect = () => {
  if (!started || subscriberCount === 0 || source || typeof window === 'undefined' || !window.EventSource) return
  const current = new window.EventSource('/api/v1/events')
  source = current

  current.addEventListener('ready', () => {
    if (source !== current) return
    reconnectAttempt = 0
    if (connectedOnce) {
      queueEvent({ type: 'sync.required', mouseId: null, occurredAt: new Date().toISOString() })
    }
    connectedOnce = true
  })

  current.addEventListener('resource-update', (message) => {
    try {
      const event = JSON.parse(message.data)
      if (event?.type && event?.occurredAt) queueEvent(event)
    } catch {
      // Ignore malformed events; a later valid event can still refresh the page.
    }
  })

  current.onerror = () => {
    if (source !== current) return
    closeSource()
    scheduleReconnect()
  }
}

const scheduleIdleClose = () => {
  clearTimeout(idleTimer)
  idleTimer = window.setTimeout(() => {
    idleTimer = undefined
    if (subscriberCount > 0) return
    clearTimeout(reconnectTimer)
    reconnectTimer = undefined
    closeSource()
  }, IDLE_CLOSE_MS)
}

export const startRealtime = () => {
  if (typeof window === 'undefined') return
  started = true
  clearTimeout(idleTimer)
  idleTimer = undefined
  connect()
}

export const stopRealtime = () => {
  started = false
  connectedOnce = false
  reconnectAttempt = 0
  clearTimeout(reconnectTimer)
  clearTimeout(idleTimer)
  clearTimeout(dispatchTimer)
  reconnectTimer = undefined
  idleTimer = undefined
  dispatchTimer = undefined
  pendingEvents.clear()
  closeSource()
}

export const onRealtime = (handler) => {
  let active = true
  const listener = (event) => handler(event.detail)
  window.addEventListener(EVENT_NAME, listener)
  subscriberCount += 1
  clearTimeout(idleTimer)
  idleTimer = undefined
  connect()

  return () => {
    if (!active) return
    active = false
    window.removeEventListener(EVENT_NAME, listener)
    subscriberCount = Math.max(0, subscriberCount - 1)
    if (subscriberCount === 0) scheduleIdleClose()
  }
}
