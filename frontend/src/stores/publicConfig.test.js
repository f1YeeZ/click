import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePublicConfigStore } from './publicConfig'

describe('public configuration', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('keeps advertising disabled when an older API response omits ad settings', () => {
    const config = usePublicConfigStore()

    config.apply({ registrationEnabled: true })

    expect(config.advertisingEnabled).toBe(false)
    expect(config.leftAd).toEqual({ enabled: true, imageUrl: '', targetUrl: '', altText: '' })
    expect(config.rightAd).toEqual({ enabled: true, imageUrl: '', targetUrl: '', altText: '' })
  })

  it('normalizes both advertising placements from the public API', () => {
    const config = usePublicConfigStore()

    config.apply({
      advertisingEnabled: true,
      leftAd: { enabled: true, imageUrl: 'https://cdn.example.com/left.webp', targetUrl: '', altText: '左侧广告' },
      rightAd: { enabled: false, imageUrl: null, targetUrl: null, altText: null },
    })

    expect(config.advertisingEnabled).toBe(true)
    expect(config.leftAd.altText).toBe('左侧广告')
    expect(config.rightAd).toEqual({ enabled: false, imageUrl: '', targetUrl: '', altText: '' })
  })
})
