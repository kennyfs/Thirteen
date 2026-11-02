#ifndef PLAYERS_MONTECARLO_H_
#define PLAYERS_MONTECARLO_H_

#include "../players/player.h"

struct CandidateStats {
  ArrangementInfo info;
  double mean = 0.0;
  // Standard error of the mean over the samples.
  double stdErr = 0.0;
};

// Best response to a fixed opponent policy by Monte Carlo. The opponents' cards are a uniformly random split of the
// 39 unseen cards, which is the exact belief: nothing else is known when setting a hand. All candidates are evaluated
// on the same samples (common random numbers), so their differences are much less noisy than their means.
class MonteCarloPlayer final : public Player {
 public:
  MonteCarloPlayer(std::shared_ptr<const Player> opponent, int numSamples);

  std::string name() const override;
  ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override;

  // Evaluates the given candidates for player 0 holding `hand`, sorted by mean, best first.
  std::vector<CandidateStats> evaluate(
    CardSet hand, const std::vector<ArrangementInfo>& candidates, const DecisionContext& ctx, Rand& rand
  ) const;

  // The candidates arrange() considers: all valid arrangements, minus the dominated ones.
  static std::vector<ArrangementInfo> candidates(CardSet hand, const DecisionContext& ctx);

 private:
  std::shared_ptr<const Player> opponent;
  int numSamples;
};

#endif // PLAYERS_MONTECARLO_H_
