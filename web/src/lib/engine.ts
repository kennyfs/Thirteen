// The engine (cpp/command/guiengine.h, as WebAssembly in a worker): one JSON request, one JSON answer, run one at a
// time in the order they are made. A port of the app's Engine.kt.
import type { Rows } from './cards';
import { values, type Values } from './rules';

export class EngineError extends Error {}

/** `royalties`: each row's under the rules (0 for none or a foul). */
export interface Info { keys: number[]; categories: number[]; valid: boolean; bonus: number; royalties: number[] }
export interface RowsEval { keys: number[]; categories: number[]; valid: boolean | null; royalties: number[] }
export interface Score {
  scoops: boolean[][]; homeRun: boolean[]; points: number[]; dealerCost: number[]; result: number[]; info: Info[];
  /** pairs[i][j]: what j pays i, home runs included. */
  pairs: number[][];
}
export interface Candidate { arrangement: Rows; info: Info; q: number | null; probs: number[] | null; extra: boolean }
export interface Analysis {
  cands: Candidate[]; numValid: number; dragon: boolean; oppBonus: number | null; rules: Values; legacyModel: boolean;
}

/** Where the built-in network is in the engine's file system, and where the page fetches it from. */
export const BUILT_IN = '/models/run2_net10.onnx';
export const BUILT_IN_URL = 'models/run2_net10.onnx';

let worker: Worker | null = null;
let next = 0;
const waiting = new Map<number, { resolve: (s: string) => void; reject: (e: Error) => void }>();

function start(): Worker {
  if (worker) return worker;
  // The worker is bundled elsewhere than the page: its name tells it where the page's engine/ is.
  worker = new Worker(new URL('./engine-worker.ts', import.meta.url), { type: 'module', name: new URL('./', document.baseURI).href });
  worker.onmessage = (e: MessageEvent) => {
    const w = waiting.get(e.data.id);
    if (!w) return;
    waiting.delete(e.data.id);
    if (e.data.error !== undefined) w.reject(new EngineError(e.data.error));
    else w.resolve(e.data.line);
  };
  worker.onerror = (e) => {
    for (const w of waiting.values()) w.reject(new EngineError(e.message || 'The engine failed to start'));
    waiting.clear();
  };
  return worker;
}

function post(msg: object, transfer: Transferable[] = []): Promise<string> {
  const w = start();
  const id = next++;
  return new Promise((resolve, reject) => {
    waiting.set(id, { resolve, reject });
    w.postMessage({ id, ...msg }, transfer);
  });
}

/** Writes a network into the engine's file system (null removes it). */
export async function putFile(path: string, data: ArrayBuffer | null): Promise<void> {
  await post({ file: { path, data } });
}

let builtIn: Promise<void> | null = null;

/** Fetches the built-in network into the engine once. */
export function loadBuiltIn(): Promise<void> {
  builtIn ??= fetch(new URL(BUILT_IN_URL, document.baseURI)).then(async (r) => {
    if (!r.ok) throw new EngineError(`${BUILT_IN_URL}: ${r.status}`);
    await putFile(BUILT_IN, await r.arrayBuffer());
  });
  return builtIn;
}

export async function request(req: Record<string, unknown>): Promise<any> {
  const a = JSON.parse(await post({ line: JSON.stringify(req) }));
  if (a.error !== undefined) throw new EngineError(a.error);
  return a;
}

/** A request that names a network: the built-in one is fetched first. */
async function withModel(req: Record<string, unknown>): Promise<any> {
  const names = JSON.stringify(req);
  if (names.includes(BUILT_IN)) await loadBuiltIn();
  return request(req);
}

const info = (o: any): Info => ({ keys: o.keys, categories: o.categories, valid: o.valid, bonus: o.bonus, royalties: o.royalties });

export async function deal(): Promise<number[][]> {
  return (await request({ cmd: 'deal' })).hands;
}

export async function arrange(spec: string, hand: number[], rules: string): Promise<Rows> {
  return (await withModel({ cmd: 'arrange', player: spec, hand, rules })).arrangement;
}

/** Keys and categories of rows being filled (0 for a row not full); valid and each row's royalty once all are full. */
export async function rows(r: Rows, rules: string): Promise<RowsEval> {
  const a = await request({ cmd: 'rows', rows: r, rules });
  return { keys: a.keys, categories: a.categories, valid: a.valid ?? null, royalties: a.royalties ?? [0, 0, 0] };
}

/** The values of a rules spec, after the engine checks it (an EngineError for bad rules). */
export async function ruleValues(spec: string): Promise<Values> {
  return (await request({ cmd: 'rules', rules: spec })).values;
}

/** The full spec, every value given ("c=0,row=1,..."), as records store it. */
export async function rulesFull(spec: string): Promise<string> {
  return (await request({ cmd: 'rules', rules: spec })).rules;
}

/** Whether a network predates the rules input (it then plays as under the family rules). */
export async function isLegacy(model: string): Promise<boolean> {
  return (await withModel({ cmd: 'model', model })).legacy;
}

export async function score(arrangements: Rows[], rules: string): Promise<Score> {
  const a = await request({ cmd: 'score', arrangements, rules });
  return {
    scoops: a.scoops, homeRun: a.home_run, points: a.points, dealerCost: a.dealer_cost, result: a.result,
    pairs: a.pairs, info: a.info.map(info),
  };
}

/** The undominated arrangements plus `extra`, with the network's Q and event probabilities when one is given. */
export async function analyze(hand: number[], rules: string, model: string | null, extra: Rows[]): Promise<Analysis> {
  const req: Record<string, unknown> = { cmd: 'analyze', hand, rules, extra };
  if (model !== null) req.model = model;
  const a = await withModel(req);
  return {
    cands: a.cands.map((cd: any) => ({
      arrangement: cd.arrangement, info: info(cd.info), q: cd.q ?? null, probs: cd.probs ?? null, extra: cd.extra,
    })),
    numValid: a.num_valid, dragon: a.dragon, oppBonus: a.opp_bonus ?? null, rules: values(a.rules), legacyModel: !!a.legacy_model,
  };
}
