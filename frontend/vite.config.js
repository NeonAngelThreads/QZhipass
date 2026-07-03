import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  // 构建后部署在 Spring Boot 静态资源目录下，使用相对路径
  base: './',
  // 构建输出目录：指向 Spring Boot 静态资源根目录
  // 这样 npm run build 后无需手动复制
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
})