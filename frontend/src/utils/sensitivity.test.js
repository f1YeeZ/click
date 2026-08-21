import { describe, expect, it } from 'vitest'
import { calculateCmPer360, convertSensitivity, getFpsGame, toPositiveNumber } from './sensitivity'

describe('FPS sensitivity conversion', () => {
  it('converts CS2 sensitivity to Valorant', () => {
    const result = convertSensitivity({
      sourceGameId: 'cs2',
      targetGameId: 'valorant',
      sourceSensitivity: 1,
      sourceDpi: 800,
      targetDpi: 800,
    })

    expect(result).toBeCloseTo(0.3142857, 6)
    expect(calculateCmPer360({ gameId: 'cs2', sensitivity: 1, dpi: 800 })).toBeCloseTo(51.9545, 3)
  })

  it('keeps the same value between CS2 and Apex at equal DPI', () => {
    expect(convertSensitivity({
      sourceGameId: 'cs2',
      targetGameId: 'apex',
      sourceSensitivity: 1.35,
      sourceDpi: 1600,
      targetDpi: 1600,
    })).toBeCloseTo(1.35, 8)
  })

  it('converts Valorant to Overwatch 2 across different DPI values', () => {
    expect(convertSensitivity({
      sourceGameId: 'valorant',
      targetGameId: 'overwatch2',
      sourceSensitivity: 0.4,
      sourceDpi: 800,
      targetDpi: 1600,
    })).toBeCloseTo(2.121212, 6)
  })

  it('preserves cm per 360 after reversing the conversion', () => {
    const input = {
      sourceGameId: 'cs2',
      targetGameId: 'valorant',
      sourceSensitivity: 1.17,
      sourceDpi: 800,
      targetDpi: 1600,
    }
    const targetSensitivity = convertSensitivity(input)
    const sourceDistance = calculateCmPer360({
      gameId: input.sourceGameId,
      sensitivity: input.sourceSensitivity,
      dpi: input.sourceDpi,
    })
    const targetDistance = calculateCmPer360({
      gameId: input.targetGameId,
      sensitivity: targetSensitivity,
      dpi: input.targetDpi,
    })

    expect(targetDistance).toBeCloseTo(sourceDistance, 10)
  })

  it.each([0, -1, '', '0', 'not-a-number', Number.POSITIVE_INFINITY, null, undefined])(
    'rejects invalid positive number input %s',
    value => expect(toPositiveNumber(value)).toBeNull(),
  )

  it('returns null for invalid conversion inputs', () => {
    expect(convertSensitivity()).toBeNull()
    expect(convertSensitivity({
      sourceGameId: 'unknown',
      targetGameId: 'cs2',
      sourceSensitivity: 1,
      sourceDpi: 800,
      targetDpi: 800,
    })).toBeNull()
    expect(calculateCmPer360({ gameId: 'cs2', sensitivity: -1, dpi: 800 })).toBeNull()
  })

  it('does not mutate its input object', () => {
    const input = Object.freeze({
      sourceGameId: 'r6',
      targetGameId: 'cs2',
      sourceSensitivity: 42,
      sourceDpi: 400,
      targetDpi: 800,
    })

    expect(() => convertSensitivity(input)).not.toThrow()
    expect(getFpsGame('r6').note).toContain('0.02')
  })
})
