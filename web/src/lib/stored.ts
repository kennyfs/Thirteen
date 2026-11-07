// The engine's large files (its WebAssembly, the built-in network) kept in Cache Storage, under their content-hashed
// URLs, and read from there before the network: a browser that keeps only a memory cache (Librewolf, Firefox without
// its disk cache), which drops entries this large, then still downloads them once and not on every load. Used by the
// page (the network) and the engine's worker (the WebAssembly). Where Cache Storage is missing or fails (a private
// window, storage blocked, no room), a plain fetch. Not in `npm run dev`, whose URLs carry no hash.

const CACHE = 'thirteen-engine';

async function open(): Promise<Cache | null> {
  if (import.meta.env.DEV || typeof caches === 'undefined') return null;
  try { return await caches.open(CACHE); } catch { return null; }
}

/**
 * The file at `url` (a hashed name: its content never changes), `size` bytes: from Cache Storage if there, else
 * fetched, and kept there when the whole of it came.
 */
export async function storedBytes(url: string, size: number): Promise<ArrayBuffer> {
  const cache = await open();
  if (cache) {
    try {
      const hit = await cache.match(url);
      if (hit) {
        const b = await hit.arrayBuffer();
        if (b.byteLength === size) return b;
        await cache.delete(url);
      }
    } catch { /* read it from the network */ }
  }
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url}: ${r.status}`);
  const b = await r.arrayBuffer();
  // A Response copies the bytes it is made from: `b` is the caller's still.
  if (cache && b.byteLength === size) cache.put(url, new Response(b)).catch(() => {});
  return b;
}

/** Removes what Cache Storage keeps but `urls` (the files of this build): an older build's engine and network. */
export async function keepOnly(urls: string[]): Promise<void> {
  const cache = await open();
  if (!cache) return;
  try {
    for (const k of await cache.keys()) if (!urls.includes(k.url)) await cache.delete(k);
  } catch { /* left for another time */ }
}
