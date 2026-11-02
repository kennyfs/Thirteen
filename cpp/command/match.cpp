#include "../command/commands.h"

#include "../core/timer.h"
#include "../players/player.h"

#include <tclap/CmdLine.h>

#include <algorithm>
#include <atomic>
#include <cmath>
#include <iostream>
#include <mutex>
#include <thread>

using namespace std;

static constexpr int N = Cards::NUM_PLAYERS;

namespace {
  struct PlayerStats {
    // Over deals: each deal contributes the player's mean result over the 24 seatings.
    int64_t numDeals = 0;
    double dealSum = 0.0;
    double dealSumSq = 0.0;
    // Over seatings.
    int64_t numGames = 0;
    double pointsSum = 0.0;
    double dealerCostSum = 0.0;
    int64_t numDealer = 0;
    int64_t numScoopsMade = 0;
    int64_t numScoopsTaken = 0;
    int64_t numHomeRuns = 0;
    int64_t numBonus = 0;
    int64_t numFouls = 0;

    void add(const PlayerStats& o) {
      numDeals += o.numDeals;
      dealSum += o.dealSum;
      dealSumSq += o.dealSumSq;
      numGames += o.numGames;
      pointsSum += o.pointsSum;
      dealerCostSum += o.dealerCostSum;
      numDealer += o.numDealer;
      numScoopsMade += o.numScoopsMade;
      numScoopsTaken += o.numScoopsTaken;
      numHomeRuns += o.numHomeRuns;
      numBonus += o.numBonus;
      numFouls += o.numFouls;
    }
  };
}

int Commands::match(vector<string> args) {
  vector<string> specs;
  int numDeals;
  double dealerCost;
  string rulesSpec;
  int numThreads;
  string seed;
  try {
    TCLAP::CmdLine cmd(
      "Plays duplicate deals. Each deal is played 24 times, once for every way of giving the four hands to the four "
      "players, so every player plays every hand against the same cards. With more than four players, each deal "
      "picks four at random.", ' ', "0"
    );
    TCLAP::MultiArg<string> playerArg("p", "player", "Player spec, e.g. greedy, indep, mc:samples=500,opp=greedy. "
                                      "Give at least four; repeat a spec to seat it more than once.", true, "SPEC");
    TCLAP::ValueArg<int> dealsArg("n", "deals", "Number of deals (default 1000)", false, 1000, "N");
    TCLAP::ValueArg<double> costArg("c", "dealer-cost", "Dealer cost c in [0, 1] (default 0)", false, 0.0, "C");
    TCLAP::ValueArg<string> rulesArg("", "rules", "Scoring rules: a preset (family, family_mid2, taiwan, pagat) "
                                     "and name=value overrides, e.g. taiwan,scoop=1 (default family; c comes from -c)",
                                     false, "family", "SPEC");
    TCLAP::ValueArg<int> threadsArg("t", "threads", "Threads (default: hardware concurrency)", false, 0, "N");
    TCLAP::ValueArg<string> seedArg("s", "seed", "Seed (default: random)", false, "", "SEED");
    cmd.add(playerArg);
    cmd.add(dealsArg);
    cmd.add(costArg);
    cmd.add(rulesArg);
    cmd.add(threadsArg);
    cmd.add(seedArg);
    cmd.parse(args);
    specs = playerArg.getValue();
    numDeals = dealsArg.getValue();
    dealerCost = costArg.getValue();
    rulesSpec = rulesArg.getValue();
    numThreads = threadsArg.getValue();
    seed = seedArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }
  if(specs.size() < (size_t)N)
    throw StringError("Need at least four players");
  if(dealerCost < 0.0 || dealerCost > 1.0)
    throw StringError("Dealer cost must be in [0, 1]");
  if(numThreads <= 0)
    numThreads = std::max(1u, std::thread::hardware_concurrency());
  if(seed.empty())
    seed = Global::uint64ToHexString(Rand().nextUInt64());

  const int numPlayers = (int)specs.size();
  vector<shared_ptr<const Player>> players;
  for(const string& spec: specs)
    players.push_back(Players::create(spec));

  DecisionContext ctx;
  ctx.rules = Rules::parse(rulesSpec);
  ctx.rules.dealerCost = dealerCost;

  vector<array<int, N>> seatings;
  {
    array<int, N> perm = {0, 1, 2, 3};
    do {
      seatings.push_back(perm);
    } while(std::next_permutation(perm.begin(), perm.end()));
  }

  vector<PlayerStats> totals(numPlayers);
  std::mutex totalsMutex;
  std::atomic<int> nextDeal(0);
  std::atomic<int> dealsDone(0);
  ClockTimer timer;

  auto worker = [&]() {
    vector<PlayerStats> local(numPlayers);
    while(true) {
      int d = nextDeal.fetch_add(1);
      if(d >= numDeals)
        break;
      Rand rand(seed + ":" + Global::intToString(d));
      int chosen[N];
      {
        vector<int> order(numPlayers);
        for(int i = 0; i < numPlayers; i++)
          order[i] = i;
        if(numPlayers > N)
          rand.shuffle(order);
        for(int i = 0; i < N; i++)
          chosen[i] = order[i];
      }
      CardSet hands[N];
      Cards::dealAll(rand, hands);
      // A player's arrangement depends only on its hand, so each (player, hand) is set once per deal.
      ArrangementInfo arranged[N][N];
      for(int i = 0; i < N; i++)
        for(int h = 0; h < N; h++)
          arranged[i][h] = players[chosen[i]]->arrange(hands[h], ctx, rand);

      double dealResult[N] = {0.0, 0.0, 0.0, 0.0};
      for(const array<int, N>& hOf: seatings) {
        const ArrangementInfo* infos[N];
        for(int i = 0; i < N; i++)
          infos[i] = &arranged[i][hOf[i]];
        DealScore score;
        Scoring::score(infos, ctx.rules, score);
        for(int i = 0; i < N; i++) {
          PlayerStats& st = local[chosen[i]];
          dealResult[i] += score.result[i];
          st.numGames++;
          st.pointsSum += score.points[i];
          st.dealerCostSum += score.dealerCost[i];
          st.numDealer += score.dealerCost[i] > 0.0 ? 1 : 0;
          st.numHomeRuns += score.homeRun[i] ? 1 : 0;
          st.numFouls += infos[i]->valid ? 0 : 1;
          st.numBonus += infos[i]->valid ? infos[i]->bonus : 0;
          for(int j = 0; j < N; j++) {
            st.numScoopsMade += score.scoops[i][j] ? 1 : 0;
            st.numScoopsTaken += score.scoops[j][i] ? 1 : 0;
          }
        }
      }
      for(int i = 0; i < N; i++) {
        PlayerStats& st = local[chosen[i]];
        double mean = dealResult[i] / seatings.size();
        st.numDeals++;
        st.dealSum += mean;
        st.dealSumSq += mean * mean;
      }
      int done = dealsDone.fetch_add(1) + 1;
      if(done % 1000 == 0) {
        std::lock_guard<std::mutex> lock(totalsMutex);
        cerr << done << " / " << numDeals << " deals, " << timer.getSeconds() << " s" << endl;
      }
    }
    std::lock_guard<std::mutex> lock(totalsMutex);
    for(int i = 0; i < numPlayers; i++)
      totals[i].add(local[i]);
  };

  vector<std::thread> threads;
  for(int t = 0; t < numThreads; t++)
    threads.emplace_back(worker);
  for(std::thread& t: threads)
    t.join();

  cout << "Seed " << seed << ", " << numDeals << " deals x " << seatings.size() << " seatings, rules "
       << ctx.rules.toString() << ", " << timer.getSeconds() << " s" << endl;
  cout << "Result: mean per game; +- is the standard error over deals. Other columns are per game." << endl;
  cout << Global::strprintf("%-3s %-36s %8s %18s %7s %7s %7s %7s %7s %7s %6s",
                            "#", "Player", "Deals", "Result", "Points", "Dealer", "Scoops", "Scooped",
                            "HomeRun", "Bonus", "Foul") << endl;
  for(int i = 0; i < numPlayers; i++) {
    const PlayerStats& st = totals[i];
    if(st.numDeals == 0) {
      cout << Global::strprintf("%-3d %-36s %8d", i, players[i]->name().c_str(), 0) << endl;
      continue;
    }
    double mean = st.dealSum / st.numDeals;
    double var = st.numDeals > 1 ? (st.dealSumSq - st.dealSum * mean) / (st.numDeals - 1) : 0.0;
    double se = std::sqrt(std::max(var, 0.0) / st.numDeals);
    double g = (double)st.numGames;
    cout << Global::strprintf("%-3d %-36s %8lld %+9.4f +- %.4f %+7.3f %7.3f %7.3f %7.3f %7.4f %7.4f %6.4f",
                              i, players[i]->name().c_str(), (long long)st.numDeals, mean, se,
                              st.pointsSum / g, st.numDealer / g, st.numScoopsMade / g, st.numScoopsTaken / g,
                              st.numHomeRuns / g, st.numBonus / g, st.numFouls / g) << endl;
  }
  return 0;
}
