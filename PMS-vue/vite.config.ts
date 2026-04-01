import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: ['637621ee.xq0.cn'],
    proxy: {
      '/pet-system': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})