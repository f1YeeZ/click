import { describe, expect, it } from 'vitest'
import { Box3, BoxGeometry, Group, Mesh, Vector3 } from 'three'
import { fitPerspectiveBounds, scaleAndCenterObject3D } from './threeCameraFit'

const handBounds = {
  min: { x: -0.568, y: -0.79, z: -0.268 },
  max: { x: 0.568, y: 0.79, z: 0.268 }
}

describe('3D hand camera fitting', () => {
  it('recenters the model after scaling instead of pushing it upward', () => {
    const model = new Group()
    const offsetMesh = new Mesh(new BoxGeometry(1, 2, 0.5))
    offsetMesh.position.y = 5
    model.add(offsetMesh)

    scaleAndCenterObject3D(model, 1.58)
    const bounds = new Box3().setFromObject(model)
    const center = bounds.getCenter(new Vector3())
    const size = bounds.getSize(new Vector3())

    expect(center.x).toBeCloseTo(0)
    expect(center.y).toBeCloseTo(0)
    expect(center.z).toBeCloseTo(0)
    expect(size.y).toBeCloseTo(1.58)
  })

  it('keeps every bound inside the viewport with a safety margin', () => {
    const fit = fitPerspectiveBounds(handBounds, 34, 1, 1.16)
    const tanY = Math.tan(34 * Math.PI / 360)
    const tanX = tanY
    const projectedX = Math.max(Math.abs(handBounds.min.x), Math.abs(handBounds.max.x))
      / ((fit.distance - handBounds.max.z) * tanX)
    const projectedY = Math.max(Math.abs(handBounds.min.y), Math.abs(handBounds.max.y))
      / ((fit.distance - handBounds.max.z) * tanY)

    expect(projectedX).toBeLessThanOrEqual(1 / 1.16)
    expect(projectedY).toBeLessThanOrEqual(1 / 1.16)
  })

  it('fits against horizontal FOV in a narrow editor', () => {
    const square = fitPerspectiveBounds(handBounds, 34, 1, 1.16)
    const narrow = fitPerspectiveBounds(handBounds, 34, 0.62, 1.16)
    expect(narrow.distance).toBeGreaterThan(square.distance)
  })
})
