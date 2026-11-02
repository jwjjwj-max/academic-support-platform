import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/week-two.css'

createApp(App).use(createPinia()).use(router).mount('#app')
