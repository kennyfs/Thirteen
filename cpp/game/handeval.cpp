#include "../game/handeval.h"

using namespace std;
using namespace HandEval;

static inline Key makeKey(Category cat, const int* seq, int seqLen) {
  Key key = (Key)cat << 20;
  for(int i = 0; i < seqLen; i++)
    key |= (Key)seq[i] << (16 - 4 * i);
  return key;
}

// Fills ranks grouped by multiplicity: all ranks of the largest multiplicity first, each group from highest to
// lowest rank. Returns the number of distinct ranks. counts[r] is the number of cards of rank r.
static int groupRanks(const int* counts, int* ranksOut, int* multOut) {
  int n = 0;
  for(int mult = 4; mult >= 1; mult--) {
    for(int r = Cards::NUM_RANKS - 1; r >= 0; r--) {
      if(counts[r] == mult) {
        ranksOut[n] = r;
        multOut[n] = mult;
        n++;
      }
    }
  }
  return n;
}

Key HandEval::eval5Reference(CardSet cards) {
  assert(Cards::count(cards) == 5);
  int counts[Cards::NUM_RANKS] = {0};
  uint32_t rankMask = 0;
  int firstSuit = Cards::suitOf(Cards::lowest(cards));
  bool flush = true;
  CardSet s = cards;
  while(s) {
    Card c = Cards::lowest(s);
    s &= s - 1;
    counts[Cards::rankOf(c)]++;
    rankMask |= 1u << Cards::rankOf(c);
    if(Cards::suitOf(c) != firstSuit)
      flush = false;
  }

  int ranks[5];
  int mults[5];
  int numDistinct = groupRanks(counts, ranks, mults);

  if(numDistinct == 5) {
    int straightTop = -1;
    int low = __builtin_ctz(rankMask);
    if((rankMask >> low) == 0x1F)
      straightTop = low + 4;
    else if(rankMask == 0x100F) // A-2-3-4-5, counted as 5-high.
      straightTop = 3;
    if(straightTop >= 0) {
      int seq[1] = {straightTop};
      return makeKey(flush ? STRAIGHT_FLUSH : STRAIGHT, seq, 1);
    }
    return makeKey(flush ? FLUSH : HIGH_CARD, ranks, 5);
  }
  if(mults[0] == 4)
    return makeKey(FOUR_OF_A_KIND, ranks, 1);
  if(mults[0] == 3)
    return makeKey(mults[1] == 2 ? FULL_HOUSE : THREE_OF_A_KIND, ranks, 1);
  if(mults[1] == 2)
    return makeKey(TWO_PAIR, ranks, 3);
  return makeKey(ONE_PAIR, ranks, 4);
}

Key HandEval::eval3Reference(CardSet cards) {
  assert(Cards::count(cards) == 3);
  int counts[Cards::NUM_RANKS] = {0};
  CardSet s = cards;
  while(s) {
    Card c = Cards::lowest(s);
    s &= s - 1;
    counts[Cards::rankOf(c)]++;
  }
  int ranks[3];
  int mults[3];
  int numDistinct = groupRanks(counts, ranks, mults);
  if(numDistinct == 1)
    return makeKey(THREE_OF_A_KIND, ranks, 1);
  if(numDistinct == 2)
    return makeKey(ONE_PAIR, ranks, 2);
  return makeKey(HIGH_CARD, ranks, 3);
}

namespace {
  // Multisets of ranks, as non-decreasing sequences r0 <= r1 <= ..., are indexed by the combinatorial number system
  // on r_i + i, which is strictly increasing: index = sum_i C(r_i + i, i + 1).
  constexpr int MAX_N = 18;
  struct Binomials {
    uint32_t c[MAX_N][6];
    Binomials() {
      for(int n = 0; n < MAX_N; n++)
        for(int k = 0; k < 6; k++)
          c[n][k] = k == 0 ? 1 : (n == 0 ? 0 : c[n - 1][k - 1] + c[n - 1][k]);
    }
  };
  const Binomials binomials;

  constexpr int NUM_MULTISETS5 = 6188; // C(17, 5)
  constexpr int NUM_MULTISETS3 = 455;  // C(15, 3)

  struct EvalTables {
    Key key5[NUM_MULTISETS5];
    Key flushKey5[NUM_MULTISETS5];
    Key key3[NUM_MULTISETS3];
    EvalTables() {
      for(int i = 0; i < NUM_MULTISETS5; i++)
        key5[i] = flushKey5[i] = 0;
      int r[5];
      for(r[0] = 0; r[0] < 13; r[0]++)
      for(r[1] = r[0]; r[1] < 13; r[1]++)
      for(r[2] = r[1]; r[2] < 13; r[2]++)
      for(r[3] = r[2]; r[3] < 13; r[3]++)
      for(r[4] = r[3]; r[4] < 13; r[4]++) {
        if(r[0] == r[4])
          continue; // Five of a rank cannot happen.
        // Suit i % 4 gives equal ranks (adjacent in r) different suits and never makes a flush. All suit 0 makes
        // the flush version when all ranks differ.
        CardSet mixed = 0;
        CardSet suited = 0;
        bool distinct = true;
        for(int i = 0; i < 5; i++) {
          int dup = 0;
          for(int j = 0; j < i; j++)
            if(r[j] == r[i])
              dup++;
          if(dup > 0)
            distinct = false;
          mixed |= Cards::bit(Cards::make(r[i], i % 4));
          suited |= Cards::bit(Cards::make(r[i], 0));
        }
        int index = 0;
        for(int i = 0; i < 5; i++)
          index += binomials.c[r[i] + i][i + 1];
        key5[index] = eval5Reference(mixed);
        if(distinct)
          flushKey5[index] = eval5Reference(suited);
      }
      for(r[0] = 0; r[0] < 13; r[0]++)
      for(r[1] = r[0]; r[1] < 13; r[1]++)
      for(r[2] = r[1]; r[2] < 13; r[2]++) {
        CardSet s = 0;
        for(int i = 0; i < 3; i++) {
          int dup = 0;
          for(int j = 0; j < i; j++)
            if(r[j] == r[i])
              dup++;
          s |= Cards::bit(Cards::make(r[i], dup));
        }
        int index = 0;
        for(int i = 0; i < 3; i++)
          index += binomials.c[r[i] + i][i + 1];
        key3[index] = eval3Reference(s);
      }
    }
  };
  const EvalTables& evalTables() {
    static const EvalTables tables;
    return tables;
  }
}

Key HandEval::eval5(CardSet cards) {
  assert(Cards::count(cards) == 5);
  const EvalTables& t = evalTables();
  // Cards in increasing order have non-decreasing ranks.
  int index = 0;
  int suitAnd = 0xF;
  for(int i = 0; i < 5; i++) {
    Card c = Cards::lowest(cards);
    cards &= cards - 1;
    index += binomials.c[Cards::rankOf(c) + i][i + 1];
    suitAnd &= 1 << Cards::suitOf(c);
  }
  return suitAnd != 0 ? t.flushKey5[index] : t.key5[index];
}

Key HandEval::eval3(CardSet cards) {
  assert(Cards::count(cards) == 3);
  const EvalTables& t = evalTables();
  int index = 0;
  for(int i = 0; i < 3; i++) {
    Card c = Cards::lowest(cards);
    cards &= cards - 1;
    index += binomials.c[Cards::rankOf(c) + i][i + 1];
  }
  return t.key3[index];
}

Key HandEval::withWheel(Key key, int wheel) {
  const Category cat = categoryOf(key);
  if(wheel == 0 || (cat != STRAIGHT && cat != STRAIGHT_FLUSH))
    return key;
  const int top = (key >> 16) & 0xF;
  const int fiveHigh = 3;
  const int aceHigh = Cards::NUM_RANKS - 1;
  if(wheel == 1) {
    if(top == aceHigh)
      return ((Key)cat << 20) | ((Key)(aceHigh + 1) << 16);
    if(top == fiveHigh)
      return ((Key)cat << 20) | ((Key)aceHigh << 16);
    return key;
  }
  if(top != fiveHigh)
    return key;
  const int ranks[5] = {aceHigh, 3, 2, 1, 0};
  return makeKey(cat == STRAIGHT_FLUSH ? FLUSH : HIGH_CARD, ranks, 5);
}

const char* HandEval::categoryName(Category cat) {
  switch(cat) {
    case HIGH_CARD: return "High card";
    case ONE_PAIR: return "One pair";
    case TWO_PAIR: return "Two pair";
    case THREE_OF_A_KIND: return "Three of a kind";
    case STRAIGHT: return "Straight";
    case FLUSH: return "Flush";
    case FULL_HOUSE: return "Full house";
    case FOUR_OF_A_KIND: return "Four of a kind";
    case STRAIGHT_FLUSH: return "Straight flush";
  }
  return "Unknown";
}

string HandEval::keyToString(Key key, int numCards) {
  // 13 is an A-high straight under wheel 1.
  static const char* RANK_CHARS = "23456789TJQKAA";
  Category cat = categoryOf(key);
  int seqLen = 0;
  switch(cat) {
    case HIGH_CARD: seqLen = numCards; break;
    case FLUSH: seqLen = 5; break;
    case ONE_PAIR: seqLen = numCards - 1; break;
    case TWO_PAIR: seqLen = 3; break;
    default: seqLen = 1; break;
  }
  string s = string(categoryName(cat)) + " (";
  for(int i = 0; i < seqLen; i++) {
    int r = (key >> (16 - 4 * i)) & 0xF;
    if(i > 0)
      s += ", ";
    s += RANK_CHARS[r];
  }
  s += ")";
  return s;
}
