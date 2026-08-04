import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1', timeout: 15000, withCredentials: true })
const isAdminContext = () => typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')
const storagePrefix = () => isAdminContext() ? 'clicker.admin' : 'clicker'
const tokenKey = (prefix = storagePrefix()) => `${prefix}.token`
const userKey = (prefix = storagePrefix()) => `${prefix}.user`
const refreshPath = (prefix = storagePrefix()) => prefix === 'clicker.admin' ? '/admin-sessions/refresh' : '/sessions/refresh'

export const getAccessToken = (prefix = storagePrefix()) => sessionStorage.getItem(tokenKey(prefix)) || ''
export const setAccessToken = (token, prefix = storagePrefix()) => {
  if (token) sessionStorage.setItem(tokenKey(prefix), token)
  else sessionStorage.removeItem(tokenKey(prefix))
}
export const clearStoredSession = (prefix = storagePrefix()) => {
  sessionStorage.removeItem(tokenKey(prefix)); sessionStorage.removeItem(userKey(prefix))
  // Remove tokens written by older releases; they must not remain usable in a browser profile.
  localStorage.removeItem(tokenKey(prefix)); localStorage.removeItem(userKey(prefix))
}

const refreshInFlight = new Map()
export const refreshAccessToken = async (prefix = storagePrefix()) => {
  if (!refreshInFlight.has(prefix)) {
    const request = axios.post(`${api.defaults.baseURL}${refreshPath(prefix)}`, null, {
      timeout: api.defaults.timeout, withCredentials: true
    }).then(({ data }) => {
      setAccessToken(data.token, prefix)
      sessionStorage.setItem(userKey(prefix), JSON.stringify(data.user))
      if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('auth:refreshed', { detail: { prefix, data } }))
      return data
    }).finally(() => refreshInFlight.delete(prefix))
    refreshInFlight.set(prefix, request)
  }
  return refreshInFlight.get(prefix)
}

api.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config || {}
    const prefix = storagePrefix()
    const canRefresh = error.response?.status === 401 && !original._retry &&
      !String(original.url || '').includes('/refresh') && !String(original.url || '').includes('/sessions')
    if (canRefresh) {
      original._retry = true
      try {
        await refreshAccessToken(prefix)
        original.headers = original.headers || {}
        original.headers.Authorization = `Bearer ${getAccessToken(prefix)}`
        return api(original)
      } catch { clearStoredSession(prefix) }
    }
    if (error.response?.status === 401) clearStoredSession(prefix)
    return Promise.reject(error)
  }
)

export const errorMessage = (error) => error.response?.data?.error?.message || '请求失败，请稍后重试'
export default api
