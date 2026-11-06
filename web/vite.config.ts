import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

// A static site: relative paths, so it works from any directory (or a file server's subpath).
export default defineConfig({
  base: './',
  plugins: [svelte()],
  worker: { format: 'es' },
  build: { target: 'es2022', chunkSizeWarningLimit: 1000 },
});
