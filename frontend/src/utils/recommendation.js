export const toggleSelection = (values, code) => values.includes(code)
  ? values.filter((value) => value !== code)
  : [...values, code]

export const recommendationReady = (gripStyle, positions) => Boolean(gripStyle && positions.length)

export const recommendationParams = (gripStyle, positions) => ({
  gripStyle,
  supportPositions: [...new Set(positions)].join(',')
})
