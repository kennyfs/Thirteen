#include "../dataio/trainingrows.h"

#include <thread>

// C interface of libthirteen_data, loaded with ctypes by python/thirteen/data.py to expand each training batch
// (like KataGo unpacks binaryInputNCHWPacked in its data loader). Arrays as in TrainingRows::expand, for n rows of
// numCands slots: deal [n, 52], count [n], rules [n, Rules::NUM_FEATURES], cands [n, numCands] in;
// keys [n, numCands, 3], targets [n, numCands, NUM_TARGETS], result [n, numCands], oppRoyalties [n, NUM_OPP_ROYALTIES]
// out. Returns 0, or -1 for a bad count.
extern "C" int thirteen_expand(int64_t n, int numCands, const uint8_t* deal, const uint8_t* count, const float* rules,
                               const uint32_t* cands, uint32_t* keys, uint8_t* targets, float* result,
                               float* oppRoyalties, int numThreads) {
  for(int64_t i = 0; i < n; i++)
    if(count[i] > numCands)
      return -1;
  const int64_t m = numCands;
  const int t = Scoring::NUM_TARGETS;
  auto worker = [&](int threadIdx) {
    for(int64_t i = threadIdx; i < n; i += numThreads)
      TrainingRows::expand(&deal[i * TrainingRows::DEAL_BYTES], &cands[i * m], count[i], numCands,
                           Rules::fromFeatures(&rules[i * Rules::NUM_FEATURES]), &keys[i * m * NUM_ROWS],
                           &targets[i * m * t], &result[i * m], &oppRoyalties[i * Scoring::NUM_OPP_ROYALTIES]);
  };
  if(numThreads <= 1) {
    numThreads = 1;
    worker(0);
    return 0;
  }
  std::vector<std::thread> threads;
  for(int i = 0; i < numThreads; i++)
    threads.emplace_back(worker, i);
  for(std::thread& th: threads)
    th.join();
  return 0;
}
