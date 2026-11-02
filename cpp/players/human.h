#ifndef PLAYERS_HUMAN_H_
#define PLAYERS_HUMAN_H_

#include "../players/player.h"

// A model of a human player: a conditional logit over all valid arrangements of a hand. Each arrangement's utility is
// a weighted sum of a few interpretable features, and P(arrangement) is proportional to exp(utility). The weights
// are fitted to recorded human choices by python/train_human.py, from features written by `thirteen humanfeatures`.

namespace RowStrength {
  // The fraction of all possible 5-card (or 3-card) hands that this key beats, counting ties as half.
  double percentile5(HandEval::Key key);
  double percentile3(HandEval::Key key);
}

namespace HumanFeatures {
  constexpr int NUM = 30;
  extern const char* const NAMES[NUM];

  void ofSplit(HandEval::Key front, HandEval::Key middle, HandEval::Key back, double dealerCost, float out[NUM]);

  // Valid splits merged when their row keys are equal (they then have equal features and equal results).
  struct SplitClass {
    HandEval::Key keys[NUM_ROWS];
    int32_t count;
    // One split of the class, as local masks of the HandTable.
    uint16_t local[NUM_ROWS];
  };
  void classes(const HandTable& table, std::vector<SplitClass>& out);
}

class HumanModelPlayer final : public Player {
 public:
  // weightsPath: lines of "feature_name weight". temperature divides the utilities; 0 plays the most likely class.
  HumanModelPlayer(const std::string& weightsPath, double temperature);

  std::string name() const override;
  ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override;

 private:
  std::string path;
  double temperature;
  float weights[HumanFeatures::NUM];
};

#endif // PLAYERS_HUMAN_H_
