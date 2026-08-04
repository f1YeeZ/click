import { describe, expect, it } from 'vitest'
import { normalizeCompareItems, readCompareItems, toggleCompareItems } from './compare'

describe('compare selection', () => {
  it('normalizes persisted items and enforces the four-item limit', () => {
    const source = Array.from({ length: 6 }, (_, index) => ({ id: `${index}`, displayName: `Mouse ${index}` }))
    expect(normalizeCompareItems(source)).toHaveLength(4)
  })

  it('adds and removes a mouse without mutating the input', () => {
    const source = [{ id: 'a', displayName: 'A' }]
    const added = toggleCompareItems(source, { id: 'b', displayName: 'B' })
    expect(added.map((item) => item.id)).toEqual(['a', 'b'])
    expect(toggleCompareItems(added, { id: 'a', displayName: 'A' })).toEqual([{ id: 'b', displayName: 'B' }])
    expect(source).toEqual([{ id: 'a', displayName: 'A' }])
  })

  it('rejects a fifth item and recovers from corrupt persisted JSON', () => {
    const full = Array.from({ length: 4 }, (_, index) => ({ id: `${index}`, displayName: `${index}` }))
    expect(() => toggleCompareItems(full, { id: '5', displayName: '5' })).toThrow('单次最多对比 4 款鼠标')
    expect(readCompareItems({ getItem: () => '{broken' })).toEqual([])
  })
})

