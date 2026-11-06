// The keys of a computer's table (DeskArranging), in the three ways the settings offer:
//  - 'pick': ← → along the hand and Space picks, 1 2 3 into a row, S sorts, Z undoes;
//  - 'keys76': a key a card, the hand in two lines as the keys (A S D F G H J over Z X C V B N), 1 2 3 into a row;
//  - 'keys13': a key a card, the hand in one line as the letters' top row (Q W E … [ ] \), 1 2 3 into a row.
// With a key a card the letters are the cards', so Space sorts and Backspace undoes; a card played leaves its place
// empty, so that the keys stay on their cards. Keys are matched by where they are (KeyboardEvent.code), not by the
// letter, so that a keyboard layout or an input method changes nothing.
import { t } from './i18n.svelte';

export type DeskKeys = 'pick' | 'keys76' | 'keys13';
export const DESK_KEYS: DeskKeys[] = ['pick', 'keys76', 'keys13'];

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
}

export function keyMap(mode: DeskKeys): KeyMap {
  const digits = { rows: ['Digit1', 'Digit2', 'Digit3'], rowLabels: ['1', '2', '3'] };
  if (mode === 'keys76')
    return { cards: CODES_76, cardLabels: LABELS_76, perLine: 7, ...digits, sort: 'Space', sortLabel: 'Space', undo: ['Backspace'], undoLabel: '⌫' };
  if (mode === 'keys13')
    return { cards: CODES_13, cardLabels: LABELS_13, perLine: 13, ...digits, sort: 'Space', sortLabel: 'Space', undo: ['Backspace'], undoLabel: '⌫' };
  return { cards: null, cardLabels: null, perLine: 13, ...digits, sort: 'KeyS', sortLabel: 'S', undo: ['KeyZ'], undoLabel: 'Z' };
}

/** The keys listed under a table's buttons (KeyList). */
export function keyList(mode: DeskKeys): [string[], string][] {
  const m = keyMap(mode);
  const pick: [string[], string] = m.cards ? [[m.cardLabels![0], '…', m.cardLabels![12]], t('k_card_keys')] : [['←', '→', 'Space'], t('k_pick')];
  return [[m.rowLabels, t('k_rows')], pick, [['Esc'], t('k_unpick')], [['?'], t('k_help')]];
}

/** The first hint, before a card is picked. */
export function startHint(mode: DeskKeys): string {
  const m = keyMap(mode);
  return m.cards ? t('hint_start_keys', m.rowLabels.join(t('list_sep'))) : t('hint_start_desk');
}
