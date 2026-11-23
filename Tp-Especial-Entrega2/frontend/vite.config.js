import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// La web corre en http://localhost:5173 y le habla al gateway en http://localhost:8080.
// El gateway tiene CORS habilitado para este origen (api-gateway/src/main/resources/application.yml).
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 5173, strictPort: true },
})
