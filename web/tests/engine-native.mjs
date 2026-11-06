// Checks the WebAssembly engine against the native one: the same requests, the same answers (Q to 1e-4).
// node tests/engine-native.mjs ENGINE_JS MODEL.onnx NATIVE_THIRTEEN, or npm test -- NATIVE_THIRTEEN (the built engine and network)
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const [enginePath, modelPath, native] = process.argv.slice(2);
const mod = await (await import(pathToFileURL(enginePath).href)).default();
mod.FS.mkdirTree('/models');
mod.FS.writeFile('/models/m.onnx', readFileSync(modelPath));
function wasm(req) {
  const p = mod.stringToNewUTF8(JSON.stringify(req));
  const out = mod._engine_handle(p);
  mod._free(p);
  const s = mod.UTF8ToString(out);
  mod._engine_free(out);
  return JSON.parse(s);
}
const hand = [51, 46, 41, 38, 33, 30, 27, 22, 17, 12, 9, 4, 0];
const reqs = [
  { cmd: 'rules', rules: 'taiwan' },
  { cmd: 'rows', rows: [[51, 50, 49], [46, 45, 44, 43, 42], [3, 7, 11, 15, 19]], rules: 'family' },
  { cmd: 'analyze', hand, rules: 'family', model: '/models/m.onnx', extra: [] },
  { cmd: 'analyze', hand, rules: 'pagat', model: '/models/m.onnx', extra: [] },
  { cmd: 'arrange', player: 'net:model=/models/m.onnx', hand, rules: 'family', seed: 'x' },
  { cmd: 'arrange', player: 'indep', hand, rules: 'taiwan', seed: 'x' },
];
const lines = reqs.map((r) => JSON.stringify(r).replaceAll('/models/m.onnx', modelPath)).join('\n') + '\n';
const nat = spawnSync(native, ['gui'], { input: lines, encoding: 'utf8' }).stdout.trim().split('\n').map((l) => JSON.parse(l));
let bad = 0;
function close(a, b, path) {
  if (typeof a === 'number' && typeof b === 'number') {
    if (Math.abs(a - b) > 1e-4) { bad++; console.log('differs', path, a, b); }
  } else if (Array.isArray(a)) a.forEach((x, i) => close(x, b?.[i], `${path}[${i}]`));
  else if (a && typeof a === 'object') for (const k of Object.keys(a)) close(a[k], b?.[k], `${path}.${k}`);
  else if (a !== b) { bad++; console.log('differs', path, a, b); }
}
const t0 = performance.now();
reqs.forEach((r, i) => {
  const t = performance.now();
  const w = wasm(r);
  console.log(r.cmd, r.rules, `${(performance.now() - t).toFixed(0)} ms`, w.error ?? '', r.cmd === 'analyze' ? `${w.cands.length} cands, best q ${w.cands[0].q}` : '');
  close(w, nat[i], r.cmd);
});
console.log(bad ? `${bad} differences` : 'all the same', `${(performance.now() - t0).toFixed(0)} ms in all`);
process.exit(bad ? 1 : 0);
