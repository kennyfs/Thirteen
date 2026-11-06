// Scoring rules as the engine takes them (cpp/game/rules.h, docs/rules.md section 11): a preset plus name=value
// overrides. The page always plays with c = 0. A port of the app's Rules.kt.

export type Values = Record<string, number>;

export const PRESETS = ['family', 'family_mid2', 'taiwan', 'pagat'];
/** The presets offered to choose from; the others are still named when a game or record has them. */
export const OFFERED = ['family', 'taiwan', 'pagat'];
/** The numbers on the rules page, in point_labels order. */
export const POINTS = ['row', 'scoop', 'home_run', 'dragon'];
export const ROYALTIES = ['front_trips', 'middle_full_house', 'middle_quads', 'middle_straight_flush', 'back_quads', 'back_straight_flush'];
export const MAX_VALUE = 100;
/** Royalties the engine wants in order, low to high (Rules::check). */
const ORDERED = [['middle_full_house', 'middle_quads', 'middle_straight_flush'], ['back_quads', 'back_straight_flush']];

export interface RuleSpec { preset: string; overrides: Values }

export const fmt = (x: number) => (Number.isInteger(x) ? x.toFixed(0) : String(x));

/** The spec syntax, "taiwan,scoop=2". */
export function specString(s: RuleSpec): string {
  return [s.preset, ...Object.entries(s.overrides).map(([k, v]) => `${k}=${fmt(v)}`)].join(',');
}

export function parseSpec(s: string): RuleSpec {
  const parts = s.split(',').map((p) => p.trim()).filter((p) => p);
  if (parts.length === 0 || !PRESETS.includes(parts[0])) return { preset: 'family', overrides: {} };
  const overrides: Values = {};
  for (const p of parts.slice(1)) {
    const [k, v] = p.split('=');
    const x = Number(v);
    if (v !== undefined && !Number.isNaN(x)) overrides[k] = x;
  }
  return { preset: parts[0], overrides };
}

/** The values `name` may take given the others: at least 0 and the royalty below it, at most the one above. */
export function range(name: string, values: Values): [number, number] {
  const chain = ORDERED.find((c) => c.includes(name));
  if (!chain) return [0, MAX_VALUE];
  const i = chain.indexOf(name);
  return [i > 0 ? values[chain[i - 1]] ?? 0 : 0, i < chain.length - 1 ? values[chain[i + 1]] ?? MAX_VALUE : MAX_VALUE];
}

/** The values of a full spec ("c=0,row=1,...", as the engine's toString and the records' rules field). */
export function values(full: string): Values {
  const out: Values = {};
  for (const p of full.split(',')) {
    const kv = p.split('=');
    if (kv.length === 2 && kv[1].trim() !== '' && !Number.isNaN(Number(kv[1]))) out[kv[0].trim()] = Number(kv[1]);
  }
  return out;
}

/** Whether two sets of values are the same rules, whatever the dealer cost. */
export function same(a: Values, b: Values): boolean {
  return [...new Set([...Object.keys(a), ...Object.keys(b)])].every((k) => k === 'c' || a[k] === b[k]);
}

/** The preset with these values, or null for custom rules. */
export function presetOf(v: Values, presets: Record<string, Values>): string | null {
  return PRESETS.find((p) => presets[p] && same(presets[p], v)) ?? null;
}

/** The Q gap to the best candidate worth a red mark: 0.1 under family, scaled with what a hand is worth. */
export function notableQGap(v: Values): number {
  return 0.1 * Math.max(1, (v.scoop ?? 1) + 6 * (v.row ?? 0));
}
