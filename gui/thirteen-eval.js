// Hand evaluation for the GUI, matching cpp/game/handeval.cpp: keys compare like hands (docs/rules.md, sections 3
// to 5). A card is rank * 4 + suit, rank 0 = 2 ... 12 = A, suit 0..3 = c, d, h, s.
(function (root) {
  "use strict";

  const RANKS = "23456789TJQKA";
  const SUITS = "cdhs";
  const SUIT_SYMBOLS = ["♣", "♦", "♥", "♠"];
  const CATEGORY_NAMES = [
    "", "High card", "One pair", "Two pair", "Three of a kind", "Straight", "Flush", "Full house",
    "Four of a kind", "Straight flush",
  ];
  const HIGH_CARD = 1, ONE_PAIR = 2, TWO_PAIR = 3, THREE_OF_A_KIND = 4, STRAIGHT = 5, FLUSH = 6, FULL_HOUSE = 7,
    FOUR_OF_A_KIND = 8, STRAIGHT_FLUSH = 9;

  function rankOf(c) { return c >> 2; }
  function suitOf(c) { return c & 3; }
  function cardToString(c) { return RANKS[rankOf(c)] + SUITS[suitOf(c)]; }
  function parseCard(s) {
    s = s.trim().toLowerCase();
    if (s.length === 3 && s.startsWith("10")) s = "t" + s[2];
    if (s.length !== 2) return -1;
    const r = RANKS.toLowerCase().indexOf(s[0]);
    const u = SUITS.indexOf(s[1]);
    return r < 0 || u < 0 ? -1 : r * 4 + u;
  }

  function makeKey(cat, seq) {
    let key = cat * (1 << 20);
    for (let i = 0; i < seq.length; i++) key += seq[i] * (1 << (16 - 4 * i));
    return key;
  }

  // Ranks grouped by multiplicity (largest first), each group from highest to lowest.
  function groups(cards) {
    const counts = new Array(13).fill(0);
    for (const c of cards) counts[rankOf(c)]++;
    const ranks = [], mults = [];
    for (let m = 4; m >= 1; m--)
      for (let r = 12; r >= 0; r--)
        if (counts[r] === m) { ranks.push(r); mults.push(m); }
    return { ranks, mults };
  }

  function eval5(cards) {
    const { ranks, mults } = groups(cards);
    const flush = cards.every((c) => suitOf(c) === suitOf(cards[0]));
    if (ranks.length === 5) {
      let top = -1;
      if (ranks[0] - ranks[4] === 4) top = ranks[0];
      else if (ranks[0] === 12 && ranks[1] === 3) top = 3; // A-2-3-4-5 is 5-high.
      if (top >= 0) return makeKey(flush ? STRAIGHT_FLUSH : STRAIGHT, [top]);
      return makeKey(flush ? FLUSH : HIGH_CARD, ranks);
    }
    if (mults[0] === 4) return makeKey(FOUR_OF_A_KIND, [ranks[0]]);
    if (mults[0] === 3) return makeKey(mults[1] === 2 ? FULL_HOUSE : THREE_OF_A_KIND, [ranks[0]]);
    if (mults[1] === 2) return makeKey(TWO_PAIR, ranks.slice(0, 3));
    return makeKey(ONE_PAIR, ranks.slice(0, 4));
  }

  function eval3(cards) {
    const { ranks } = groups(cards);
    if (ranks.length === 1) return makeKey(THREE_OF_A_KIND, [ranks[0]]);
    if (ranks.length === 2) return makeKey(ONE_PAIR, ranks);
    return makeKey(HIGH_CARD, ranks);
  }

  function categoryOf(key) { return Math.floor(key / (1 << 20)); }

  // The key under another ranking of A-2-3-4-5, as HandEval::withWheel: 0 the lowest straight, 1 the second highest
  // (A-high straights get 13, A-2-3-4-5 gets 12), 2 no straight (high card or flush A 5 4 3 2).
  function withWheel(key, wheel) {
    const cat = categoryOf(key);
    if (!wheel || (cat !== STRAIGHT && cat !== STRAIGHT_FLUSH)) return key;
    const top = Math.floor(key / (1 << 16)) % 16;
    if (wheel === 1) return top === 12 ? makeKey(cat, [13]) : top === 3 ? makeKey(cat, [12]) : key;
    return top === 3 ? makeKey(cat === STRAIGHT_FLUSH ? FLUSH : HIGH_CARD, [12, 3, 2, 1, 0]) : key;
  }

  // { keys, valid } for rows of 3, 5 and 5 cards. opts: { wheel, strict } (docs/rules.md section 11). With strict,
  // equal rows are a foul here; the engine also accepts them for the rare hand that has no strictly increasing split.
  function evaluateArrangement(front, middle, back, opts = {}) {
    const wheel = opts.wheel || 0;
    const keys = [eval3(front), withWheel(eval5(middle), wheel), withWheel(eval5(back), wheel)];
    const valid = opts.strict ? keys[0] < keys[1] && keys[1] < keys[2] : keys[0] <= keys[1] && keys[1] <= keys[2];
    return { keys, valid };
  }

  const api = {
    RANKS, SUITS, SUIT_SYMBOLS, CATEGORY_NAMES, rankOf, suitOf, cardToString, parseCard, eval5, eval3, categoryOf,
    withWheel, evaluateArrangement,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.ThirteenEval = api;
})(this);
