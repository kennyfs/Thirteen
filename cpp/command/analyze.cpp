#include "../command/commands.h"

#include "../core/timer.h"
#include "../players/montecarlo.h"

#include <tclap/CmdLine.h>

#include <iostream>

using namespace std;

int Commands::analyze(vector<string> args) {
  string handStr;
  string oppSpec;
  int numSamples;
  double dealerCost;
  string rulesSpec;
  int top;
  string seed;
  vector<string> compareSpecs;
  try {
    TCLAP::CmdLine cmd(
      "Evaluates the undominated valid arrangements of one hand against three opponents of a given type, by Monte "
      "Carlo over the 39 unseen cards, and shows the best ones.", ' ', "0"
    );
    TCLAP::ValueArg<string> handArg("", "hand", "13 cards, e.g. \"As Kd Tc 9h ...\" (default: a random hand)",
                                    false, "", "CARDS");
    TCLAP::ValueArg<string> oppArg("o", "opp", "Opponent player spec (default greedy)", false, "greedy", "SPEC");
    TCLAP::ValueArg<int> samplesArg("n", "samples", "Monte Carlo samples (default 2000)", false, 2000, "N");
    TCLAP::ValueArg<double> costArg("c", "dealer-cost", "Dealer cost c in [0, 1] (default 0)", false, 0.0, "C");
    TCLAP::ValueArg<string> rulesArg("", "rules", "Scoring rules: a preset (family, family_mid2, taiwan, pagat) "
                                     "and name=value overrides, e.g. taiwan,scoop=1 (default family; c comes from -c)",
                                     false, "family", "SPEC");
    TCLAP::ValueArg<int> topArg("k", "top", "Arrangements to show (default 10)", false, 10, "K");
    TCLAP::ValueArg<string> seedArg("s", "seed", "Seed (default: random)", false, "", "SEED");
    TCLAP::MultiArg<string> compareArg("p", "player", "Also show what this player sets, and its value (default: "
                                       "greedy and indep)", false, "SPEC");
    cmd.add(handArg);
    cmd.add(oppArg);
    cmd.add(samplesArg);
    cmd.add(costArg);
    cmd.add(rulesArg);
    cmd.add(topArg);
    cmd.add(seedArg);
    cmd.add(compareArg);
    cmd.parse(args);
    handStr = handArg.getValue();
    oppSpec = oppArg.getValue();
    numSamples = samplesArg.getValue();
    dealerCost = costArg.getValue();
    rulesSpec = rulesArg.getValue();
    top = topArg.getValue();
    seed = seedArg.getValue();
    compareSpecs = compareArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }
  if(dealerCost < 0.0 || dealerCost > 1.0)
    throw StringError("Dealer cost must be in [0, 1]");
  if(numSamples <= 0)
    throw StringError("Samples must be positive");
  if(compareSpecs.empty())
    compareSpecs = {"greedy", "indep"};
  if(seed.empty())
    seed = Global::uint64ToHexString(Rand().nextUInt64());
  Rand rand(seed);

  CardSet hand;
  if(handStr.empty()) {
    CardSet hands[Cards::NUM_PLAYERS];
    Cards::dealAll(rand, hands);
    hand = hands[0];
  }
  else {
    hand = Cards::parseCardSet(handStr);
    if(Cards::count(hand) != Cards::HAND_SIZE)
      throw StringError("A hand has 13 cards, got " + Global::intToString(Cards::count(hand)));
  }

  DecisionContext ctx;
  ctx.rules = Rules::parse(rulesSpec);
  ctx.rules.dealerCost = dealerCost;
  ClockTimer timer;

  vector<ArrangementInfo> valid;
  Arrangements::enumerate(hand, ctx.rules, true, valid);
  vector<ArrangementInfo> candidates = MonteCarloPlayer::candidates(hand, ctx);
  const size_t numUndominated = candidates.size();

  // The compared players' choices, added as candidates if they are dominated.
  vector<ArrangementInfo> choices;
  for(const string& spec: compareSpecs) {
    ArrangementInfo choice = Players::create(spec)->arrange(hand, ctx, rand);
    choices.push_back(choice);
    bool present = false;
    for(const ArrangementInfo& c: candidates)
      if(c.arr == choice.arr)
        present = true;
    if(!present)
      candidates.push_back(choice);
  }

  MonteCarloPlayer mc(Players::create(oppSpec), numSamples);
  vector<CandidateStats> stats = mc.evaluate(hand, candidates, ctx, rand);

  cout << "Seed " << seed << endl;
  cout << "Hand: " << Cards::toString(hand) << (Arrangements::isDragon(hand) ? "  (dragon)" : "") << endl;
  cout << "Splits " << Arrangements::NUM_SPLITS << ", valid " << valid.size() << ", undominated " << numUndominated
       << endl;
  cout << "Opponents " << oppSpec << ", " << numSamples << " samples, rules " << ctx.rules.toString() << ", "
       << timer.getSeconds() << " s" << endl;
  cout << endl;
  for(size_t i = 0; i < stats.size() && (int)i < top; i++)
    cout << Global::strprintf("%3d  %+.4f +- %.4f  ", (int)i + 1, stats[i].mean, stats[i].stdErr)
         << stats[i].info.toString() << endl;
  cout << endl;
  for(size_t p = 0; p < compareSpecs.size(); p++) {
    for(size_t i = 0; i < stats.size(); i++) {
      if(stats[i].info.arr == choices[p].arr) {
        cout << Global::strprintf("%-12s rank %3d  %+.4f (best %+.4f)  ", compareSpecs[p].c_str(), (int)i + 1,
                                  stats[i].mean, stats[0].mean)
             << choices[p].arr.toString() << endl;
        break;
      }
    }
  }
  return 0;
}
