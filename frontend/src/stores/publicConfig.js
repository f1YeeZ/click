import { defineStore } from 'pinia'
import api from '../api/client'

let loadInFlight

export const usePublicConfigStore = defineStore('publicConfig', {
  state: () => ({
    maintenanceNotice: '',
    registrationEnabled: true,
    reviewSubmissionEnabled: true,
    loaded: false,
  }),
  actions: {
    apply(data = {}) {
      this.maintenanceNotice = String(data.maintenanceNotice || '')
      this.registrationEnabled = data.registrationEnabled !== false
      this.reviewSubmissionEnabled = data.reviewSubmissionEnabled !== false
      this.loaded = true
    },
    async load() {
      if (!loadInFlight) {
        loadInFlight = api.get('/config')
          .then(({ data }) => { this.apply(data); return data })
          .finally(() => { loadInFlight = undefined })
      }
      return loadInFlight
    },
  },
})
