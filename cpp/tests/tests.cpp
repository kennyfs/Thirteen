#include "../tests/tests.h"

#include "../core/test.h"
#include "../core/timer.h"
#include "../dataio/trainingrows.h"
#include "../players/montecarlo.h"

#include <algorithm>
#include <cmath>
#include <iostream>
#include <tuple>

using namespace std;
using namespace HandEval;

static Key k5(const string& s) { return eval5(Cards::parseCardSet(s)); }
static Key k3(const string& s) { return eval3(Cards::parseCardSet(s)); }

static void testCards() {
  Card c;
  testAssert(Cards::tryParseCard("As", c) && Cards::rankOf(c) == 12 && Cards::suitOf(c) == 3);
  testAssert(Cards::tryParseCard("10h", c) && Cards::rankOf(c) == 8 && Cards::suitOf(c) == 2);
  testAssert(Cards::tryParseCard("2C", c) && c == 0);
  testAssert(!Cards::tryParseCard("1c", c));
  testAssert(!Cards::tryParseCard("Ax", c));
  testAssert(Cards::toString(Cards::parseCardSet("2c, Td As")) == "As Td 2c");
  bool threw = false;
  try { Cards::parseCardSet("As as"); } catch(const StringError&) { threw = true; }
  testAssert(threw);

  Rand rand("cards");
  for(int i = 0; i < 100; i++) {
    CardSet hands[4];
    Cards::dealAll(rand, hands);
    CardSet all = 0;
    for(int p = 0; p < 4; p++) {
      testAssert(Cards::count(hands[p]) == 13);
      testAssert((all & hands[p]) == 0);
      all |= hands[p];
    }
    testAssert(all == Cards::FULL_DECK);
    CardSet others[3];
    Cards::dealOthers(rand, hands[0], others);
    testAssert((others[0] | others[1] | others[2] | hands[0]) == Cards::FULL_DECK);
  }
}

static void testCategoryCounts() {
  // Standard counts over all C(52,5) and C(52,3) hands.
  int64_t counts5[NUM_CATEGORIES] = {0};
  for(int a = 0; a < 52; a++)
    for(int b = a + 1; b < 52; b++)
      for(int c = b + 1; c < 52; c++)
        for(int d = c + 1; d < 52; d++)
          for(int e = d + 1; e < 52; e++) {
            CardSet s = Cards::bit(a) | Cards::bit(b) | Cards::bit(c) | Cards::bit(d) | Cards::bit(e);
            counts5[categoryOf(eval5(s))]++;
          }
  testAssert(counts5[STRAIGHT_FLUSH] == 40);
  testAssert(counts5[FOUR_OF_A_KIND] == 624);
  testAssert(counts5[FULL_HOUSE] == 3744);
  testAssert(counts5[FLUSH] == 5108);
  testAssert(counts5[STRAIGHT] == 10200);
  testAssert(counts5[THREE_OF_A_KIND] == 54912);
  testAssert(counts5[TWO_PAIR] == 123552);
  testAssert(counts5[ONE_PAIR] == 1098240);
  testAssert(counts5[HIGH_CARD] == 1302540);

  int64_t counts3[NUM_CATEGORIES] = {0};
  for(int a = 0; a < 52; a++)
    for(int b = a + 1; b < 52; b++)
      for(int c = b + 1; c < 52; c++)
        counts3[categoryOf(eval3(Cards::bit(a) | Cards::bit(b) | Cards::bit(c)))]++;
  testAssert(counts3[THREE_OF_A_KIND] == 52);
  testAssert(counts3[ONE_PAIR] == 3744);
  testAssert(counts3[HIGH_CARD] == 18304);
  int64_t total3 = 0;
  for(int i = 0; i < NUM_CATEGORIES; i++)
    total3 += counts3[i];
  testAssert(total3 == 22100);
}

static void testTableEval() {
  // The table lookups agree with the direct implementations on every 5- and 3-card hand.
  for(int a = 0; a < 52; a++)
    for(int b = a + 1; b < 52; b++)
      for(int c = b + 1; c < 52; c++) {
        CardSet three = Cards::bit(a) | Cards::bit(b) | Cards::bit(c);
        testAssert(eval3(three) == eval3Reference(three));
        for(int d = c + 1; d < 52; d++)
          for(int e = d + 1; e < 52; e++) {
            CardSet five = three | Cards::bit(d) | Cards::bit(e);
            testAssert(eval5(five) == eval5Reference(five));
          }
      }
}

static void testComparisons() {
  // Examples in docs/rules.md section 4.
  testAssert(k5("Ks Kh 9c 7d 3s") > k5("Kd Kc 9s 6h 4h"));
  testAssert(k5("Ah 9h 7h 4h 2h") == k5("Ac 9c 7c 4c 2c"));
  testAssert(k5("As 2d 3c 4h 5s") < k5("2c 3d 4s 5h 6c"));

  // Straights: A-2-3-4-5 is the lowest, 10-J-Q-K-A the highest, no wrap-around.
  testAssert(categoryOf(k5("As 2d 3c 4h 5s")) == STRAIGHT);
  testAssert(categoryOf(k5("Ts Jd Qc Kh As")) == STRAIGHT);
  testAssert(categoryOf(k5("Qs Kd Ac 2h 3s")) == HIGH_CARD);
  testAssert(k5("9s Td Jc Qh Ks") < k5("Ts Jd Qc Kh As"));
  testAssert(categoryOf(k5("As 2s 3s 4s 5s")) == STRAIGHT_FLUSH);
  testAssert(k5("As 2s 3s 4s 5s") < k5("2h 3h 4h 5h 6h"));
  testAssert(k5("2h 3h 4h 5h 6h") == k5("2c 3c 4c 5c 6c"));
  testAssert(k5("As 2d 3c 4h 5s") == k5("Ah 2c 3d 4s 5c"));

  // Category order.
  testAssert(k5("2s 3s 4s 5s 6s") > k5("As Ah Ad Ac Ks"));
  testAssert(k5("2s 2h 2d 2c 3s") > k5("As Ah Ad Kc Ks"));
  testAssert(k5("2s 2h 2d 3c 3s") > k5("As Ks Qs Js 9s"));
  testAssert(k5("2s 3s 4s 5s 7s") > k5("Ts Jd Qc Kh As"));
  testAssert(k5("As 2d 3c 4h 5s") > k5("As Ah Ad Kc Qs"));
  testAssert(k5("2s 2h 2d 3c 4s") > k5("As Ah Kd Kc Qs"));
  testAssert(k5("2s 2h 3d 3c 4s") > k5("As Ah Kd Qc Js"));
  testAssert(k5("2s 2h 3d 4c 5s") > k5("As Kh Qd Jc 9s"));

  // Tie-breaks.
  testAssert(k5("Ks Kh 2d 2c 3s") > k5("Qs Qh Jd Jc As"));
  testAssert(k5("Ks Kh 3d 3c 2s") > k5("Kd Kc 2h 2s As"));
  testAssert(k5("Ks Kh 3d 3c 4s") > k5("Kd Kc 3h 3s 2s"));
  testAssert(k5("Ks Kh 3d 3c 4s") == k5("Kd Kc 3h 3s 4h"));
  testAssert(k5("As Kh Qd Jc 9s") > k5("Ad Kc Qh Jh 8s"));
  testAssert(k5("As Kh Qd Jc 9s") == k5("Ad Kc Qh Js 9h"));
  testAssert(k5("As Ks Qs Js 9s") > k5("Ah Kh Qh Jh 8h"));
  testAssert(k5("3s 3h 3d 2c 2s") < k5("4s 4h 4d 2h 2d"));
  testAssert(k5("3s 3h 3d 3c 2s") < k5("4s 4h 4d 4c 2d"));

  // Front hands.
  testAssert(k3("As Ah Ad") > k3("Ks Kh Ac"));
  testAssert(k3("2s 2h 3d") > k3("As Kh Qd"));
  testAssert(k3("Ks Kh 4d") > k3("Kd Kc 3s"));
  testAssert(k3("Ks Kh 4d") == k3("Kd Kc 4s"));
  testAssert(categoryOf(k3("2s 3s 4s")) == HIGH_CARD);
  testAssert(k3("As Kh Qd") > k3("As Kh Jd"));

  // Front against middle (section 5): equal when the front's sequence runs out with all entries equal.
  testAssert(k3("As Kh Qd") <= k5("Ac Kd Qc 5s 3h"));
  testAssert(k3("8s 8h Kd") <= k5("8c 8d Kc 7s 3h"));
  testAssert(k3("8s 8h Kd") > k5("8c 8d Qc Js 3h"));
  testAssert(k3("As Kh Qd") > k5("Ac Kd Jc Ts 3h"));
  testAssert(k3("2s 2h 2d") > k5("As Ah Kd Kc Qs"));
  testAssert(k3("2s 2h 2d") < k5("As 2c 3c 4h 5s"));
}

static void testArrangements() {
  Rand rand("arrangements");
  vector<ArrangementInfo> all;
  vector<ArrangementInfo> valid;
  for(int i = 0; i < 20; i++) {
    CardSet hands[4];
    Cards::dealAll(rand, hands);
    Arrangements::enumerate(hands[0], Rules(), false, all);
    testAssert((int)all.size() == Arrangements::NUM_SPLITS);
    int numValid = 0;
    for(const ArrangementInfo& info: all) {
      testAssert(info.arr.hand() == hands[0]);
      ArrangementInfo again = Arrangements::evaluate(info.arr, Rules());
      testAssert(again.keys[FRONT] == info.keys[FRONT]);
      testAssert(again.keys[MIDDLE] == info.keys[MIDDLE]);
      testAssert(again.keys[BACK] == info.keys[BACK]);
      testAssert(again.valid == info.valid);
      testAssert(again.bonus == info.bonus);
      if(info.valid)
        numValid++;
    }
    Arrangements::enumerate(hands[0], Rules(), true, valid);
    testAssert((int)valid.size() == numValid);
    testAssert(numValid > 0);
  }

  // Bonuses.
  {
    ArrangementInfo info = Arrangements::evaluate(Arrangements::parse("7s 7h 7d | 9s 9h 9d 9c 2s | Ts Js Qs Ks As"), Rules());
    testAssert(info.valid);
    testAssert(info.bonus == 3);
  }
  {
    // A dragon of one suit is still worth only 1, plus the straight flush rows.
    CardSet hand = Cards::parseCardSet("2s 3s 4s 5s 6s 7s 8s 9s Ts Js Qs Ks As");
    testAssert(Arrangements::isDragon(hand));
    ArrangementInfo info = Arrangements::evaluate(Arrangements::parse("2s 3s 4s | 5s 6s 7s 8s 9s | Ts Js Qs Ks As"), Rules());
    testAssert(info.valid);
    testAssert(info.bonus == 3);
    info = Arrangements::evaluate(Arrangements::parse("2s 3s 4s | 5s 6s 7s 8s Ks | 9s Ts Js Qs As"), Rules());
    testAssert(info.valid);
    testAssert(info.bonus == 1);
  }
  {
    // Four of a kind split across rows earns nothing; a fouled arrangement still records its bonus.
    ArrangementInfo info = Arrangements::evaluate(Arrangements::parse("9s 2h 3d | 9h 9d 4c 5s 7h | 9c Js Qs Kh Ad"), Rules());
    testAssert(info.bonus == 0);
    info = Arrangements::evaluate(Arrangements::parse("As Ah Ad | 9s 9h 9d 9c 2s | 3s 4h 6d 8c Th"), Rules());
    testAssert(!info.valid);
    testAssert(info.bonus == 2);
  }
  {
    // Equal middle and back are valid.
    ArrangementInfo info = Arrangements::evaluate(Arrangements::parse("Ac Kd 2c | 3s 4s 5h 6d 7c | 3h 4h 5s 6c 7d"), Rules());
    testAssert(info.keys[MIDDLE] == info.keys[BACK]);
    testAssert(info.valid);
    // Not with strict rows, since the hand has strictly increasing splits.
    testAssert(!Arrangements::evaluate(info.arr, Rules::parse("family,strict=1")).valid);
  }
  // The ranking of A-2-3-4-5.
  {
    const Key wheel = eval5(Cards::parseCardSet("As 2d 3c 4h 5s"));
    const Key kingHigh = eval5(Cards::parseCardSet("Ks Qd Jc Th 9s"));
    const Key aceHigh = eval5(Cards::parseCardSet("As Kd Qc Jh Ts"));
    // High card A 5 4 3 2.
    const Key aceFive = ((Key)HIGH_CARD << 20) | (12u << 16) | (3u << 12) | (2u << 8) | (1u << 4);
    testAssert(wheel < kingHigh && categoryOf(wheel) == STRAIGHT);
    testAssert(withWheel(kingHigh, 1) < withWheel(wheel, 1) && withWheel(wheel, 1) < withWheel(aceHigh, 1));
    testAssert(withWheel(wheel, 2) == aceFive && categoryOf(withWheel(wheel, 2)) == HIGH_CARD);
    const Key steelWheel = eval5(Cards::parseCardSet("As 2s 3s 4s 5s"));
    testAssert(categoryOf(withWheel(steelWheel, 2)) == FLUSH && categoryOf(withWheel(steelWheel, 1)) == STRAIGHT_FLUSH);
    testAssert(withWheel(kingHigh, 2) == kingHigh && withWheel(aceHigh, 2) == aceHigh);
  }
}

// Builds an info with the given keys directly, for scoring tests.
static ArrangementInfo synthetic(Key front, Key middle, Key back, int bonus, bool valid = true) {
  ArrangementInfo info;
  info.keys[FRONT] = front;
  info.keys[MIDDLE] = middle;
  info.keys[BACK] = back;
  info.bonus = (uint8_t)bonus;
  // Keys here have no category, so the only bonus a synthetic arrangement can have is the dragon.
  info.dragon = bonus > 0;
  info.valid = valid;
  return info;
}

static Rules rulesWith(const string& spec, double c) {
  Rules rules = Rules::parse(spec);
  rules.dealerCost = c;
  return rules;
}

static void checkScore(const ArrangementInfo* const p[4], const Rules& rules, const double expected[4]) {
  DealScore score;
  Scoring::score(p, rules, score);
  double pointSum = 0.0;
  for(int i = 0; i < 4; i++) {
    testAssert(std::fabs(score.result[i] - expected[i]) < 1e-9);
    pointSum += score.points[i];
  }
  testAssert(std::fabs(pointSum) < 1e-9);
  // resultOfPlayer0 for every seat.
  for(int seat = 0; seat < 4; seat++) {
    const ArrangementInfo* rotated[4];
    for(int i = 0; i < 4; i++)
      rotated[i] = p[(seat + i) % 4];
    testAssert(std::fabs(Scoring::resultOfPlayer0(rotated, rules) - expected[seat]) < 1e-9);
    Scoring::Opponents opps(rotated + 1);
    testAssert(std::fabs(opps.resultOf(*rotated[0], rules) - expected[seat]) < 1e-9);
  }
}

static void testScoring() {
  // The worked example in docs/rules.md section 10, with keys standing in for hands. Higher numbers beat lower.
  {
    ArrangementInfo p1 = synthetic(100, 100, 100, 0);
    ArrangementInfo p2 = synthetic(50, 60, 60, 0);
    ArrangementInfo p3 = synthetic(60, 50, 50, 1);
    // P3 does not scoop P4: tie in the middle.
    ArrangementInfo p4 = synthetic(40, 50, 40, 0);
    const ArrangementInfo* p[4] = {&p1, &p2, &p3, &p4};
    double expected[4] = {4.5, -2.0, 1.0, -4.0};
    checkScore(p, rulesWith("family", 0.5), expected);
  }
  // Ties never scoop; tied largest backs split the dealer cost.
  {
    ArrangementInfo a = synthetic(10, 20, 30, 0);
    ArrangementInfo b = synthetic(10, 20, 30, 0);
    ArrangementInfo c = synthetic(5, 5, 5, 0);
    ArrangementInfo d = synthetic(11, 21, 29, 0);
    const ArrangementInfo* p[4] = {&a, &b, &c, &d};
    // a, b and d each scoop c; nobody else scoops anyone. a and b share the dealer cost 0.8.
    double expected[4] = {1.0 - 0.4, 1.0 - 0.4, -3.0, 1.0};
    checkScore(p, rulesWith("family", 0.8), expected);
  }
  // Fouls: a fouled player is scooped by every valid player; two fouled players do not score; a valid player who
  // scoops everyone gets a home run even when scoops come from fouls; fouled players get no bonus and no dealer cost.
  {
    ArrangementInfo a = synthetic(10, 20, 30, 1);
    ArrangementInfo b = synthetic(1, 2, 3, 0);
    ArrangementInfo f1 = synthetic(90, 90, 90, 2, false);
    ArrangementInfo f2 = synthetic(90, 90, 99, 1, false);
    const ArrangementInfo* p[4] = {&a, &b, &f1, &f2};
    // a: scoops b, f1, f2 (+3) and home run (+3), bonus 1 (+3); dealer (largest valid back) -1.
    // b: -1 to a, -1 home run, +1 from f1, +1 from f2, -1 bonus = -1.
    // f1, f2: -1 to a, -1 a's home run, -1 to b, -1 a's bonus = -4 each.
    double expected[4] = {9.0 - 1.0, -1.0, -4.0, -4.0};
    checkScore(p, rulesWith("family", 1.0), expected);
  }
  // Everyone fouls: nothing happens.
  {
    ArrangementInfo f = synthetic(90, 50, 10, 1, false);
    const ArrangementInfo* p[4] = {&f, &f, &f, &f};
    double expected[4] = {0.0, 0.0, 0.0, 0.0};
    checkScore(p, rulesWith("family", 1.0), expected);
  }
  // An opponent's home run that includes player 0.
  {
    ArrangementInfo me = synthetic(10, 10, 10, 0);
    ArrangementInfo strong = synthetic(50, 50, 50, 0);
    ArrangementInfo x = synthetic(20, 5, 20, 0);
    ArrangementInfo y = synthetic(5, 20, 20, 0);
    const ArrangementInfo* p[4] = {&me, &strong, &x, &y};
    // strong home run: everyone else pays 2. x and y do not scoop me or each other.
    double expected[4] = {-2.0, 6.0 - 0.0, -2.0, -2.0};
    checkScore(p, rulesWith("family", 0.0), expected);
  }
  // Rows count and royalties (docs/rules.md section 11). a has front trips, d a middle full house.
  {
    const Key trips = (Key)HandEval::THREE_OF_A_KIND << 20;
    const Key fullHouse = (Key)HandEval::FULL_HOUSE << 20;
    ArrangementInfo a = synthetic(trips, 40, 50, 0);
    ArrangementInfo b = synthetic(20, 45, 40, 0);
    ArrangementInfo c = synthetic(10, 10, 10, 0);
    ArrangementInfo d = synthetic(5, fullHouse, 60, 0);
    const ArrangementInfo* p[4] = {&a, &b, &c, &d};
    // taiwan: a +1 (b) +6 (scoops c) -1 (d) +9 (trips from everyone); the full house earns nothing; d deals.
    double taiwan[4] = {15.0, 1.0, -16.0, -0.5};
    checkScore(p, rulesWith("taiwan", 0.5), taiwan);
    // pagat: royalties only from the opponents whose row they beat. a: +4 (b), +9 (c), 0 (d: +1 +3 -1 -2 -1).
    double pagat[4] = {13.0, -1.0, -18.0, 5.5};
    checkScore(p, rulesWith("pagat", 0.5), pagat);
  }
  // Specs.
  {
    Rules r = Rules::parse("taiwan,scoop=1,on_win=1,c=0.25");
    testAssert(r.scoopPoint == 1.0 && r.royaltyOnWin && r.dealerCost == 0.25 && r.rowPoint == 1.0);
    testAssert(Rules::parse(r.toString()) == r);
    bool threw = false;
    try { Rules::parse("family,middle_quads=5,middle_straight_flush=1"); } catch(const StringError&) { threw = true; }
    testAssert(threw);
  }
}

static void testPruning() {
  // The best response over the pruned candidates equals the best response over all valid arrangements, for random
  // fixed opponents.
  Rand rand("pruning");
  shared_ptr<const Player> opp = Players::create("random");
  vector<ArrangementInfo> all;
  vector<ArrangementInfo> pruned;
  const Rules ruleSets[] = {
    rulesWith("family", 0.0), rulesWith("family", 0.5), rulesWith("family", 1.0), rulesWith("taiwan", 0.5),
    rulesWith("pagat", 0.0), rulesWith("family_mid2,row=1,home_run=2,on_win=1,dragon=3", 0.3),
    rulesWith("taiwan,strict=1,wheel=2", 0.5), rulesWith("family,wheel=1", 0.0),
  };
  for(const Rules& rules: ruleSets) {
    const double c = rules.dealerCost;
    for(int trial = 0; trial < 20; trial++) {
      CardSet hands[4];
      Cards::dealAll(rand, hands);
      Arrangements::enumerate(hands[0], rules, true, all);
      Arrangements::pruneDominated(all, c, pruned);
      testAssert(pruned.size() <= all.size());
      testAssert(!pruned.empty());
      DecisionContext ctx;
      ctx.rules = rules;
      ArrangementInfo opps[3];
      for(int j = 0; j < 3; j++)
        opps[j] = opp->arrange(hands[j + 1], ctx, rand);
      const ArrangementInfo* p[4] = {nullptr, &opps[0], &opps[1], &opps[2]};
      const ArrangementInfo* oppPtrs[3] = {&opps[0], &opps[1], &opps[2]};
      Scoring::Opponents summary(oppPtrs);
      double bestAll = -1e9;
      for(const ArrangementInfo& info: all) {
        p[0] = &info;
        double r = Scoring::resultOfPlayer0(p, rules);
        DealScore score;
        Scoring::score(p, rules, score);
        testAssert(std::fabs(score.result[0] - r) < 1e-9);
        testAssert(std::fabs(summary.resultOf(info, rules) - r) < 1e-9);
        // The targets compose to the result.
        uint8_t t[Scoring::NUM_TARGETS];
        summary.targetsOf(info, t);
        float fractions[Scoring::NUM_TARGETS];
        for(int k = 0; k < Scoring::NUM_TARGETS; k++)
          fractions[k] = (float)t[k] / Scoring::TARGET_UNITS;
        testAssert(std::fabs(Scoring::compose(rules, info, fractions, summary.oppRoyalties) - r) < 1e-4);
        bestAll = std::max(bestAll, r);
      }
      double bestPruned = -1e9;
      for(const ArrangementInfo& info: pruned) {
        p[0] = &info;
        bestPruned = std::max(bestPruned, Scoring::resultOfPlayer0(p, rules));
      }
      testAssert(std::fabs(bestAll - bestPruned) < 1e-9);
    }
  }
}

static void testHandTable() {
  Rand rand("handtable");
  vector<ArrangementInfo> valid;
  vector<ArrangementInfo> pruned;
  vector<ArrangementInfo> fast;
  for(const char* spec: {"family", "family,strict=1", "family,wheel=1", "family,wheel=2,strict=1"}) {
    for(int trial = 0; trial < 15; trial++) {
      CardSet hands[4];
      Cards::dealAll(rand, hands);
      for(double c: {0.0, 0.5}) {
        const Rules rules = rulesWith(spec, c);
        HandTable table(hands[0], rules);
        Arrangements::enumerate(hands[0], rules, true, valid);
        const ArrangementInfo* best = &valid[0];
        for(const ArrangementInfo& info: valid) {
          const ArrangementInfo& b = *best;
          if(std::tie(info.keys[BACK], info.keys[MIDDLE], info.keys[FRONT], info.bonus) >
             std::tie(b.keys[BACK], b.keys[MIDDLE], b.keys[FRONT], b.bonus))
            best = &info;
        }
        ArrangementInfo greedy = table.strongestBack();
        testAssert(greedy.valid);
        testAssert(greedy.keys[FRONT] == best->keys[FRONT]);
        testAssert(greedy.keys[MIDDLE] == best->keys[MIDDLE]);
        testAssert(greedy.keys[BACK] == best->keys[BACK]);
        testAssert(greedy.bonus == best->bonus);
        Arrangements::pruneDominated(valid, c, pruned);
        table.undominated(fast);
        testAssert(pruned.size() == fast.size());
        for(size_t i = 0; i < fast.size(); i++) {
          for(int r = 0; r < NUM_ROWS; r++)
            testAssert(pruned[i].keys[r] == fast[i].keys[r]);
          testAssert(pruned[i].bonus == fast[i].bonus);
          testAssert(Arrangements::evaluate(fast[i].arr, rules).valid);
        }
      }
    }
  }
}

static void testPlayers() {
  Rand rand("players");
  DecisionContext ctx;
  ctx.rules = rulesWith("taiwan", 0.5);
  for(const char* spec: {"random", "greedy", "indep:hands=400", "mc:samples=20,opp=greedy"}) {
    shared_ptr<const Player> player = Players::create(spec);
    for(int i = 0; i < 5; i++) {
      CardSet hands[4];
      Cards::dealAll(rand, hands);
      ArrangementInfo info = player->arrange(hands[0], ctx, rand);
      testAssert(info.valid);
      testAssert(info.arr.hand() == hands[0]);
    }
  }
  bool threw = false;
  try { Players::create("greedy:x=1"); } catch(const StringError&) { threw = true; }
  testAssert(threw);
}

static void testTrainingRows() {
  // Compact rows expand to the same keys and targets as scoring the candidates directly.
  Rand rand("trainingrows");
  shared_ptr<const Player> random = Players::create("random");
  DecisionContext ctx;
  for(int i = 0; i < 50; i++) {
    ctx.rules = rulesWith(i % 2 == 0 ? "family" : "pagat", rand.nextDouble());
    CardSet hands[4];
    Cards::dealAll(rand, hands);
    ArrangementInfo arranged[4];
    for(int s = 0; s < 4; s++)
      arranged[s] = random->arrange(hands[s], ctx, rand);
    for(int seat = 0; seat < 4; seat++) {
      uint8_t deal[TrainingRows::DEAL_BYTES];
      TrainingRows::packDeal(arranged, seat, deal);
      testAssert(TrainingRows::unpackArrangement(deal) == arranged[seat].arr);
      const ArrangementInfo* opps[3];
      for(int j = 1; j < 4; j++) {
        opps[j - 1] = &arranged[(seat + j) % 4];
        testAssert(TrainingRows::unpackArrangement(deal + j * Cards::HAND_SIZE) == opps[j - 1]->arr);
      }
      Scoring::Opponents summary(opps);

      vector<ArrangementInfo> cands;
      HandTable(hands[seat], ctx.rules).undominated(cands);
      const int m = 8;
      int count = std::min((int)cands.size(), m - 1);
      uint32_t packed[m];
      for(int k = 0; k < count; k++) {
        packed[k] = TrainingRows::packCandidate(hands[seat], cands[k].arr);
        testAssert(TrainingRows::unpackCandidate(hands[seat], packed[k]) == cands[k].arr);
      }
      uint32_t keys[m * NUM_ROWS];
      uint8_t targets[m * Scoring::NUM_TARGETS];
      float result[m];
      float oppRoyalties[Scoring::NUM_OPP_ROYALTIES];
      TrainingRows::expand(deal, packed, count, m, ctx.rules, keys, targets, result, oppRoyalties);
      for(int t = 0; t < Scoring::NUM_OPP_ROYALTIES; t++)
        testAssert(oppRoyalties[t] == summary.oppRoyalties[t]);
      for(int k = 0; k < m; k++) {
        uint8_t t[Scoring::NUM_TARGETS] = {};
        if(k < count)
          summary.targetsOf(cands[k], t);
        for(int j = 0; j < Scoring::NUM_TARGETS; j++)
          testAssert(targets[k * Scoring::NUM_TARGETS + j] == t[j]);
        for(int r = 0; r < NUM_ROWS; r++)
          testAssert(keys[k * NUM_ROWS + r] == (k < count ? cands[k].keys[r] : 0));
        testAssert(result[k] == (k < count ? (float)summary.resultOf(cands[k], ctx.rules) : 0.0f));
      }
    }
  }
}

void Tests::runAll() {
  ClockTimer timer;
  cout << "Cards" << endl;
  testCards();
  cout << "Category counts" << endl;
  testCategoryCounts();
  cout << "Table evaluation" << endl;
  testTableEval();
  cout << "Comparisons" << endl;
  testComparisons();
  cout << "Arrangements" << endl;
  testArrangements();
  cout << "Scoring" << endl;
  testScoring();
  cout << "Pruning" << endl;
  testPruning();
  cout << "Hand table" << endl;
  testHandTable();
  cout << "Players" << endl;
  testPlayers();
  cout << "Training rows" << endl;
  testTrainingRows();
  cout << "All tests passed (" << timer.getSeconds() << " s)" << endl;
}
