import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { mallMock } from './src/mock'

// VITE_USE_MOCK=true 时由内置 mock 服务承接 /api 请求(无需启动后端);
// 置为 false 后 /api 代理转发至 mall-gateway(http://127.0.0.1:10010)。
const useMock = process.env.VITE_USE_MOCK !== 'false'

export default defineConfig({
  plugins: [vue(), ...(useMock ? [mallMock()] : [])],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:10010',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
      // 上传的商品图片由 mall-mgr 提供（/uploads/** 静态映射）
      '/uploads': {
        target: 'http://127.0.0.1:10010',
        changeOrigin: true,
      },
    },
  },
})
