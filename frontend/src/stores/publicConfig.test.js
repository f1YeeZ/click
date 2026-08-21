import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePublicConfigStore } from './publicConfig'

describe('public configuration', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('applies the public feature settings', () => {
    const config = usePublicConfigStore()

    config.apply({
      maintenanceNotice: '今晚维护',
      registrationEnabled: false,
      reviewSubmissionEnabled: false,
    })

    expect(config.maintenanceNotice).toBe('今晚维护')
    expect(config.registrationEnabled).toBe(false)
    expect(config.reviewSubmissionEnabled).toBe(false)
    expect(config.loaded).toBe(true)
  })
})
