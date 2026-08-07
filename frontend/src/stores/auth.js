import { defineStore } from 'pinia'
import api, { clearStoredSession, getAccessToken, refreshAccessToken, setAccessToken } from '../api/client'

// Access tokens are intentionally session-scoped; discard credentials left by older releases.
if (typeof window !== 'undefined') {
  for (const key of ['clicker.token', 'clicker.admin.token']) localStorage.removeItem(key)
}

const parseUser = (key) => {
  try { return JSON.parse(sessionStorage.getItem(key) || 'null') } catch { return null }
}

const createAuthStore = (id, storagePrefix) => defineStore(id, {
  state: () => ({
    token: getAccessToken(storagePrefix),
    user: parseUser(`${storagePrefix}.user`),
    pendingChallenge: null,
    restoring: false
  }),
  getters: {
    authenticated: (state) => Boolean(state.token && state.user),
    admin: (state) => state.user?.role === 'ADMIN'
  },
  actions: {
    async login(payload) {
      const path = storagePrefix === 'clicker.admin' ? '/admin-sessions' : '/sessions'
      const response = await api.post(path, payload)
      if (response.status === 202 || response.data?.challengeId) {
        this.pendingChallenge = response.data
        return response.data
      }
      this.persist(response.data)
      return response.data
    },
    async verifyAdminSecondFactor(payload) {
      const { data } = await api.post('/admin-sessions/verify', payload)
      this.pendingChallenge = null
      this.persist(data)
      return data
    },
    async sendRegistrationCode(email) { const { data } = await api.post('/registration-verification-codes', { email }); return data },
    async register(payload) { const { data } = await api.post('/users', payload); this.persist(data); return data },
    async refresh() {
      this.restoring = true
      try {
        const data = await refreshAccessToken(storagePrefix)
        this.persist(data)
        return data
      } catch {
        this.clear()
        return null
      } finally { this.restoring = false }
    },
    persist(data) {
      this.token = data?.token || ''
      this.user = data?.user || null
      this.pendingChallenge = null
      setAccessToken(this.token, storagePrefix)
      if (this.user) sessionStorage.setItem(`${storagePrefix}.user`, JSON.stringify(this.user))
    },
    clear() {
      this.token = ''; this.user = null; this.pendingChallenge = null
      clearStoredSession(storagePrefix)
    },
    async logout() {
      try { await api.delete(storagePrefix === 'clicker.admin' ? '/admin-sessions/current' : '/sessions/current') } catch { /* session may already be expired */ }
      this.clear()
    }
  }
})

export const useAuthStore = createAuthStore('auth', 'clicker')
export const useAdminAuthStore = createAuthStore('adminAuth', 'clicker.admin')
