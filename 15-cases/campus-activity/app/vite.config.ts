import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // 生产部署在子路径 /app/ 时用 VITE_BASE=/app/ pnpm build:h5；本地开发保持 /
  base: process.env.VITE_BASE || '/',
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8016',
        changeOrigin: true
      },
      // 后端上传接口返回 /uploads/xxx 相对地址，代理到后端静态资源，H5 下 <image> 才能显示
      '/uploads': {
        target: 'http://127.0.0.1:8016',
        changeOrigin: true
      }
    }
  }
})
