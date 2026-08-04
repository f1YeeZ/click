import { describe, expect, it } from 'vitest'
import {
  interpolateSupportDabs,
  legacyCellsToDabs,
  mirrorSupportGridX,
  mirrorSupportX,
  replaySupportDabs,
  SUPPORT_GRID_COLUMNS,
  SUPPORT_GRID_ROWS,
  supportCoveragePercentage,
  supportHeatStyle
} from './supportHeatmap'

describe('support heatmap brush', () => {
  it('interpolates fast pointer movement into an overlapping continuous stroke', () => {
    const dabs = interpolateSupportDabs({ x: 100, y: 500 }, { x: 900, y: 500 }, 40)
    const largestGap = dabs.slice(1).reduce((largest, dab, index) => Math.max(
      largest,
      Math.hypot(dab.x - dabs[index].x, dab.y - dabs[index].y)
    ), 0)

    expect(dabs.length).toBeGreaterThan(50)
    expect(largestGap).toBeLessThanOrEqual(13)
  })

  it('replays paint and erase commands in order', () => {
    const painted = replaySupportDabs([{ x: 500, y: 500, radius: 120, mode: 'PAINT' }])
    const erased = replaySupportDabs([
      { x: 500, y: 500, radius: 120, mode: 'PAINT' },
      { x: 500, y: 500, radius: 40, mode: 'ERASE' }
    ])
    const center = Math.floor(SUPPORT_GRID_ROWS / 2) * SUPPORT_GRID_COLUMNS + Math.floor(SUPPORT_GRID_COLUMNS / 2)

    expect(painted[center]).toBe(1)
    expect(erased[center]).toBe(0)
    expect(erased.reduce((sum, value) => sum + value, 0)).toBeLessThan(painted.reduce((sum, value) => sum + value, 0))
  })

  it('uses brush radius to control covered area', () => {
    const small = supportCoveragePercentage([{ x: 500, y: 500, radius: 20, mode: 'PAINT' }])
    const large = supportCoveragePercentage([{ x: 500, y: 500, radius: 150, mode: 'PAINT' }])
    expect(large).toBeGreaterThan(small)
  })

  it('converts legacy cells without exposing grid coordinates to the renderer', () => {
    const dabs = legacyCellsToDabs([{ x: 10, y: 18 }])
    expect(dabs).toEqual([{ x: 438, y: 578, radius: 24, mode: 'PAINT' }])
    expect(replaySupportDabs(dabs).some(Boolean)).toBe(true)
  })

  it('mirrors presentation coordinates without changing canonical stored data', () => {
    expect(mirrorSupportX(0)).toBe(1000)
    expect(mirrorSupportX(275)).toBe(725)
    expect(mirrorSupportX(mirrorSupportX(275))).toBe(275)
    expect(mirrorSupportGridX(0, 64)).toBe(63)
    expect(mirrorSupportGridX(63, 64)).toBe(0)
  })

  it('makes frequently selected areas darker and more opaque', () => {
    const low = supportHeatStyle(1, 10)
    const high = supportHeatStyle(10, 10)
    expect(high.opacity).toBeGreaterThan(low.opacity)
    expect(high.fill).not.toBe(low.fill)
  })
})
