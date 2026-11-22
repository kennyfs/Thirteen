// The sizes of cards, worked out from the room left as the app's Sizing.kt does: CSS px stand for dp, and `u` is what
// a 14px text takes, which grows with the browser's font size and the page's own text size.

export const LINE_GAP = 2;
export const LINE_GROUP_GAP = 8;
export const PAGE_PAD = 16;
export const MAX_OTHER = 56;
const MAX_RECORD = 64;
export const MAX_PLAY = 100;
export const CARD_GAP = 4;
export const HAND_GAP = 4;
export const SUIT_GAP = 12;
export const HAND_LINE_GAP = 6;
export const HAND_LINE_GAP_TIGHT = 2;
export const ROW_PAD = 6;
export const ROWS_GAP = 10;
export const ROW_STACK_GAP = 6;
export const BIG_BUTTON = 56;
export const BAR_BUTTON = 46;

const down = (x: number) => Math.floor(x * 2) / 2;
export const cardHeight = (w: number) => w * 1.4;
export const rowWidth = (n: number, w: number, gap = CARD_GAP) => w * n + gap * Math.max(0, n - 1);

/** The width at which 13 cards in three rows, side by side, fill `width`. */
export const lineCard = (width: number) => Math.max(8, down((width - LINE_GAP * 10 - LINE_GROUP_GAP * 2) / 13));

/**
 * `card`: the rows' cards; `hand`: the hand's (larger on a wide page when that fills the width, see ROWS_TO_HAND);
 * `even`: the size both would have alike, which the other pages take as the play size (CardSizes.play).
 */
export interface ArrangeFit {
  card: number; hand: number; even: number; above: boolean; perLine: number;
  /**
   * The rows' labels' text size against the page's, below 1 where a label at the page's size would make the cards
   * smaller than a label at the normal size does (see arrangeFit).
   */
  labels?: number;
}

/**
 * On a wide page the hand may be larger than the rows' cards, up to filling the felt's width, so long as the rows' keep
 * at least this much of its size (the rows give up the height the hand takes).
 */
export const ROWS_TO_HAND = 0.75;

/**
 * How the arranging page is laid out, for an area `w` x `h`; `u` is the text's unit (what 14px of text takes).
 *
 * At a large text size the rows' labels (a name and a line of state beside or above each row's cards) and, on a wide
 * page, the room kept for the buttons can take the cards' room. Then the labels are made only as much smaller as keeps
 * the cards (the rows' and the hand's) as large as labels at the normal size would leave them, within 1 px: `labels`
 * says how much. The buttons' names wrap or give way to icons instead. The app's Sizing.kt does the same.
 */
export function arrangeFit(w0: number, h0: number, wide: boolean, u: number, maxCard = MAX_PLAY): ArrangeFit {
  const fit = (lu: number, least?: number) => (wide ? wideFit(w0, h0, lu, maxCard, least) : tallFit(w0, h0, lu, maxCard, least));
  const full = fit(u);
  if (u <= 14) return full;
  // Compared before the least card size holds them: on a small page the labels may crush cards already at it (their
  // rows then taller than the page).
  const normal = fit(14, -Infinity);
  const crushed = (lu: number) => {
    const f = fit(lu, -Infinity);
    return f.card < normal.card - 1 || f.hand < normal.hand - 1;
  };
  if (!crushed(u)) return full;
  // The largest label size that does not: the cards shrink (or stay) as the labels grow.
  let lo = 14;
  let hi = u;
  for (let i = 0; i < 10; i++) {
    const mid = (lo + hi) / 2;
    if (crushed(mid)) hi = mid;
    else lo = mid;
  }
  return { ...fit(lo), labels: lo / u };
}

/** The tall layout, for the rows' labels' text unit `lu` (see arrangeFit). */
function tallFit(w0: number, h0: number, lu: number, maxCard: number, least = 20): ArrangeFit {
  const room = (sp: number) => lu * (sp / 14);
  const w = w0 - 32;
  const h = h0 - (BIG_BUTTON + 16) - 16;
  const header = Math.max(32, room(26));
  let best: ArrangeFit | null = null;
  for (const perLine of [13, 7]) {
    const lines = Math.ceil(13 / perLine);
    for (const above of [false, true]) {
      const labels = above ? 0 : Math.max(72, room(64)) + 8;
      const byRowsWidth = (w - labels - ROW_PAD * 2 - CARD_GAP * 4) / 5;
      const byHandWidth = (w - HAND_GAP * (perLine - 1) - (perLine >= 13 ? (SUIT_GAP - HAND_GAP) * 3 : 0)) / perLine;
      // The rows' padding and the two gaps between them (8 under headers, ROW_STACK_GAP beside labels), the 8 above the
      // hand and its lines' gaps.
      const fixed = ROW_PAD * 2 * 3 + (above ? 8 : ROW_STACK_GAP) * 2 + 8 + HAND_LINE_GAP * (lines - 1) + (above ? header * 3 : 0);
      const byHeight = (h - fixed) / (1.4 * (3 + lines) + 0.22);
      let s = Math.max(least, down(Math.min(byRowsWidth, byHandWidth, byHeight, maxCard)));
      // A label beside taller than the card (as drawn) makes its row taller (its name and a line of state); 2 more for
      // the fractions of the three labels' heights, which would otherwise scroll the rows by a pixel.
      const label = room(40) + 2;
      if (!above && s * 1.4 < label) s = Math.max(least, down(Math.min(s, (h - fixed - label * 3 - 2) / (1.4 * lines + 0.22))));
      if (!best || s > best.card + 0.5) best = { card: s, hand: s, even: s, above, perLine };
    }
  }
  return best!;
}

export interface CardSizes { play: number; other: number; record: number }

/**
 * The arranging screens' words (the practice page's header and table, the battle's while a hand is set, the tutorial's
 * practice) at most this much larger than the browser's own size, times the page's text size: a larger font took the
 * cards' room for the words alone. As the app's TABLE_FONT_MAX.
 */
export const TABLE_FONT_MAX = 1.5;

/** The sizes for a window area `w` x `h` (what the screens share, beside the rail or above the bar). */
export function cardSizes(w: number, h: number, u: number, tableU = u): CardSizes {
  const wide = w > h * 1.15;
  // The arranging pages' words at the table's text size (TABLE_FONT_MAX): `tableU` for them, `u` for the others.
  const room = (sp: number) => tableU * (sp / 14);
  const otherRoom = (sp: number) => u * (sp / 14);
  const scoreLines = (room(16) * 4.6 + 8) * 4 > w - 28 ? 2 : 1;
  const header = wide ? 54 : 6 + Math.max(48, room(26)) + (room(20) + 6) * scoreLines;
  const play = arrangeFit(w, h - header, wide, tableU).even;
  const other = wide
    ? Math.min(lineCard(w * 0.66 - PAGE_PAD * 2), down(((h - Math.max(48, otherRoom(32)) - 6 - 12 - 4 * 3) / 4 - otherRoom(18) - 12) / 1.4), MAX_OTHER)
    : Math.min(lineCard(w - PAGE_PAD * 2), MAX_OTHER);
  const record = w >= 560 + PAGE_PAD * 2 ? lineCard(w - PAGE_PAD * 2) : down(((w - PAGE_PAD * 2 - 12) / 2 - 8 - LINE_GAP * 4) / 5);
  return { play, other: Math.max(14, other), record: Math.max(14, Math.min(record, MAX_RECORD)) };
}

// ---- A computer's window: the practice page has a side panel (its header, the scores and the buttons), the table
// the rest: the rows one above another in the middle, the hand in a line under them. ----

/** The side panel's width. */
export const ASIDE = 320;
/** The largest card on a computer: a mouse needs no more, and a big screen should not show huge ones. */
export const MAX_DESK = 104;
export const DESK_LABEL = 76;

/** The card for the table on a computer: an area `w` x `h` (beside the side panel). */
export function deskFit(w: number, h: number): number {
  const innerW = w - 24 - 32;
  const innerH = h - 12 - 32;
  const byHand = (innerW - HAND_GAP * 12 - (SUIT_GAP - HAND_GAP) * 3) / 13;
  const byRows = (innerW - DESK_LABEL - 8 - ROW_PAD * 2 - CARD_GAP * 4) / 5;
  // Three rows (padded), their gaps, the gap above the hand, the hand and a picked card's lift.
  const byHeight = (innerH - ROW_PAD * 6 - 8 * 2 - 28) / (1.4 * 4 + 0.22);
  return Math.max(24, down(Math.min(byHand, byRows, byHeight, MAX_DESK)));
}

/** The sizes on a computer, for the window area `w` x `h`: the practice page's table is beside its side panel. */
export function deskCardSizes(w: number, h: number, u: number): CardSizes {
  const s = cardSizes(w - ASIDE, h, u);
  return { ...s, play: deskArrangeFit(w - ASIDE - 24, h - 24, u).card };
}

// ---- A computer's own pages (Desk*.svelte) ----

/** The largest card on a computer's table: large on a large screen, yet the rows and the hand still in sight at once. */
export const MAX_DESK_PLAY = 150;
/** The gap between the rows and the hand on a computer's table, and the table's padding. */
export const DESK_HAND_GAP = 24;
export const DESK_PAD = 20;

/** The label at the left of a row on a computer's table: its name and key, and its state ("2/5", "同花順到 K"). */
export const deskRowLabel = (u: number) => Math.max(116, u * 8.2);

export interface DeskArrangeFit { card: number; perLine: number }

/**
 * A computer's table `w` x `h`: the rows one above another, the hand under them in `perLine` cards a line (13: one
 * line, 7: 7 and 6), `below` px under each card for its key.
 */
export function deskArrangeFit(w: number, h: number, u: number, perLine = 13, below = 0): DeskArrangeFit {
  const innerW = w - DESK_PAD * 2;
  const innerH = h - DESK_PAD * 2;
  const byRows = (innerW - deskRowLabel(u) - 12 - ROW_PAD * 2 - CARD_GAP * 4) / 5;
  const lines = Math.ceil(13 / perLine);
  const byHand = (innerW - HAND_GAP * (perLine - 1) - (perLine === 13 ? (SUIT_GAP - HAND_GAP) * 3 : 0)) / perLine;
  const fixed = ROW_PAD * 6 + ROW_STACK_GAP * 2 + DESK_HAND_GAP + (HAND_LINE_GAP + below) * lines;
  const byHeight = (innerH - fixed) / (1.4 * (3 + lines) + 0.22);
  return { card: Math.max(24, Math.floor(Math.min(byRows, byHand, byHeight, MAX_DESK_PLAY))), perLine };
}

/** The wide layout, for the rows' labels' text unit `lu` (see arrangeFit). */
function wideFit(w0: number, h0: number, lu: number, maxCard: number, least = 18): ArrangeFit {
  const room = (sp: number) => lu * (sp / 14);
  const w = w0 - 20 - 12;
  const h = h0 - 6 - 12;
  const labels = Math.max(72, room(64)) + 8;
  // The buttons' room is kept by the labels' unit too: their names wrap, or give way to icons, before the cards shrink.
  const buttons = Math.max(200, room(190));
  const byRowsWidth = (w - buttons - ROWS_GAP - labels - ROW_PAD * 2 - CARD_GAP * 4) / 5;
  const byHandWidth = (w - HAND_GAP * 12 - (SUIT_GAP - HAND_GAP) * 3) / 13;
  const fixed = ROW_PAD * 6 + ROW_STACK_GAP * 2 + 6;
  const byHeight = (h - fixed) / (1.4 * 4 + 0.22);
  const label = room(40) + 2;
  if (byHeight * 1.4 < label) {
    // Each row is as tall as its label already: its cards may be as tall too; only the hand takes what height is left.
    const handByHeight = (h - fixed - label * 3) / (1.4 + 0.22);
    const rowCard = Math.max(least, down(Math.min(byRowsWidth, label / 1.4, maxCard)));
    const handCard = Math.max(least, down(Math.min(byHandWidth, handByHeight, maxCard)));
    return { card: rowCard, hand: handCard, even: Math.min(rowCard, handCard), above: false, perLine: 13 };
  }
  const card = Math.max(least, down(Math.min(byRowsWidth, byHandWidth, byHeight, maxCard)));
  // A larger hand, as wide as the felt at most, with the rows as large as the height then leaves them.
  const avail = h - fixed;
  // Not capped by maxCard (the size the pages that arrange share): the hand takes the width this page has.
  const hand = down(Math.min(byHandWidth, MAX_PLAY, avail / (1.4 * 3 * ROWS_TO_HAND + 1.4 + 0.22)));
  const rows = down(Math.min((avail - hand * (1.4 + 0.22)) / (1.4 * 3), byRowsWidth, maxCard));
  if (hand > card && rows >= 18 && rows >= hand * ROWS_TO_HAND - 0.5 && rows * 1.4 >= label) {
    return { card: rows, hand, even: card, above: false, perLine: 13 };
  }
  return { card, hand: card, even: card, above: false, perLine: 13 };
}
