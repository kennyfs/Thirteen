#ifndef PLAYERS_PLAYER_H_
#define PLAYERS_PLAYER_H_

#include "../game/scoring.h"

#include <memory>

struct DecisionContext {
  // The scoring rules, including the dealer cost c (docs/rules.md sections 7 and 11).
  Rules rules;
  // The "fouls allowed" variant. The players here never foul either way; it is for learned players.
  bool foulsAllowed = false;
};

// A policy for setting a hand. Implementations are immutable after construction, so one instance can be shared by
// threads; all randomness comes from the Rand passed in.
class Player {
 public:
  virtual ~Player() {}
  virtual std::string name() const = 0;
  virtual ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const = 0;
};

namespace Players {
  // Spec syntax: NAME or NAME:key=value,key=value. See the implementations in players.cpp for names and keys:
  //   random                      A uniformly random valid arrangement.
  //   greedy                      The strongest back, then middle, then front.
  //   indep[:hands=N]             Maximizes an estimate that treats rows and opponents as independent, from row-hand
  //                               distributions of greedy players over N random hands (default 4000).
  //   mc[:samples=N,opp=SPEC]     Monte Carlo best response: deals the other 39 cards N times (default 500), has
  //                               three opponents SPEC (default greedy) set their hands, and picks the arrangement
  //                               with the best average result. opp= must come last; it takes the rest of the spec.
  //   net:model=PATH[,pop=P,temp=T]  A network (ONNX); responds to population P (default 0). With T > 0, samples
  //                               with probability proportional to exp(Q / T) instead of taking the best Q.
  //   human:weights=PATH[,temp=T] A human model (players/human.h); T divides the utilities (default 1).
  std::shared_ptr<const Player> create(const std::string& spec);
}

#endif // PLAYERS_PLAYER_H_
