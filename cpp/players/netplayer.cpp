#include "../players/netplayer.h"

#include <cmath>

#ifdef USE_ONNXRUNTIME
#include <onnxruntime_cxx_api.h>
#endif

using namespace std;

#ifdef USE_ONNXRUNTIME

struct NetModel::Impl {
  unique_ptr<Ort::Session> session;
};

static Ort::Env& ortEnv() {
  static Ort::Env env(ORT_LOGGING_LEVEL_WARNING, "thirteen");
  return env;
}

NetModel::NetModel(const string& path) : modelPath(path), impl(new Impl()) {
  Ort::SessionOptions options;
  options.SetIntraOpNumThreads(1);
  options.SetInterOpNumThreads(1);
  options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
  // The arena allocator takes a lock on every allocation, which many threads sharing one session fight over: on
  // 32 threads of a two-socket host, exploit ran 2.5 times faster without it.
  options.DisableCpuMemArena();
  try {
    impl->session = make_unique<Ort::Session>(ortEnv(), path.c_str(), options);
    Ort::AllocatorWithDefaultOptions allocator;
    for(size_t i = 0; i < impl->session->GetInputCount(); i++) {
      string name = impl->session->GetInputNameAllocated(i, allocator).get();
      if(name == "c")
        isLegacy = true;
      if(name == "rules") {
        vector<int64_t> shape = impl->session->GetInputTypeInfo(i).GetTensorTypeAndShapeInfo().GetShape();
        if(shape.size() == 2 && shape[1] > 0 && shape[1] <= Rules::NUM_FEATURES)
          numRuleFeatures = shape[1];
      }
    }
  }
  catch(const Ort::Exception& e) {
    delete impl;
    throw StringError("Could not load model " + path + ": " + e.what());
  }
}

NetModel::~NetModel() {
  delete impl;
}

bool NetModel::available() {
  return true;
}

void NetModel::evaluate(CardSet hand, const Rules& rules, int pop, const vector<ArrangementInfo>& cands,
                        vector<float>& q) const {
  run(hand, rules, pop, cands, q, nullptr, nullptr);
}

void NetModel::evaluate(CardSet hand, const Rules& rules, int pop, const vector<ArrangementInfo>& cands,
                        vector<float>& q, vector<float>& probs, vector<float>& oppRoyalties) const {
  run(hand, rules, pop, cands, q, &probs, &oppRoyalties);
}

// Legacy networks: 11 targets and one opp_bonus output.
static constexpr int LEGACY_NUM_TARGETS = 11;

void NetModel::run(CardSet hand, const Rules& rules, int pop, const vector<ArrangementInfo>& cands, vector<float>& q,
                   vector<float>* probs, vector<float>* oppRoyalties) const {
  const int64_t k = (int64_t)cands.size();
  q.assign(cands.size(), 0.0f);
  if(probs != nullptr)
    probs->assign(cands.size() * Scoring::NUM_TARGETS, 0.0f);
  if(oppRoyalties != nullptr)
    oppRoyalties->assign(Scoring::NUM_OPP_ROYALTIES, 0.0f);
  if(k == 0)
    return;
  vector<int64_t> cards;
  for(CardSet s = hand; s; s &= s - 1)
    cards.push_back(Cards::lowest(s));
  assert(cards.size() == (size_t)Cards::HAND_SIZE);
  float features[Rules::NUM_FEATURES];
  rules.toFeatures(features);
  int64_t popValue = pop;
  vector<int64_t> masks(k * NUM_ROWS);
  vector<int64_t> keys(k * NUM_ROWS);
  for(int64_t i = 0; i < k; i++) {
    for(int r = 0; r < NUM_ROWS; r++) {
      masks[i * NUM_ROWS + r] = Arrangements::localMask(hand, cands[i].arr.rows[r]);
      keys[i * NUM_ROWS + r] = cands[i].keys[r];
    }
  }

  Ort::MemoryInfo mem = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeCPU);
  const int64_t cardsShape[2] = {1, Cards::HAND_SIZE};
  const int64_t oneShape[1] = {1};
  const int64_t rulesShape[2] = {1, numRuleFeatures};
  const int64_t candShape[3] = {1, k, NUM_ROWS};
  Ort::Value inputs[5] = {
    Ort::Value::CreateTensor<int64_t>(mem, cards.data(), cards.size(), cardsShape, 2),
    isLegacy ? Ort::Value::CreateTensor<float>(mem, &features[0], 1, oneShape, 1)
             : Ort::Value::CreateTensor<float>(mem, features, numRuleFeatures, rulesShape, 2),
    Ort::Value::CreateTensor<int64_t>(mem, &popValue, 1, oneShape, 1),
    Ort::Value::CreateTensor<int64_t>(mem, masks.data(), masks.size(), candShape, 3),
    Ort::Value::CreateTensor<int64_t>(mem, keys.data(), keys.size(), candShape, 3),
  };
  static const char* inputNames[5] = {"cards", "rules", "pop", "masks", "keys"};
  static const char* legacyInputNames[5] = {"cards", "c", "pop", "masks", "keys"};
  static const char* outputNames[3] = {"q", "probs", "opp_royalty"};
  static const char* legacyOutputNames[3] = {"q", "probs", "opp_bonus"};
  try {
    vector<Ort::Value> outputs =
      impl->session->Run(Ort::RunOptions{nullptr}, isLegacy ? legacyInputNames : inputNames, inputs, 5,
                         isLegacy ? legacyOutputNames : outputNames, probs != nullptr ? 3 : 1);
    const float* out = outputs[0].GetTensorData<float>();
    for(int64_t i = 0; i < k; i++)
      q[i] = out[i];
    if(probs != nullptr) {
      const float* p = outputs[1].GetTensorData<float>();
      if(isLegacy) {
        for(int64_t i = 0; i < k; i++)
          std::copy(p + i * LEGACY_NUM_TARGETS, p + (i + 1) * LEGACY_NUM_TARGETS,
                    probs->begin() + i * Scoring::NUM_TARGETS);
      }
      else {
        probs->assign(p, p + k * Scoring::NUM_TARGETS);
        const float* o = outputs[2].GetTensorData<float>();
        oppRoyalties->assign(o, o + Scoring::NUM_OPP_ROYALTIES);
      }
    }
  }
  catch(const Ort::Exception& e) {
    throw StringError("Error running model " + modelPath + ": " + e.what());
  }
}

#else

struct NetModel::Impl {};

NetModel::NetModel(const string& path) : modelPath(path), impl(nullptr) {
  throw StringError("This build has no ONNX Runtime; configure with -DONNXRUNTIME_ROOT=... to use " + path);
}

NetModel::~NetModel() {}

bool NetModel::available() {
  return false;
}

void NetModel::evaluate(CardSet, const Rules&, int, const vector<ArrangementInfo>&, vector<float>&) const {
  ASSERT_UNREACHABLE;
}

void NetModel::evaluate(CardSet, const Rules&, int, const vector<ArrangementInfo>&, vector<float>&, vector<float>&,
                        vector<float>&) const {
  ASSERT_UNREACHABLE;
}

#endif

NetPlayer::NetPlayer(shared_ptr<const NetModel> model, int pop, double temp)
  : netModel(model), popId(pop), temperature(temp) {}

string NetPlayer::name() const {
  // The file name only, to keep tables readable.
  string file = netModel->path();
  size_t slash = file.find_last_of('/');
  if(slash != string::npos)
    file = file.substr(slash + 1);
  string s = "net:model=" + file + ",pop=" + Global::intToString(popId);
  if(temperature > 0.0)
    s += ",temp=" + Global::doubleToString(temperature);
  return s;
}

ArrangementInfo NetPlayer::arrange(CardSet hand, const DecisionContext& ctx, Rand& rand) const {
  static thread_local vector<ArrangementInfo> cands;
  static thread_local vector<float> q;
  HandTable(hand, ctx.rules).undominated(cands);
  if(cands.size() == 1)
    return cands[0];
  netModel->evaluate(hand, ctx.rules, popId, cands, q);
  size_t best = 0;
  for(size_t i = 1; i < q.size(); i++)
    if(q[i] > q[best])
      best = i;
  if(temperature <= 0.0)
    return cands[best];
  vector<double> weights(q.size());
  double total = 0.0;
  for(size_t i = 0; i < q.size(); i++) {
    weights[i] = std::exp((q[i] - q[best]) / temperature);
    total += weights[i];
  }
  double x = rand.nextDouble() * total;
  for(size_t i = 0; i < q.size(); i++) {
    x -= weights[i];
    if(x <= 0.0)
      return cands[i];
  }
  return cands[best];
}
