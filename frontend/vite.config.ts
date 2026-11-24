import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// The dev server forwards API calls to the Spring Boot backend, so the browser only
// ever talks to one site: the session cookie then works without any CORS setup.
// /actuator is forwarded for the health check shown on every page; on Vercel, vercel.json
// forwards /api and only /actuator/health.
const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: backend, changeOrigin: false },
      '/actuator': { target: backend, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    restoreMocks: true,
    unstubGlobals: true,
    // Tests that type whole forms take a few seconds on a busy machine; 5 s is too tight.
    testTimeout: 15_000,
  },
});
