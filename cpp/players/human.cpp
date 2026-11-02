#include "../players/human.h"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <map>

using namespace std;
using namespace HandEval;

namespace {
  // Sorted distinct keys with the number of hands below each and equal to each.
  struct StrengthTable {
    vector<Key> keys;
    vector<double> below;
    vector<double> equal;
    double total = 0.0;

    void build(const vector<Key>& all) {
      vector<Key> sorted = all;
      std::sort(sorted.begin(), sorted.end());
      total = (double)sorted.size();
      size_t i = 0;
      while(i < sorted.size()) {
        size_t j = i;
        while(j < sorted.size() && sorted[j] == sorted[i])
          j++;
        keys.push_back(sorted[i]);
        below.push_back((double)i);
        equal.push_back((double)(j - i));
        i = j;
      }
    }
    double percentile(Key key) const {
      size_t i = std::lower_bound(keys.begin(), keys.end(), key) - keys.begin();
      if(i < keys.size() && keys[i] == key)
        return (below[i] + 0.5 * equal[i]) / total;
      return (i < keys.size() ? below[i] : total) / total;
    }
  };

  const StrengthTable& table5() {
    static const StrengthTable t = [] {
      vector<Key> all;
      all.reserve(2598960);
      for(int a = 0; a < 52; a++)
        for(int b = a + 1; b < 52; b++)
          for(int c = b + 1; c < 52; c++)
            for(int d = c + 1; d < 52; d++)
              for(int e = d + 1; e < 52; e++)
                all.push_back(eval5(Cards::bit(a) | Cards::bit(b) | Cards::bit(c) | Cards::bit(d) | Cards::bit(e)));
      StrengthTable st;
      st.build(all);
      return st;
    }();
    return t;
  }

  const StrengthTable& table3() {
    static const StrengthTable t = [] {
      vector<Key> all;
      for(int a = 0; a < 52; a++)
        for(int b = a + 1; b < 52; b++)
          for(int c = b + 1; c < 52; c++)
            all.push_back(eval3(Cards::bit(a) | Cards::bit(b) | Cards::bit(c)));
      StrengthTable st;
      st.build(all);
      return st;
    }();
    return t;
  }
}

double RowStrength::percentile5(Key key) {
  return table5().percentile(key);
}

double RowStrength::percentile3(Key key) {
  return table3().percentile(key);
}

const char* const HumanFeatures::NAMES[HumanFeatures::NUM] = {
  "front_high_card", "front_one_pair", "front_three_of_a_kind",
  "middle_high_card", "middle_one_pair", "middle_two_pair", "middle_three_of_a_kind", "middle_straight",
  "middle_flush", "middle_full_house", "middle_four_of_a_kind", "middle_straight_flush",
  "back_high_card", "back_one_pair", "back_two_pair", "back_three_of_a_kind", "back_straight",
  "back_flush", "back_full_house", "back_four_of_a_kind", "back_straight_flush",
  "front_pct", "middle_pct", "back_pct",
  "front_pct_sq", "middle_pct_sq", "back_pct_sq",
  "bonus",
  "c_back_pct", "c_back_pct_cube",
};

void HumanFeatures::ofSplit(Key front, Key middle, Key back, double dealerCost, float out[NUM]) {
  for(int i = 0; i < NUM; i++)
    out[i] = 0.0f;
  Category fc = categoryOf(front);
  out[fc == HIGH_CARD ? 0 : fc == ONE_PAIR ? 1 : 2] = 1.0f;
  out[3 + (int)categoryOf(middle) - 1] = 1.0f;
  out[12 + (int)categoryOf(back) - 1] = 1.0f;
  double pf = RowStrength::percentile3(front);
  double pm = RowStrength::percentile5(middle);
  double pb = RowStrength::percentile5(back);
  out[21] = (float)pf;
  out[22] = (float)pm;
  out[23] = (float)pb;
  out[24] = (float)(pf * pf);
  out[25] = (float)(pm * pm);
  out[26] = (float)(pb * pb);
  out[27] = (float)Arrangements::rowBonuses(front, middle, back);
  out[28] = (float)(dealerCost * pb);
  out[29] = (float)(dealerCost * pb * pb * pb);
}

void HumanFeatures::classes(const HandTable& table, vector<SplitClass>& out) {
  static thread_local vector<SplitClass> all;
  all.clear();
  table.forEachSplit(true, [&](uint32_t f, uint32_t m, uint32_t b, Key kf, Key km, Key kb) {
    SplitClass sc;
    sc.keys[FRONT] = kf;
    sc.keys[MIDDLE] = km;
    sc.keys[BACK] = kb;
    sc.count = 1;
    sc.local[FRONT] = (uint16_t)f;
    sc.local[MIDDLE] = (uint16_t)m;
    sc.local[BACK] = (uint16_t)b;
    all.push_back(sc);
  });
  std::sort(all.begin(), all.end(), [](const SplitClass& a, const SplitClass& b) {
    return std::tie(a.keys[BACK], a.keys[MIDDLE], a.keys[FRONT]) > std::tie(b.keys[BACK], b.keys[MIDDLE], b.keys[FRONT]);
  });
  out.clear();
  for(const SplitClass& sc: all) {
    if(!out.empty() && out.back().keys[FRONT] == sc.keys[FRONT] && out.back().keys[MIDDLE] == sc.keys[MIDDLE] &&
       out.back().keys[BACK] == sc.keys[BACK])
      out.back().count++;
    else
      out.push_back(sc);
  }
}

HumanModelPlayer::HumanModelPlayer(const string& weightsPath, double temp) : path(weightsPath), temperature(temp) {
  ifstream in(weightsPath);
  if(!in)
    throw StringError("Could not open human model weights: " + weightsPath);
  map<string, float> byName;
  string line;
  while(getline(in, line)) {
    line = Global::trim(line);
    if(line.empty() || line[0] == '#')
      continue;
    vector<string> parts = Global::split(line);
    if(parts.size() != 2)
      throw StringError("Bad line in " + weightsPath + ": " + line);
    byName[parts[0]] = (float)Global::stringToDouble(parts[1]);
  }
  for(int i = 0; i < HumanFeatures::NUM; i++) {
    auto it = byName.find(HumanFeatures::NAMES[i]);
    if(it == byName.end())
      throw StringError(string("Missing feature ") + HumanFeatures::NAMES[i] + " in " + weightsPath);
    weights[i] = it->second;
  }
  if(byName.size() != (size_t)HumanFeatures::NUM)
    throw StringError("Unknown features in " + weightsPath);
}

string HumanModelPlayer::name() const {
  string file = path;
  size_t slash = file.find_last_of('/');
  if(slash != string::npos)
    file = file.substr(slash + 1);
  return "human:weights=" + file + ",temp=" + Global::doubleToString(temperature);
}

ArrangementInfo HumanModelPlayer::arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const {
  HandTable table(hand, ctx.rules);
  static thread_local vector<HumanFeatures::SplitClass> classes;
  HumanFeatures::classes(table, classes);
  vector<double> utility(classes.size());
  size_t best = 0;
  for(size_t i = 0; i < classes.size(); i++) {
    float f[HumanFeatures::NUM];
    HumanFeatures::ofSplit(classes[i].keys[FRONT], classes[i].keys[MIDDLE], classes[i].keys[BACK], ctx.rules.dealerCost, f);
    double u = 0.0;
    for(int j = 0; j < HumanFeatures::NUM; j++)
      u += weights[j] * f[j];
    utility[i] = u;
    if(u > utility[best])
      best = i;
  }
  size_t chosen = best;
  if(temperature > 0.0) {
    double total = 0.0;
    vector<double> w(classes.size());
    for(size_t i = 0; i < classes.size(); i++) {
      w[i] = classes[i].count * std::exp((utility[i] - utility[best]) / temperature);
      total += w[i];
    }
    double x = rand.nextDouble() * total;
    for(size_t i = 0; i < classes.size(); i++) {
      x -= w[i];
      if(x <= 0.0) {
        chosen = i;
        break;
      }
    }
  }
  const HumanFeatures::SplitClass& sc = classes[chosen];
  return table.info(sc.local[FRONT], sc.local[MIDDLE], sc.local[BACK]);
}
