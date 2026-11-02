#ifndef GAME_HANDEVAL_H_
#define GAME_HANDEVAL_H_

#include "../game/cards.h"

// Evaluation of row hands (docs/rules.md, sections 3 and 4).
//
// A hand's Key is a uint32 that compares like the hand: a larger key beats a smaller one and equal keys tie.
// Bits 20-23 hold the category; bits 16-19, 12-15, 8-11, 4-7 and 0-3 hold the tie-break sequence, first entry in the
// highest nibble, unused entries 0. Front (3-card) keys use the same layout, so comparing a front key with a middle
// key gives the front-vs-middle comparison of section 5: when the front's sequence runs out with all entries equal,
// the front key is <= the middle key.
namespace HandEval {
  typedef uint32_t Key;

  enum Category : uint32_t {
    HIGH_CARD = 1,
    ONE_PAIR = 2,
    TWO_PAIR = 3,
    THREE_OF_A_KIND = 4,
    STRAIGHT = 5,
    FLUSH = 6,
    FULL_HOUSE = 7,
    FOUR_OF_A_KIND = 8,
    STRAIGHT_FLUSH = 9,
  };
  constexpr int NUM_CATEGORIES = 10; // Indexable by Category; 0 is unused.

  // Requires exactly 5 cards. By table lookup on the rank multiset.
  Key eval5(CardSet cards);
  // Requires exactly 3 cards. By table lookup on the rank multiset.
  Key eval3(CardSet cards);
  // Direct implementations of the rules, used to build the tables and in tests.
  Key eval5Reference(CardSet cards);
  Key eval3Reference(CardSet cards);

  inline Category categoryOf(Key key) { return (Category)(key >> 20); }

  // The key under another ranking of A-2-3-4-5 (Rules::wheel, docs/rules.md section 11). eval5 and eval3 give wheel 0,
  // A-2-3-4-5 as the lowest straight. Wheel 1 makes it the second highest: A-high straights get the tie-break 13 and
  // A-2-3-4-5 gets 12. Wheel 2 makes it no straight: high card A 5 4 3 2, or a flush when suited.
  Key withWheel(Key key, int wheel);
  const char* categoryName(Category cat);
  // E.g. "Two pair (Q, 7, 3)". numCards is 5 for a middle or back key and 3 for a front key.
  std::string keyToString(Key key, int numCards = 5);
}

#endif // GAME_HANDEVAL_H_
