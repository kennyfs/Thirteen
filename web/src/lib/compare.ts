// Comparing candidates of an analysis (a computer's pages): which one is the player's, its rank, and the figures shown
// side by side (the expected points and the chances), each with whether higher is better and what difference is notable
// (as CandidateList's).
import { sameArrangement, type Rows } from './cards';
import type { Analysis, Candidate } from './engine';
import { ROYALTIES } from './rules';

/** The chances shown, by index into Candidate.probs (prob_names), without the dealer's (4). */
export const SHOWN = [0, 1, 2, 3, 5, 6, 7];
export const GOOD_WHEN_HIGH = [true, false, true, false, false, true, true, true];
export const NOTABLE_GAP = [0.03, 0.03, 0.005, 0.005, 0.03, 0.1, 0.1, 0.1];
/** Where the chances of losing to each special row start in probs. */
export const ROYALTY_LOST = 11;

/** The index of `mine` among the candidates, or null. */
export function mineIndex(a: Analysis, mine: Rows | null): number | null {
  if (!mine) return null;
  const i = a.cands.findIndex((c) => sameArrangement(c.arrangement, mine));
  return i >= 0 ? i : null;
}

/** Each candidate's rank, 1 for the best; the extra ones (beaten in every row) share the last's. */
export function ranks(a: Analysis): number[] {
  let n = 0;
  return a.cands.map((c) => (c.extra ? n : ++n));
}

/** The special rows lost that the rules pay for (on_win rules), by index into ROYALTIES. */
export function lostShown(a: Analysis): number[] {
  return a.rules.on_win === 1 ? ROYALTIES.map((_, i) => i).filter((i) => (a.rules[ROYALTIES[i]] ?? 0) > 0) : [];
}

/** -1 worse, 0 about the same, 1 better: chance `k` of `x` against `base`'s. */
export function tone(k: number, x: number, base: number): number {
  const d = x - base;
  if (Math.abs(d) < NOTABLE_GAP[k]) return 0;
  return d > 0 === GOOD_WHEN_HIGH[k] ? 1 : -1;
}

export const pct = (x: number) => (100 * x).toFixed(1) + '%';
export const pctDiff = (d: number) => (d >= 0 ? '+' : '−') + Math.abs(100 * d).toFixed(1);

/** The cards of `c` in another row than in `base`. */
export function moved(c: Candidate, base: Candidate): Set<number> {
  const out = new Set<number>();
  c.arrangement.forEach((row, r) => row.forEach((card) => { if (!base.arrangement[r].includes(card)) out.add(card); }));
  return out;
}
