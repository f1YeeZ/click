import { describe, expect, it } from 'vitest'
import { shouldTrackPath } from './trafficAnalytics'

describe('traffic analytics route filtering', () => {
  it('tracks public product routes', () => {
    expect(shouldTrackPath('/')).toBe(true)
    expect(shouldTrackPath('/mice/123')).toBe(true)
    expect(shouldTrackPath('/compare')).toBe(true)
  })

  it('does not track admin or development routes', () => {
    expect(shouldTrackPath('/admin')).toBe(false)
    expect(shouldTrackPath('/admin/login')).toBe(false)
    expect(shouldTrackPath('/dev/code-map')).toBe(false)
  })
})
