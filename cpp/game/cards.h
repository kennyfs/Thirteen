#ifndef GAME_CARDS_H_
#define GAME_CARDS_H_

#include "../core/global.h"
#include "../core/rand.h"

// A card is 0..51: rank = card / 4 (0 = 2, 1 = 3, ..., 8 = T, 9 = J, 10 = Q, 11 = K, 12 = A), suit = card % 4
// (0 = clubs, 1 = diamonds, 2 = hearts, 3 = spades). Suits never rank; see docs/rules.md.
typedef uint8_t Card;
// A set of cards as a bitmask: bit c is card c.
typedef uint64_t CardSet;

namespace Cards {
  constexpr int NUM_CARDS = 52;
  constexpr int NUM_RANKS = 13;
  constexpr int NUM_SUITS = 4;
  constexpr int NUM_PLAYERS = 4;
  constexpr int HAND_SIZE = 13;
  constexpr CardSet FULL_DECK = ((CardSet)1 << NUM_CARDS) - 1;

  inline int rankOf(Card c) { return c >> 2; }
  inline int suitOf(Card c) { return c & 3; }
  inline Card make(int rank, int suit) { return (Card)(rank * 4 + suit); }
  inline CardSet bit(Card c) { return (CardSet)1 << c; }
  inline int count(CardSet s) { return __builtin_popcountll(s); }
  inline Card lowest(CardSet s) { return (Card)__builtin_ctzll(s); }

  // The 13-bit mask of the ranks present in s.
  uint32_t rankMask(CardSet s);
  // The cards of s, from highest to lowest (rank, then suit).
  std::vector<Card> toVector(CardSet s);

  // "As", "Td", "2c". Ranks 2-9, T (or 10), J, Q, K, A; suits c, d, h, s. Case-insensitive when parsing.
  std::string toString(Card c);
  // Cards from highest to lowest, separated by spaces.
  std::string toString(CardSet s);
  bool tryParseCard(const std::string& str, Card& out);
  // Cards separated by spaces or commas. Throws StringError on bad or repeated cards.
  CardSet parseCardSet(const std::string& str);

  // Deals the whole deck into four hands of 13.
  void dealAll(Rand& rand, CardSet hands[NUM_PLAYERS]);
  // Deals the cards not in `known` (39 of them) into three hands of 13.
  void dealOthers(Rand& rand, CardSet known, CardSet others[NUM_PLAYERS - 1]);
}

#endif // GAME_CARDS_H_
