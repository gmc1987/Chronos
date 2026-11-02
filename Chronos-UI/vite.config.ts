import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  // 不强制按体积继续拆分 Element Plus。组件库内部存在相互引用，按 maxSize
  // 二次切块会在生产构建中形成跨 chunk 初始化环，导致登录页加载时报
  // “Qe is not a function”。交由 Vite/Rolldown 根据真实模块图安全分包。
  server: {
    // Native Android/iOS shells load the portal from devices on the same LAN.
    // Listening only on localhost makes the configured host IP unreachable.
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
