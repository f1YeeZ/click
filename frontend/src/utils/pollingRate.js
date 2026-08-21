const STANDARD_POLLING_RATES = [125, 250, 500, 1000, 2000, 4000, 8000]

const rateFromWindow = timestamps => {
  if (timestamps.length < 2) return 0
  const duration = timestamps.at(-1) - timestamps[0]
  if (duration <= 0) return 0
  return Math.round(((timestamps.length - 1) * 1000) / duration)
}

export const calculatePollingStats = (timestamps, now = timestamps.at(-1) ?? 0) => {
  const validTimestamps = timestamps.filter(Number.isFinite)
  const recentTimestamps = validTimestamps.filter(timestamp => timestamp >= now - 1000)
  const currentRate = rateFromWindow(recentTimestamps)
  const averageRate = rateFromWindow(validTimestamps)

  return {
    currentRate,
    averageRate,
    sampleCount: validTimestamps.length,
  }
}

export const estimatePollingRate = measuredRate => {
  if (!Number.isFinite(measuredRate) || measuredRate < 60) return 0
  return STANDARD_POLLING_RATES.reduce((closest, rate) => (
    Math.abs(rate - measuredRate) < Math.abs(closest - measuredRate) ? rate : closest
  ))
}

