import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发期把这两个前缀的请求转发给后端。
    // 浏览器全程只访问 5173，同源，所以开发时其实用不到后端那份 CORS 配置。
    proxy: {
      '/api': { target: 'http://127.0.0.1:8009', changeOrigin: true },
      '/auth': { target: 'http://127.0.0.1:8009', changeOrigin: true },
    },
  },
})
