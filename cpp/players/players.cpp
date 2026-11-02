#include "../players/player.h"
#include "../players/human.h"
#include "../players/montecarlo.h"
#include "../players/netplayer.h"

#include <algorithm>
#include <cmath>
#include <map>
#include <mutex>

using namespace std;

namespace {

  class RandomPlayer final : public Player {
   public:
    string name() const override { return "random"; }
    ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override {
      (void)ctx;
      HandTable table(hand, ctx.rules);
      uint32_t numValid = 0;
      table.forEachSplit(true, [&](uint32_t, uint32_t, uint32_t, HandEval::Key, HandEval::Key, HandEval::Key) {
        numValid++;
      });
      uint32_t target = rand.nextUInt(numValid);
      uint32_t index = 0;
      ArrangementInfo chosen;
      table.forEachSplit(true, [&](uint32_t f, uint32_t m, uint32_t b, HandEval::Key, HandEval::Key, HandEval::Key) {
        if(index++ == target)
          chosen = table.info(f, m, b);
      });
      return chosen;
    }
  };

  // The strongest back row, then middle, then front, then bonus. Close to how many people set a hand.
  class GreedyPlayer final : public Player {
   public:
    string name() const override { return "greedy"; }
    ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override {
      (void)ctx;
      (void)rand;
      return HandTable(hand, ctx.rules).strongestBack();
    }
  };

  // Estimates the expected result as if each opponent's rows were independent draws from the row-hand distributions
  // of greedy players, and independent of each other and of this player's cards.
  class IndependentRowsPlayer final : public Player {
   public:
    explicit IndependentRowsPlayer(int numHands): numTableHands(numHands) {
      Rand rand("indep-table");
      GreedyPlayer greedy;
      DecisionContext ctx;
      vector<ArrangementInfo> sampled;
      while((int)sampled.size() < numHands) {
        CardSet hands[Cards::NUM_PLAYERS];
        Cards::dealAll(rand, hands);
        for(int p = 0; p < Cards::NUM_PLAYERS && (int)sampled.size() < numHands; p++)
          sampled.push_back(greedy.arrange(hands[p], ctx, rand));
      }
      for(int r = 0; r < NUM_ROWS; r++) {
        for(const ArrangementInfo& info: sampled)
          rowKeys[r].push_back(info.keys[r]);
        std::sort(rowKeys[r].begin(), rowKeys[r].end());
      }
      // How often one greedy hand scoops another, from disjoint pairs.
      int numPairs = 0;
      int numScoops = 0;
      for(size_t i = 0; i + 1 < sampled.size(); i += 2) {
        numPairs += 2;
        numScoops += Scoring::scoops(sampled[i], sampled[i + 1]) ? 1 : 0;
        numScoops += Scoring::scoops(sampled[i + 1], sampled[i]) ? 1 : 0;
      }
      scoopRate = numPairs > 0 ? (double)numScoops / numPairs : 0.0;
    }

    string name() const override { return "indep:hands=" + Global::intToString(numTableHands); }

    ArrangementInfo arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const override {
      (void)rand;
      HandTable table(hand, ctx.rules);
      // Each row hand's chance to beat, and to lose to, an opponent's row, by local mask. Middle and back use
      // different distributions for the same five cards.
      static thread_local float win[NUM_ROWS][1 << Cards::HAND_SIZE];
      static thread_local float lose[NUM_ROWS][1 << Cards::HAND_SIZE];
      for(uint16_t m: HandTable::threeMasks()) {
        win[FRONT][m] = (float)fractionBelow(FRONT, table.key(m));
        lose[FRONT][m] = (float)fractionAbove(FRONT, table.key(m));
      }
      for(uint16_t m: HandTable::fiveMasks()) {
        for(int r = MIDDLE; r <= BACK; r++) {
          win[r][m] = (float)fractionBelow(r, table.key(m));
          lose[r][m] = (float)fractionAbove(r, table.key(m));
        }
      }
      const int numOpps = Cards::NUM_PLAYERS - 1;
      const Rules& rules = ctx.rules;
      // Chance that no opponent's back beats this back.
      static thread_local float notBeaten[1 << Cards::HAND_SIZE];
      for(uint16_t m: HandTable::fiveMasks())
        notBeaten[m] = (float)std::pow(1.0 - lose[BACK][m], numOpps);
      // Per row hand, what its rows and royalties are worth against one opponent: the row point, my royalty (always,
      // or when the row wins), and with royaltyOnWin the opponent's royalties when their row beats mine. Royalties
      // paid whatever the rows do are the same for every split as far as the opponents go, so they are left out.
      static thread_local float rowValue[NUM_ROWS][1 << Cards::HAND_SIZE];
      auto fillRow = [&](int r, uint16_t m) {
        const HandEval::Key key = table.key(m);
        const double mine = rules.royaltyOf(r, key);
        double v = rules.rowPoint * (win[r][m] - lose[r][m]) + mine * (rules.royaltyOnWin ? win[r][m] : 1.0);
        if(rules.royaltyOnWin)
          for(int t = 0; t < Royalty::NUM; t++)
            if(Royalty::ROW_OF[t] == r && rules.royalty[t] > 0.0)
              v -= rules.royalty[t] * fractionAboveInType(r, key, t);
        rowValue[r][m] = (float)v;
      };
      for(uint16_t m: HandTable::threeMasks())
        fillRow(FRONT, m);
      for(uint16_t m: HandTable::fiveMasks()) {
        fillRow(MIDDLE, m);
        fillRow(BACK, m);
      }
      const double oppHomeRunFactor = numOpps * std::pow(scoopRate, numOpps - 1);
      bool found = false;
      double bestValue = 0.0;
      uint32_t best[NUM_ROWS] = {0, 0, 0};
      table.forEachSplit(true, [&](uint32_t f, uint32_t m, uint32_t b, HandEval::Key kf, HandEval::Key km,
                                   HandEval::Key kb) {
        double pScoop = (double)win[FRONT][f] * win[MIDDLE][m] * win[BACK][b];
        double pScooped = (double)lose[FRONT][f] * lose[MIDDLE][m] * lose[BACK][b];
        (void)kf;
        (void)km;
        (void)kb;
        // My scoops and home run, an opponent's home run that includes me, rows and royalties, and the dealer cost.
        // The dragon is the same for every split, so it is left out.
        double value = numOpps * rules.scoopPoint * (pScoop - pScooped)
          + rules.homeRunPoint * (numOpps * pScoop * pScoop * pScoop - oppHomeRunFactor * pScooped)
          + numOpps * ((double)rowValue[FRONT][f] + rowValue[MIDDLE][m] + rowValue[BACK][b])
          - rules.dealerCost * notBeaten[b];
        if(!found || value > bestValue) {
          found = true;
          bestValue = value;
          best[FRONT] = f;
          best[MIDDLE] = m;
          best[BACK] = b;
        }
      });
      return table.info(best[FRONT], best[MIDDLE], best[BACK]);
    }

   private:
    int numTableHands;
    vector<HandEval::Key> rowKeys[NUM_ROWS];
    double scoopRate;

    double fractionBelow(int row, HandEval::Key key) const {
      const vector<HandEval::Key>& v = rowKeys[row];
      return (double)(std::lower_bound(v.begin(), v.end(), key) - v.begin()) / v.size();
    }
    double fractionAbove(int row, HandEval::Key key) const {
      const vector<HandEval::Key>& v = rowKeys[row];
      return (double)(v.end() - std::upper_bound(v.begin(), v.end(), key)) / v.size();
    }
    // The fraction of row hands above `key` that have royalty type t.
    double fractionAboveInType(int row, HandEval::Key key, int t) const {
      const vector<HandEval::Key>& v = rowKeys[row];
      HandEval::Key lo = 0, hi = 0;
      for(uint32_t cat = 1; cat < HandEval::NUM_CATEGORIES; cat++) {
        if(Royalty::typeOf(row, cat << 20) == t) {
          lo = cat << 20;
          hi = (cat + 1) << 20;
        }
      }
      lo = std::max(lo, key + 1);
      if(lo >= hi)
        return 0.0;
      return (double)(std::lower_bound(v.begin(), v.end(), hi) - std::lower_bound(v.begin(), v.end(), lo)) / v.size();
    }
  };

  map<string, string> parseParams(const string& spec, const string& paramsStr) {
    map<string, string> params;
    if(paramsStr.empty())
      return params;
    // "opp=" takes the rest of the spec, so that it can hold a spec with its own parameters.
    string rest = paramsStr;
    size_t oppPos = rest.find("opp=");
    while(oppPos != string::npos && oppPos != 0 && rest[oppPos - 1] != ',')
      oppPos = rest.find("opp=", oppPos + 1);
    if(oppPos != string::npos) {
      params["opp"] = rest.substr(oppPos + 4);
      rest = oppPos == 0 ? "" : rest.substr(0, oppPos - 1);
    }
    if(rest.empty())
      return params;
    for(const string& piece: Global::split(rest, ',')) {
      vector<string> kv = Global::split(piece, '=');
      if(kv.size() != 2)
        throw StringError("Bad player parameter \"" + piece + "\" in: " + spec);
      params[Global::trim(kv[0])] = Global::trim(kv[1]);
    }
    return params;
  }

  int takeInt(map<string, string>& params, const string& key, int defaultValue, const string& spec, int minValue = 1) {
    auto it = params.find(key);
    if(it == params.end())
      return defaultValue;
    int x;
    if(!Global::tryStringToInt(it->second, x) || x < minValue)
      throw StringError("Bad value for " + key + " in: " + spec);
    params.erase(it);
    return x;
  }

  double takeDouble(map<string, string>& params, const string& key, double defaultValue, const string& spec) {
    auto it = params.find(key);
    if(it == params.end())
      return defaultValue;
    double x;
    if(!Global::tryStringToDouble(it->second, x) || x < 0.0)
      throw StringError("Bad value for " + key + " in: " + spec);
    params.erase(it);
    return x;
  }

  string takeString(map<string, string>& params, const string& key, const string& spec) {
    auto it = params.find(key);
    if(it == params.end() || it->second.empty())
      throw StringError("Missing " + key + " in: " + spec);
    string x = it->second;
    params.erase(it);
    return x;
  }

  // Models are shared between players that name the same file.
  shared_ptr<const NetModel> loadModel(const string& path) {
    static std::mutex mutex;
    static map<string, weak_ptr<const NetModel>> cache;
    std::lock_guard<std::mutex> lock(mutex);
    shared_ptr<const NetModel> model = cache[path].lock();
    if(model == nullptr) {
      model = make_shared<NetModel>(path);
      cache[path] = model;
    }
    return model;
  }
}

shared_ptr<const Player> Players::create(const string& spec) {
  string trimmed = Global::trim(spec);
  size_t colon = trimmed.find(':');
  string name = Global::toLower(trimmed.substr(0, colon));
  string paramsStr = colon == string::npos ? "" : trimmed.substr(colon + 1);
  map<string, string> params = parseParams(spec, paramsStr);

  shared_ptr<const Player> player;
  if(name == "random")
    player = make_shared<RandomPlayer>();
  else if(name == "greedy")
    player = make_shared<GreedyPlayer>();
  else if(name == "indep")
    player = make_shared<IndependentRowsPlayer>(takeInt(params, "hands", 4000, spec));
  else if(name == "mc") {
    string oppName = "greedy";
    if(params.count("opp")) {
      oppName = params["opp"];
      params.erase("opp");
    }
    int numSamples = takeInt(params, "samples", 500, spec);
    player = make_shared<MonteCarloPlayer>(create(oppName), numSamples);
  }
  else if(name == "net") {
    string path = takeString(params, "model", spec);
    int pop = takeInt(params, "pop", 0, spec, 0);
    double temp = takeDouble(params, "temp", 0.0, spec);
    player = make_shared<NetPlayer>(loadModel(path), pop, temp);
  }
  else if(name == "human") {
    string path = takeString(params, "weights", spec);
    double temp = takeDouble(params, "temp", 1.0, spec);
    player = make_shared<HumanModelPlayer>(path, temp);
  }
  else
    throw StringError("Unknown player: " + spec);

  if(!params.empty())
    throw StringError("Unknown parameter \"" + params.begin()->first + "\" in: " + spec);
  return player;
}
