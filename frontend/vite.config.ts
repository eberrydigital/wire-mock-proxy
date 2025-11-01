import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
    base: '/_proxy-ui/',
    plugins: [react()],
    server: {
        port: 5173,
        strictPort: true,
        proxy: {
            '/_proxy-api': 'http://localhost:8080',
            '/__admin': 'http://localhost:8080'
        }
    }
})