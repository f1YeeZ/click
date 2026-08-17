import { defineStore } from 'pinia'
import api from '../api/client'

let loadInFlight

const normalizeAd = data => ({
  enabled: data?.enabled !== false,
  imageUrl: String(data?.imageUrl || ''),
  targetUrl: String(data?.targetUrl || ''),
  altText: String(data?.altText || ''),
})

export const usePublicConfigStore = defineStore('publicConfig', {
  state: () => ({
    maintenanceNotice: '',
    registrationEnabled: true,
    reviewSubmissionEnabled: true,
    advertisingEnabled: false,
    leftAd: normalizeAd(),
    rightAd: normalizeAd(),
    loaded: false,
  }),
  actions: {
    apply(data = {}) {
      this.maintenanceNotice = String(data.maintenanceNotice || '')
      this.registrationEnabled = data.registrationEnabled !== false
      this.reviewSubmissionEnabled = data.reviewSubmissionEnabled !== false
      this.advertisingEnabled = data.advertisingEnabled === true
      this.leftAd = normalizeAd(data.leftAd)
      this.rightAd = normalizeAd(data.rightAd)
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
