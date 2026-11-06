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

const base = self.name || new URL('../', self.location.href).href;
const ready: Promise<Engine> = import(/* @vite-ignore */ base + 'engine/engine.js').then((m) =>
  m.default({ locateFile: (f: string) => base + 'engine/' + f }),
);

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
