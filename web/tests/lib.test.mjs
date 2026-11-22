// Tests of web/src/lib in Node, the modules loaded through Vite (as the page has them), with a stand-in engine worker:
//   node tests/lib.test.mjs   (npm run test:lib)
// The battle server's address as typed (IPv6 in brackets), and an engine worker that dies or fails to start.
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const WEB = fileURLToPath(new URL('..', import.meta.url));
globalThis.document = { baseURI: 'http://localhost/', documentElement: { lang: '' }, title: '', hidden: false, addEventListener() {} };
globalThis.window = { addEventListener() {}, removeEventListener() {} };
Object.defineProperty(globalThis, 'navigator', { value: { languages: ['zh-TW'], language: 'zh-TW' }, configurable: true });
const kept = new Map();
globalThis.localStorage = { getItem: (k) => kept.get(k) ?? null, setItem: (k, v) => kept.set(k, String(v)), removeItem: (k) => kept.delete(k) };
globalThis.caches = undefined;

// The engine's worker: `mode` decides how the next one behaves.
let mode = 'ok';
const workers = [];
class FakeWorker {
  onmessage = null;
  onerror = null;
  files = new Map();
  terminated = false;
  constructor() { this.mode = mode; workers.push(this); }
  postMessage(msg) {
    setTimeout(() => {
      if (this.terminated) return;
      if (this.mode === 'dies') return this.onerror?.({ message: 'boom', preventDefault() {} });
      if (this.mode === 'nostart') return this.onmessage?.({ data: { id: msg.id, error: 'no engine', fatal: true } });
      if (msg.file) { this.files.set(msg.file.path, msg.file.data); return this.onmessage?.({ data: { id: msg.id, line: '{}' } }); }
      const req = JSON.parse(msg.line);
      this.onmessage?.({ data: { id: msg.id, line: JSON.stringify(req.cmd === 'deal' ? { hands: [[1], [2], [3], [4]] } : { files: [...this.files.keys()] }) } });
    }, 1);
  }
  terminate() { this.terminated = true; }
}
globalThis.Worker = FakeWorker;

const { createServer } = await import(`${WEB}/node_modules/vite/dist/node/index.js`);
const server = await createServer({ root: WEB, logLevel: 'error', server: { middlewareMode: true, hmr: false }, appType: 'custom' });
let failed = 0;
async function test(name, f) {
  try { await f(); console.log('ok  ', name); } catch (e) { failed++; console.log('FAIL', name, '\n', e); }
}

try {
  const { serverUrl } = await server.ssrLoadModule('/src/lib/battle.svelte.ts');
  const engine = await server.ssrLoadModule('/src/lib/engine.ts');

  await test('server addresses', () => {
    assert.equal(serverUrl('thirteen.example.com'), 'wss://thirteen.example.com/ws');
    assert.equal(serverUrl('localhost:8090'), 'ws://localhost:8090/ws');
    assert.equal(serverUrl('https://a.example:8443'), 'wss://a.example:8443/ws');
    assert.equal(serverUrl('[::1]:8090'), 'ws://[::1]:8090/ws');
    assert.equal(serverUrl('[2001:db8::1]:8443'), 'wss://[2001:db8::1]:8443/ws');
    assert.equal(serverUrl('ws://[2001:db8::1]:8090'), null);
    assert.equal(serverUrl('[]:8090'), null);
    assert.equal(serverUrl('ws://a.example'), null);
    assert.equal(serverUrl('a b'), null);
  });

  await test('a dead worker is replaced, with the networks put into it', async () => {
    await engine.putFile('/loaded/mine.onnx', new ArrayBuffer(4));
    mode = 'dies';
    workers.at(-1).mode = 'dies';
    await assert.rejects(engine.deal(), engine.EngineError);
    assert.equal(workers.at(-1).terminated, true);
    mode = 'ok';
    assert.deepEqual(await engine.deal(), [[1], [2], [3], [4]]);
    assert.equal(workers.length, 2);
    assert.ok(workers[1].files.has('/loaded/mine.onnx'), 'the network is put into the new worker');
  });

  await test('an engine that fails to start is tried again on the next request', async () => {
    workers.at(-1).mode = 'nostart';
    await assert.rejects(engine.deal(), /no engine/);
    assert.equal(workers.at(-1).terminated, true);
    assert.deepEqual(await engine.deal(), [[1], [2], [3], [4]]);
  });

  await test('a removed network is not put back', async () => {
    await engine.putFile('/loaded/mine.onnx', null);
    workers.at(-1).mode = 'dies';
    await assert.rejects(engine.deal());
    await engine.deal();
    assert.ok(!workers.at(-1).files.has('/loaded/mine.onnx'));
  });
} finally {
  await server.close();
}
if (failed) { console.log(`${failed} failed`); process.exit(1); }
console.log('all passed');
