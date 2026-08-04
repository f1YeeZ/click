import { replaySupportDabs, SUPPORT_GRID_COLUMNS, SUPPORT_GRID_ROWS } from './supportHeatmap'

const LEGACY_GRID_COLUMNS = 24
const LEGACY_GRID_ROWS = 32
const SUPPORT_POSITION_ANCHORS = [
  { code: 'THUMB_BASE', x: 5, y: 16 },
  { code: 'INDEX_BASE', x: 8, y: 12 },
  { code: 'MIDDLE_BASE', x: 11, y: 11 },
  { code: 'RING_BASE', x: 14, y: 12 },
  { code: 'LITTLE_BASE', x: 18, y: 14 },
  { code: 'PALM_CENTER', x: 12, y: 19 },
  { code: 'PALM_HEEL', x: 12, y: 25 }
]

export const toggleSelection = (values, code) => values.includes(code)
  ? values.filter((value) => value !== code)
  : [...values, code]

/** Mirrors the server's nearest-anchor conversion from painted grid cells to recommendation areas. */
export const supportPositionsFromDabs = (dabs) => {
  const mask = replaySupportDabs(dabs)
  const selected = new Set()
  mask.forEach((painted, index) => {
    if (!painted) return
    const gridX = index % SUPPORT_GRID_COLUMNS
    const gridY = Math.floor(index / SUPPORT_GRID_COLUMNS)
    const legacyX = (gridX + 0.5) * LEGACY_GRID_COLUMNS / SUPPORT_GRID_COLUMNS - 0.5
    const legacyY = (gridY + 0.5) * LEGACY_GRID_ROWS / SUPPORT_GRID_ROWS - 0.5
    const nearest = SUPPORT_POSITION_ANCHORS.reduce((closest, area) => {
      const distance = Math.hypot(legacyX - area.x, legacyY - area.y)
      return !closest || distance < closest.distance ? { code: area.code, distance } : closest
    }, null)
    if (nearest) selected.add(nearest.code)
  })
  return SUPPORT_POSITION_ANCHORS.map((area) => area.code).filter((code) => selected.has(code))
}

export const recommendationReady = (gripStyle, positions) => Boolean(gripStyle && positions.length)

export const recommendationShapeReady = (gripStyle, dabs) => Boolean(
  gripStyle && replaySupportDabs(dabs).some(Boolean)
)

export const recommendationParams = (gripStyle, positions) => ({
  gripStyle,
  supportPositions: [...new Set(positions)].join(',')
})

export const recommendationShapeRequest = (gripStyle, dabs) => ({
  gripStyle,
  dabs: [...dabs]
})
