import axios from 'axios'

const api = axios.create({ baseURL: '/api/v1', timeout: 15000 })
const isAdminContext = () => typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')
const storagePrefix = () => isAdminContext() ? 'clicker.admin' : 'clicker'

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(`${storagePrefix()}.token`)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      const prefix = storagePrefix()
      localStorage.removeItem(`${prefix}.token`)
      localStorage.removeItem(`${prefix}.user`)
    }
    return Promise.reject(error)
  }
)

export const errorMessage = (error) => error.response?.data?.error?.message || '请求失败，请稍后重试'
export default api
