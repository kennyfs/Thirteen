#ifndef GAME_RULES_H_
#define GAME_RULES_H_

#include "../game/handeval.h"

// The scoring rules of one game (docs/rules.md, section 11): how much rows, scoops, home runs, row bonuses
// (royalties), the dragon and the dealer cost are worth. Everything here is a number, so that one network can take the
// rules as input and play many variants. The default is the house rules of sections 6 and 7.
//
// Between each pair of players A and B (both valid), B pays A:
//   rowPoint per row A wins, minus rowPoint per row B wins (a tie is 0),
//   scoopPoint if A scoops B,
//   royalty[t] for each row of A with royalty type t: always, or with royaltyOnWin only if A's row beats B's,
//   dragonPoint if A holds a dragon,
// and homeRunPoint more if A scoops all three opponents. The dealer pays dealerCost, shared among ties.
namespace Royalty {
  enum Type : int {
    FRONT_TRIPS = 0,
    MIDDLE_FULL_HOUSE,
    MIDDLE_QUADS,
    MIDDLE_STRAIGHT_FLUSH,
    BACK_QUADS,
    BACK_STRAIGHT_FLUSH,
    NUM
  };
  extern const char* const NAMES[NUM];
  // The row of each type.
  extern const int ROW_OF[NUM];
  // The royalty type of a row hand, or -1.
  int typeOf(int row, HandEval::Key key);
}

struct Rules {
  double dealerCost = 0.0;
  double rowPoint = 0.0;
  double scoopPoint = 1.0;
  double homeRunPoint = 1.0;
  double royalty[Royalty::NUM] = {1.0, 0.0, 1.0, 1.0, 1.0, 1.0};
  bool royaltyOnWin = false;
  double dragonPoint = 1.0;
  // Rows must be strictly increasing (front < middle < back) instead of front <= middle <= back. When a hand has no
  // such arrangement, the non-strict ones are valid.
  bool strictRows = false;
  // A-2-3-4-5: 0 the lowest straight, 1 the second highest, 2 not a straight (HandEval::withWheel).
  int wheel = 0;

  // The value of a row's royalty, 0 when it has none.
  double royaltyOf(int row, HandEval::Key key) const {
    int t = Royalty::typeOf(row, key);
    return t < 0 ? 0.0 : royalty[t];
  }

  // The network's rules input, in this order (python/thirteen/model.py RULE_NAMES).
  static constexpr int NUM_FEATURES = 14;
  static const char* const FEATURE_NAMES[NUM_FEATURES];
  void toFeatures(float out[NUM_FEATURES]) const;
  static Rules fromFeatures(const float in[NUM_FEATURES]);

  // "PRESET" or "PRESET,name=value,...", names as in FEATURE_NAMES (on_win and strict take 0 or 1, wheel 0, 1 or 2).
  // Presets: family (the default), family_mid2, taiwan, pagat; see docs/rules.md section 11. Throws StringError on bad
  // input or rules that fail check().
  static Rules parse(const std::string& spec);
  // The values in the spec syntax, with every field.
  std::string toString() const;
  // Throws StringError unless every value is >= 0, c <= 1, wheel is 0, 1 or 2, and royalties never decrease with a
  // row's category (middle full house <= quads <= straight flush, back quads <= straight flush). Dominance pruning
  // needs the last.
  void check() const;

  bool operator==(const Rules& other) const;
};

#endif // GAME_RULES_H_
