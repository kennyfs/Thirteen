#include "../game/arrangement.h"

#include <algorithm>
#include <tuple>

using namespace std;
using namespace HandEval;

string Arrangement::toString() const {
  return Cards::toString(rows[FRONT]) + " | " + Cards::toString(rows[MIDDLE]) + " | " + Cards::toString(rows[BACK]);
}

string ArrangementInfo::toString() const {
  string s = arr.toString() + "  [" + keyToString(keys[FRONT], 3) + " / " + keyToString(keys[MIDDLE]) + " / " +
    keyToString(keys[BACK]) + "]";
  if(!valid)
    s += " FOUL";
  if(bonus > 0)
    s += " bonus " + Global::intToString(bonus);
  return s;
}

bool Arrangements::isDragon(CardSet hand) {
  return Cards::rankMask(hand) == (1u << Cards::NUM_RANKS) - 1;
}

static inline uint8_t rowBonusesOf(const Key keys[NUM_ROWS]) {
  return Arrangements::rowBonuses(keys[FRONT], keys[MIDDLE], keys[BACK]);
}

ArrangementInfo Arrangements::evaluate(const Arrangement& arr, const Rules& rules) {
  assert(Cards::count(arr.rows[FRONT]) == 3);
  assert(Cards::count(arr.rows[MIDDLE]) == 5);
  assert(Cards::count(arr.rows[BACK]) == 5);
  assert(Cards::count(arr.hand()) == 13);
  ArrangementInfo info;
  info.arr = arr;
  info.keys[FRONT] = eval3(arr.rows[FRONT]);
  info.keys[MIDDLE] = withWheel(eval5(arr.rows[MIDDLE]), rules.wheel);
  info.keys[BACK] = withWheel(eval5(arr.rows[BACK]), rules.wheel);
  info.valid = info.keys[FRONT] <= info.keys[MIDDLE] && info.keys[MIDDLE] <= info.keys[BACK];
  // Equal rows under strictRows: a foul unless the hand has no strictly increasing split. Rare, so the table is only
  // built then.
  if(info.valid && rules.strictRows && (info.keys[FRONT] == info.keys[MIDDLE] || info.keys[MIDDLE] == info.keys[BACK]))
    info.valid = !HandTable(arr.hand(), rules).strict();
  info.dragon = isDragon(arr.hand());
  info.bonus = rowBonusesOf(info.keys) + (info.dragon ? 1 : 0);
  return info;
}

namespace {
  struct SplitTables {
    vector<uint16_t> fives;
    vector<uint16_t> threes;
    vector<uint16_t> middlesOfBack;
    SplitTables() {
      const uint32_t all = (1u << Cards::HAND_SIZE) - 1;
      for(uint32_t m = 0; m <= all; m++) {
        if(__builtin_popcount(m) == 5)
          fives.push_back((uint16_t)m);
        if(__builtin_popcount(m) == 3)
          threes.push_back((uint16_t)m);
      }
      for(uint16_t back: fives)
        for(uint16_t middle: fives)
          if((middle & back) == 0)
            middlesOfBack.push_back(middle);
      assert(fives.size() == 1287);
      assert(middlesOfBack.size() == 1287 * 56);
    }
  };
  const SplitTables& splitTables() {
    static const SplitTables tables;
    return tables;
  }

  // What dominance and scoring look at, plus the split, in 20 bytes.
  struct Split {
    Key keys[NUM_ROWS];
    uint8_t bonus;
    uint16_t local[NUM_ROWS];
  };

  // The skyline described at Arrangements::pruneDominated, on anything with keys[] and bonus.
  template<typename T>
  void skyline(vector<T>& items, double dealerCost, vector<T>& out) {
    std::sort(items.begin(), items.end(), [](const T& a, const T& b) {
      if(a.keys[BACK] != b.keys[BACK]) return a.keys[BACK] > b.keys[BACK];
      if(a.keys[MIDDLE] != b.keys[MIDDLE]) return a.keys[MIDDLE] > b.keys[MIDDLE];
      if(a.keys[FRONT] != b.keys[FRONT]) return a.keys[FRONT] > b.keys[FRONT];
      return a.bonus > b.bonus;
    });
    const bool backMustMatch = dealerCost > 0.0;
    out.clear();
    // With the sort above, a later item can never dominate an earlier one unless they are equal in all compared
    // values, so one pass that checks each against the kept ones is enough.
    size_t groupStart = 0;
    for(const T& b: items) {
      if(backMustMatch && !out.empty() && out.back().keys[BACK] != b.keys[BACK])
        groupStart = out.size();
      bool dominated = false;
      for(size_t i = backMustMatch ? groupStart : 0; i < out.size(); i++) {
        const T& a = out[i];
        if(a.keys[FRONT] >= b.keys[FRONT] && a.keys[MIDDLE] >= b.keys[MIDDLE] && a.bonus >= b.bonus) {
          dominated = true;
          break;
        }
      }
      if(!dominated)
        out.push_back(b);
    }
  }
}

const vector<uint16_t>& HandTable::fiveMasks() {
  return splitTables().fives;
}

const vector<uint16_t>& HandTable::threeMasks() {
  return splitTables().threes;
}

const vector<uint16_t>& HandTable::middlesOfBack() {
  return splitTables().middlesOfBack;
}

HandTable::HandTable(CardSet hand, const Rules& rules) : handCards(hand), dealerCost(rules.dealerCost) {
  assert(Cards::count(hand) == Cards::HAND_SIZE);
  CardSet s = hand;
  for(int i = 0; i < Cards::HAND_SIZE; i++) {
    cards[i] = Cards::lowest(s);
    s &= s - 1;
  }
  dragon = Arrangements::isDragon(hand);
  // Only 3- and 5-position masks are ever read.
  for(uint16_t m: splitTables().fives)
    keys[m] = withWheel(eval5(toCardSet(m)), rules.wheel);
  for(uint16_t m: splitTables().threes)
    keys[m] = eval3(toCardSet(m));
  strictRows = false;
  strictRows = rules.strictRows && hasStrictSplit();
}

bool HandTable::hasStrictSplit() const {
  constexpr uint32_t ALL = (1u << Cards::HAND_SIZE) - 1;
  const vector<uint16_t>& fives = fiveMasks();
  const uint16_t* middles = middlesOfBack().data();
  for(size_t b = 0; b < fives.size(); b++) {
    const uint16_t* mids = middles + 56 * b;
    for(int m = 0; m < 56; m++)
      if(keys[mids[m]] < keys[fives[b]] && keys[ALL & ~fives[b] & ~mids[m]] < keys[mids[m]])
        return true;
  }
  return false;
}

CardSet HandTable::toCardSet(uint32_t local) const {
  CardSet s = 0;
  while(local) {
    s |= Cards::bit(cards[__builtin_ctz(local)]);
    local &= local - 1;
  }
  return s;
}

ArrangementInfo HandTable::info(uint32_t frontLocal, uint32_t middleLocal, uint32_t backLocal) const {
  ArrangementInfo info;
  info.arr.rows[FRONT] = toCardSet(frontLocal);
  info.arr.rows[MIDDLE] = toCardSet(middleLocal);
  info.arr.rows[BACK] = toCardSet(backLocal);
  info.keys[FRONT] = keys[frontLocal];
  info.keys[MIDDLE] = keys[middleLocal];
  info.keys[BACK] = keys[backLocal];
  info.valid = isValid(info.keys[FRONT], info.keys[MIDDLE], info.keys[BACK]);
  info.dragon = dragon;
  info.bonus = rowBonusesOf(info.keys) + (dragon ? 1 : 0);
  return info;
}

ArrangementInfo HandTable::strongestBack() const {
  constexpr uint32_t ALL = (1u << Cards::HAND_SIZE) - 1;
  const vector<uint16_t>& fives = fiveMasks();
  const uint16_t* middles = middlesOfBack().data();
  // Without strictRows, the strongest five cards always have a valid split: the best five of the other eight are at
  // least as strong as any three of them. So only backs with the maximum key need to be searched; with strictRows,
  // the next weaker backs when those have none.
  Key maxBack = 0;
  for(uint16_t b: fives)
    maxBack = std::max(maxBack, keys[b]);

  bool found = false;
  Key bestKeys[NUM_ROWS] = {0, 0, 0};
  uint8_t bestBonus = 0;
  uint32_t best[NUM_ROWS] = {0, 0, 0};
  while(!found) {
    Key nextBack = 0;
    for(size_t i = 0; i < fives.size(); i++) {
      const uint32_t backLocal = fives[i];
      const Key backKey = keys[backLocal];
      if(backKey < maxBack)
        nextBack = std::max(nextBack, backKey);
      if(backKey != maxBack)
        continue;
      const uint16_t* mids = middles + 56 * i;
      for(int m = 0; m < 56; m++) {
        const uint32_t middleLocal = mids[m];
        const Key middleKey = keys[middleLocal];
        const uint32_t frontLocal = ALL & ~backLocal & ~middleLocal;
        const Key frontKey = keys[frontLocal];
        if(!isValid(frontKey, middleKey, backKey))
          continue;
        Key k[NUM_ROWS] = {frontKey, middleKey, backKey};
        uint8_t bonus = rowBonusesOf(k);
        if(!found || std::tie(middleKey, frontKey, bonus) > std::tie(bestKeys[MIDDLE], bestKeys[FRONT], bestBonus)) {
          found = true;
          bestKeys[FRONT] = frontKey;
          bestKeys[MIDDLE] = middleKey;
          bestKeys[BACK] = backKey;
          bestBonus = bonus;
          best[FRONT] = frontLocal;
          best[MIDDLE] = middleLocal;
          best[BACK] = backLocal;
        }
      }
    }
    maxBack = nextBack;
  }
  return info(best[FRONT], best[MIDDLE], best[BACK]);
}

void HandTable::undominated(vector<ArrangementInfo>& out) const {
  constexpr uint32_t ALL = (1u << Cards::HAND_SIZE) - 1;
  const vector<uint16_t>& fives = fiveMasks();
  const uint16_t* middles = middlesOfBack().data();
  static thread_local vector<Split> group;
  static thread_local vector<Split> groupKept;
  static thread_local vector<Split> kept;

  // Backs from strongest to weakest, so that equal back keys are adjacent.
  uint16_t order[1287];
  for(int i = 0; i < 1287; i++)
    order[i] = (uint16_t)i;
  std::sort(order, order + 1287, [&](uint16_t a, uint16_t b) { return keys[fives[a]] > keys[fives[b]]; });

  // Splits with the same back key can only dominate each other through the front, middle and bonus, under either
  // dealer cost rule. So prune each group of equal back keys on its own. With dealerCost == 0, a group's survivors
  // must also not be dominated by a kept split of a stronger back; splits of weaker backs can never dominate them.
  kept.clear();
  for(int start = 0; start < 1287;) {
    const Key backKey = keys[fives[order[start]]];
    int end = start;
    group.clear();
    while(end < 1287 && keys[fives[order[end]]] == backKey) {
      const uint32_t backLocal = fives[order[end]];
      const uint16_t* mids = middles + 56 * order[end];
      for(int m = 0; m < 56; m++) {
        const uint32_t middleLocal = mids[m];
        const Key middleKey = keys[middleLocal];
        const uint32_t frontLocal = ALL & ~backLocal & ~middleLocal;
        const Key frontKey = keys[frontLocal];
        if(!isValid(frontKey, middleKey, backKey))
          continue;
        Split s;
        s.keys[FRONT] = frontKey;
        s.keys[MIDDLE] = middleKey;
        s.keys[BACK] = backKey;
        s.bonus = rowBonusesOf(s.keys);
        s.local[FRONT] = (uint16_t)frontLocal;
        s.local[MIDDLE] = (uint16_t)middleLocal;
        s.local[BACK] = (uint16_t)backLocal;
        group.push_back(s);
      }
      end++;
    }
    skyline(group, 1.0, groupKept);
    const size_t numStronger = kept.size();
    for(const Split& b: groupKept) {
      bool dominated = false;
      if(dealerCost <= 0.0) {
        for(size_t i = 0; i < numStronger && !dominated; i++) {
          const Split& a = kept[i];
          dominated = a.keys[FRONT] >= b.keys[FRONT] && a.keys[MIDDLE] >= b.keys[MIDDLE] && a.bonus >= b.bonus;
        }
      }
      if(!dominated)
        kept.push_back(b);
    }
    start = end;
  }

  std::sort(kept.begin(), kept.end(), [](const Split& a, const Split& b) {
    if(a.keys[BACK] != b.keys[BACK]) return a.keys[BACK] > b.keys[BACK];
    if(a.keys[MIDDLE] != b.keys[MIDDLE]) return a.keys[MIDDLE] > b.keys[MIDDLE];
    if(a.keys[FRONT] != b.keys[FRONT]) return a.keys[FRONT] > b.keys[FRONT];
    return a.bonus > b.bonus;
  });
  out.clear();
  for(const Split& s: kept)
    out.push_back(info(s.local[FRONT], s.local[MIDDLE], s.local[BACK]));
}

void Arrangements::enumerate(CardSet hand, const Rules& rules, bool validOnly, vector<ArrangementInfo>& out) {
  HandTable table(hand, rules);
  out.clear();
  table.forEachSplit(validOnly, [&](uint32_t f, uint32_t m, uint32_t b, Key, Key, Key) {
    out.push_back(table.info(f, m, b));
  });
}

void Arrangements::pruneDominated(const vector<ArrangementInfo>& in, double dealerCost, vector<ArrangementInfo>& out) {
  vector<ArrangementInfo> items = in;
  for(const ArrangementInfo& info: items) {
    (void)info;
    assert(info.valid);
  }
  skyline(items, dealerCost, out);
}

uint16_t Arrangements::localMask(CardSet hand, CardSet row) {
  uint16_t mask = 0;
  int pos = 0;
  for(CardSet s = hand; s; s &= s - 1, pos++)
    if(row & Cards::bit(Cards::lowest(s)))
      mask |= (uint16_t)(1u << pos);
  return mask;
}

Arrangement Arrangements::parse(const string& str) {
  vector<string> parts = Global::split(str, '|');
  if(parts.size() != NUM_ROWS)
    throw StringError("Expected \"front | middle | back\", got: " + str);
  Arrangement arr;
  for(int r = 0; r < NUM_ROWS; r++) {
    arr.rows[r] = Cards::parseCardSet(parts[r]);
    if(Cards::count(arr.rows[r]) != ROW_SIZES[r])
      throw StringError(Global::strprintf("Row %d needs %d cards: %s", r, ROW_SIZES[r], parts[r].c_str()));
  }
  if(Cards::count(arr.hand()) != Cards::HAND_SIZE)
    throw StringError("Repeated card in arrangement: " + str);
  return arr;
}
