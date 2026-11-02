#include "../game/rules.h"

using namespace std;
using namespace HandEval;

const char* const Royalty::NAMES[Royalty::NUM] = {
  "front_trips", "middle_full_house", "middle_quads", "middle_straight_flush", "back_quads", "back_straight_flush",
};

const int Royalty::ROW_OF[Royalty::NUM] = {0, 1, 1, 1, 2, 2};

int Royalty::typeOf(int row, Key key) {
  const Category cat = categoryOf(key);
  if(row == 0)
    return cat == THREE_OF_A_KIND ? FRONT_TRIPS : -1;
  if(row == 1) {
    if(cat == FULL_HOUSE) return MIDDLE_FULL_HOUSE;
    if(cat == FOUR_OF_A_KIND) return MIDDLE_QUADS;
    if(cat == STRAIGHT_FLUSH) return MIDDLE_STRAIGHT_FLUSH;
    return -1;
  }
  if(cat == FOUR_OF_A_KIND) return BACK_QUADS;
  if(cat == STRAIGHT_FLUSH) return BACK_STRAIGHT_FLUSH;
  return -1;
}

const char* const Rules::FEATURE_NAMES[Rules::NUM_FEATURES] = {
  "c", "row", "scoop", "home_run",
  "front_trips", "middle_full_house", "middle_quads", "middle_straight_flush", "back_quads", "back_straight_flush",
  "on_win", "dragon", "strict", "wheel",
};

void Rules::toFeatures(float out[NUM_FEATURES]) const {
  out[0] = (float)dealerCost;
  out[1] = (float)rowPoint;
  out[2] = (float)scoopPoint;
  out[3] = (float)homeRunPoint;
  for(int t = 0; t < Royalty::NUM; t++)
    out[4 + t] = (float)royalty[t];
  out[10] = royaltyOnWin ? 1.0f : 0.0f;
  out[11] = (float)dragonPoint;
  out[12] = strictRows ? 1.0f : 0.0f;
  out[13] = (float)wheel;
}

Rules Rules::fromFeatures(const float in[NUM_FEATURES]) {
  Rules r;
  r.dealerCost = in[0];
  r.rowPoint = in[1];
  r.scoopPoint = in[2];
  r.homeRunPoint = in[3];
  for(int t = 0; t < Royalty::NUM; t++)
    r.royalty[t] = in[4 + t];
  r.royaltyOnWin = in[10] > 0.5f;
  r.dragonPoint = in[11];
  r.strictRows = in[12] > 0.5f;
  r.wheel = (int)(in[13] + 0.5f);
  return r;
}

static Rules preset(const string& name) {
  Rules r;
  if(name == "family")
    return r;
  if(name == "family_mid2") {
    r.royalty[Royalty::MIDDLE_QUADS] = 2.0;
    r.royalty[Royalty::MIDDLE_STRAIGHT_FLUSH] = 2.0;
    return r;
  }
  if(name == "taiwan" || name == "pagat") {
    r.rowPoint = 1.0;
    r.scoopPoint = 3.0;
    const double royalties[Royalty::NUM] = {3.0, 0.0, 8.0, 10.0, 4.0, 5.0};
    for(int t = 0; t < Royalty::NUM; t++)
      r.royalty[t] = royalties[t];
    if(name == "taiwan") {
      // A scoop doubles the 3 rows (6), a home run doubles again (12 from each); royalties are not doubled.
      r.homeRunPoint = 6.0;
      r.dragonPoint = 36.0;
    }
    else {
      r.homeRunPoint = 0.0;
      r.royalty[Royalty::MIDDLE_FULL_HOUSE] = 2.0;
      r.royaltyOnWin = true;
      // "The back hand must beat the middle, and the middle must beat the front."
      r.strictRows = true;
      r.dragonPoint = 13.0;
    }
    return r;
  }
  throw StringError("Unknown rules preset \"" + name + "\" (family, family_mid2, taiwan, pagat)");
}

Rules Rules::parse(const string& spec) {
  vector<string> pieces = Global::split(spec, ',');
  string first = Global::trim(pieces.empty() ? string() : pieces[0]);
  size_t start = 0;
  Rules r;
  if(!first.empty() && first.find('=') == string::npos) {
    r = preset(first);
    start = 1;
  }
  for(size_t i = start; i < pieces.size(); i++) {
    if(Global::trim(pieces[i]).empty())
      continue;
    vector<string> kv = Global::split(pieces[i], '=');
    double x;
    if(kv.size() != 2 || !Global::tryStringToDouble(Global::trim(kv[1]), x))
      throw StringError("Bad rules item \"" + pieces[i] + "\" in: " + spec);
    string name = Global::trim(kv[0]);
    int k = 0;
    while(k < NUM_FEATURES && name != FEATURE_NAMES[k])
      k++;
    if(k == NUM_FEATURES)
      throw StringError("Unknown rules item \"" + name + "\" in: " + spec);
    if(k == 0) r.dealerCost = x;
    else if(k == 1) r.rowPoint = x;
    else if(k == 2) r.scoopPoint = x;
    else if(k == 3) r.homeRunPoint = x;
    else if(k < 10) r.royalty[k - 4] = x;
    else if(k == 10) r.royaltyOnWin = x > 0.5;
    else if(k == 11) r.dragonPoint = x;
    else if(k == 12) r.strictRows = x > 0.5;
    else {
      r.wheel = (int)x;
      if(r.wheel != x || r.wheel < 0 || r.wheel > 2)
        throw StringError("wheel must be 0, 1 or 2 in: " + spec);
    }
  }
  r.check();
  return r;
}

string Rules::toString() const {
  float f[NUM_FEATURES];
  toFeatures(f);
  string s;
  for(int k = 0; k < NUM_FEATURES; k++)
    s += (k > 0 ? "," : "") + string(FEATURE_NAMES[k]) + "=" + Global::doubleToString(f[k]);
  return s;
}

void Rules::check() const {
  float f[NUM_FEATURES];
  toFeatures(f);
  for(int k = 0; k < NUM_FEATURES; k++)
    if(!(f[k] >= 0.0f))
      throw StringError("Rules values must be >= 0: " + toString());
  if(dealerCost > 1.0)
    throw StringError("The dealer cost c must be in [0, 1]: " + toString());
  if(wheel < 0 || wheel > 2)
    throw StringError("wheel must be 0, 1 or 2: " + toString());
  if(royalty[Royalty::MIDDLE_FULL_HOUSE] > royalty[Royalty::MIDDLE_QUADS] ||
     royalty[Royalty::MIDDLE_QUADS] > royalty[Royalty::MIDDLE_STRAIGHT_FLUSH] ||
     royalty[Royalty::BACK_QUADS] > royalty[Royalty::BACK_STRAIGHT_FLUSH])
    throw StringError("Royalties must not decrease with a row's category: " + toString());
}

bool Rules::operator==(const Rules& o) const {
  float a[NUM_FEATURES], b[NUM_FEATURES];
  toFeatures(a);
  o.toFeatures(b);
  for(int k = 0; k < NUM_FEATURES; k++)
    if(a[k] != b[k])
      return false;
  return true;
}
