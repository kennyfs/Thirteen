#include "../dataio/trainingrows.h"

#include <cstring>

using namespace std;

static constexpr int N = Cards::NUM_PLAYERS;
static constexpr uint32_t LOCAL_ALL = (1u << Cards::HAND_SIZE) - 1;

void TrainingRows::packDeal(const ArrangementInfo arranged[N], int seat, uint8_t out[DEAL_BYTES]) {
  int i = 0;
  for(int j = 0; j < N; j++)
    for(int r = 0; r < NUM_ROWS; r++)
      for(CardSet s = arranged[(seat + j) % N].arr.rows[r]; s; s &= s - 1)
        out[i++] = Cards::lowest(s);
}

Arrangement TrainingRows::unpackArrangement(const uint8_t cards[Cards::HAND_SIZE]) {
  Arrangement arr;
  int i = 0;
  for(int r = 0; r < NUM_ROWS; r++)
    for(int k = 0; k < ROW_SIZES[r]; k++)
      arr.rows[r] |= Cards::bit(cards[i++]);
  return arr;
}

uint32_t TrainingRows::packCandidate(CardSet hand, const Arrangement& arr) {
  return Arrangements::localMask(hand, arr.rows[FRONT]) |
    ((uint32_t)Arrangements::localMask(hand, arr.rows[MIDDLE]) << LOCAL_MASK_BITS);
}

Arrangement TrainingRows::unpackCandidate(CardSet hand, uint32_t packed) {
  uint32_t local[NUM_ROWS];
  local[FRONT] = packed & LOCAL_ALL;
  local[MIDDLE] = (packed >> LOCAL_MASK_BITS) & LOCAL_ALL;
  local[BACK] = LOCAL_ALL & ~(local[FRONT] | local[MIDDLE]);
  Arrangement arr;
  int pos = 0;
  for(CardSet s = hand; s; s &= s - 1, pos++)
    for(int r = 0; r < NUM_ROWS; r++)
      if(local[r] & (1u << pos))
        arr.rows[r] |= Cards::bit(Cards::lowest(s));
  return arr;
}

void TrainingRows::expand(const uint8_t deal[DEAL_BYTES], const uint32_t* cands, int count, int numCands,
                          const Rules& rules, uint32_t* keys, uint8_t* targets, float* result, float* oppRoyalties) {
  ArrangementInfo opps[N - 1];
  const ArrangementInfo* oppPtrs[N - 1];
  for(int j = 0; j < N - 1; j++) {
    opps[j] = Arrangements::evaluate(unpackArrangement(deal + (j + 1) * Cards::HAND_SIZE), rules);
    oppPtrs[j] = &opps[j];
  }
  Scoring::Opponents summary(oppPtrs);
  for(int t = 0; t < Scoring::NUM_OPP_ROYALTIES; t++)
    oppRoyalties[t] = summary.oppRoyalties[t];
  CardSet hand = unpackArrangement(deal).hand();
  for(int i = 0; i < numCands; i++) {
    if(i < count) {
      ArrangementInfo cand = Arrangements::evaluate(unpackCandidate(hand, cands[i]), rules);
      for(int r = 0; r < NUM_ROWS; r++)
        keys[i * NUM_ROWS + r] = cand.keys[r];
      summary.targetsOf(cand, targets + i * Scoring::NUM_TARGETS);
      result[i] = (float)summary.resultOf(cand, rules);
    }
    else {
      std::memset(keys + i * NUM_ROWS, 0, NUM_ROWS * sizeof(uint32_t));
      std::memset(targets + i * Scoring::NUM_TARGETS, 0, Scoring::NUM_TARGETS);
      result[i] = 0.0f;
    }
  }
}
