export const browserStorage = () => typeof window === 'undefined' ? null : window.localStorage

export const readStoredJson = (storage, key, fallback = null) => {
  if (!storage) return fallback
  try {
    const raw = storage.getItem(key)
    return raw == null ? fallback : JSON.parse(raw)
  } catch {
    return fallback
  }
}

