import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

class FakeEventSource {
  static instances = []

  constructor(url) {
    this.url = url
    this.listeners = new Map()
    FakeEventSource.instances.push(this)
  }

  addEventListener(type, listener) {
    this.listeners.set(type, listener)
  }

  emit(type, data) {
    this.listeners.get(type)?.({ data })
  }

  close() {}
}

const createWindow = () => {
  const target = new EventTarget()
  return {
    EventSource: FakeEventSource,
    addEventListener: target.addEventListener.bind(target),
    removeEventListener: target.removeEventListener.bind(target),
    dispatchEvent: target.dispatchEvent.bind(target),
    setInterval,
    clearInterval,
    setTimeout,
    clearTimeout
  }
}

describe('realtime consistency refresh', () => {
  let realtime

  beforeEach(async () => {
    vi.useFakeTimers()
    vi.spyOn(Math, 'random').mockReturnValue(0)
    vi.stubGlobal('window', createWindow())
    vi.stubGlobal('document', { visibilityState: 'visible' })
    if (typeof CustomEvent === 'undefined') {
      vi.stubGlobal('CustomEvent', class extends Event {
        constructor(type, options) {
          super(type)
          this.detail = options?.detail
        }
      })
    }
    FakeEventSource.instances = []
    vi.resetModules()
    realtime = await import('./realtime.js')
  })

  afterEach(() => {
    realtime.stopRealtime()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('does not force a refresh while the event stream is healthy', () => {
    const events = []
    realtime.startRealtime()
    realtime.onRealtime((event) => events.push(event))
    FakeEventSource.instances[0].emit('ready')

    vi.advanceTimersByTime(32000)

    expect(events).toEqual([])
  })

  it('keeps the periodic refresh as a fallback before the stream is ready', () => {
    const events = []
    realtime.startRealtime()
    realtime.onRealtime((event) => events.push(event))

    vi.advanceTimersByTime(32000)

    expect(events).toHaveLength(1)
    expect(events[0].type).toBe('sync.required')
  })
})
