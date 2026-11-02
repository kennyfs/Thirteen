#include "../game/scoring.h"

using namespace std;

static constexpr int N = Cards::NUM_PLAYERS;

// Players who did not foul and whose back row equals the largest back row among such players.
static int dealers(const ArrangementInfo* const players[N], bool isDealer[N]) {
  HandEval::Key maxBack = 0;
  bool anyValid = false;
  for(int i = 0; i < N; i++) {
    if(players[i]->valid && (!anyValid || players[i]->keys[BACK] > maxBack)) {
      maxBack = players[i]->keys[BACK];
      anyValid = true;
    }
  }
  int numDealers = 0;
  for(int i = 0; i < N; i++) {
    isDealer[i] = anyValid && players[i]->valid && players[i]->keys[BACK] == maxBack;
    if(isDealer[i])
      numDealers++;
  }
  return numDealers;
}

// What a fouled player pays the valid player a: every row lost, a scoop, and all of a's royalties and dragon.
static double againstFoul(const ArrangementInfo& a, const Rules& rules) {
  double p = NUM_ROWS * rules.rowPoint + rules.scoopPoint;
  for(int r = 0; r < NUM_ROWS; r++)
    p += rules.royaltyOf(r, a.keys[r]);
  if(a.dragon)
    p += rules.dragonPoint;
  return p;
}

double Scoring::pairPoints(const ArrangementInfo& a, const ArrangementInfo& b, const Rules& rules) {
  if(!a.valid || !b.valid) {
    if(a.valid)
      return againstFoul(a, rules);
    if(b.valid)
      return -againstFoul(b, rules);
    return 0.0;
  }
  double p = 0.0;
  int aWins = 0;
  int bWins = 0;
  for(int r = 0; r < NUM_ROWS; r++) {
    if(a.keys[r] > b.keys[r]) {
      aWins++;
      p += rules.rowPoint + (rules.royaltyOnWin ? rules.royaltyOf(r, a.keys[r]) : 0.0);
    }
    else if(a.keys[r] < b.keys[r]) {
      bWins++;
      p -= rules.rowPoint + (rules.royaltyOnWin ? rules.royaltyOf(r, b.keys[r]) : 0.0);
    }
    if(!rules.royaltyOnWin)
      p += rules.royaltyOf(r, a.keys[r]) - rules.royaltyOf(r, b.keys[r]);
  }
  if(aWins == NUM_ROWS)
    p += rules.scoopPoint;
  else if(bWins == NUM_ROWS)
    p -= rules.scoopPoint;
  if(a.dragon)
    p += rules.dragonPoint;
  if(b.dragon)
    p -= rules.dragonPoint;
  return p;
}

void Scoring::score(const ArrangementInfo* const players[N], const Rules& rules, DealScore& out) {
  for(int i = 0; i < N; i++) {
    out.points[i] = 0.0;
    out.dealerCost[i] = 0.0;
  }

  for(int i = 0; i < N; i++) {
    int numScooped = 0;
    for(int j = 0; j < N; j++) {
      out.scoops[i][j] = i != j && scoops(*players[i], *players[j]);
      if(out.scoops[i][j])
        numScooped++;
      // Each pair once.
      if(j > i) {
        double p = pairPoints(*players[i], *players[j], rules);
        out.points[i] += p;
        out.points[j] -= p;
      }
    }
    out.homeRun[i] = numScooped == N - 1;
    if(out.homeRun[i]) {
      for(int j = 0; j < N; j++) {
        if(j != i) {
          out.points[i] += rules.homeRunPoint;
          out.points[j] -= rules.homeRunPoint;
        }
      }
    }
  }

  bool isDealer[N];
  int numDealers = dealers(players, isDealer);
  for(int i = 0; i < N; i++) {
    if(isDealer[i])
      out.dealerCost[i] = rules.dealerCost / numDealers;
    out.result[i] = out.points[i] - out.dealerCost[i];
  }
}

double Scoring::resultOfPlayer0(const ArrangementInfo* const players[N], const Rules& rules) {
  const ArrangementInfo& me = *players[0];
  double r = 0.0;
  int numIScoop = 0;
  for(int j = 1; j < N; j++) {
    const ArrangementInfo& opp = *players[j];
    r += pairPoints(me, opp, rules);
    if(scoops(me, opp)) {
      numIScoop++;
    }
    else if(scoops(opp, me)) {
      // Opponent j's home run needs j to scoop the other two opponents too.
      bool homeRun = true;
      for(int k = 1; k < N; k++)
        if(k != j && !scoops(opp, *players[k]))
          homeRun = false;
      if(homeRun)
        r -= rules.homeRunPoint;
    }
  }
  if(numIScoop == N - 1)
    r += (N - 1) * rules.homeRunPoint;

  if(rules.dealerCost > 0.0 && me.valid) {
    bool isDealer[N];
    int numDealers = dealers(players, isDealer);
    if(isDealer[0])
      r -= rules.dealerCost / numDealers;
  }
  return r;
}

Scoring::Opponents::Opponents(const ArrangementInfo* const o[N - 1]) {
  for(int t = 0; t < NUM_OPP_ROYALTIES; t++)
    oppRoyalties[t] = 0.0f;
  anyValid = false;
  maxBack = 0;
  numAtMaxBack = 0;
  for(int j = 0; j < N - 1; j++) {
    opps[j] = o[j];
    scoopsOtherOpps[j] = true;
    for(int k = 0; k < N - 1; k++)
      if(k != j && !scoops(*o[j], *o[k]))
        scoopsOtherOpps[j] = false;
    if(o[j]->valid) {
      for(int r = 0; r < NUM_ROWS; r++) {
        int t = Royalty::typeOf(r, o[j]->keys[r]);
        if(t >= 0)
          oppRoyalties[t] += 1.0f;
      }
      if(o[j]->dragon)
        oppRoyalties[Royalty::NUM] += 1.0f;
      if(!anyValid || o[j]->keys[BACK] > maxBack) {
        maxBack = o[j]->keys[BACK];
        numAtMaxBack = 0;
      }
      anyValid = true;
      if(o[j]->keys[BACK] == maxBack)
        numAtMaxBack++;
    }
  }
}

double Scoring::Opponents::resultOf(const ArrangementInfo& me, const Rules& rules) const {
  double r = 0.0;
  int numIScoop = 0;
  for(int j = 0; j < N - 1; j++) {
    const ArrangementInfo& opp = *opps[j];
    r += pairPoints(me, opp, rules);
    if(scoops(me, opp))
      numIScoop++;
    else if(scoopsOtherOpps[j] && scoops(opp, me))
      r -= rules.homeRunPoint;
  }
  if(!me.valid)
    return r;
  if(numIScoop == N - 1)
    r += (N - 1) * rules.homeRunPoint;
  if(rules.dealerCost > 0.0) {
    if(!anyValid || me.keys[BACK] > maxBack)
      r -= rules.dealerCost;
    else if(me.keys[BACK] == maxBack)
      r -= rules.dealerCost / (numAtMaxBack + 1);
  }
  return r;
}

const char* const Scoring::TARGET_NAMES[Scoring::NUM_TARGETS] = {
  "scoop", "scooped", "home_run", "opp_home_run", "dealer_share",
  "row_win_front", "row_win_middle", "row_win_back", "row_lose_front", "row_lose_middle", "row_lose_back",
  "royalty_lost_front_trips", "royalty_lost_middle_full_house", "royalty_lost_middle_quads",
  "royalty_lost_middle_straight_flush", "royalty_lost_back_quads", "royalty_lost_back_straight_flush",
};

void Scoring::Opponents::targetsOf(const ArrangementInfo& me, uint8_t out[]) const {
  assert(me.valid);
  const int unitsPerOpp = TARGET_UNITS / (N - 1);
  for(int t = 0; t < NUM_TARGETS; t++)
    out[t] = 0;
  int numIScoop = 0;
  for(int j = 0; j < N - 1; j++) {
    const ArrangementInfo& opp = *opps[j];
    if(scoops(me, opp)) {
      numIScoop++;
      out[SCOOP] += unitsPerOpp;
    }
    else if(scoops(opp, me)) {
      out[SCOOPED] += unitsPerOpp;
      if(scoopsOtherOpps[j])
        out[OPP_HOME_RUN] += unitsPerOpp;
    }
    for(int r = 0; r < NUM_ROWS; r++) {
      // A fouled opponent loses every comparison; this only matters for the fouls variant.
      if(!opp.valid || me.keys[r] > opp.keys[r]) {
        out[ROW_WIN_FRONT + r] += unitsPerOpp;
      }
      else if(me.keys[r] < opp.keys[r]) {
        out[ROW_LOSE_FRONT + r] += unitsPerOpp;
        int t = Royalty::typeOf(r, opp.keys[r]);
        if(t >= 0)
          out[ROYALTY_LOST_FRONT_TRIPS + t] += unitsPerOpp;
      }
    }
  }
  if(numIScoop == N - 1)
    out[HOME_RUN] = TARGET_UNITS;
  if(!anyValid || me.keys[BACK] > maxBack)
    out[DEALER_SHARE] = TARGET_UNITS;
  else if(me.keys[BACK] == maxBack)
    out[DEALER_SHARE] = (uint8_t)(TARGET_UNITS / (numAtMaxBack + 1));
}

double Scoring::compose(const Rules& rules, const ArrangementInfo& me, const float p[NUM_TARGETS],
                        const float oppRoyalties[NUM_OPP_ROYALTIES]) {
  const double n = N - 1;
  double q = n * rules.scoopPoint * (p[SCOOP] - p[SCOOPED]) + n * rules.homeRunPoint * (p[HOME_RUN] - p[OPP_HOME_RUN])
    - rules.dealerCost * p[DEALER_SHARE];
  for(int r = 0; r < NUM_ROWS; r++) {
    q += n * rules.rowPoint * (p[ROW_WIN_FRONT + r] - p[ROW_LOSE_FRONT + r]);
    q += n * rules.royaltyOf(r, me.keys[r]) * (rules.royaltyOnWin ? p[ROW_WIN_FRONT + r] : 1.0);
  }
  for(int t = 0; t < Royalty::NUM; t++)
    q -= rules.royalty[t] * (rules.royaltyOnWin ? n * p[ROYALTY_LOST_FRONT_TRIPS + t] : oppRoyalties[t]);
  if(me.dragon)
    q += n * rules.dragonPoint;
  q -= rules.dragonPoint * oppRoyalties[Royalty::NUM];
  return q;
}
