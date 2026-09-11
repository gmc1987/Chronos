import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  build: {
    rolldownOptions: {
      output: {
        // 将大型框架依赖从业务入口拆出，降低登录页和门户首屏下载体积。
        codeSplitting: {
          maxSize: 450 * 1024,
          groups: [
            {
              name: 'element-plus',
              test: /node_modules[\\/]element-plus/,
              priority: 30,
            },
            {
              name: 'vue-vendor',
              test: /node_modules[\\/](?:vue|@vue|vue-router|pinia)[\\/]/,
              priority: 20,
            },
            {
              name: 'vendor',
              test: /node_modules/,
              priority: 10,
            },
          ],
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
})
