const NUMBER_PATTERN = /^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/
const MISSING_VALUES = new Set(['', '-', '—'])

const parseDecimal = (value) => {
  const text = String(value ?? '').trim()
  if (!NUMBER_PATTERN.test(text)) return null

  const negative = text.startsWith('-')
  const unsigned = text.replace(/^[+-]/, '')
  const [integerPart, fractionPart = ''] = unsigned.split('.')
  const digits = `${integerPart || '0'}${fractionPart}`

  return {
    scale: fractionPart.length,
    value: BigInt(digits) * (negative ? -1n : 1n)
  }
}

const scaleDecimal = (decimal, scale) => decimal.value * (10n ** BigInt(scale - decimal.scale))

const formatDifference = (base, value) => {
  const scale = Math.max(base.scale, value.scale)
  const difference = scaleDecimal(value, scale) - scaleDecimal(base, scale)
  if (difference === 0n) return ''

  const sign = difference > 0n ? '+' : '-'
  const absolute = (difference < 0n ? -difference : difference).toString().padStart(scale + 1, '0')
  if (!scale) return `${sign}${absolute}`

  const integerPart = absolute.slice(0, -scale) || '0'
  const fractionPart = absolute.slice(-scale).replace(/0+$/, '')
  return `${sign}${integerPart}${fractionPart ? `.${fractionPart}` : ''}`
}

const isMissing = (value) => value == null || MISSING_VALUES.has(String(value).trim())

const normalizeRow = (row) => {
  const cells = Array.isArray(row?.cells)
    ? row.cells.map((cell) => ({ ...cell, value: isMissing(cell?.value) ? '-' : cell.value }))
    : []
  const base = parseDecimal(cells[0]?.value)
  const hasTextDifference = cells.some((cell) => cell?.delta === '不同')
  const isNumericRow = base && !hasTextDifference && cells.every((cell) => isMissing(cell?.value) || parseDecimal(cell?.value))
  if (!isNumericRow) return row

  return {
    ...row,
    cells: cells.map((cell, index) => {
      const value = parseDecimal(cell?.value)
      return {
        ...cell,
        delta: index === 0 || !value ? '' : formatDifference(base, value)
      }
    })
  }
}

export const normalizeComparison = (comparison = {}) => ({
  ...comparison,
  items: Array.isArray(comparison.items) ? comparison.items : [],
  rows: Array.isArray(comparison.rows) ? comparison.rows.map(normalizeRow) : []
})
