#include "../players/montecarlo.h"

#include <algorithm>
#include <cmath>

using namespace std;

MonteCarloPlayer::MonteCarloPlayer(shared_ptr<const Player> opp, int n)
  : opponent(opp), numSamples(n) {
  assert(opponent != nullptr);
  assert(numSamples > 0);
}

string MonteCarloPlayer::name() const {
  return "mc:samples=" + Global::intToString(numSamples) + ",opp=" + opponent->name();
}

vector<ArrangementInfo> MonteCarloPlayer::candidates(CardSet hand, const DecisionContext& ctx) {
  vector<ArrangementInfo> kept;
  HandTable(hand, ctx.rules).undominated(kept);
  return kept;
}

vector<CandidateStats> MonteCarloPlayer::evaluate(
  CardSet hand, const vector<ArrangementInfo>& cands, const DecisionContext& ctx, Rand& rand
) const {
  const size_t n = cands.size();
  vector<double> sum(n, 0.0);
  vector<double> sumSq(n, 0.0);
  DecisionContext oppCtx = ctx;
  for(int s = 0; s < numSamples; s++) {
    CardSet others[Cards::NUM_PLAYERS - 1];
    Cards::dealOthers(rand, hand, others);
    ArrangementInfo opps[Cards::NUM_PLAYERS - 1];
    for(int j = 0; j < Cards::NUM_PLAYERS - 1; j++)
      opps[j] = opponent->arrange(others[j], oppCtx, rand);
    const ArrangementInfo* oppPtrs[Cards::NUM_PLAYERS - 1] = {&opps[0], &opps[1], &opps[2]};
    Scoring::Opponents summary(oppPtrs);
    for(size_t i = 0; i < n; i++) {
      double r = summary.resultOf(cands[i], ctx.rules);
      sum[i] += r;
      sumSq[i] += r * r;
    }
  }

  vector<CandidateStats> stats(n);
  for(size_t i = 0; i < n; i++) {
    stats[i].info = cands[i];
    stats[i].mean = sum[i] / numSamples;
    double var = numSamples > 1 ? (sumSq[i] - sum[i] * stats[i].mean) / (numSamples - 1) : 0.0;
    stats[i].stdErr = std::sqrt(std::max(var, 0.0) / numSamples);
  }
  std::stable_sort(stats.begin(), stats.end(), [](const CandidateStats& a, const CandidateStats& b) {
    return a.mean > b.mean;
  });
  return stats;
}

ArrangementInfo MonteCarloPlayer::arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const {
  vector<ArrangementInfo> cands = candidates(hand, ctx);
  if(cands.size() == 1)
    return cands[0];
  return evaluate(hand, cands, ctx, rand)[0].info;
}
