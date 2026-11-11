import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8016',
        changeOrigin: true
      },
      // 后端返回 /uploads/xxx 相对地址，代理到静态资源，活动封面与上传预览才能显示
      '/uploads': {
        target: 'http://127.0.0.1:8016',
        changeOrigin: true
      }
    }
  }
})
