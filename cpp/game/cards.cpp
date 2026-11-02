#include "../game/cards.h"

using namespace std;

static const char* RANK_CHARS = "23456789TJQKA";
static const char* SUIT_CHARS = "cdhs";

uint32_t Cards::rankMask(CardSet s) {
  uint32_t mask = 0;
  while(s) {
    Card c = lowest(s);
    s &= s - 1;
    mask |= 1u << rankOf(c);
  }
  return mask;
}

vector<Card> Cards::toVector(CardSet s) {
  vector<Card> v;
  for(int c = NUM_CARDS - 1; c >= 0; c--)
    if(s & bit((Card)c))
      v.push_back((Card)c);
  return v;
}

string Cards::toString(Card c) {
  string s;
  s += RANK_CHARS[rankOf(c)];
  s += SUIT_CHARS[suitOf(c)];
  return s;
}

string Cards::toString(CardSet s) {
  string out;
  for(Card c: toVector(s)) {
    if(!out.empty())
      out += " ";
    out += toString(c);
  }
  return out;
}

bool Cards::tryParseCard(const string& str, Card& out) {
  string s = Global::toLower(Global::trim(str));
  string rankStr;
  char suitChar;
  if(s.size() == 2) {
    rankStr = s.substr(0, 1);
    suitChar = s[1];
  }
  else if(s.size() == 3 && s.substr(0, 2) == "10") {
    rankStr = "t";
    suitChar = s[2];
  }
  else
    return false;
  int rank = -1;
  for(int r = 0; r < NUM_RANKS; r++)
    if(Global::toLower(string(1, RANK_CHARS[r])) == rankStr)
      rank = r;
  int suit = -1;
  for(int u = 0; u < NUM_SUITS; u++)
    if(SUIT_CHARS[u] == suitChar)
      suit = u;
  if(rank < 0 || suit < 0)
    return false;
  out = make(rank, suit);
  return true;
}

CardSet Cards::parseCardSet(const string& str) {
  string s = str;
  for(char& ch: s)
    if(ch == ',')
      ch = ' ';
  CardSet set = 0;
  for(const string& piece: Global::split(s)) {
    if(Global::trim(piece).empty())
      continue;
    Card c;
    if(!tryParseCard(piece, c))
      throw StringError("Could not parse card: " + piece);
    if(set & bit(c))
      throw StringError("Repeated card: " + piece);
    set |= bit(c);
  }
  return set;
}

static void dealFrom(Rand& rand, CardSet pool, int numHands, CardSet* hands) {
  Card cards[Cards::NUM_CARDS];
  int n = 0;
  while(pool) {
    cards[n++] = Cards::lowest(pool);
    pool &= pool - 1;
  }
  assert(n == numHands * Cards::HAND_SIZE);
  for(int i = n - 1; i > 0; i--) {
    int j = (int)rand.nextUInt((uint32_t)(i + 1));
    std::swap(cards[i], cards[j]);
  }
  for(int h = 0; h < numHands; h++) {
    hands[h] = 0;
    for(int i = 0; i < Cards::HAND_SIZE; i++)
      hands[h] |= Cards::bit(cards[h * Cards::HAND_SIZE + i]);
  }
}

void Cards::dealAll(Rand& rand, CardSet hands[NUM_PLAYERS]) {
  dealFrom(rand, FULL_DECK, NUM_PLAYERS, hands);
}

void Cards::dealOthers(Rand& rand, CardSet known, CardSet others[NUM_PLAYERS - 1]) {
  dealFrom(rand, FULL_DECK & ~known, NUM_PLAYERS - 1, others);
}
