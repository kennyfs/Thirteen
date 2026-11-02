#include "../command/commands.h"

#include "../players/netplayer.h"

#include <tclap/CmdLine.h>

#include <iostream>

using namespace std;

// Prints a model's Q for the undominated candidates of a hand, as "front_mask middle_mask back_mask front_key
// middle_key back_key q" lines after a "cards ..." line, for python/check_onnx.py to compare with PyTorch.
int Commands::netQ(vector<string> args) {
  string modelPath;
  string handStr;
  double dealerCost;
  string rulesSpec;
  int pop;
  string seed;
  try {
    TCLAP::CmdLine cmd("Prints a model's Q for every undominated candidate of a hand.", ' ', "0");
    TCLAP::ValueArg<string> modelArg("m", "model", "ONNX model", true, "", "FILE");
    TCLAP::ValueArg<string> handArg("", "hand", "13 cards (default: random)", false, "", "CARDS");
    TCLAP::ValueArg<double> costArg("c", "dealer-cost", "Dealer cost (default 0.5)", false, 0.5, "C");
    TCLAP::ValueArg<string> rulesArg("", "rules", "Scoring rules: a preset (family, family_mid2, taiwan, pagat) "
                                     "and name=value overrides, e.g. taiwan,scoop=1 (default family; c comes from -c)",
                                     false, "family", "SPEC");
    TCLAP::ValueArg<int> popArg("", "pop", "Population id (default 0)", false, 0, "N");
    TCLAP::ValueArg<string> seedArg("s", "seed", "Seed for a random hand", false, "netq", "SEED");
    cmd.add(modelArg); cmd.add(handArg); cmd.add(costArg); cmd.add(rulesArg); cmd.add(popArg); cmd.add(seedArg);
    cmd.parse(args);
    modelPath = modelArg.getValue();
    handStr = handArg.getValue();
    dealerCost = costArg.getValue();
    rulesSpec = rulesArg.getValue();
    pop = popArg.getValue();
    seed = seedArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }
  CardSet hand;
  if(handStr.empty()) {
    Rand rand(seed);
    CardSet hands[Cards::NUM_PLAYERS];
    Cards::dealAll(rand, hands);
    hand = hands[0];
  }
  else
    hand = Cards::parseCardSet(handStr);
  Rules rules = Rules::parse(rulesSpec);
  rules.dealerCost = dealerCost;
  rules.check();
  NetModel model(modelPath);
  vector<ArrangementInfo> cands;
  HandTable(hand, rules).undominated(cands);
  vector<float> q;
  model.evaluate(hand, rules, pop, cands, q);
  cout << "cards";
  for(CardSet s = hand; s; s &= s - 1)
    cout << " " << (int)Cards::lowest(s);
  float features[Rules::NUM_FEATURES];
  rules.toFeatures(features);
  cout << "\nrules";
  for(float f: features)
    cout << " " << f;
  cout << "\npop " << pop << "\n";
  for(size_t i = 0; i < cands.size(); i++) {
    for(int r = 0; r < NUM_ROWS; r++)
      cout << Arrangements::localMask(hand, cands[i].arr.rows[r]) << " ";
    for(int r = 0; r < NUM_ROWS; r++)
      cout << cands[i].keys[r] << " ";
    cout << Global::strprintf("%.7g", q[i]) << "\n";
  }
  return 0;
}
