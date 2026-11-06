import { svelte } from '@sveltejs/vite-plugin-svelte';
import { defineConfig } from 'vite';

const battle = process.env.BATTLE_SERVER ?? 'ws://127.0.0.1:8090';

// A static site: relative paths, so it works from any directory (or a file server's subpath).
export default defineConfig({
  base: './',
  plugins: [svelte()],
  worker: { format: 'es' },
  build: { target: 'es2022', chunkSizeWarningLimit: 1000 },
  // For testing battles: `vite dev` and `vite preview` pass /ws on to a battle server on this machine (BATTLE_SERVER,
  // by default port 8090), so the page and its server share one address, as behind Caddy (server/Caddyfile).
  server: { proxy: { '/ws': { target: battle, ws: true } } },
  preview: { proxy: { '/ws': { target: battle, ws: true } } },
});
