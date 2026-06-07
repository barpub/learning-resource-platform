import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: {
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element-plus'
          if (id.includes('docx-preview') || id.includes('jszip') || id.includes('xlsx')) return 'vendor-office-preview'
          if (id.includes('vue') || id.includes('vue-router') || id.includes('vuex')) return 'vendor-vue'
          return 'vendor'
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
