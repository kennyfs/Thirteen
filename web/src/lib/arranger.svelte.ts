// A hand being set into rows: the selection, the row picked first, and the engine's view of the rows (keys,
// categories, validity) under `rules` (a full spec). A port of the app's Arranger.kt.
import { ROW_SIZES, sortHand, type Rows } from './cards';
import * as engine from './engine';
import type { RowsEval } from './engine';

const EMPTY: Rows = [[], [], []];

export class Arranger {
  hand = $state<number[]>([]);
  rows = $state<Rows>(EMPTY);
  selected = $state<Set<number>>(new Set());
  /** The row picked first: cards tapped then go to it once they fill it. */
  armed = $state<number | null>(null);
  rowsEval = $state<RowsEval | null>(null);
  sortBySuit = $state(false);
  rules = 'family';
  /** Earlier rows of this hand, for undo; the latest last. */
  private history = $state<Rows[]>([]);

  constructor(private onError: (m: string) => void, private rowRoom: (row: number, free: number) => string) {}

  get canUndo() { return this.history.length > 0; }
  get unplaced(): number[] { return sortHand(this.hand.filter((c) => !this.rows.some((r) => r.includes(c))), this.sortBySuit); }
  /** Whether "take all back" has anything to do: cards in the rows, or picked ones (it unpicks them). */
  get canClear() { return this.selected.size > 0 || this.rows.some((r) => r.length > 0); }

  reset(hand: number[], rules: string, placed: Rows = EMPTY) {
    this.hand = hand;
    this.rules = rules;
    this.selected = new Set();
    this.armed = null;
    this.history = [];
    this.update(placed);
  }

  toggle(card: number) {
    const s = new Set(this.selected);
    if (s.has(card)) s.delete(card); else s.add(card);
    this.selected = s;
    this.selectionDone();
  }

  /** Selects or unselects cards during a swipe over the hand; selectionDone() when it ends. */
  select(cards: number[], on: boolean) {
    const s = new Set(this.selected);
    for (const c of cards) if (on) s.add(c); else s.delete(c);
    this.selected = s;
  }

  /** With a row picked first, places the selected cards once they fill it. */
  selectionDone() {
    const row = this.armed;
    if (row !== null && this.selected.size === ROW_SIZES[row] - this.rows[row].length) this.placeInto(row);
  }

  /** A tap on a row: places the selected cards, or with none selected, picks the row for the next cards. */
  tapRow(row: number) {
    if (this.selected.size > 0) this.placeInto(row);
    else this.armed = this.armed === row || this.rows[row].length === ROW_SIZES[row] ? null : row;
  }

  /** Puts the selected cards into a row, then fills the last open row if the cards left fit it exactly. */
  placeInto(row: number) {
    const free = ROW_SIZES[row] - this.rows[row].length;
    if (this.selected.size === 0) return;
    if (this.selected.size > free) return this.onError(this.rowRoom(row, free));
    let next = this.rows.map((cards, r) => (r === row ? [...cards, ...[...this.selected].sort((a, b) => b - a)] : cards));
    this.selected = new Set();
    this.armed = null;
    this.save();
    const left = this.hand.filter((c) => !next.some((r) => r.includes(c)));
    const open = [0, 1, 2].filter((r) => next[r].length < ROW_SIZES[r]);
    if (open.length === 1 && left.length === ROW_SIZES[open[0]] - next[open[0]].length)
      next = next.map((cards, r) => (r === open[0] ? [...cards, ...left] : cards));
    this.update(next);
  }

  /** Returns a card to the hand; a swipe over several saves one undo step, at its first card. */
  unplace(card: number, saveUndo = true) {
    if (!this.rows.some((r) => r.includes(card))) return;
    if (saveUndo) this.save();
    this.update(this.rows.map((r) => r.filter((c) => c !== card)));
  }

  clearRow(row: number) {
    if (this.rows[row].length === 0) return;
    this.save();
    this.update(this.rows.map((cards, r) => (r === row ? [] : cards)));
  }

  clearRows() {
    this.selected = new Set();
    this.armed = null;
    if (this.rows.some((r) => r.length > 0)) this.save();
    this.update(EMPTY);
  }

  undo() {
    const last = this.history[this.history.length - 1];
    if (!last) return;
    this.history = this.history.slice(0, -1);
    this.selected = new Set();
    this.armed = null;
    this.update(last);
  }

  private save() { this.history = [...this.history, this.rows].slice(-50); }

  // Which update the engine's answer is for: $state wraps the rows in a proxy, so they are not compared themselves.
  private version = 0;

  private update(next: Rows) {
    this.rows = next;
    this.rowsEval = null;
    if (this.hand.length === 0) return;
    const v = ++this.version;
    engine.rows(next, this.rules).then((e) => { if (v === this.version) this.rowsEval = e; }, (e) => this.onError(e.message));
  }

  /** The rows as the engine sees them now. */
  async eval(): Promise<RowsEval> { return this.rowsEval ?? engine.rows(this.rows, this.rules); }
}
