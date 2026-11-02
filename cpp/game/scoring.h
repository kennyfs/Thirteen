#ifndef GAME_SCORING_H_
#define GAME_SCORING_H_

#include "../game/arrangement.h"

// Scoring one hand among four players (docs/rules.md, sections 6 to 9 and 11).

struct DealScore {
  // scoops[i][j]: player i scoops player j.
  bool scoops[Cards::NUM_PLAYERS][Cards::NUM_PLAYERS];
  bool homeRun[Cards::NUM_PLAYERS];
  // Points received minus points paid. Sums to 0.
  double points[Cards::NUM_PLAYERS];
  // Each player's share of the dealer cost (>= 0).
  double dealerCost[Cards::NUM_PLAYERS];
  // points - dealerCost.
  double result[Cards::NUM_PLAYERS];
};

namespace Scoring {
  // a scoops b, including the foul rules of section 8.
  inline bool scoops(const ArrangementInfo& a, const ArrangementInfo& b) {
    if(!a.valid)
      return false;
    if(!b.valid)
      return true;
    return a.keys[FRONT] > b.keys[FRONT] && a.keys[MIDDLE] > b.keys[MIDDLE] && a.keys[BACK] > b.keys[BACK];
  }

  // What b pays a (negative: a pays b) for rows, scoops, royalties and dragons; everything but home runs and the
  // dealer cost. A fouled player loses every row to a valid one and earns nothing.
  double pairPoints(const ArrangementInfo& a, const ArrangementInfo& b, const Rules& rules);

  // players[i] is player i's arrangement; fouls are scored as in section 8.
  void score(const ArrangementInfo* const players[Cards::NUM_PLAYERS], const Rules& rules, DealScore& out);

  // Only player 0's result; same as score(...).result[0].
  double resultOfPlayer0(const ArrangementInfo* const players[Cards::NUM_PLAYERS], const Rules& rules);

  // Per-hand totals over the valid opponents: how many rows of each royalty type (Royalty::Type order) they set,
  // then how many dragons they hold. The network predicts these per hand.
  constexpr int NUM_OPP_ROYALTIES = Royalty::NUM + 1;

  // What player 0's result needs from three fixed opponents, to score many arrangements of player 0 quickly.
  struct Opponents {
    const ArrangementInfo* opps[Cards::NUM_PLAYERS - 1];
    // Opponent j scoops the other two opponents, so scooping player 0 too makes a home run.
    bool scoopsOtherOpps[Cards::NUM_PLAYERS - 1];
    // See NUM_OPP_ROYALTIES.
    float oppRoyalties[NUM_OPP_ROYALTIES];
    bool anyValid;
    HandEval::Key maxBack;
    // Valid opponents whose back equals maxBack.
    int numAtMaxBack;

    explicit Opponents(const ArrangementInfo* const opps[Cards::NUM_PLAYERS - 1]);
    // Same as resultOfPlayer0 with `me` as player 0.
    double resultOf(const ArrangementInfo& me, const Rules& rules) const;
    // The training targets of `me` (a valid arrangement) against these opponents; see Target.
    void targetsOf(const ArrangementInfo& me, uint8_t out[]) const;
  };

  // Training targets for one arrangement against three opponents, in units of 1/TARGET_UNITS so that they are exact in
  // a uint8 (thirds and the dealer shares 1, 1/2, 1/3, 1/4 are all multiples of 1/12). "Fraction" means the fraction
  // of the three opponents. They do not depend on the Rules: compose() turns them, with the opponents' royalties, into
  // the exact result under any Rules.
  enum Target : int {
    SCOOP = 0,         // Fraction of opponents I scoop.
    SCOOPED,           // Fraction of opponents who scoop me.
    HOME_RUN,          // 1 if I scoop all three.
    OPP_HOME_RUN,      // Fraction of opponents who scoop me and both other opponents.
    DEALER_SHARE,      // My share of the dealer cost (1/k when k players tie for the largest back), before times c.
    ROW_WIN_FRONT,     // Fraction of opponents whose front I beat; then middle and back.
    ROW_WIN_MIDDLE,
    ROW_WIN_BACK,
    ROW_LOSE_FRONT,    // Fraction of opponents whose front beats mine; then middle and back.
    ROW_LOSE_MIDDLE,
    ROW_LOSE_BACK,
    // Fraction of opponents whose row with that royalty type beats my row (Royalty::Type order), for royaltyOnWin.
    ROYALTY_LOST_FRONT_TRIPS,
    ROYALTY_LOST_MIDDLE_FULL_HOUSE,
    ROYALTY_LOST_MIDDLE_QUADS,
    ROYALTY_LOST_MIDDLE_STRAIGHT_FLUSH,
    ROYALTY_LOST_BACK_QUADS,
    ROYALTY_LOST_BACK_STRAIGHT_FLUSH,
    NUM_TARGETS
  };
  constexpr int TARGET_UNITS = 12;
  extern const char* const TARGET_NAMES[NUM_TARGETS];

  // The result of a valid arrangement `me` from its targets as fractions (target / TARGET_UNITS, or the network's
  // probabilities) and the opponents' royalties (exact counts, or the network's expectations). Exact for exact
  // inputs; python/thirteen/model.py composes Q the same way.
  double compose(const Rules& rules, const ArrangementInfo& me, const float fractions[NUM_TARGETS],
                 const float oppRoyalties[NUM_OPP_ROYALTIES]);
}

#endif // GAME_SCORING_H_
