import { Box3, Vector3 } from 'three'

export const scaleAndCenterObject3D = (object, targetHeight) => {
  object.updateMatrixWorld(true)
  const initialSize = new Box3().setFromObject(object).getSize(new Vector3())
  const scale = targetHeight / Math.max(initialSize.y, Number.EPSILON)
  object.scale.multiplyScalar(scale)
  object.updateMatrixWorld(true)

  const scaledBounds = new Box3().setFromObject(object)
  const scaledCenter = scaledBounds.getCenter(new Vector3())
  object.position.sub(scaledCenter)
  object.updateMatrixWorld(true)
  return new Box3().setFromObject(object)
}

export const fitPerspectiveBounds = (bounds, verticalFovDegrees, aspect, padding = 1.16) => {
  const tanY = Math.tan(verticalFovDegrees * Math.PI / 360)
  const tanX = tanY * Math.max(0.1, aspect)
  const maxX = Math.max(Math.abs(bounds.min.x), Math.abs(bounds.max.x))
  const maxY = Math.max(Math.abs(bounds.min.y), Math.abs(bounds.max.y))
  const nearestZ = Math.max(bounds.min.z, bounds.max.z)
  const distance = Math.max(
    nearestZ + padding * maxX / tanX,
    nearestZ + padding * maxY / tanY
  )

  return { distance }
}
