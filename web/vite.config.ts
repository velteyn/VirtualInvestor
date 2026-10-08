import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    rollupOptions: {
      // Second entry: the game-master backyard, served at /admin.html.
      input: { main: 'index.html', admin: 'admin.html' },
    },
  },
  server: {
    // Listen on all interfaces so the dev server is reachable over the Tailnet
    // (Vite's default localhost-only bind is not). Host validation must then allow
    // the Tailnet names explicitly, or Vite answers 403 "Blocked request".
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: ['mastermind3.tailf64712.ts.net', '100.108.50.9', '192.168.1.145'],
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
