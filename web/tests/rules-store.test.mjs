// Tests of rules parsing/formatting, RecordStore corrupt lines, and App persistence parity.
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const WEB = fileURLToPath(new URL('..', import.meta.url));
globalThis.document = { baseURI: 'http://localhost/', documentElement: { lang: '' }, title: '', hidden: false, addEventListener() {} };
globalThis.window = { addEventListener() {}, removeEventListener() {} };
globalThis.location = { hash: '#/play', search: '' };
globalThis.history = { state: null, replaceState(s, _, u) { this.state = s; globalThis.location.hash = u; }, pushState(s, _, u) { this.state = s; globalThis.location.hash = u; }, back() {} };
globalThis.addEventListener = () => {};
Object.defineProperty(globalThis, 'navigator', { value: { languages: ['en'], language: 'en' }, configurable: true });
const kept = new Map();
globalThis.localStorage = { getItem: (k) => kept.get(k) ?? null, setItem: (k, v) => kept.set(k, String(v)), removeItem: (k) => kept.delete(k) };
globalThis.sessionStorage = { getItem: () => null, setItem() {}, removeItem() {} };
globalThis.caches = undefined;
globalThis.indexedDB = { open() { const r = {}; queueMicrotask(() => r.onerror?.()); return r; } };
globalThis.fetch = async () => ({ ok: true, arrayBuffer: async () => new ArrayBuffer(0) });

class FakeWorker {
  onmessage = null;
  onerror = null;
  postMessage(msg) {
    setTimeout(() => {
      if (msg.file) return this.onmessage?.({ data: { id: msg.id, line: '{}' } });
      const req = JSON.parse(msg.line);
      if (req.cmd === 'rules') return this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ rules: req.rules, values: { c: 0, row: 0, scoop: 1 } }) } });
      if (req.cmd === 'deal') return this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ hands: [[0], [1], [2], [3]] }) } });
      if (req.cmd === 'rows') return this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ keys: [0, 0, 0], categories: [0, 0, 0], valid: null, royalties: [0, 0, 0] }) } });
      if (req.cmd === 'arrange') return this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ arrangement: [[], [], []] }) } });
      this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ legacy: false }) } });
    }, 0);
  }
  terminate() {}
}
globalThis.Worker = FakeWorker;

const { createServer } = await import(`${WEB}/node_modules/vite/dist/node/index.js`);
const server = await createServer({ root: WEB, logLevel: 'error', server: { middlewareMode: true, hmr: false }, appType: 'custom' });

let failed = 0;
async function test(name, f) {
  kept.clear();
  try { await f(); console.log('ok  ', name); } catch (e) { failed++; console.log('FAIL', name, '\n', e); }
}

try {
  const R = await server.ssrLoadModule('/src/lib/rules.ts');
  const store = await server.ssrLoadModule('/src/lib/store.ts');
  const { App } = await server.ssrLoadModule('/src/lib/app.svelte.ts');

  await test('parseSpec and values reject empty and non-finite numbers and trim keys', () => {
    assert.deepEqual(R.parseSpec('taiwan, scoop = 2, row=, dragon=Infinity, bad=1=2'), { preset: 'taiwan', overrides: { scoop: 2 } });
    assert.deepEqual(R.parseSpec('nonsense,row=1'), { preset: 'family', overrides: {} });
    assert.deepEqual(R.values('c=0,row=1,scoop=3,wheel=2,bad=NaN,inf=Infinity'), { c: 0, row: 1, scoop: 3, wheel: 2 });
  });

  await test('RecordStore skips corrupt lines in both all() and count()', () => {
    kept.set('thirteen.records', '{"id":"1"}\n{broken\n{"id":"2"}\n');
    const rs = new store.RecordStore('records');
    assert.equal(rs.all().length, 2);
    assert.equal(rs.count(), 2);
  });

  await test('App validates stored look settings and maps legacy LARGER to LARGE', () => {
    kept.set('thirteen.look.colors', 'BOGUS');
    kept.set('thirteen.look.dark', 'BOGUS');
    kept.set('thirteen.look.text', 'LARGER');
    const app = new App();
    assert.equal(app.colorStyle, 'TABLE');
    assert.equal(app.darkMode, 'SYSTEM');
    assert.equal(app.textSize, 'LARGE');
  });

  await test('battleHandRecord falls back to favorites after records are cleared', () => {
    const app = new App();
    const rec = { id: 'r1', battle: 'b1', battle_hand: 0 };
    app.toggleFavorite(rec);
    assert.deepEqual(app.battleHandRecord('b1', 0), rec);
  });
} finally {
  await server.close();
}
if (failed) { console.log(`${failed} failed`); process.exit(1); }
console.log('all passed');
