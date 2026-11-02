#include "../command/guiengine.h"

#include "../game/scoring.h"
#include "../players/montecarlo.h"
#include "../players/netplayer.h"

using namespace std;
using json = nlohmann::json;

namespace {
  CardSet handOf(const json& cards) {
    CardSet s = 0;
    for(const json& c: cards) {
      int id = c.get<int>();
      if(id < 0 || id >= Cards::NUM_CARDS || (s & Cards::bit((Card)id)))
        throw StringError("Bad or repeated card id " + Global::intToString(id));
      s |= Cards::bit((Card)id);
    }
    return s;
  }

  json cardsOf(CardSet s) {
    json out = json::array();
    for(; s; s &= s - 1)
      out.push_back((int)Cards::lowest(s));
    return out;
  }

  ArrangementInfo arrangementOf(const json& rows, CardSet hand, const Rules& rules) {
    if(!rows.is_array() || rows.size() != NUM_ROWS)
      throw StringError("An arrangement is [front, middle, back]");
    Arrangement arr;
    for(int r = 0; r < NUM_ROWS; r++) {
      arr.rows[r] = handOf(rows[r]);
      if(Cards::count(arr.rows[r]) != ROW_SIZES[r])
        throw StringError("Wrong number of cards in a row");
    }
    if(arr.hand() != hand || Cards::count(hand) != Cards::HAND_SIZE)
      throw StringError("The arrangement does not use the hand's 13 cards");
    return Arrangements::evaluate(arr, rules);
  }

  json toJson(const Arrangement& arr) {
    return json::array({cardsOf(arr.rows[FRONT]), cardsOf(arr.rows[MIDDLE]), cardsOf(arr.rows[BACK])});
  }

  // Each row's royalty under the rules (0 for none, and for every row of a fouled arrangement).
  json royaltiesOf(const ArrangementInfo& info, const Rules& rules) {
    json out = json::array();
    for(int r = 0; r < NUM_ROWS; r++)
      out.push_back(info.valid ? rules.royaltyOf(r, info.keys[r]) : 0.0);
    return out;
  }

  json infoJson(const ArrangementInfo& info, const Rules& rules) {
    json keys = json::array(), cats = json::array();
    for(int r = 0; r < NUM_ROWS; r++) {
      keys.push_back(info.keys[r]);
      cats.push_back((int)HandEval::categoryOf(info.keys[r]));
    }
    return {{"keys", keys}, {"categories", cats}, {"valid", info.valid}, {"bonus", info.bonus},
            {"royalties", royaltiesOf(info, rules)}, {"dragon", info.dragon}};
  }

  CardSet handArg(const json& req) {
    CardSet hand = handOf(req.at("hand"));
    if(Cards::count(hand) != Cards::HAND_SIZE)
      throw StringError("A hand has 13 cards");
    return hand;
  }

  double costArg(const json& req) {
    double c = req.value("c", 0.0);
    if(!(c >= 0.0 && c <= 1.0))
      throw StringError("c must be in [0, 1]");
    return c;
  }

  Rules rulesArg(const json& req) {
    Rules rules = Rules::parse(req.value("rules", string("family")));
    rules.dealerCost = costArg(req);
    return rules;
  }

  Rand randArg(const json& req) {
    string seed = req.value("seed", string());
    return seed.empty() ? Rand() : Rand(seed);
  }

}

json GuiEngine::handle(const json& req) {
  const string cmd = req.at("cmd").get<string>();
  if(cmd == "deal") {
    Rand rand = randArg(req);
    CardSet hands[Cards::NUM_PLAYERS];
    Cards::dealAll(rand, hands);
    json out = json::array();
    for(CardSet h: hands)
      out.push_back(cardsOf(h));
    return {{"hands", out}};
  }
  if(cmd == "arrange") {
    CardSet hand = handArg(req);
    DecisionContext ctx;
    ctx.rules = rulesArg(req);
    Rand rand = randArg(req);
    ArrangementInfo info = player(req.at("player").get<string>())->arrange(hand, ctx, rand);
    return {{"arrangement", toJson(info.arr)}, {"info", infoJson(info, ctx.rules)}};
  }
  if(cmd == "analyze")
    return analyze(req);
  if(cmd == "mc") {
    CardSet hand = handArg(req);
    DecisionContext ctx;
    ctx.rules = rulesArg(req);
    Rand rand = randArg(req);
    int samples = req.value("samples", 500);
    if(samples <= 0)
      throw StringError("samples must be positive");
    vector<ArrangementInfo> cands;
    for(const json& a: req.at("cands"))
      cands.push_back(arrangementOf(a, hand, ctx.rules));
    MonteCarloPlayer mc(player(req.value("opp", string("greedy"))), samples);
    vector<CandidateStats> stats = mc.evaluate(hand, cands, ctx, rand);
    json out = json::array();
    for(const ArrangementInfo& cand: cands)
      for(const CandidateStats& s: stats)
        if(s.info.arr == cand.arr) {
          out.push_back({{"mean", s.mean}, {"std_err", s.stdErr}});
          break;
        }
    return {{"stats", out}};
  }
  if(cmd == "score") {
    const json& arrs = req.at("arrangements");
    if(!arrs.is_array() || arrs.size() != Cards::NUM_PLAYERS)
      throw StringError("score needs four arrangements");
    const Rules rules = rulesArg(req);
    ArrangementInfo infos[Cards::NUM_PLAYERS];
    const ArrangementInfo* ptrs[Cards::NUM_PLAYERS];
    json infoOut = json::array();
    for(int p = 0; p < Cards::NUM_PLAYERS; p++) {
      CardSet hand = 0;
      for(const json& row: arrs[p])
        hand |= handOf(row);
      infos[p] = arrangementOf(arrs[p], hand, rules);
      ptrs[p] = &infos[p];
      infoOut.push_back(infoJson(infos[p], rules));
    }
    DealScore score;
    Scoring::score(ptrs, rules, score);
    json scoops = json::array(), pairs = json::array();
    for(int i = 0; i < Cards::NUM_PLAYERS; i++) {
      scoops.push_back(vector<bool>(score.scoops[i], score.scoops[i] + Cards::NUM_PLAYERS));
      // What j pays i, home runs included; each row sums to points[i].
      vector<double> row(Cards::NUM_PLAYERS, 0.0);
      for(int j = 0; j < Cards::NUM_PLAYERS; j++)
        if(j != i)
          row[j] = Scoring::pairPoints(infos[i], infos[j], rules) +
            rules.homeRunPoint * ((score.homeRun[i] ? 1 : 0) - (score.homeRun[j] ? 1 : 0));
      pairs.push_back(row);
    }
    return {
      {"scoops", scoops},
      {"home_run", vector<bool>(score.homeRun, score.homeRun + Cards::NUM_PLAYERS)},
      {"points", vector<double>(score.points, score.points + Cards::NUM_PLAYERS)},
      {"dealer_cost", vector<double>(score.dealerCost, score.dealerCost + Cards::NUM_PLAYERS)},
      {"result", vector<double>(score.result, score.result + Cards::NUM_PLAYERS)},
      {"pairs", pairs},
      {"info", infoOut},
    };
  }
  if(cmd == "rows") {
    const Rules rules = rulesArg(req);
    const json& rows = req.at("rows");
    if(!rows.is_array() || rows.size() != NUM_ROWS)
      throw StringError("rows is [front, middle, back]");
    Arrangement arr;
    bool full = true;
    json keys = json::array(), cats = json::array();
    for(int r = 0; r < NUM_ROWS; r++) {
      arr.rows[r] = handOf(rows[r]);
      if(r > 0 && (arr.rows[r] & (arr.rows[FRONT] | (r > MIDDLE ? arr.rows[MIDDLE] : 0))))
        throw StringError("A card is in two rows");
      int n = Cards::count(arr.rows[r]);
      HandEval::Key key = n != ROW_SIZES[r] ? 0 : r == FRONT ? HandEval::eval3(arr.rows[r])
        : HandEval::withWheel(HandEval::eval5(arr.rows[r]), rules.wheel);
      full = full && key != 0;
      keys.push_back(key);
      cats.push_back((int)HandEval::categoryOf(key));
    }
    json out = {{"keys", keys}, {"categories", cats}};
    if(full) {
      ArrangementInfo info = Arrangements::evaluate(arr, rules);
      out["valid"] = info.valid;
      out["bonus"] = info.bonus;
      out["royalties"] = royaltiesOf(info, rules);
    }
    return out;
  }
  if(cmd == "rules") {
    Rules rules = rulesArg(req);
    float f[Rules::NUM_FEATURES];
    rules.toFeatures(f);
    json values = json::object();
    for(int k = 0; k < Rules::NUM_FEATURES; k++)
      values[Rules::FEATURE_NAMES[k]] = f[k];
    return {{"rules", rules.toString()}, {"values", values}};
  }
  if(cmd == "model")
    return {{"legacy", model(req.at("model").get<string>())->legacy()}};
  throw StringError("Unknown cmd " + cmd);
}

shared_ptr<const Player> GuiEngine::player(const string& spec) {
  auto it = players.find(spec);
  if(it == players.end())
    it = players.emplace(spec, Players::create(spec)).first;
  return it->second;
}

shared_ptr<const NetModel> GuiEngine::model(const string& path) {
  auto it = models.find(path);
  if(it == models.end())
    it = models.emplace(path, make_shared<NetModel>(path)).first;
  return it->second;
}

json GuiEngine::analyze(const json& req) {
  CardSet hand = handArg(req);
  const Rules rules = rulesArg(req);
  vector<ArrangementInfo> cands;
  HandTable(hand, rules).undominated(cands);
  vector<bool> isExtra(cands.size(), false);
  for(const json& a: req.value("extra", json::array())) {
    ArrangementInfo info = arrangementOf(a, hand, rules);
    // The same row hands as a candidate with other kickers or suits (Tc and Td swapped between a pair's kicker and a
    // straight) score the same under any rules: the arrangement takes that candidate's place instead of being added
    // as dominated.
    size_t i = 0;
    while(i < cands.size() && !(cands[i].valid == info.valid && std::equal(info.keys, info.keys + NUM_ROWS, cands[i].keys)))
      i++;
    if(i == cands.size()) {
      cands.push_back(info);
      isExtra.push_back(true);
    }
    else
      cands[i] = info;
  }
  vector<ArrangementInfo> valid;
  Arrangements::enumerate(hand, rules, true, valid);

  const string modelPath = req.value("model", string());
  vector<float> q, probs, oppRoyalties;
  vector<size_t> order(cands.size());
  for(size_t i = 0; i < order.size(); i++)
    order[i] = i;
  if(!modelPath.empty()) {
    model(modelPath)->evaluate(hand, rules, req.value("pop", 0), cands, q, probs, oppRoyalties);
    std::stable_sort(order.begin(), order.end(), [&](size_t a, size_t b) { return q[a] > q[b]; });
  }
  json out = json::array();
  for(size_t i: order) {
    json cand = {{"arrangement", toJson(cands[i].arr)}, {"info", infoJson(cands[i], rules)}, {"extra", (bool)isExtra[i]}};
    if(!modelPath.empty()) {
      cand["q"] = q[i];
      cand["probs"] = vector<float>(probs.begin() + i * Scoring::NUM_TARGETS,
                                    probs.begin() + (i + 1) * Scoring::NUM_TARGETS);
    }
    out.push_back(cand);
  }
  json result = {{"cands", out}, {"num_valid", valid.size()}, {"dragon", Arrangements::isDragon(hand)},
                 {"rules", rules.toString()}};
  if(!modelPath.empty()) {
    double oppBonus = rules.dragonPoint * oppRoyalties[Royalty::NUM];
    for(int t = 0; t < Royalty::NUM && !rules.royaltyOnWin; t++)
      oppBonus += rules.royalty[t] * oppRoyalties[t];
    result["opp_royalty"] = oppRoyalties;
    result["opp_bonus"] = oppBonus;
    result["legacy_model"] = model(modelPath)->legacy();
  }
  return result;
}

string GuiEngine::handleLine(const string& line) {
  json answer;
  try {
    answer = handle(json::parse(line));
  }
  catch(const std::exception& e) {
    answer = {{"error", e.what()}};
  }
  return answer.dump();
}
