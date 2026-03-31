import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    sourcemap: false,
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/auth': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/fetch-next-puzzle': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/leaderboard': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
