import { describe, expect, it } from 'vitest'
import {
  recommendationParams,
  recommendationReady,
  recommendationShapeReady,
  recommendationShapeRequest,
  supportPositionsFromDabs,
  toggleSelection
} from './recommendation'

describe('recommendation selection', () => {
  it('adds and removes a support position without mutating the input', () => {
    const original = ['PALM_CENTER']
    expect(toggleSelection(original, 'PALM_HEEL')).toEqual(['PALM_CENTER', 'PALM_HEEL'])
    expect(toggleSelection(original, 'PALM_CENTER')).toEqual([])
    expect(original).toEqual(['PALM_CENTER'])
  })

  it('requires both a grip and at least one support position', () => {
    expect(recommendationReady('CLAW', ['PALM_HEEL'])).toBe(true)
    expect(recommendationReady('', ['PALM_HEEL'])).toBe(false)
    expect(recommendationReady('CLAW', [])).toBe(false)
  })

  it('deduplicates positions when creating request parameters', () => {
    expect(recommendationParams('PALM', ['PALM_CENTER', 'PALM_CENTER'])).toEqual({
      gripStyle: 'PALM', supportPositions: 'PALM_CENTER'
    })
  })

  it('maps painted and erased support dabs to the same named areas as the server', () => {
    const palmCenter = { x: 521, y: 609, radius: 18, mode: 'PAINT' }
    const palmHeel = { x: 521, y: 797, radius: 18, mode: 'PAINT' }
    expect(supportPositionsFromDabs([palmCenter, palmHeel])).toEqual(['PALM_CENTER', 'PALM_HEEL'])
    expect(supportPositionsFromDabs([
      palmCenter,
      palmHeel,
      { ...palmCenter, radius: 30, mode: 'ERASE' }
    ])).toEqual(['PALM_HEEL'])
  })

  it('sends the original paint commands for shape matching', () => {
    const dabs = [{ x: 500, y: 620, radius: 55, mode: 'PAINT' }]
    expect(recommendationShapeReady('CLAW', dabs)).toBe(true)
    expect(recommendationShapeReady('', dabs)).toBe(false)
    expect(recommendationShapeReady('CLAW', [dabs[0], { ...dabs[0], mode: 'ERASE' }])).toBe(false)
    expect(recommendationShapeRequest('CLAW', dabs)).toEqual({
      gripStyle: 'CLAW',
      dabs
    })
  })
})
