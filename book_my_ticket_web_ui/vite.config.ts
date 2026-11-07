/// <reference types="vitest" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Dev server listens on :3000 because the API gateway CORS policy and the Keycloak
// "bmtickets-web" client (redirect URIs / web origins) allow http://localhost:3000.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const gateway = env.GATEWAY_URL ?? 'http://localhost:8080';
  return {
    plugins: [react()],
    server: {
      port: 3000,
      strictPort: true,
      // Same-origin /api calls in dev: no CORS preflights, identical paths to production (ingress /api -> gateway).
      proxy: { '/api': { target: gateway, changeOrigin: true } },
    },
    preview: { port: 3000, strictPort: true, proxy: { '/api': { target: gateway, changeOrigin: true } } },
    build: {
      sourcemap: true,
      target: 'es2022',
      rollupOptions: {
        output: {
          manualChunks: {
            react: ['react', 'react-dom', 'react-router-dom'],
            motion: ['framer-motion'],
            query: ['@tanstack/react-query'],
          },
        },
      },
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
      restoreMocks: true,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html', 'lcov'],
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/test/**', 'src/main.tsx', 'src/**/*.test.{ts,tsx}'],
      },
    },
  };
});
