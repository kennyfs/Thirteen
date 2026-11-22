// Tests of web/src/lib/battle.svelte.ts in Node with a stand-in WebSocket and engine worker.
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';

const WEB = fileURLToPath(new URL('..', import.meta.url));
globalThis.document = { baseURI: 'http://localhost/', documentElement: { lang: '' }, title: '', hidden: false, addEventListener() {} };
globalThis.window = { addEventListener() {}, removeEventListener() {} };
Object.defineProperty(globalThis, 'navigator', { value: { languages: ['en'], language: 'en' }, configurable: true });
const kept = new Map();
const sess = new Map();
globalThis.localStorage = { getItem: (k) => kept.get(k) ?? null, setItem: (k, v) => kept.set(k, String(v)), removeItem: (k) => kept.delete(k) };
globalThis.sessionStorage = { getItem: (k) => sess.get(k) ?? null, setItem: (k, v) => sess.set(k, String(v)), removeItem: (k) => sess.delete(k) };
globalThis.caches = undefined;

const realSetTimeout = globalThis.setTimeout;

// Stand-in engine worker.
class FakeWorker {
  onmessage = null;
  onerror = null;
  postMessage(msg) {
    realSetTimeout(() => {
      if (msg.file) return this.onmessage?.({ data: { id: msg.id, line: '{}' } });
      const req = JSON.parse(msg.line);
      if (req.cmd === 'rows') {
        this.onmessage?.({ data: { id: msg.id, line: JSON.stringify({ keys: [1, 2, 3], categories: [1, 2, 3], valid: true, royalties: [0, 0, 0] }) } });
      } else {
        this.onmessage?.({ data: { id: msg.id, line: '{}' } });
      }
    }, 1);
  }
  terminate() {}
}
globalThis.Worker = FakeWorker;

// Stand-in WebSocket.
const sockets = [];
class FakeWebSocket {
  static OPEN = 1;
  static CLOSED = 3;
  readyState = FakeWebSocket.OPEN;
  sent = [];
  onopen = null;
  onmessage = null;
  onclose = null;
  constructor(url) {
    this.url = url;
    sockets.push(this);
  }
  send(data) { this.sent.push(JSON.parse(data)); }
  close() { this.readyState = FakeWebSocket.CLOSED; }
  // Test helpers
  open() { this.onopen?.(); }
  recv(msg) { this.onmessage?.({ data: JSON.stringify(msg) }); }
  drop(reason = 'dropped') { this.readyState = FakeWebSocket.CLOSED; this.onclose?.({ code: 1006, reason }); }
}
globalThis.WebSocket = FakeWebSocket;

const { createServer } = await import(`${WEB}/node_modules/vite/dist/node/index.js`);
const server = await createServer({ root: WEB, logLevel: 'error', server: { middlewareMode: true, hmr: false }, appType: 'custom' });

const wait = (ms) => new Promise((r) => realSetTimeout(r, ms));
const ROOM = {
  pin: '123456',
  settings: { mode: 'PUZZLE', hands: 2, rules: { preset: 'family', overrides: {} }, timeLimit: 0 },
  rules: 'c=0,row=0,scoop=1,home_run=1',
  phase: 'PLAYING',
  hand: 0,
  host: 0,
  players: [
    { id: 0, name: 'A', bot: false, connected: true, submitted: false, ready: false, total: 0 },
    { id: 1, name: 'B', bot: false, connected: true, submitted: false, ready: false, total: 0 },
  ],
};

let failed = 0;
async function test(name, f) {
  kept.clear();
  sess.clear();
  sockets.length = 0;
  try { await f(); console.log('ok  ', name); } catch (e) { failed++; console.log('FAIL', name, '\n', e); }
}

try {
  const { Battle } = await server.ssrLoadModule('/src/lib/battle.svelte.ts');
  // Speed up sleep() inside battle.svelte.ts reconnect loops (after Vite loads modules).
  globalThis.setTimeout = (fn, ms, ...args) => realSetTimeout(fn, Math.min(ms ?? 0, 20), ...args);

  function makeBattle() {
    const errors = [];
    const b = new Battle((m) => errors.push(m), () => ({ preset: 'family', overrides: {} }), () => {});
    b.server = 'localhost:18080';
    return { b, errors };
  }

  await test('NO_ROOM on resume during reconnect closes the room and stops retrying', async () => {
    const { b, errors } = makeBattle();
    b.create('PUZZLE');
    const ws1 = sockets.at(-1);
    ws1.open();
    ws1.recv({ t: 'welcome', version: 2 });
    ws1.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    ws1.recv({ t: 'state', room: ROOM });
    assert.equal(b.busy, false);
    assert.equal(b.inRoom, true);

    ws1.drop();
    assert.equal(b.busy, true);
    await wait(30);
    assert.equal(sockets.length, 2);
    const ws2 = sockets.at(-1);
    ws2.open();
    ws2.recv({ t: 'welcome', version: 2 });
    assert.equal(ws2.sent.at(-1).t, 'resume');
    ws2.recv({ t: 'error', code: 'NO_ROOM' });

    assert.equal(b.busy, false);
    assert.equal(b.room, null);
    assert.ok(errors.length >= 1);
    const countAfter = sockets.length;
    await wait(60);
    assert.equal(sockets.length, countAfter, 'no further reconnect attempts after NO_ROOM');
  });

  await test('transient RATE error during reconnect keeps busy true and retries', async () => {
    const { b } = makeBattle();
    b.create('PUZZLE');
    const ws1 = sockets.at(-1);
    ws1.open();
    ws1.recv({ t: 'welcome', version: 2 });
    ws1.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    ws1.recv({ t: 'state', room: ROOM });

    ws1.drop();
    await wait(30);
    const ws2 = sockets.at(-1);
    ws2.open();
    ws2.recv({ t: 'welcome', version: 2 });
    ws2.recv({ t: 'error', code: 'RATE' });
    assert.equal(b.busy, true, 'busy stays true while reconnect loop is still running');

    await wait(45);
    assert.ok(sockets.length >= 3, 'reconnect loop retries after RATE');
    const ws3 = sockets.at(-1);
    ws3.open();
    ws3.recv({ t: 'welcome', version: 2 });
    ws3.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    assert.equal(b.busy, false);
    b.dismiss();
  });

  await test('closed message while reconnecting with outcomes finishes cleanly without further retries', async () => {
    const { b } = makeBattle();
    b.create('PUZZLE');
    const ws1 = sockets.at(-1);
    ws1.open();
    ws1.recv({ t: 'welcome', version: 2 });
    ws1.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    ws1.recv({ t: 'state', room: ROOM });
    ws1.recv({
      t: 'result',
      outcome: {
        index: 0,
        plays: [
          { player: 0, rows: [[0, 1, 2], [3, 4, 5, 6, 7], [8, 9, 10, 11, 12]] },
          { player: 1, rows: [[0, 1, 2], [3, 4, 5, 6, 7], [8, 9, 10, 11, 12]] },
        ],
        losses: [0, 0.2],
      },
    });

    ws1.drop();
    await wait(30);
    const ws2 = sockets.at(-1);
    ws2.open();
    ws2.recv({ t: 'closed', reason: 'expired' });
    assert.equal(b.busy, false);
    assert.equal(b.finished, true);
    const countAfter = sockets.length;
    await wait(60);
    assert.equal(sockets.length, countAfter, 'no further reconnect attempts after closed');
    b.dismiss();
  });

  await test('battle record keeps final totals when state arrives after result', async () => {
    const { b } = makeBattle();
    b.create('PUZZLE');
    const ws = sockets.at(-1);
    ws.open();
    ws.recv({ t: 'welcome', version: 2 });
    ws.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    ws.recv({ t: 'state', room: { ...ROOM, settings: { ...ROOM.settings, hands: 1 } } });
    // Room.kt sends HandResult before broadcastState with updated totals and OVER.
    ws.recv({
      t: 'result',
      outcome: {
        index: 0,
        plays: [
          { player: 0, rows: [[0, 1, 2], [3, 4, 5, 6, 7], [8, 9, 10, 11, 12]] },
          { player: 1, rows: [[0, 1, 2], [3, 4, 5, 6, 7], [8, 9, 10, 11, 12]] },
        ],
        losses: [0.5, 0.1],
      },
    });
    ws.recv({
      t: 'state',
      room: {
        ...ROOM,
        settings: { ...ROOM.settings, hands: 1 },
        phase: 'OVER',
        players: [
          { ...ROOM.players[0], total: 0.5 },
          { ...ROOM.players[1], total: 0.1 },
        ],
      },
    });
    const saved = JSON.parse(kept.get('thirteen.battles').trim());
    assert.equal(saved.players[0].total, 0.5, 'player 0 final total is saved');
    assert.equal(saved.players[1].total, 0.1, 'player 1 final total is saved');
    b.dismiss();
  });

  await test('double-tap on create opens one socket, and double-tap on submit sends one submit', async () => {
    const { b } = makeBattle();
    b.create('PUZZLE');
    b.create('PUZZLE');
    assert.equal(sockets.length, 1, 'second create while busy is ignored');
    const ws = sockets.at(-1);
    ws.open();
    ws.recv({ t: 'welcome', version: 2 });
    ws.recv({ t: 'joined', room: 'r1', you: 0, token: 'tok' });
    ws.recv({ t: 'state', room: ROOM });
    ws.recv({ t: 'deal', hand: 0, cards: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] });

    // Double-tap submit before arranger.eval() resolves.
    const p1 = b.submit();
    const p2 = b.submit();
    await Promise.all([p1, p2]);
    const submits = ws.sent.filter((m) => m.t === 'submit');
    assert.equal(submits.length, 1, 'only one submit sent on double tap');

    // A new deal arriving while submit is evaluating discards the stale submit.
    ws.recv({ t: 'deal', hand: 1, cards: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] });
    const p3 = b.submit();
    ws.recv({ t: 'deal', hand: 2, cards: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] });
    await p3;
    assert.equal(ws.sent.filter((m) => m.t === 'submit' && m.hand === 1).length, 0, 'stale submit not sent');
    b.dismiss();
  });
} finally {
  globalThis.setTimeout = realSetTimeout;
  await server.close();
}
if (failed) { console.log(`${failed} failed`); process.exit(1); }
console.log('all passed');
