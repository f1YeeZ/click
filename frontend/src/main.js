import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/app.css'
import './assets/contrast-palette.css'
import './assets/dark-velocity.css'
import './assets/app-mobile.css'

createApp(App).use(createPinia()).use(router).mount('#app')
