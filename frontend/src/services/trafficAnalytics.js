const VISITOR_KEY = 'clicker.analytics.visitor'
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

export const shouldTrackPath = path => {
  const normalized = String(path || '').trim().toLowerCase()
  return normalized.startsWith('/') &&
    normalized !== '/admin' && !normalized.startsWith('/admin/') &&
    normalized !== '/dev' && !normalized.startsWith('/dev/')
}

const fallbackUuid = () => {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  return [...bytes].map((value, index) =>
    `${[4, 6, 8, 10].includes(index) ? '-' : ''}${value.toString(16).padStart(2, '0')}`
  ).join('')
}

export const visitorId = () => {
  try {
    const stored = localStorage.getItem(VISITOR_KEY)
    if (UUID_PATTERN.test(stored || '')) return stored
    const created = crypto.randomUUID ? crypto.randomUUID() : fallbackUuid()
    localStorage.setItem(VISITOR_KEY, created)
    return created
  } catch { return crypto.randomUUID ? crypto.randomUUID() : fallbackUuid() }
}

export const trackPageView = path => {
  if (typeof window === 'undefined' || !shouldTrackPath(path) || navigator.doNotTrack === '1') return
  const cleanPath = String(path).split(/[?#]/, 1)[0]
  fetch('/api/v1/analytics/page-views', {
    method: 'POST',
    credentials: 'same-origin',
    keepalive: true,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ visitorId: visitorId(), path: cleanPath }),
  }).catch(() => {})
}
