#ifndef PLAYERS_NETPLAYER_H_
#define PLAYERS_NETPLAYER_H_

#include "../players/player.h"

// A network exported by python/thirteen/model.py, run with ONNX Runtime on the CPU. One instance can be used by many
// threads at once; each call runs single-threaded. Networks from before the rules input (with a "c" input, see
// legacy()) still work: they get only the dealer cost and answer as if under the family rules.
class NetModel {
 public:
  explicit NetModel(const std::string& path);
  ~NetModel();
  NetModel(const NetModel&) = delete;
  NetModel& operator=(const NetModel&) = delete;

  const std::string& path() const { return modelPath; }
  bool legacy() const { return isLegacy; }

  // Expected results (Q) of the candidates for one hand. Candidates must be arrangements of `hand`.
  void evaluate(CardSet hand, const Rules& rules, int pop, const std::vector<ArrangementInfo>& cands,
                std::vector<float>& q) const;
  // Also the event probabilities, [cands.size() * Scoring::NUM_TARGETS] in Scoring::Target order, and the opponents'
  // expected royalties [Scoring::NUM_OPP_ROYALTIES]. A legacy network gives zeros for what it does not predict.
  void evaluate(CardSet hand, const Rules& rules, int pop, const std::vector<ArrangementInfo>& cands,
                std::vector<float>& q, std::vector<float>& probs, std::vector<float>& oppRoyalties) const;

  // Whether this build has ONNX Runtime.
  static bool available();

 private:
  std::string modelPath;
  bool isLegacy = false;
  // Networks from before the last rules were added take only the first ones.
  int64_t numRuleFeatures = Rules::NUM_FEATURES;
  struct Impl;
  void run(CardSet hand, const Rules& rules, int pop, const std::vector<ArrangementInfo>& cands, std::vector<float>& q,
           std::vector<float>* probs, std::vector<float>* oppRoyalties) const;
  Impl* impl;
};

// Plays the candidate with the largest Q among the undominated valid arrangements, or samples with probability
// proportional to exp(Q / temperature) when temperature > 0.
class NetPlayer final : public Player {
 public:
  NetPlayer(std::shared_ptr<const NetModel> model, int pop, double temperature);

  std::string name() const override;
  ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override;

  const NetModel& model() const { return *netModel; }
  int pop() const { return popId; }

 private:
  std::shared_ptr<const NetModel> netModel;
  int popId;
  double temperature;
};

#endif // PLAYERS_NETPLAYER_H_
