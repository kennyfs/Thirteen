#ifndef DATAIO_TRAININGROWS_H_
#define DATAIO_TRAININGROWS_H_

#include "../game/scoring.h"

// The compact training row (python/thirteen/data.py has the full layout). In the spirit of KataGo's
// binaryInputNCHWPacked, a row stores only what cannot be recomputed cheaply: the deal with every seat's arrangement,
// and the candidates (which depend on the ranker's Q). The targets are exact functions of these, so the trainer
// recomputes them for each batch (libthirteen_data, trainingrows_c.cpp), which makes a row about 6 times smaller.
namespace TrainingRows {
  constexpr int DEAL_BYTES = Cards::NUM_CARDS;
  constexpr uint32_t LOCAL_MASK_BITS = 13;

  // The four arrangements, starting with `seat` and then the following seats; each as its front, middle and back
  // cards, each row in increasing order.
  void packDeal(const ArrangementInfo arranged[Cards::NUM_PLAYERS], int seat, uint8_t out[DEAL_BYTES]);
  Arrangement unpackArrangement(const uint8_t cards[Cards::HAND_SIZE]);

  // A candidate of `hand` as front | middle << 13, local masks (Arrangements::localMask); the back is the rest.
  uint32_t packCandidate(CardSet hand, const Arrangement& arr);
  Arrangement unpackCandidate(CardSet hand, uint32_t packed);

  // Everything the trainer needs that the compact row does not store, for one row with numCands candidate slots
  // (count used): keys [numCands * 3], targets [numCands * NUM_TARGETS], result [numCands] under `rules`, and the
  // opponents' royalties [NUM_OPP_ROYALTIES]. Unused slots get zeros.
  void expand(const uint8_t deal[DEAL_BYTES], const uint32_t* cands, int count, int numCands, const Rules& rules,
              uint32_t* keys, uint8_t* targets, float* result, float* oppRoyalties);
}

#endif // DATAIO_TRAININGROWS_H_
