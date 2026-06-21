import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // Proxy all /suggest, /search, /trending, /metrics, /cache, /batch
      // calls to the Spring Boot backend — avoids CORS issues in dev
      '/suggest':  'http://localhost:8080',
      '/search':   'http://localhost:8080',
      '/trending': 'http://localhost:8080',
      '/metrics':  'http://localhost:8080',
      '/cache':    'http://localhost:8080',
      '/batch':    'http://localhost:8080',
    }
  }
})
