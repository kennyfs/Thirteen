#include "../command/commands.h"

#include "../core/timer.h"
#include "../dataio/npz.h"
#include "../dataio/trainingrows.h"
#include "../players/netplayer.h"

#include <tclap/CmdLine.h>

#include <atomic>
#include <fstream>
#include <iostream>
#include <mutex>
#include <thread>

using namespace std;

static constexpr int N = Cards::NUM_PLAYERS;

namespace {
  struct Member {
    double weight;
    string spec;
    shared_ptr<const Player> player;
    // A greedy (temperature 0) net player with the ranker's model and population, whose choice can reuse the
    // ranker's Q.
    bool sameAsRanker = false;
  };

  vector<Member> loadPopulation(const string& path) {
    ifstream in(path);
    if(!in)
      throw StringError("Could not open population file " + path);
    vector<Member> members;
    string line;
    while(getline(in, line)) {
      line = Global::trim(line);
      if(line.empty() || line[0] == '#')
        continue;
      size_t space = line.find_first_of(" \t");
      if(space == string::npos)
        throw StringError("Expected \"WEIGHT SPEC\" in " + path + ": " + line);
      Member m;
      if(!Global::tryStringToDouble(line.substr(0, space), m.weight) || m.weight < 0.0)
        throw StringError("Bad weight in " + path + ": " + line);
      m.spec = Global::trim(line.substr(space));
      if(m.weight > 0.0)
        members.push_back(m);
    }
    if(members.empty())
      throw StringError("Empty population: " + path);
    return members;
  }

  const char* const PRESETS[] = {"family", "family_mid2", "taiwan", "pagat"};

  // Rules for one deal, without the dealer cost: a preset with probability presetProb, otherwise random values around
  // the two families of presets (only scoops count, or every row counts), always passing Rules::check.
  Rules sampleRules(Rand& rand, double presetProb) {
    if(rand.nextBool(presetProb))
      return Rules::parse(PRESETS[rand.nextUInt(4)]);
    auto uniform = [&](double lo, double hi) { return lo + (hi - lo) * rand.nextDouble(); };
    Rules r;
    const bool rows = rand.nextBool(0.5);
    // Royalties and the dragon are smaller where only scoops count.
    const double scale = rows ? 1.0 : 0.3;
    r.rowPoint = rows ? 1.0 : 0.0;
    r.scoopPoint = rows ? uniform(0.0, 4.0) : uniform(0.5, 2.0);
    r.homeRunPoint = uniform(0.0, 2.0 * (NUM_ROWS * r.rowPoint + r.scoopPoint));
    r.royalty[Royalty::FRONT_TRIPS] = uniform(0.0, 4.0 * scale);
    r.royalty[Royalty::MIDDLE_FULL_HOUSE] = rand.nextBool(0.5) ? 0.0 : uniform(0.0, 3.0 * scale);
    r.royalty[Royalty::MIDDLE_QUADS] = uniform(r.royalty[Royalty::MIDDLE_FULL_HOUSE], 10.0 * scale);
    r.royalty[Royalty::MIDDLE_STRAIGHT_FLUSH] = uniform(r.royalty[Royalty::MIDDLE_QUADS], 12.0 * scale);
    r.royalty[Royalty::BACK_QUADS] = uniform(0.0, 6.0 * scale);
    r.royalty[Royalty::BACK_STRAIGHT_FLUSH] = uniform(r.royalty[Royalty::BACK_QUADS], 8.0 * scale);
    r.royaltyOnWin = rand.nextBool(rows ? 0.5 : 0.2);
    r.dragonPoint = uniform(0.0, rows ? 40.0 : 5.0);
    r.strictRows = rand.nextBool(0.3);
    const double w = rand.nextDouble();
    r.wheel = w < 0.7 ? 0 : w < 0.85 ? 1 : 2;
    return r;
  }

  // "P:X,P:X,..." with X a dealer cost or u for uniform in [0, 1].
  struct CostDist {
    vector<double> probs;
    vector<double> values;  // -1 for uniform.
    explicit CostDist(const string& spec) {
      double total = 0.0;
      for(const string& piece: Global::split(spec, ',')) {
        vector<string> pv = Global::split(Global::trim(piece), ':');
        double p, x = -1.0;
        if(pv.size() != 2 || !Global::tryStringToDouble(pv[0], p) || p < 0.0 ||
           (pv[1] != "u" && (!Global::tryStringToDouble(pv[1], x) || x < 0.0 || x > 1.0)))
          throw StringError("Bad --c-dist: " + spec);
        probs.push_back(p);
        values.push_back(x);
        total += p;
      }
      if(probs.empty() || total <= 0.0)
        throw StringError("Bad --c-dist: " + spec);
      for(double& p: probs)
        p /= total;
    }
    double sample(Rand& rand) const {
      double x = rand.nextDouble();
      size_t i = 0;
      while(i + 1 < probs.size() && x >= probs[i])
        x -= probs[i++];
      return values[i] < 0.0 ? rand.nextDouble() : values[i];
    }
  };

  // Rows for one output file, in the compact layout of dataio/trainingrows.h. See python/thirteen/data.py.
  struct Rows {
    int numCands;
    int64_t n = 0;
    vector<uint8_t> deal;
    vector<float> rules;
    vector<uint8_t> pop;
    vector<uint16_t> member;
    vector<uint8_t> count;
    vector<uint32_t> cands;

    explicit Rows(int m) : numCands(m) {}
    void clear() {
      n = 0;
      deal.clear(); rules.clear(); pop.clear(); member.clear(); count.clear(); cands.clear();
    }
    void write(const string& path) const {
      NpzWriter w;
      w.add("deal", {n, TrainingRows::DEAL_BYTES}, deal);
      w.add("rules", {n, Rules::NUM_FEATURES}, rules);
      w.add("pop", {n}, pop);
      w.add("member", {n}, member);
      w.add("count", {n}, count);
      w.add("cands", {n, (int64_t)numCands}, cands);
      w.write(path);
    }
  };
}

int Commands::selfplay(vector<string> args) {
  string popPath;
  int popId;
  int64_t numDeals;
  string outDir;
  int rowsPerFile;
  int numCands;
  int topK;
  string rankerSpec;
  string cDistStr;
  string rulesSpec;
  double presetProb;
  int numThreads;
  string seed;
  try {
    TCLAP::CmdLine cmd(
      "Generates training data. For each deal, every seat's arrangement comes from a player drawn from the population "
      "(by weight). Then for each seat, up to --cands undominated candidates of its hand are scored exactly against "
      "the other three seats' actual arrangements, which gives the training targets of every candidate, whatever the "
      "seat itself chose. The data stores the deal and the candidates; the trainer recomputes the targets.",
      ' ', "0"
    );
    TCLAP::ValueArg<string> popArg("", "pop", "Population file: lines of \"WEIGHT SPEC\"", true, "", "FILE");
    TCLAP::ValueArg<int> popIdArg("", "pop-id", "Population id written to the data (default 0)", false, 0, "N");
    TCLAP::ValueArg<int64_t> dealsArg("n", "deals", "Deals (4 rows each)", true, 0, "N");
    TCLAP::ValueArg<string> outArg("o", "out", "Output directory (must exist)", true, "", "DIR");
    TCLAP::ValueArg<int> rowsArg("", "rows-per-file", "Rows per .npz file (default 20000)", false, 20000, "N");
    TCLAP::ValueArg<int> candsArg("", "cands", "Candidates per row (default 32)", false, 32, "N");
    TCLAP::ValueArg<int> topkArg("", "topk", "Of those, the ranker's best (default 8)", false, 8, "N");
    TCLAP::ValueArg<string> rankerArg("", "ranker", "A net player spec that ranks candidates; without it, the "
                                      "candidates are a uniform sample", false, "", "SPEC");
    TCLAP::ValueArg<string> cDistArg("", "c-dist", "Distribution of the dealer cost: PROB:C items, C a value or u "
                                     "for uniform in [0, 1] (default \"0.7:0,0.1:0.5,0.1:1,0.1:u\")",
                                     false, "0.7:0,0.1:0.5,0.1:1,0.1:u", "DIST");
    TCLAP::ValueArg<string> rulesArg("", "rules", "Scoring rules of every deal (a spec as in match; its c is replaced "
                                     "by the drawn c), or \"mix\" to draw them per deal (default mix)",
                                     false, "mix", "SPEC");
    TCLAP::ValueArg<double> presetProbArg("", "preset-prob", "With --rules mix, the probability of a preset (family, "
                                          "family_mid2, taiwan, pagat) rather than random values (default 0.5)",
                                          false, 0.5, "P");
    TCLAP::ValueArg<int> threadsArg("t", "threads", "Threads (default: hardware concurrency)", false, 0, "N");
    TCLAP::ValueArg<string> seedArg("s", "seed", "Seed (default: random)", false, "", "SEED");
    cmd.add(popArg); cmd.add(popIdArg); cmd.add(dealsArg); cmd.add(outArg); cmd.add(rowsArg); cmd.add(candsArg);
    cmd.add(topkArg); cmd.add(rankerArg); cmd.add(cDistArg); cmd.add(rulesArg);
    cmd.add(presetProbArg); cmd.add(threadsArg);
    cmd.add(seedArg);
    cmd.parse(args);
    popPath = popArg.getValue();
    popId = popIdArg.getValue();
    numDeals = dealsArg.getValue();
    outDir = outArg.getValue();
    rowsPerFile = rowsArg.getValue();
    numCands = candsArg.getValue();
    topK = topkArg.getValue();
    rankerSpec = rankerArg.getValue();
    cDistStr = cDistArg.getValue();
    rulesSpec = rulesArg.getValue();
    presetProb = presetProbArg.getValue();
    numThreads = threadsArg.getValue();
    seed = seedArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }
  if(numCands <= 0 || numCands > 255 || topK < 0 || topK > numCands || rowsPerFile <= 0 || numDeals <= 0)
    throw StringError("Bad --cands, --topk, --rows-per-file or --deals");
  if(popId < 0 || popId > 255)
    throw StringError("Bad --pop-id");
  const CostDist costDist(cDistStr);
  const bool mixRules = rulesSpec == "mix";
  const Rules fixedRules = mixRules ? Rules() : Rules::parse(rulesSpec);
  if(numThreads <= 0)
    numThreads = std::max(1u, std::thread::hardware_concurrency());
  if(seed.empty())
    seed = Global::uint64ToHexString(Rand().nextUInt64());

  vector<Member> members = loadPopulation(popPath);
  double totalWeight = 0.0;
  for(Member& m: members) {
    m.player = Players::create(m.spec);
    totalWeight += m.weight;
  }
  shared_ptr<const NetPlayer> ranker;
  if(!rankerSpec.empty()) {
    ranker = dynamic_pointer_cast<const NetPlayer>(Players::create(rankerSpec));
    if(ranker == nullptr)
      throw StringError("--ranker must be a net player");
  }

  for(Member& m: members) {
    shared_ptr<const NetPlayer> net = dynamic_pointer_cast<const NetPlayer>(m.player);
    m.sameAsRanker = ranker != nullptr && net != nullptr && &net->model() == &ranker->model() &&
      net->pop() == ranker->pop() && net->name().find(",temp=") == string::npos;
  }
  cerr << "Population:" << endl;
  for(const Member& m: members)
    cerr << "  " << m.weight << "  " << m.player->name() << endl;

  std::atomic<int64_t> nextDeal(0);
  std::atomic<int64_t> rowsWritten(0);
  std::mutex logMutex;
  ClockTimer timer;

  auto worker = [&](int threadIdx) {
    Rows rows(numCands);
    int fileIdx = 0;
    auto flush = [&]() {
      if(rows.n == 0)
        return;
      string path = outDir + "/" + seed + "-t" + Global::intToString(threadIdx) + "-" +
        Global::intToString(fileIdx++) + ".npz";
      rows.write(path);
      rowsWritten += rows.n;
      rows.clear();
    };
    vector<ArrangementInfo> seatCands[N];
    vector<float> seatQ[N];
    vector<int> order;
    while(true) {
      int64_t d = nextDeal.fetch_add(1);
      if(d >= numDeals)
        break;
      Rand rand(seed + ":" + Global::int64ToString(d));
      DecisionContext ctx;
      ctx.rules = mixRules ? sampleRules(rand, presetProb) : fixedRules;
      ctx.rules.dealerCost = costDist.sample(rand);
      float ruleFeatures[Rules::NUM_FEATURES];
      ctx.rules.toFeatures(ruleFeatures);
      CardSet hands[N];
      Cards::dealAll(rand, hands);
      // Each seat's undominated candidates and, with a ranker, their Q under the ranker.
      for(int s = 0; s < N; s++) {
        HandTable(hands[s], ctx.rules).undominated(seatCands[s]);
        if(ranker != nullptr && (int)seatCands[s].size() > 1)
          ranker->model().evaluate(hands[s], ctx.rules, ranker->pop(), seatCands[s], seatQ[s]);
        else
          seatQ[s].clear();
      }
      int memberOf[N];
      ArrangementInfo arranged[N];
      for(int s = 0; s < N; s++) {
        double x = rand.nextDouble() * totalWeight;
        int idx = 0;
        while(idx + 1 < (int)members.size() && x >= members[idx].weight) {
          x -= members[idx].weight;
          idx++;
        }
        memberOf[s] = idx;
        if(members[idx].sameAsRanker && !seatQ[s].empty()) {
          // Same choice as NetPlayer::arrange, from the Q already computed.
          size_t best = 0;
          for(size_t i = 1; i < seatQ[s].size(); i++)
            if(seatQ[s][i] > seatQ[s][best])
              best = i;
          arranged[s] = seatCands[s][best];
        }
        else
          arranged[s] = members[idx].player->arrange(hands[s], ctx, rand);
      }

      for(int s = 0; s < N; s++) {
        const vector<ArrangementInfo>& cands = seatCands[s];
        const vector<float>& q = seatQ[s];
        order.resize(cands.size());
        for(size_t i = 0; i < cands.size(); i++)
          order[i] = (int)i;
        int numTaken = (int)std::min(cands.size(), (size_t)numCands);
        if((int)cands.size() > numCands) {
          int numTop = 0;
          if(!q.empty() && topK > 0) {
            std::partial_sort(order.begin(), order.begin() + topK, order.end(),
                              [&](int a, int b) { return q[a] > q[b]; });
            numTop = topK;
          }
          // Fill the rest with a uniform sample of the others.
          for(int i = numTop; i < numCands; i++) {
            int j = i + (int)rand.nextUInt((uint32_t)(order.size() - i));
            std::swap(order[i], order[j]);
          }
        }

        uint8_t deal[TrainingRows::DEAL_BYTES];
        TrainingRows::packDeal(arranged, s, deal);
        rows.deal.insert(rows.deal.end(), deal, deal + TrainingRows::DEAL_BYTES);
        rows.rules.insert(rows.rules.end(), ruleFeatures, ruleFeatures + Rules::NUM_FEATURES);
        rows.pop.push_back((uint8_t)popId);
        rows.member.push_back((uint16_t)memberOf[s]);
        rows.count.push_back((uint8_t)numTaken);
        for(int i = 0; i < numCands; i++)
          rows.cands.push_back(i < numTaken ? TrainingRows::packCandidate(hands[s], cands[order[i]].arr) : 0);
        rows.n++;
        if(rows.n >= rowsPerFile)
          flush();
      }
      if((d + 1) % 2000 == 0) {
        std::lock_guard<std::mutex> lock(logMutex);
        cerr << d + 1 << " / " << numDeals << " deals, " << timer.getSeconds() << " s" << endl;
      }
    }
    flush();
  };

  vector<std::thread> threads;
  for(int t = 0; t < numThreads; t++)
    threads.emplace_back(worker, t);
  for(std::thread& t: threads)
    t.join();
  cerr << "Wrote " << rowsWritten.load() << " rows from " << numDeals << " deals in " << timer.getSeconds() << " s"
       << endl;
  return 0;
}
