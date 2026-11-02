#ifndef GAME_ARRANGEMENT_H_
#define GAME_ARRANGEMENT_H_

#include "../game/rules.h"

// Setting 13 cards into front (3), middle (5) and back (5) rows (docs/rules.md, sections 2, 5 and 6.3).

enum Row : int { FRONT = 0, MIDDLE = 1, BACK = 2 };
constexpr int NUM_ROWS = 3;
constexpr int ROW_SIZES[NUM_ROWS] = {3, 5, 5};

struct Arrangement {
  CardSet rows[NUM_ROWS] = {0, 0, 0};

  CardSet hand() const { return rows[FRONT] | rows[MIDDLE] | rows[BACK]; }
  bool operator==(const Arrangement& other) const {
    return rows[FRONT] == other.rows[FRONT] && rows[MIDDLE] == other.rows[MIDDLE] && rows[BACK] == other.rows[BACK];
  }
  // "front | middle | back", each row from highest to lowest card.
  std::string toString() const;
};

// An arrangement with what scoring needs.
struct ArrangementInfo {
  Arrangement arr;
  HandEval::Key keys[NUM_ROWS] = {0, 0, 0};
  // front <= middle <= back.
  bool valid = false;
  // The number of special bonuses (section 6.3), including the dragon. Scoring ignores them for an invalid (fouled)
  // arrangement; what they are worth depends on the Rules.
  uint8_t bonus = 0;
  // The 13 cards make a dragon.
  bool dragon = false;

  std::string toString() const;
};

// The keys of every row hand that can be made from one 13-card hand, for fast enumeration, under the rules' ranking
// of A-2-3-4-5, and which splits are valid under its strictRows. Positions 0..12 index the hand's cards from lowest to
// highest, and a "local mask" is a 13-bit mask of positions.
class HandTable {
 public:
  HandTable(CardSet hand, const Rules& rules);

  CardSet hand() const { return handCards; }
  bool isDragon() const { return dragon; }
  // Whether valid means strictly increasing rows: strictRows, and the hand has such a split.
  bool strict() const { return strictRows; }
  bool isValid(HandEval::Key front, HandEval::Key middle, HandEval::Key back) const {
    return strictRows ? front < middle && middle < back : front <= middle && middle <= back;
  }
  CardSet toCardSet(uint32_t local) const;
  // Requires a local mask of 3 or 5 positions.
  HandEval::Key key(uint32_t local) const { return keys[local]; }
  ArrangementInfo info(uint32_t frontLocal, uint32_t middleLocal, uint32_t backLocal) const;

  // Calls f(frontLocal, middleLocal, backLocal, frontKey, middleKey, backKey) for every split, or every valid one.
  template<typename F>
  void forEachSplit(bool validOnly, F&& f) const;

  // The valid arrangement with the strongest back, then middle, then front, then bonus.
  ArrangementInfo strongestBack() const;
  // The valid arrangements minus dominated ones under the rules' dealer cost; see Arrangements::pruneDominated.
  void undominated(std::vector<ArrangementInfo>& out) const;

  // All local masks with 5 positions, and for the i-th of them as the back row, the 56 middle rows from the other
  // 8 positions, at middlesOfBack()[56 * i ...].
  static const std::vector<uint16_t>& fiveMasks();
  // All local masks with 3 positions.
  static const std::vector<uint16_t>& threeMasks();
  static const std::vector<uint16_t>& middlesOfBack();

 private:
  CardSet handCards;
  Card cards[Cards::HAND_SIZE];
  bool dragon;
  bool strictRows;
  double dealerCost;
  HandEval::Key keys[1 << Cards::HAND_SIZE];

  bool hasStrictSplit() const;
};

template<typename F>
void HandTable::forEachSplit(bool validOnly, F&& f) const {
  constexpr uint32_t ALL = (1u << Cards::HAND_SIZE) - 1;
  const std::vector<uint16_t>& fives = fiveMasks();
  const uint16_t* middles = middlesOfBack().data();
  for(size_t b = 0; b < fives.size(); b++) {
    const uint32_t backLocal = fives[b];
    const HandEval::Key backKey = keys[backLocal];
    const uint16_t* mids = middles + 56 * b;
    for(int m = 0; m < 56; m++) {
      const uint32_t middleLocal = mids[m];
      const HandEval::Key middleKey = keys[middleLocal];
      if(validOnly && (middleKey > backKey || (strictRows && middleKey == backKey)))
        continue;
      const uint32_t frontLocal = ALL & ~backLocal & ~middleLocal;
      const HandEval::Key frontKey = keys[frontLocal];
      if(validOnly && (frontKey > middleKey || (strictRows && frontKey == middleKey)))
        continue;
      f(frontLocal, middleLocal, backLocal, frontKey, middleKey, backKey);
    }
  }
}

namespace Arrangements {
  // Number of ways to split 13 cards into rows of 3, 5 and 5: C(13,3) * C(10,5).
  constexpr int NUM_SPLITS = 72072;

  // The 13 cards contain all 13 ranks.
  bool isDragon(CardSet hand);
  // Bonuses earned by the rows (front trips, middle and back four of a kind or straight flush), without the dragon.
  inline uint8_t rowBonuses(HandEval::Key front, HandEval::Key middle, HandEval::Key back) {
    return (uint8_t)((HandEval::categoryOf(front) == HandEval::THREE_OF_A_KIND ? 1 : 0) +
                     (HandEval::categoryOf(middle) >= HandEval::FOUR_OF_A_KIND ? 1 : 0) +
                     (HandEval::categoryOf(back) >= HandEval::FOUR_OF_A_KIND ? 1 : 0));
  }

  // Requires rows of 3, 5 and 5 cards with no card repeated. Keys and validity follow the rules' wheel and strictRows.
  ArrangementInfo evaluate(const Arrangement& arr, const Rules& rules);

  // Every split of a 13-card hand, or only the valid ones. Appends to out after clearing it.
  void enumerate(CardSet hand, const Rules& rules, bool validOnly, std::vector<ArrangementInfo>& out);

  // Removes arrangements that can never do better than another one, whatever the opponents set.
  //
  // A player's result is non-decreasing in each of their front and middle keys and in their bonus, under any Rules
  // that pass Rules::check (royalties never decrease with a row's category). It is
  // non-decreasing in the back key too, except through the dealer cost, which a larger back can only add. So B is
  // dropped when some kept A has keys[FRONT], keys[MIDDLE] and bonus >= B's, and A's back key is >= B's when
  // dealerCost == 0, or equal to B's when dealerCost > 0. Of arrangements equal in all of these, one is kept.
  // The input must contain valid arrangements only; the output is sorted by back, middle, front key, then bonus,
  // all descending.
  void pruneDominated(const std::vector<ArrangementInfo>& in, double dealerCost, std::vector<ArrangementInfo>& out);

  // The 13-bit mask of the positions of `row`'s cards within `hand`, positions counted in increasing card order (as
  // in HandTable and in the network's inputs).
  uint16_t localMask(CardSet hand, CardSet row);

  // Parses "front | middle | back". Throws StringError on bad input; does not check validity.
  Arrangement parse(const std::string& str);
}

#endif // GAME_ARRANGEMENT_H_
