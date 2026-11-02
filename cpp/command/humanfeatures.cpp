#include "../command/commands.h"

#include "../dataio/npz.h"
#include "../players/human.h"

#include <nlohmann/json.hpp>
#include <tclap/CmdLine.h>

#include <fstream>
#include <iostream>

using namespace std;
using json = nlohmann::json;

// Reads the records exported by gui/collect.html (JSON lines with "hand", "front", "middle", "back", "c") and writes,
// for each record, the features of every class of valid arrangements of the hand and which class was chosen.
int Commands::humanFeatures(vector<string> args) {
  vector<string> inPaths;
  string outPath;
  try {
    TCLAP::CmdLine cmd("Turns recorded human arrangements into features for python/train_human.py.", ' ', "0");
    TCLAP::MultiArg<string> inArg("i", "in", "JSON lines file from the GUI (repeatable)", true, "FILE");
    TCLAP::ValueArg<string> outArg("o", "out", "Output .npz", true, "", "FILE");
    cmd.add(inArg);
    cmd.add(outArg);
    cmd.parse(args);
    inPaths = inArg.getValue();
    outPath = outArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }

  vector<float> feats;
  vector<int32_t> counts;
  vector<int64_t> offsets = {0};
  vector<int64_t> chosen;
  vector<float> dealerCosts;
  int64_t numSkipped = 0;
  vector<HumanFeatures::SplitClass> classes;

  for(const string& inPath: inPaths) {
    ifstream in(inPath);
    if(!in)
      throw StringError("Could not open " + inPath);
    string line;
    int lineNum = 0;
    while(getline(in, line)) {
      lineNum++;
      if(Global::trim(line).empty())
        continue;
      string where = inPath + ":" + Global::intToString(lineNum);
      try {
        json rec = json::parse(line);
        CardSet hand = Cards::parseCardSet(rec.at("hand").get<string>());
        Arrangement arr = Arrangements::parse(rec.at("front").get<string>() + " | " + rec.at("middle").get<string>() +
                                              " | " + rec.at("back").get<string>());
        double c = rec.value("c", 0.0);
        if(Cards::count(hand) != Cards::HAND_SIZE || arr.hand() != hand)
          throw StringError("rows do not match the hand");
        ArrangementInfo info = Arrangements::evaluate(arr, Rules());
        if(!info.valid)
          throw StringError("fouled arrangement");

        HumanFeatures::classes(HandTable(hand, Rules()), classes);
        int64_t chosenIdx = -1;
        for(size_t i = 0; i < classes.size(); i++) {
          const HumanFeatures::SplitClass& sc = classes[i];
          if(sc.keys[FRONT] == info.keys[FRONT] && sc.keys[MIDDLE] == info.keys[MIDDLE] &&
             sc.keys[BACK] == info.keys[BACK])
            chosenIdx = (int64_t)i;
          float f[HumanFeatures::NUM];
          HumanFeatures::ofSplit(sc.keys[FRONT], sc.keys[MIDDLE], sc.keys[BACK], c, f);
          feats.insert(feats.end(), f, f + HumanFeatures::NUM);
          counts.push_back(sc.count);
        }
        assert(chosenIdx >= 0);
        chosen.push_back(chosenIdx);
        offsets.push_back((int64_t)counts.size());
        dealerCosts.push_back((float)c);
      }
      catch(const std::exception& e) {
        cerr << "Skipping " << where << ": " << e.what() << endl;
        numSkipped++;
        // Drop any classes added for this record.
        feats.resize(offsets.back() * HumanFeatures::NUM);
        counts.resize(offsets.back());
      }
    }
  }

  const int64_t numRecords = (int64_t)chosen.size();
  const int64_t numClasses = (int64_t)counts.size();
  NpzWriter w;
  w.add("feats", {numClasses, HumanFeatures::NUM}, feats);
  w.add("counts", {numClasses}, counts);
  w.add("offsets", {numRecords + 1}, offsets);
  w.add("chosen", {numRecords}, chosen);
  w.add("c", {numRecords}, dealerCosts);
  w.write(outPath);
  ofstream names(outPath + ".features.txt");
  for(int i = 0; i < HumanFeatures::NUM; i++)
    names << HumanFeatures::NAMES[i] << "\n";
  cerr << "Wrote " << numRecords << " records (" << numClasses << " arrangement classes) to " << outPath;
  if(numSkipped > 0)
    cerr << ", skipped " << numSkipped;
  cerr << endl;
  return 0;
}
