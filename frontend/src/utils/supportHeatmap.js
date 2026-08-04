export const SUPPORT_GRID_COLUMNS = 64
export const SUPPORT_GRID_ROWS = 96
export const SUPPORT_VIEWBOX_WIDTH = 1000
export const SUPPORT_VIEWBOX_HEIGHT = 1000
export const SUPPORT_DAB_LIMIT = 1200

const clamp = (value, minimum, maximum) => Math.min(maximum, Math.max(minimum, value))

export const mirrorSupportX = (x, extent = SUPPORT_VIEWBOX_WIDTH) => Math.round(
  extent - clamp(Number(x) || 0, 0, extent)
)

export const mirrorSupportGridX = (x, columns = SUPPORT_GRID_COLUMNS) => (
  columns - 1 - clamp(Math.round(Number(x) || 0), 0, columns - 1)
)

export const normalizeSupportDab = (dab) => ({
  x: Math.round(clamp(Number(dab?.x) || 0, 0, 1000)),
  y: Math.round(clamp(Number(dab?.y) || 0, 0, 1000)),
  radius: Math.round(clamp(Number(dab?.radius) || 50, 5, 200)),
  mode: dab?.mode === 'ERASE' ? 'ERASE' : 'PAINT'
})

/**
 * Converts sparse pointer events into tightly overlapping circular dabs. The
 * overlap is intentional: replaying the result produces one continuous stroke
 * at every pointer speed instead of a row of visible dots.
 */
export const interpolateSupportDabs = (from, to, radius, mode = 'PAINT') => {
  const safeTo = normalizeSupportDab({ ...to, radius, mode })
  if (!from) return [safeTo]
  const safeFrom = normalizeSupportDab({ ...from, radius, mode })
  const distance = Math.hypot(safeTo.x - safeFrom.x, safeTo.y - safeFrom.y)
  const spacing = Math.max(2, safeTo.radius * 0.32)
  const steps = Math.max(1, Math.ceil(distance / spacing))
  const dabs = []
  for (let step = 1; step <= steps; step += 1) {
    const progress = step / steps
    dabs.push(normalizeSupportDab({
      x: safeFrom.x + (safeTo.x - safeFrom.x) * progress,
      y: safeFrom.y + (safeTo.y - safeFrom.y) * progress,
      radius: safeTo.radius,
      mode: safeTo.mode
    }))
  }
  return dabs
}

/** Replays ordered paint/erase commands into a private boolean grid. */
export const replaySupportDabs = (dabs, columns = SUPPORT_GRID_COLUMNS, rows = SUPPORT_GRID_ROWS) => {
  const mask = new Uint8Array(columns * rows)
  for (const rawDab of dabs || []) {
    const dab = normalizeSupportDab(rawDab)
    const centerX = dab.x / 1000 * columns
    const centerY = dab.y / 1000 * rows
    const radiusX = dab.radius / 1000 * columns
    const radiusY = dab.radius / 1000 * rows
    const minX = Math.max(0, Math.floor(centerX - radiusX))
    const maxX = Math.min(columns - 1, Math.ceil(centerX + radiusX))
    const minY = Math.max(0, Math.floor(centerY - radiusY))
    const maxY = Math.min(rows - 1, Math.ceil(centerY + radiusY))
    for (let y = minY; y <= maxY; y += 1) {
      for (let x = minX; x <= maxX; x += 1) {
        const dx = ((x + 0.5) - centerX) / Math.max(radiusX, 0.001)
        const dy = ((y + 0.5) - centerY) / Math.max(radiusY, 0.001)
        if (dx * dx + dy * dy <= 1) mask[y * columns + x] = dab.mode === 'ERASE' ? 0 : 1
      }
    }
  }
  return mask
}

export const supportCoveragePercentage = (dabs) => {
  const mask = replaySupportDabs(dabs)
  const covered = mask.reduce((total, value) => total + value, 0)
  return covered ? Math.max(1, Math.round(covered / mask.length * 100)) : 0
}

/** Converts old 24×32 saved selections into visually equivalent paint dabs. */
export const legacyCellsToDabs = (cells, columns = 24, rows = 32) => (cells || []).map(({ x, y }) => normalizeSupportDab({
  x: (x + 0.5) / columns * 1000,
  y: (y + 0.5) / rows * 1000,
  radius: Math.round(Math.max(1000 / columns, 1000 / rows) * 0.58),
  mode: 'PAINT'
}))

export const supportHeatStyle = (count, maxCount) => {
  const intensity = Math.max(0, Math.min(1, count / Math.max(1, maxCount)))
  const hue = Math.round(42 - intensity * 37)
  return {
    fill: `hsl(${hue} 92% ${Math.round(63 - intensity * 13)}%)`,
    opacity: 0.2 + intensity * 0.7
  }
}
