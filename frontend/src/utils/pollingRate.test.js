import { describe, expect, it } from 'vitest'
import { calculatePollingStats, estimatePollingRate } from './pollingRate'

describe('mouse polling rate calculations', () => {
  it('calculates current and average rates from pointer timestamps', () => {
    const timestamps = Array.from({ length: 501 }, (_, index) => index * 2)

    expect(calculatePollingStats(timestamps, 1000)).toEqual({
      currentRate: 500,
      averageRate: 500,
      sampleCount: 501,
    })
  })

  it('uses only the latest second for the current rate', () => {
    const oldSamples = [0, 10, 20]
    const recentSamples = Array.from({ length: 126 }, (_, index) => 1000 + index * 8)
    const stats = calculatePollingStats([...oldSamples, ...recentSamples], 2000)

    expect(stats.currentRate).toBe(125)
    expect(stats.averageRate).toBeLessThan(125)
  })

  it('maps a measured result to the nearest standard rate', () => {
    expect(estimatePollingRate(970)).toBe(1000)
    expect(estimatePollingRate(420)).toBe(500)
    expect(estimatePollingRate(20)).toBe(0)
  })

  it('ignores invalid timestamps and handles an empty sample set', () => {
    expect(calculatePollingStats([0, Number.NaN, 8], 8)).toEqual({
      currentRate: 125,
      averageRate: 125,
      sampleCount: 2,
    })
    expect(calculatePollingStats([])).toEqual({ currentRate: 0, averageRate: 0, sampleCount: 0 })
  })
})
