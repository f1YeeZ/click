import { describe, expect, it } from 'vitest'
import { normalizeComparison } from './comparison'

const numericRow = (label, unit, values, deltas = []) => ({
  group: '尺寸与重量',
  label,
  unit,
  different: true,
  cells: values.map((value, index) => ({ value, delta: deltas[index] || '' }))
})

describe('comparison response normalization', () => {
  it('calculates actual differences from the displayed numeric values', () => {
    const comparison = normalizeComparison({
      items: [],
      rows: [
        numericRow('长度', 'mm', ['118.2', '119', '125', '127.1'], ['', '+0.7%', '+5.8%', '+7.5%']),
        numericRow('宽度', 'mm', ['60.5', '65']),
        numericRow('高度', 'mm', ['37.2', '42']),
        numericRow('重量', 'g', ['51.5', '52']),
        numericRow('最大回报率', 'Hz', ['4000', '8000'])
      ]
    })

    expect(comparison.rows.map((row) => row.cells.map((cell) => cell.delta))).toEqual([
      ['', '+0.8', '+6.8', '+8.9'],
      ['', '+4.5'],
      ['', '+4.8'],
      ['', '+0.5'],
      ['', '+4000']
    ])
  })

  it('preserves text differences and leaves missing numeric values without a delta', () => {
    const comparison = normalizeComparison({
      items: [],
      rows: [
        { group: '外形', label: '外形类型', unit: '', different: true, cells: [
          { value: '对称', delta: '' },
          { value: '人体工学', delta: '不同' }
        ] },
        { group: '按键与滚轮', label: '微动', unit: '', different: true, cells: [
          { value: '2.0', delta: '' },
          { value: '3.0', delta: '不同' }
        ] },
        numericRow('重量', 'g', ['51.5', '—'])
      ]
    })

    expect(comparison.rows[0].cells[1].delta).toBe('不同')
    expect(comparison.rows[1].cells[1].delta).toBe('不同')
    expect(comparison.rows[2].cells[1].delta).toBe('')
  })

  it('does not mutate the API response object', () => {
    const response = { items: [], rows: [numericRow('长度', 'mm', ['118.2', '119'], ['', '+0.7%'])] }
    normalizeComparison(response)
    expect(response.rows[0].cells[1].delta).toBe('+0.7%')
  })
})
