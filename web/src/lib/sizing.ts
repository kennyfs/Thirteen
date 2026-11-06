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

export interface ArrangeFit { card: number; above: boolean; perLine: number }

/** How the arranging page is laid out, for an area `w` x `h`. */
export function arrangeFit(w0: number, h0: number, wide: boolean, u: number, maxCard = MAX_PLAY): ArrangeFit {
  const room = (sp: number) => u * (sp / 14);
  if (wide) {
    const w = w0 - 20 - 12;
    const h = h0 - 6 - 12;
    const labels = Math.max(72, room(64)) + 8;
    const buttons = Math.max(200, room(190));
    const byRowsWidth = (w - buttons - ROWS_GAP - labels - ROW_PAD * 2 - CARD_GAP * 4) / 5;
    const byHandWidth = (w - HAND_GAP * 12 - (SUIT_GAP - HAND_GAP) * 3) / 13;
    const fixed = ROW_PAD * 6 + ROW_STACK_GAP * 2 + 6;
    let byHeight = (h - fixed) / (1.4 * 4 + 0.22);
    const label = room(40) + 2;
    if (byHeight * 1.4 < label) byHeight = (h - fixed - label * 3) / (1.4 + 0.22);
    return { card: Math.max(18, down(Math.min(byRowsWidth, byHandWidth, byHeight, maxCard))), above: false, perLine: 13 };
  }
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
      const fixed = ROW_PAD * 2 * 3 + 8 * 2 + HAND_LINE_GAP * (lines - 1) + (above ? header * 3 : 0);
      const byHeight = (h - fixed) / (1.4 * (3 + lines) + 0.22);
      const s = Math.max(20, down(Math.min(byRowsWidth, byHandWidth, byHeight, maxCard)));
      if (!best || s > best.card + 0.5) best = { card: s, above, perLine };
    }
  }
  return best!;
}

export interface CardSizes { play: number; other: number; record: number }

/** The sizes for a window area `w` x `h` (what the screens share, beside the rail or above the bar). */
export function cardSizes(w: number, h: number, u: number): CardSizes {
  const wide = w > h * 1.15;
  const room = (sp: number) => u * (sp / 14);
  const scoreLines = (room(16) * 4.6 + 8) * 4 > w - 28 ? 2 : 1;
  const header = wide ? 54 : 6 + Math.max(48, room(26)) + (room(20) + 6) * scoreLines;
  const play = arrangeFit(w, h - header, wide, u).card;
  const other = wide
    ? Math.min(lineCard(w * 0.66 - PAGE_PAD * 2), down(((h - Math.max(48, room(32)) - 6 - 12 - 4 * 3) / 4 - room(18) - 12) / 1.4), MAX_OTHER)
    : Math.min(lineCard(w - PAGE_PAD * 2), MAX_OTHER);
  const record = w >= 560 + PAGE_PAD * 2 ? lineCard(w - PAGE_PAD * 2) : down(((w - PAGE_PAD * 2 - 12) / 2 - 8 - LINE_GAP * 4) / 5);
  return { play, other: Math.max(14, other), record: Math.max(14, Math.min(record, MAX_RECORD)) };
}
