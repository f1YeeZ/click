import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/app.css'
import './assets/contrast-palette.css'
import './assets/dark-velocity.css'
import './assets/adaptive-layout.css'
import './assets/app-mobile.css'
import './assets/figma-redesign.css'
import './assets/app-admin.css'
import './assets/app-select.css'

createApp(App).use(createPinia()).use(router).mount('#app')
