import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // 开发期把这三个前缀的请求转发给后端。
    // 浏览器全程只访问 5173，同源，所以开发时其实用不到后端那份 CORS 配置。
    //
    // 少写一条的后果：/admin/users 会打到 Vite 自己身上，返回 index.html，
    // 前端解析 JSON 失败，页面看着像“接口没数据”。代理前缀必须和实际路径一一对上。
    proxy: {
      '/api': { target: 'http://127.0.0.1:8009', changeOrigin: true },
      '/auth': { target: 'http://127.0.0.1:8009', changeOrigin: true },
      '/admin': { target: 'http://127.0.0.1:8009', changeOrigin: true },
    },
  },
})
