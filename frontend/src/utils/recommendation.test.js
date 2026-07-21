import { describe, expect, it } from 'vitest'
import { recommendationParams, recommendationReady, toggleSelection } from './recommendation'

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
})
