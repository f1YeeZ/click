import { defineStore } from 'pinia'
import api from '../api/client'

const parseUser = (key) => {
  try { return JSON.parse(localStorage.getItem(key) || 'null') } catch { return null }
}

const migrateLegacyAdminSession = () => {
  const legacyUser = parseUser('clicker.user')
  const legacyToken = localStorage.getItem('clicker.token')
  if (legacyUser?.role === 'ADMIN' && legacyToken && !localStorage.getItem('clicker.admin.token')) {
    localStorage.setItem('clicker.admin.token', legacyToken)
    localStorage.setItem('clicker.admin.user', JSON.stringify(legacyUser))
    localStorage.removeItem('clicker.token')
    localStorage.removeItem('clicker.user')
  }
}
migrateLegacyAdminSession()

const createAuthStore = (id, storagePrefix, migrateLegacyAdmin = false) => defineStore(id, {
  state: () => {
    let token = localStorage.getItem(`${storagePrefix}.token`) || ''
    let user = parseUser(`${storagePrefix}.user`)
    if (migrateLegacyAdmin && !token && parseUser('clicker.user')?.role === 'ADMIN') {
      token = localStorage.getItem('clicker.token') || ''
      user = parseUser('clicker.user')
      if (token) localStorage.setItem(`${storagePrefix}.token`, token)
      if (user) localStorage.setItem(`${storagePrefix}.user`, JSON.stringify(user))
      localStorage.removeItem('clicker.token'); localStorage.removeItem('clicker.user')
    }
    return { token, user }
  },
  getters: {
    authenticated: (state) => Boolean(state.token && state.user),
    admin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    async login(payload) { const { data } = await api.post('/auth/login', payload); this.persist(data) },
    async sendRegistrationCode(email) { const { data } = await api.post('/auth/register/code', { email }); return data },
    async register(payload) { const { data } = await api.post('/auth/register', payload); this.persist(data) },
    async refresh() {
      if (!this.token) return
      try { const { data } = await api.get('/auth/me'); this.user = data; localStorage.setItem(`${storagePrefix}.user`, JSON.stringify(data)) }
      catch { this.logout() }
    },
    persist(data) {
      this.token = data.token; this.user = data.user
      localStorage.setItem(`${storagePrefix}.token`, data.token)
      localStorage.setItem(`${storagePrefix}.user`, JSON.stringify(data.user))
    },
    logout() {
      this.token = ''; this.user = null
      localStorage.removeItem(`${storagePrefix}.token`); localStorage.removeItem(`${storagePrefix}.user`)
    }
  }
})

export const useAuthStore = createAuthStore('auth', 'clicker')
export const useAdminAuthStore = createAuthStore('adminAuth', 'clicker.admin', true)
