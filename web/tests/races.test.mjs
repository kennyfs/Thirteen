// Tests of async races in web/src/lib/app.svelte.ts and battle.svelte.ts (Review) with a controllable engine worker.
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

// Controllable engine worker: queue or reply immediately per command.
const held = [];
let holdCmd = null;
const HAND0 = Array.from({ length: 13 }, (_, i) => i);
const HAND1 = Array.from({ length: 13 }, (_, i) => i + 13);
const HAND2 = Array.from({ length: 13 }, (_, i) => i + 26);
const HAND3 = Array.from({ length: 13 }, (_, i) => i + 39);
const ROWS0 = [HAND0.slice(0, 3), HAND0.slice(3, 8), HAND0.slice(8, 13)];

function replyFor(req) {
  switch (req.cmd) {
    case 'rules': {
      const vals = { c: 0, row: 0, scoop: 1, home_run: 1 };
      for (const p of req.rules.split(',').slice(1)) {
        const [k, v] = p.split('=');
        if (v !== undefined) vals[k] = Number(v);
      }
      return { rules: req.rules, values: vals };
    }
    case 'deal': return { hands: [HAND0, HAND1, HAND2, HAND3] };
    case 'rows': return { keys: [1, 2, 3], categories: [1, 2, 3], valid: true, royalties: [0, 0, 0] };
    case 'arrange': return { arrangement: [req.hand.slice(0, 3), req.hand.slice(3, 8), req.hand.slice(8, 13)] };
    case 'score': return {
      scoops: Array(4).fill(Array(4).fill(false)), home_run: [false, false, false, false],
      points: [1, -1, 0, 0], dealer_cost: [0, 0, 0, 0], result: [1, -1, 0, 0],
      pairs: Array(4).fill([0, 0, 0, 0]),
      info: Array(4).fill({ keys: [1, 2, 3], categories: [1, 2, 3], valid: true, bonus: 0, royalties: [0, 0, 0] }),
    };
    case 'analyze': return {
      cands: [{ arrangement: [req.hand.slice(0, 3), req.hand.slice(3, 8), req.hand.slice(8, 13)], info: { keys: [1, 2, 3], categories: [1, 2, 3], valid: true, bonus: 0, royalties: [0, 0, 0] }, q: req.hand[0], probs: Array(17).fill(0), extra: false }],
      num_valid: 1, dragon: false, opp_bonus: 0, rules: 'c=0,row=0,scoop=1', legacy_model: false,
    };
    case 'model': return { legacy: false };
    default: return {};
  }
}

class FakeWorker {
  onmessage = null;
  onerror = null;
  postMessage(msg) {
    if (msg.file) return setTimeout(() => this.onmessage?.({ data: { id: msg.id, line: '{}' } }), 0);
    const req = JSON.parse(msg.line);
    const deliver = () => this.onmessage?.({ data: { id: msg.id, line: JSON.stringify(replyFor(req)) } });
    if (holdCmd && req.cmd === holdCmd) held.push({ req, deliver });
    else setTimeout(deliver, 0);
  }
  terminate() {}
}
globalThis.Worker = FakeWorker;

const { createServer } = await import(`${WEB}/node_modules/vite/dist/node/index.js`);
const server = await createServer({ root: WEB, logLevel: 'error', server: { middlewareMode: true, hmr: false }, appType: 'custom' });
const tick = (ms = 20) => new Promise((r) => setTimeout(r, ms));

let failed = 0;
async function test(name, f) {
  holdCmd = null;
  held.length = 0;
  kept.clear();
  try { await f(); console.log('ok  ', name); } catch (e) { failed++; console.log('FAIL', name, '\n', e); }
}

try {
  const { App } = await server.ssrLoadModule('/src/lib/app.svelte.ts');
  const { Review } = await server.ssrLoadModule('/src/lib/battle.svelte.ts');
  const { nav } = await server.ssrLoadModule('/src/lib/nav.svelte.ts');

  await test('confirm() while deal() is in flight does not record the previous hand twice', async () => {
    const app = new App();
    await tick(40);
    assert.equal(app.ready, true);
    app.play.reset(HAND0, app.gameRules, ROWS0);
    app.confirm();
    await tick(30);
    assert.equal(app.recordCount, 1);

    // Start next deal, holding engine.deal so play.rows still holds the previous hand.
    holdCmd = 'deal';
    const dealing = app.deal();
    app.confirm();
    await tick(20);
    assert.equal(app.recordCount, 1, 'confirm() ignored while deal() is in flight');
    holdCmd = null;
    while (held.length) held.shift().deliver();
    await dealing;
    assert.equal(app.recordCount, 1);
  });

  await test('stale analyze() result is discarded after hand changes', async () => {
    const app = new App();
    await tick(40);
    nav.go('ANALYZE');
    app.setAnalyzeHand(new Set(HAND0));
    holdCmd = 'analyze';
    app.analyze();
    await tick(10);
    assert.equal(app.analyzing, true);
    assert.equal(held.length, 1);

    // Change hand before analyze() returns.
    app.setAnalyzeHand(new Set(HAND1));
    assert.equal(app.analyzing, false);
    assert.equal(app.analysis, null);
    assert.equal(app.anaPage, 0);

    // Now the stale analyze() returns: must NOT populate analysis or open candidates.
    holdCmd = null;
    held.shift().deliver();
    await tick(20);
    assert.equal(app.analysis, null, 'stale analysis discarded');
    assert.equal(app.anaPage, 0, 'did not jump to candidates for stale analysis');
  });

  await test('rapid setRule() calls accumulate overrides instead of clobbering each other', async () => {
    const app = new App();
    await tick(40);
    holdCmd = 'rules';
    app.setRule('scoop', 3);
    app.setRule('row', 1);
    await tick(10);
    assert.equal(held.length, 2);
    // Deliver both in order.
    holdCmd = null;
    held.shift().deliver();
    held.shift().deliver();
    await tick(20);
    assert.equal(app.ruleSpec.overrides.scoop, 3, 'first override preserved');
    assert.equal(app.ruleSpec.overrides.row, 1, 'second override applied');
  });

  await test('Review.openAnalysis() discards stale result after clear() and new battle', async () => {
    const rev = new Review(() => {});
    rev.rules = 'c=0,row=0,scoop=1';
    rev.names = { 0: 'A' };
    rev.add([{ index: 0, plays: [{ player: 0, rows: ROWS0 }] }]);
    rev.openDetail(0);

    holdCmd = 'analyze';
    rev.openAnalysis(0);
    await tick(10);
    assert.equal(held.length, 1);

    // Clear and open a different battle with the same detail index (0) and player (0).
    rev.clear();
    rev.rules = 'c=0,row=0,scoop=1';
    rev.names = { 0: 'B' };
    rev.add([{ index: 0, plays: [{ player: 0, rows: [HAND1.slice(0, 3), HAND1.slice(3, 8), HAND1.slice(8, 13)] }] }]);
    rev.openDetail(0);
    rev.openAnalysis(0);
    await tick(10);
    assert.equal(held.length, 2);

    // Deliver the first (stale) analysis: must NOT overwrite detailAnalysis.
    holdCmd = null;
    held.shift().deliver();
    await tick(20);
    assert.equal(rev.detailAnalysis, null, 'stale battle analysis ignored');

    // Deliver the second (current) analysis.
    held.shift().deliver();
    await tick(20);
    assert.equal(rev.detailAnalysis.cands[0].q, 13, 'current battle analysis applied');
  });
} finally {
  await server.close();
}
if (failed) { console.log(`${failed} failed`); process.exit(1); }
console.log('all passed');
