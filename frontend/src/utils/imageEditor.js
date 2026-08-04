export const CARD_ASPECT_RATIO = 16 / 9

export const clamp = (value, minimum, maximum) => Math.min(maximum, Math.max(minimum, value))

export const rotatedDimensions = (width, height, rotation) => {
  const normalized = ((rotation % 360) + 360) % 360
  return normalized === 90 || normalized === 270
    ? { width: height, height: width }
    : { width, height }
}

export const coverScale = (imageWidth, imageHeight, outputWidth, outputHeight, rotation = 0) => {
  if (!imageWidth || !imageHeight || !outputWidth || !outputHeight) return 1
  const rotated = rotatedDimensions(imageWidth, imageHeight, rotation)
  return Math.max(outputWidth / rotated.width, outputHeight / rotated.height)
}

export const offsetLimits = ({ imageWidth, imageHeight, outputWidth, outputHeight, rotation = 0, zoom = 1 }) => {
  const rotated = rotatedDimensions(imageWidth, imageHeight, rotation)
  const scale = coverScale(imageWidth, imageHeight, outputWidth, outputHeight, rotation) * zoom
  return {
    x: Math.max(0, (rotated.width * scale - outputWidth) / 2),
    y: Math.max(0, (rotated.height * scale - outputHeight) / 2),
  }
}

export const outputExtension = (mimeType) => ({
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
}[mimeType] || 'webp')

export const editedFilename = (sourceName, mimeType) => {
  const base = String(sourceName || 'mouse-image')
    .replace(/\.[^.]+$/, '')
    .replace(/[^a-zA-Z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'mouse-image'
  return `${base}-card.${outputExtension(mimeType)}`
}
