// Short remarks on an analysis: names for common arrangements, what kind of choice the best one is, and how the
// player's arrangement could do better. A port of the app's Commentary.kt; remarks.ts words them.
import { sameArrangement, rank, suit, type Rows } from './cards';
import type { Analysis, Candidate } from './engine';
import { notableQGap, type Values } from './rules';

export const HIGH = 1, PAIR = 2, TWO_PAIR = 3, TRIPS = 4, STRAIGHT = 5, FLUSH = 6, FULL_HOUSE = 7;
const SCOOP = 0, SCOOPED = 1, HOME_RUN = 2, WIN = 5, LOSE = 8;

/** Names of arrangements by their row categories (shape_names order), a classification only. */
export enum Shape { THREE_PAIRS, FOUR_PAIRS, FIVE_PAIRS, TWO_FLUSHES, THREE_FLUSHES, TWO_STRAIGHTS, THREE_STRAIGHTS, STRAIGHT_AND_FLUSH, TWO_FULL_HOUSES, TWO_TRIPS }

/** The name of an arrangement, or null. `wheel` is the rules' ranking of A-2-3-4-5 (2: not a straight). */
export function shapeOf(rows: Rows, categories: number[], wheel = 0): Shape | null {
  const [f, m, b] = categories;
  if (f === PAIR && m === PAIR && b === PAIR) return Shape.THREE_PAIRS;
  if (f === PAIR && m === PAIR && b === TWO_PAIR) return Shape.FOUR_PAIRS;
  if (f === PAIR && m === TWO_PAIR && b === TWO_PAIR) return Shape.FIVE_PAIRS;
  if (m === FLUSH && b === FLUSH) return new Set(rows[0].map(suit)).size === 1 ? Shape.THREE_FLUSHES : Shape.TWO_FLUSHES;
  if (m === STRAIGHT && b === STRAIGHT) return frontStraight(rows[0], wheel) ? Shape.THREE_STRAIGHTS : Shape.TWO_STRAIGHTS;
  if (m === STRAIGHT && b === FLUSH) return Shape.STRAIGHT_AND_FLUSH;
  if (m === FULL_HOUSE && b === FULL_HOUSE) return Shape.TWO_FULL_HOUSES;
  if (m === TRIPS && b === TRIPS) return Shape.TWO_TRIPS;
  return null;
}

function frontStraight(front: number[], wheel: number): boolean {
  const r = front.map(rank).sort((a, b) => a - b);
  if (new Set(r).size !== 3) return false;
  return r[2] - r[0] === 2 || (wheel !== 2 && r.join() === '0,1,12');
}

/** How far below the best a choice must be for a "you could do better" remark: half the red-mark gap. */
export const mistakeGap = (rules: Values) => 0.5 * notableQGap(rules);

export type MistakeKind = 'MISSED' | 'SWAPPED' | 'BROKE' | 'PAIR_TO_FRONT' | 'PAIR_OUT_OF_FRONT' | 'STRONGER_FRONT' | 'DETAIL' | 'OTHER';
export type StyleKind = 'BOTH' | 'ATTACK' | 'DEFENSE';
export type RowsKind = 'BOTH' | 'ROWS' | 'SCOOPS';

export type Remark =
  | { kind: 'same'; loss: number }
  | { kind: 'mistake'; loss: number; what: MistakeKind; categories: number[]; replaced: number[]; vsMine: StyleKind | null }
  | { kind: 'only' }
  | { kind: 'close'; gap: number }
  | { kind: 'style'; style: StyleKind; row: number }
  | { kind: 'rows'; rows: RowsKind }
  | { kind: 'strong'; p: number }
  | { kind: 'weak'; p: number }
  | { kind: 'homeRun'; p: number };

const TOL = 0.01;
const ROW_TOL = 0.02;

function styleOf(best: number[], other: number[]): StyleKind | null {
  const ds = best[SCOOP] - other[SCOOP];
  const dd = best[SCOOPED] - other[SCOOPED];
  if (ds >= -TOL && dd <= TOL) return ds > TOL || dd < -TOL ? 'BOTH' : null;
  if (ds > 0 && dd > 0) return 'ATTACK';
  if (ds < 0 && dd < 0) return 'DEFENSE';
  return null;
}

function mistake(loss: number, m: Candidate, best: Candidate): Remark {
  const mc = m.info.categories;
  const bc = best.info.categories;
  const vsMine = styleOf(best.probs!, m.probs!);
  const of = (what: MistakeKind, categories: number[] = [], replaced: number[] = []): Remark =>
    ({ kind: 'mistake', loss, what, categories, replaced, vsMine });
  if (mc.join() === bc.join()) return of(best.info.keys[0] > m.info.keys[0] ? 'STRONGER_FRONT' : 'DETAIL');
  const bestMade = [1, 2].filter((r) => bc[r] >= STRAIGHT);
  const mineMade = [1, 2].map((r) => mc[r]).filter((c) => c >= STRAIGHT);
  const missed = bestMade.filter((r) => {
    const i = mineMade.indexOf(bc[r]);
    if (i < 0) return true;
    mineMade.splice(i, 1);
    return false;
  });
  if (missed.length) return of(mineMade.length === 0 ? 'MISSED' : 'SWAPPED', missed.map((r) => bc[r]), mineMade);
  if (mineMade.length) return of('BROKE', mineMade);
  if (mc[0] === HIGH && bc[0] === PAIR) return of('PAIR_TO_FRONT');
  if (mc[0] === PAIR && bc[0] === HIGH) return of('PAIR_OUT_OF_FRONT');
  return of('OTHER');
}

/** The remarks on an analysis with the network's Q and probabilities, the player's first when `mine` is a candidate. */
export function remarks(a: Analysis, mine: Rows | null): Remark[] {
  const best = a.cands[0];
  if (!best || best.q === null || best.probs === null) return [];
  const bq = best.q;
  const bp = best.probs;
  const qGap = notableQGap(a.rules);
  const out: Remark[] = [];
  const m = mine ? a.cands.find((c) => sameArrangement(c.arrangement, mine)) : undefined;
  if (m && m.q !== null && m.probs !== null) {
    const loss = bq - m.q;
    out.push(loss < mistakeGap(a.rules) ? { kind: 'same', loss } : mistake(loss, m, best));
  }
  const alt = a.cands.slice(1).find((c) => c.info.categories.join() !== best.info.categories.join() && c.q !== null && c.probs !== null);
  if (!alt) out.push({ kind: 'only' });
  else {
    const gap = bq - alt.q!;
    const ap = alt.probs!;
    if (gap < 0.3 * qGap) out.push({ kind: 'close', gap });
    else if ((a.rules.row ?? 0) > 0) {
      let rowsD = 0;
      for (let r = 0; r < 3; r++) rowsD += bp[WIN + r] - bp[LOSE + r] - ap[WIN + r] + ap[LOSE + r];
      const scoops = bp[SCOOP] - bp[SCOOPED] - ap[SCOOP] + ap[SCOOPED];
      if (rowsD >= -ROW_TOL && styleOf(bp, ap) === 'BOTH') out.push({ kind: 'rows', rows: 'BOTH' });
      else if (rowsD > 0 && scoops < 0) out.push({ kind: 'rows', rows: 'ROWS' });
      else if (rowsD < 0 && scoops > 0) out.push({ kind: 'rows', rows: 'SCOOPS' });
    } else {
      const s = styleOf(bp, ap);
      if (s) {
        let row = 0;
        for (let r = 1; r < 3; r++) if (bp[WIN + r] - ap[WIN + r] > bp[WIN + row] - ap[WIN + row]) row = r;
        out.push({ kind: 'style', style: s, row });
      }
    }
  }
  if (bp[SCOOP] >= 0.3) out.push({ kind: 'strong', p: bp[SCOOP] });
  else if (bp[SCOOPED] >= 0.3) out.push({ kind: 'weak', p: bp[SCOOPED] });
  if (bp[HOME_RUN] >= 0.02) out.push({ kind: 'homeRun', p: bp[HOME_RUN] });
  return out;
}
