export const FPS_GAMES = Object.freeze([
  Object.freeze({ id: 'cs2', name: 'CS2', yaw: 0.022, precision: 4 }),
  Object.freeze({ id: 'valorant', name: '无畏契约', shortName: 'VALORANT', yaw: 0.07, precision: 4 }),
  Object.freeze({ id: 'apex', name: 'Apex Legends', yaw: 0.022, precision: 4 }),
  Object.freeze({ id: 'overwatch2', name: '守望先锋 2', shortName: 'OVERWATCH 2', yaw: 0.0066, precision: 3 }),
  Object.freeze({
    id: 'r6',
    name: '彩虹六号：围攻',
    shortName: 'RAINBOW SIX SIEGE',
    yaw: 0.00572957795,
    precision: 2,
    note: '按默认 MouseSensitivityMultiplierUnit 0.02 换算',
  }),
])

const GAME_BY_ID = new Map(FPS_GAMES.map(game => [game.id, game]))

export const getFpsGame = id => GAME_BY_ID.get(id) ?? null

export const toPositiveNumber = value => {
  if (value === '' || value === null || value === undefined || typeof value === 'boolean') return null
  const number = typeof value === 'number' ? value : Number(String(value).trim())
  return Number.isFinite(number) && number > 0 ? number : null
}

export const convertSensitivity = ({
  sourceGameId,
  targetGameId,
  sourceSensitivity,
  sourceDpi,
  targetDpi,
} = {}) => {
  const sourceGame = getFpsGame(sourceGameId)
  const targetGame = getFpsGame(targetGameId)
  const sensitivity = toPositiveNumber(sourceSensitivity)
  const fromDpi = toPositiveNumber(sourceDpi)
  const toDpi = toPositiveNumber(targetDpi)

  if (!sourceGame || !targetGame || !sensitivity || !fromDpi || !toDpi) return null

  return sensitivity * fromDpi * sourceGame.yaw / (toDpi * targetGame.yaw)
}

export const calculateCmPer360 = ({ gameId, sensitivity, dpi } = {}) => {
  const game = getFpsGame(gameId)
  const validSensitivity = toPositiveNumber(sensitivity)
  const validDpi = toPositiveNumber(dpi)

  if (!game || !validSensitivity || !validDpi) return null
  return 2.54 * 360 / (validDpi * validSensitivity * game.yaw)
}

export const calculateEdpi = ({ sensitivity, dpi } = {}) => {
  const validSensitivity = toPositiveNumber(sensitivity)
  const validDpi = toPositiveNumber(dpi)
  return validSensitivity && validDpi ? validSensitivity * validDpi : null
}
