import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['fastball-staple-regular.ngrok-free.dev', '.ngrok-free.dev'],
    proxy: {
      '/api': {
        target: 'http://localhost:7510',
        changeOrigin: true
      }
    }
  }
})
