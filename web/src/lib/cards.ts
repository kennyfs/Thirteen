// Card ids as in cpp/game/cards.h: rank = id >> 2 (0 = 2, ..., 12 = A), suit = id & 3 (clubs, diamonds, hearts,
// spades). A port of the app's Cards.kt.

export type Rows = number[][];

export const RANKS = '23456789TJQKA';
export const SUITS = 'cdhs';
export const ROW_SIZES = [3, 5, 5];

export const rank = (c: number) => c >> 2;
export const suit = (c: number) => c & 3;
export const isRed = (c: number) => suit(c) === 1 || suit(c) === 2;
const rankChar = (r: string) => (r === 'T' ? '10' : r);
export const rankText = (c: number) => rankChar(RANKS[rank(c)]);

/** "Td", as in Cards::toString in C++. */
export const cardString = (c: number) => RANKS[rank(c)] + SUITS[suit(c)];
/** Highest first, separated by spaces: the record format of gui/collect.html. */
export const cardsString = (cards: number[]) => [...cards].sort((a, b) => b - a).map(cardString).join(' ');

/** Cards in the record format ("As Td 2c"). */
export function parseCards(s: string): number[] {
  return s.split(' ').filter((t) => t.trim()).map((t) => RANKS.indexOf(t[0].toUpperCase()) * 4 + SUITS.indexOf(t[1].toLowerCase()));
}

export const categoryOf = (key: number) => key >> 20;

/**
 * A row's hand in short from its key: the category and the ranks that define it ("Pair A", "Two pair K 7"). `wheel` is
 * the rules' ranking of A-2-3-4-5: under 1, a straight to A has 13 and A-2-3-4-5 12.
 */
export function rowLabel(key: number, labels: string[], wheel = 0): string {
  const cat = categoryOf(key);
  const wheelHigh = wheel === 1 && (cat === 5 || cat === 9);
  const r = (i: number) => {
    let n = (key >> (16 - 4 * i)) & 15;
    if (wheelHigh && i === 0) n = n === 13 ? 12 : n === 12 ? 3 : n;
    return rankChar(RANKS[n]);
  };
  return `${labels[cat]} ${r(0)}` + (cat === 3 ? ` ${r(1)}` : '');
}

/** A card in a laid-out row: `tint` is the row's category for a card that makes the hand (0 for a kicker). */
export interface Placed { card: number; tint: number; light: boolean }

/**
 * A row in reading order: the cards that make the hand first, larger groups first and then higher ranks, then the
 * kickers; a straight, flush or straight flush is one group from its top card (A-2-3-4-5 reads 5 4 3 2 A). The second
 * group of a two pair or full house is light. A row whose category is not known (0) is just sorted.
 */
export function rowLayout(cards: number[], category: number): Placed[] {
  const sorted = [...cards].sort((a, b) => b - a);
  if (category === 0) return sorted.map((card) => ({ card, tint: 0, light: false }));
  if (category === 5 || category === 6 || category === 9) {
    const ranks = new Set(sorted.map(rank));
    const wheel = category !== 6 && ranks.has(12) && ranks.has(0);
    const order = wheel ? [...sorted.filter((c) => rank(c) !== 12), ...sorted.filter((c) => rank(c) === 12)] : sorted;
    return order.map((card) => ({ card, tint: category, light: false }));
  }
  const count = new Map<number, number>();
  for (const c of sorted) count.set(rank(c), (count.get(rank(c)) ?? 0) + 1);
  const ranks = [...count.keys()].sort((a, b) => count.get(b)! - count.get(a)! || b - a);
  let group = 0;
  return ranks.flatMap((r) => {
    const pair = count.get(r)! >= 2;
    const light = pair && group++ > 0;
    return sorted.filter((c) => rank(c) === r).map((card) => ({ card, tint: pair && category > 1 ? category : 0, light }));
  });
}

/** The same cards as sets, whatever their order in each row. */
export function sameArrangement(a: Rows, b: Rows): boolean {
  return a.every((row, i) => row.length === b[i].length && row.every((c) => b[i].includes(c)));
}

/** The category (1..9) of a 3- or 5-card row, for tinting when only the cards are known. */
export function guessCategory(cards: number[]): number {
  if (cards.length === 0) return 0;
  const m = new Map<number, number>();
  for (const c of cards) m.set(rank(c), (m.get(rank(c)) ?? 0) + 1);
  const counts = [...m.values()].sort((a, b) => b - a);
  if (cards.length === 3) return counts[0] === 3 ? 4 : counts[0] === 2 ? 2 : 1;
  if (cards.length !== 5) return 0;
  const flush = new Set(cards.map(suit)).size === 1;
  const ranks = [...new Set(cards.map(rank))].sort((a, b) => a - b);
  const straight = ranks.length === 5 && (ranks[4] - ranks[0] === 4 || ranks.join() === '0,1,2,3,12');
  if (flush && straight) return 9;
  if (counts[0] === 4) return 8;
  if (counts[0] === 3 && counts[1] === 2) return 7;
  if (flush) return 6;
  if (straight) return 5;
  if (counts[0] === 3) return 4;
  if (counts[0] === 2 && counts[1] === 2) return 3;
  if (counts[0] === 2) return 2;
  return 1;
}

/** Sorted for the hand area: by rank, or by suit and then rank. */
export function sortHand(cards: number[], bySuit: boolean): number[] {
  return [...cards].sort(bySuit ? (a, b) => suit(a) - suit(b) || rank(a) - rank(b) : (a, b) => a - b);
}

export function shuffled<T>(xs: T[]): T[] {
  const a = [...xs];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}
