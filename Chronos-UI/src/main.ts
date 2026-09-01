import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import './style.css'
import './assets/admin.css'
import './assets/admin-portal.css'
import App from './App.vue'
import router from './router'

createApp(App).use(router).use(ElementPlus).mount('#app')
