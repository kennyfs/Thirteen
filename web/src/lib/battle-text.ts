// What a battle's pages write, the phone's (Battle.svelte) and a computer's (DeskBattle.svelte) alike.
import { fmtLoss, type HandOutcome, type Mode, type PlayerView, type RoomView } from './battle.svelte';
import { t } from './i18n.svelte';
import * as R from './rules';
import type { Values } from './rules';
import { signed } from './ui.svelte';

/** A clock's time left, "m:ss". */
export const clock = (ms: number) => { const s = Math.floor(ms / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };
export const modeName = (m: Mode) => t(m === 'PUZZLE' ? 'battle_puzzle' : 'battle_game');
export const timeText = (s: number) => (s === 0 ? t('battle_no_limit') : t('battle_seconds', s));
export const timedOut = () => t('battle_timed_out').trim();

/** A player's total: a puzzle's loss, or a table's points. */
export const totalText = (p: PlayerView, puzzle: boolean) => (puzzle ? t('battle_total_loss', fmtLoss(p.total)) : signed(p.total));

/** Under a player's name: you, the host, away. A network's seat is already named "AI n". */
export function playerNotes(p: PlayerView, room: RoomView | null, me: number): string {
  const out: string[] = [];
  if (p.id === me) out.push(t('you'));
  if (room && p.id === room.host) out.push(t('battle_host'));
  if (!p.bot && !p.connected) out.push(t('battle_away'));
  return out.join(t('list_sep'));
}

/** A hand's value: a puzzle's loss ("0.144", nothing lost "0"), or a table's points ("+3"). */
export function handValue(o: HandOutcome, i: number): string {
  const loss = o.losses?.[i];
  if (loss != null) return loss < 0.0005 ? '0' : fmtLoss(loss);
  const x = o.score?.result[i];
  return x == null ? '' : signed(x);
}

/** Its colour: nothing lost or points won good, a notable loss or points lost bad. */
export function handClass(o: HandOutcome, i: number, values: Values): string {
  const loss = o.losses?.[i];
  if (loss != null) return loss < 0.0005 ? 'good' : loss >= 0.5 * R.notableQGap(values) ? 'bad' : '';
  const x = o.score?.result[i] ?? 0;
  return x > 0 ? 'good' : x < 0 ? 'bad' : '';
}

/** A room's settings in a line: the hands, the rules, the time limit. */
export function settingsLine(room: RoomView, rulesName: string): string {
  return [t('battle_hands_n', room.settings.hands), rulesName, t('battle_time') + ' ' + timeText(room.settings.timeLimit)].join(t('clause_sep'));
}
