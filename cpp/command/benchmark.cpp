#include "../command/commands.h"

#include "../core/timer.h"
#include "../players/montecarlo.h"

#include <tclap/CmdLine.h>

#include <iostream>

using namespace std;

// Times the building blocks on random hands, single-threaded.
int Commands::benchmark(vector<string> args) {
  int numHands;
  try {
    TCLAP::CmdLine cmd("Times hand evaluation, enumeration, players and Monte Carlo on one thread.", ' ', "0");
    TCLAP::ValueArg<int> handsArg("n", "hands", "Hands per measurement (default 2000)", false, 2000, "N");
    cmd.add(handsArg);
    cmd.parse(args);
    numHands = handsArg.getValue();
  }
  catch(TCLAP::ArgException& e) {
    cerr << "Error: " << e.error() << " for argument " << e.argId() << endl;
    return 1;
  }

  Rand rand("benchmark");
  vector<CardSet> hands(numHands);
  for(int i = 0; i < numHands; i++) {
    CardSet four[Cards::NUM_PLAYERS];
    Cards::dealAll(rand, four);
    hands[i] = four[0];
  }
  uint64_t sink = 0;
  auto report = [&](const char* what, double seconds, double count) {
    cout << Global::strprintf("%-40s %10.3f us", what, seconds / count * 1e6) << endl;
  };

  {
    ClockTimer t;
    int64_t n = 0;
    for(CardSet hand: hands) {
      vector<Card> cards = Cards::toVector(hand);
      for(int i = 0; i < 8; i++) {
        CardSet five = Cards::bit(cards[i]) | Cards::bit(cards[i + 1]) | Cards::bit(cards[i + 2]) |
          Cards::bit(cards[i + 3]) | Cards::bit(cards[i + 4]);
        sink += HandEval::eval5(five);
        n++;
      }
    }
    report("eval5", t.getSeconds(), (double)n);
  }
  {
    ClockTimer t;
    for(CardSet hand: hands) {
      HandTable table(hand, Rules());
      sink += table.key(0x1F);
    }
    report("HandTable construction", t.getSeconds(), numHands);
  }
  {
    ClockTimer t;
    int64_t n = 0;
    for(CardSet hand: hands) {
      HandTable table(hand, Rules());
      table.forEachSplit(true, [&](uint32_t, uint32_t, uint32_t, HandEval::Key, HandEval::Key, HandEval::Key) {
        n++;
      });
    }
    report("HandTable + all valid splits", t.getSeconds(), numHands);
    cout << Global::strprintf("%-40s %10.1f", "  valid splits per hand", (double)n / numHands) << endl;
  }
  {
    ClockTimer t;
    for(CardSet hand: hands)
      sink += HandTable(hand, Rules()).strongestBack().keys[BACK];
    report("greedy (HandTable + strongestBack)", t.getSeconds(), numHands);
  }
  for(double c: {0.0, 0.5}) {
    ClockTimer t;
    vector<ArrangementInfo> out;
    size_t total = 0;
    for(CardSet hand: hands) {
      HandTable(hand, Rules::parse("family,c=" + Global::doubleToString(c))).undominated(out);
      total += out.size();
    }
    string what = Global::strprintf("undominated, c = %.1f", c);
    report(what.c_str(), t.getSeconds(), numHands);
    cout << Global::strprintf("%-40s %10.1f", "  candidates per hand", (double)total / numHands) << endl;
  }
  {
    shared_ptr<const Player> indep = Players::create("indep");
    DecisionContext ctx;
    ctx.rules.dealerCost = 0.5;
    ClockTimer t;
    int n = std::min(numHands, 500);
    for(int i = 0; i < n; i++)
      sink += indep->arrange(hands[i], ctx, rand).keys[BACK];
    report("indep arrange, c = 0.5", t.getSeconds(), n);
  }
  for(double c: {0.0, 0.5}) {
    MonteCarloPlayer mc(Players::create("greedy"), 100);
    DecisionContext ctx;
    ctx.rules.dealerCost = c;
    ClockTimer t;
    int n = std::min(numHands, 50);
    for(int i = 0; i < n; i++)
      sink += mc.arrange(hands[i], ctx, rand).keys[BACK];
    string what = Global::strprintf("mc vs greedy, per sample, c = %.1f", c);
    report(what.c_str(), t.getSeconds(), n * 100.0);
  }
  cout << "(checksum " << sink << ")" << endl;
  return 0;
}
