#include "../command/commands.h"

#include "../players/player.h"

#include <nlohmann/json.hpp>
#include <tclap/CmdLine.h>

#include <fstream>
#include <iostream>

using namespace std;
using json = nlohmann::json;

// Writes a player's arrangements of random hands in the format of gui/collect.html, e.g. to test the human model
// pipeline on a known player.
int Commands::record(vector<string> args) {
  string spec;
  int numHands;
  double dealerCost;
  string rulesSpec;
  string outPath;
  string seed;
  try {
    TCLAP::CmdLine cmd("Records a player's arrangements of random hands as JSON lines, like gui/collect.html.", ' ', "0");
    TCLAP::ValueArg<string> playerArg("p", "player", "Player spec", true, "", "SPEC");
    TCLAP::ValueArg<int> handsArg("n", "hands", "Hands (default 100)", false, 100, "N");
    TCLAP::ValueArg<double> costArg("c", "dealer-cost", "Dealer cost (default 0.5)", false, 0.5, "C");
    TCLAP::ValueArg<string> rulesArg("", "rules", "Scoring rules: a preset (family, family_mid2, taiwan, pagat) "
                                     "and name=value overrides, e.g. taiwan,scoop=1 (default family; c comes from -c)",
                                     false, "family", "SPEC");
    TCLAP::ValueArg<string> outArg("o", "out", "Output .jsonl", true, "", "FILE");
    TCLAP::ValueArg<string> seedArg("s", "seed", "Seed (default: random)", false, "", "SEED");
    cmd.add(playerArg); cmd.add(handsArg); cmd.add(costArg); cmd.add(rulesArg); cmd.add(outArg); cmd.add(seedArg);
    cmd.parse(args);
    spec = playerArg.getValue();
    numHands = handsArg.getValue();
    dealerCost = costArg.getValue();
    rulesSpec = rulesArg.getValue();
    outPath = outArg.getValue();
    seed = seedArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }
  if(seed.empty())
    seed = Global::uint64ToHexString(Rand().nextUInt64());
  shared_ptr<const Player> player = Players::create(spec);
  DecisionContext ctx;
  ctx.rules = Rules::parse(rulesSpec);
  ctx.rules.dealerCost = dealerCost;
  Rand rand(seed);
  ofstream out(outPath);
  if(!out)
    throw StringError("Could not open " + outPath);
  for(int i = 0; i < numHands; i++) {
    CardSet hands[Cards::NUM_PLAYERS];
    Cards::dealAll(rand, hands);
    ArrangementInfo info = player->arrange(hands[0], ctx, rand);
    json rec = {
      {"version", 1},
      {"id", seed + "-" + Global::intToString(i)},
      {"hand", Cards::toString(hands[0])},
      {"front", Cards::toString(info.arr.rows[FRONT])},
      {"middle", Cards::toString(info.arr.rows[MIDDLE])},
      {"back", Cards::toString(info.arr.rows[BACK])},
      {"c", dealerCost},
      {"rules", ctx.rules.toString()},
      {"label", player->name()},
    };
    out << rec.dump() << "\n";
  }
  return 0;
}
