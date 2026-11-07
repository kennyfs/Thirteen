// The engine's worker: the WebAssembly build of GuiEngine (web/engine), one request at a time in the order they come.
// Messages in: {id, line} (a request line), {id, file: {path, data}} (a network to write into the engine's file
// system, before a request names it). Out: {id, line} (the answer line) or {id, error}.
/// <reference lib="webworker" />

interface Engine {
  FS: { mkdirTree(p: string): void; writeFile(p: string, d: Uint8Array): void; unlink(p: string): void };
  stringToNewUTF8(s: string): number;
  UTF8ToString(p: number): string;
  _engine_handle(p: number): number;
  _engine_free(p: number): void;
  _free(p: number): void;
}

import { storedBytes } from './stored';

/** Where the engine is (the page's virtual:engine-files, made absolute), in the worker's name. */
const where: { js: string; wasm: string; wasmSize: number } = JSON.parse(self.name);
// The glue (engine.js) and its WebAssembly, of one build: their names carry their hashes. The WebAssembly is compiled
// from bytes kept in Cache Storage (stored.ts), so a browser without a disk cache does not fetch it on every load.
// Emscripten's hook has no way to fail: a failure ends `ready` here instead.
let fail: (e: unknown) => void = () => {};
const ready: Promise<Engine> = Promise.race([
  new Promise<never>((_, reject) => { fail = reject; }),
  import(/* @vite-ignore */ where.js).then((m) =>
    m.default({
      locateFile: (f: string) => (f.endsWith('.wasm') ? where.wasm : new URL(f, where.js).href),
      instantiateWasm(imports: WebAssembly.Imports, done: (i: WebAssembly.Instance) => void) {
        storedBytes(where.wasm, where.wasmSize)
          .then((b) => WebAssembly.instantiate(b, imports))
          .then((r) => done(r.instance), fail);
        return {};
      },
    }),
  ),
]);

self.onmessage = async (e: MessageEvent) => {
  const { id } = e.data;
  try {
    const engine = await ready;
    if (e.data.file) {
      const { path, data } = e.data.file as { path: string; data: ArrayBuffer | null };
      if (data === null) {
        try { engine.FS.unlink(path); } catch { /* not there */ }
      } else {
        engine.FS.mkdirTree(path.slice(0, path.lastIndexOf('/')) || '/');
        engine.FS.writeFile(path, new Uint8Array(data));
      }
      self.postMessage({ id, line: '{}' });
      return;
    }
    const p = engine.stringToNewUTF8(e.data.line);
    const out = engine._engine_handle(p);
    engine._free(p);
    const line = engine.UTF8ToString(out);
    engine._engine_free(out);
    self.postMessage({ id, line });
  } catch (err) {
    self.postMessage({ id, error: String((err as Error)?.message ?? err) });
  }
};
