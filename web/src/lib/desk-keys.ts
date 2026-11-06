// The keys of a computer's table (DeskArranging): how the cards are picked, in the three ways the settings offer, and
// how the hand is laid out, in one line of 13 (no line to change) or two of 7 and 6 (larger cards), any way with any
// layout.
//  - 'pick': ← → along the hand (↑ ↓ between the two lines) and Space picks, S sorts, Z undoes;
//  - 'cursor': the left hand alone: A D (or ← →; ↑ ↓ between the lines) move a cursor along the hand, W starts a sweep
//    from the card under it (picking, or unpicking from a picked card, as the mouse's) that each move carries on, S
//    stops it; Space sorts, Backspace undoes;
//  - 'keys': a key a card: in one line the letters' top row (Q W E … [ ] \), in two lines the keys as the cards are
//    (A S D F G H J over Z X C V B N); Space sorts, Backspace undoes.
// In every way 1 2 3 put the picked cards into a row, Shift and 1 2 3 take a row back, and L shows the hand before
// (DeskPlay). The cards left close up after a play, the keys with them, so that the first key is always the first card
// left. Keys are matched by where they are (KeyboardEvent.code), not by the letter, so that a keyboard layout or an
// input method changes nothing.
import { t } from './i18n.svelte';

export type DeskInput = 'pick' | 'cursor' | 'keys';
export const DESK_INPUTS: DeskInput[] = ['pick', 'cursor', 'keys'];
export type DeskLines = 13 | 7;
export const DESK_LINES: DeskLines[] = [13, 7];
export interface DeskKeys { input: DeskInput; lines: DeskLines }

/** The settings as they were kept: an input, or (before the layout was its own) 'keys76' and 'keys13'. */
export function parseDeskKeys(input: string, lines: string): DeskKeys {
  if (input === 'keys76') return { input: 'keys', lines: 7 };
  if (input === 'keys13') return { input: 'keys', lines: 13 };
  return { input: DESK_INPUTS.includes(input as DeskInput) ? (input as DeskInput) : 'pick', lines: lines === '7' ? 7 : 13 };
}

const CODES_76 = ['KeyA', 'KeyS', 'KeyD', 'KeyF', 'KeyG', 'KeyH', 'KeyJ', 'KeyZ', 'KeyX', 'KeyC', 'KeyV', 'KeyB', 'KeyN'];
const CODES_13 = ['KeyQ', 'KeyW', 'KeyE', 'KeyR', 'KeyT', 'KeyY', 'KeyU', 'KeyI', 'KeyO', 'KeyP', 'BracketLeft', 'BracketRight', 'Backslash'];
const LABELS_76 = ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'Z', 'X', 'C', 'V', 'B', 'N'];
const LABELS_13 = ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P', '[', ']', '\\'];

export interface KeyMap {
  /** The cards' keys, by place in the hand (null: no key a card). */
  cards: string[] | null;
  cardLabels: string[] | null;
  /** The hand's places a line: 13, or 7 (7 and 6). */
  perLine: number;
  rows: string[];
  rowLabels: string[];
  sort: string;
  sortLabel: string;
  undo: string[];
  undoLabel: string;
  /** The cursor's keys (the 'cursor' way): left, right, start a sweep, stop it. */
  cursor: { left: string[]; right: string[]; on: string; off: string } | null;
}

export function keyMap(k: DeskKeys): KeyMap {
  const perLine = k.lines;
  const digits = { rows: ['Digit1', 'Digit2', 'Digit3'], rowLabels: ['1', '2', '3'] };
  const letters = { sort: 'Space', sortLabel: 'Space', undo: ['Backspace'], undoLabel: '⌫' };
  if (k.input === 'keys')
    return perLine === 7
      ? { cards: CODES_76, cardLabels: LABELS_76, perLine, ...digits, ...letters, cursor: null }
      : { cards: CODES_13, cardLabels: LABELS_13, perLine, ...digits, ...letters, cursor: null };
  if (k.input === 'cursor')
    return { cards: null, cardLabels: null, perLine, ...digits, ...letters,
      cursor: { left: ['KeyA', 'ArrowLeft'], right: ['KeyD', 'ArrowRight'], on: 'KeyW', off: 'KeyS' } };
  return { cards: null, cardLabels: null, perLine, ...digits, sort: 'KeyS', sortLabel: 'S', undo: ['KeyZ'], undoLabel: 'Z', cursor: null };
}

/** The keys listed under a table's buttons (KeyList). */
export function keyList(k: DeskKeys): [string[], string][] {
  const m = keyMap(k);
  const rows: [string[], string] = [m.rowLabels, t('k_rows')];
  const back: [string[], string] = [['⇧', m.rowLabels.join(' ')], t('k_row_back')];
  const tail: [string[], string][] = [[['Esc'], t('k_unpick')], [['L'], t('last_hand')], [['?'], t('k_hide_keys')]];
  if (m.cursor) return [rows, back, [['A', 'D'], t('k_cursor_move')], [['W'], t('k_cursor_on')], [['S'], t('k_cursor_off')], [[m.sortLabel], t('k_sort')], [[m.undoLabel], t('k_undo')], ...tail];
  const pick: [string[], string] = m.cards ? [[m.cardLabels![0], '…', m.cardLabels![12]], t('k_card_keys')] : [['←', '→', 'Space'], t('k_pick')];
  return [rows, back, pick, ...tail];
}

/** The first hint, before a card is picked. */
export function startHint(k: DeskKeys): string {
  const m = keyMap(k);
  if (m.cursor) return t('hint_start_cursor', m.rowLabels.join(t('list_sep')));
  return m.cards ? t('hint_start_keys', m.rowLabels.join(t('list_sep'))) : t('hint_start_desk');
}
