import { describe, expect, it } from 'vitest'
import { coverScale, editedFilename, offsetLimits, rotatedDimensions } from './imageEditor'

describe('image editor geometry', () => {
  it('swaps image bounds for quarter turns', () => {
    expect(rotatedDimensions(1200, 800, 90)).toEqual({ width: 800, height: 1200 })
    expect(rotatedDimensions(1200, 800, 180)).toEqual({ width: 1200, height: 800 })
  })

  it('scales an image until the whole crop viewport is covered', () => {
    expect(coverScale(800, 1200, 1200, 675, 0)).toBe(1.5)
    expect(coverScale(800, 1200, 1200, 675, 90)).toBe(1)
  })

  it('limits panning so the crop never exposes an empty edge', () => {
    expect(offsetLimits({
      imageWidth: 1600,
      imageHeight: 900,
      outputWidth: 1200,
      outputHeight: 675,
      zoom: 2,
    })).toEqual({ x: 600, y: 337.5 })
  })

  it('creates a safe filename matching the chosen output format', () => {
    expect(editedFilename('MX Master 3S.png', 'image/webp')).toBe('MX-Master-3S-card.webp')
    expect(editedFilename('鼠标.png', 'image/jpeg')).toBe('mouse-image-card.jpg')
  })
})
