// The records by kind, as the app's records screen: practice, table games and puzzle contests. A battle's hands are
// listed under the battle (BattleRecord); a battle hand without one (kept before battles were) is listed on its own.
import type { BattleRecord } from './battle.svelte';
import type { Rec } from './store';

export type RecordKind = 'PRACTICE' | 'GAME' | 'PUZZLE';
export const RECORD_KINDS: RecordKind[] = ['PRACTICE', 'GAME', 'PUZZLE'];
export const KIND_NAMES = ['records_practice', 'records_game', 'records_puzzle'];

/** A record's kind; null for a hand played again by an older version, which is in none. */
export function kindOf(r: Rec): RecordKind | null {
  return r.label === 'replay' ? null : r.label === 'battle' ? 'GAME' : r.label === 'puzzle' ? 'PUZZLE' : 'PRACTICE';
}

export type Entry = { battle: BattleRecord } | { hand: Rec };

/** Newest first: the hands of `kind` (`recs` newest first), the battles' under their battle. */
export function entries(kind: RecordKind, recs: Rec[], battles: BattleRecord[]): Entry[] {
  const ofKind = recs.filter((r) => kindOf(r) === kind);
  if (kind === 'PRACTICE') return ofKind.map((hand) => ({ hand }));
  const mine = battles.filter((b) => (b.settings.mode === 'PUZZLE') === (kind === 'PUZZLE'));
  const ids = new Set(mine.map((b) => b.id));
  const timed: [number, Entry][] = [
    ...mine.map((b) => [b.startedAt, { battle: b }] as [number, Entry]),
    ...ofKind.filter((r) => !r.battle || !ids.has(r.battle)).map((r) => [Date.parse(r.timestamp) || 0, { hand: r }] as [number, Entry]),
  ];
  return timed.sort((a, b) => b[0] - a[0]).map((x) => x[1]);
}

/** The rules of a record without the dealer's cost, as the totals are kept. */
export const noC = (s: string) => (s || 'family').split(',').filter((p) => !p.startsWith('c=')).join(',');

/** The chart's groups: the hands of each set of rules, oldest first (`recs` newest first). */
export function chartGroups(recs: Rec[]): Map<string, Rec[]> {
  const g = new Map<string, Rec[]>();
  for (const r of [...recs].reverse()) if (typeof r.result === 'number') {
    const k = noC(r.rules);
    if (!g.has(k)) g.set(k, []);
    g.get(k)!.push(r);
  }
  return g;
}
