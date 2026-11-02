# Plan

How to build a strong Thirteen player, what exists now, and what to borrow from KataGo.

## 1. What makes this game different

- **One decision per hand.** Each player sets 13 cards once, without seeing anything from the others. There is no
  game tree to search.
- **The belief about the opponents is exact.** The whole deck is dealt, so the other 39 cards are a uniformly random
  split into three hands of 13. There is nothing to infer from.
- **The action space can be listed.** There are 72,072 splits of a hand, typically about 20,000 of them valid. Most
  are dominated: another valid arrangement is at least as strong in front, middle and bonus, and in back (equal back
  when `c > 0`). With `c = 0` about 15 to 250 remain; with `c > 0` about 500 to 2,500 (from a few random hands).
- **The only uncertainty is the opponents' cards and how they set them.** The value of an arrangement against a fixed
  opponent policy can be estimated by Monte Carlo as precisely as compute allows.
- **It is a symmetric simultaneous-move game.** Points are zero-sum; the dealer cost is not. "Strong" can mean a
  best response to a given population (people, heuristics), or low exploitability (close to an equilibrium).

So the "search" is Monte Carlo evaluation of the candidate arrangements, and a neural net's job is to provide a
fast, accurate value for each candidate: to act alone when time is short, to serve as a realistic opponent model,
and to narrow the candidates for Monte Carlo.

## 2. What exists (C++)

- Hand evaluation, arrangement enumeration with per-hand key tables, validity, bonuses, dominance pruning, scoring
  for all of docs/rules.md including the fouls variant. Unit tests check, among other things, the standard category
  counts over all 5- and 3-card hands, the table evaluator against a direct one on every hand, the rules' worked
  example, and that pruning never removes the best response.
- Players: `random`, `greedy`, `indep` (independent-rows estimate), `mc` (Monte Carlo best response).
- `match` (duplicate deals, 24 seatings each, multithreaded), `analyze`, `exploit`, `benchmark`.

**Fouls.** The main line forbids fouls, as the real rules do: players choose among valid arrangements only, and
mostly among undominated ones. Scoring keeps the "fouls allowed" variant (`DecisionContext::foulsAllowed`) for a
possible side experiment.

### 2.1 Speed and memory

One thread (`thirteen benchmark`):

| Step | Time |
|---|---|
| `eval5` (table lookup on the rank multiset) | ~0.08 µs |
| Per-hand key table (1,287 five-card and 286 three-card hands) | ~14 µs |
| All ~22,000 valid splits of a hand | ~100 µs |
| `greedy` | ~14 µs |
| Undominated candidates (~100 at `c = 0`, ~2,750 at `c = 0.5`) | ~1.1–1.2 ms |
| `indep` | ~0.3 ms |
| One Monte Carlo sample against three `greedy` (deal, three arrangements, score all candidates) | ~55–90 µs |
| `analyze`, 2,000 samples against `greedy` / against `indep` | ~0.2 s / ~1.9 s |

How:

- `eval5` and `eval3` look up a table indexed by the rank multiset (combinatorial number system on the sorted ranks;
  6,188 and 455 entries), with a separate flush table. The direct implementations stay as references for tests.
- A `HandTable` keys every 3- and 5-card subset of the hand once; enumeration then only reads keys. The middles for
  each back come from a static table (1,287 × 56).
- `greedy` only searches backs with the maximum key: the strongest five cards always have a valid split.
- Dominance pruning works per group of equal back keys, then against stronger backs (only when `c = 0`).
- Monte Carlo scores all candidates against a per-sample summary of the three opponents (`Scoring::Opponents`).
- `indep` precomputes each row hand's win and loss chances once per hand, then scans the valid splits.

Memory is small: the static tables are under 0.3 MB, per-thread scratch under 1 MB, and a 4-thread `match` peaks at
about 13 MB resident. CPU time is all that matters.

### 2.2 First results

- `match`, 200 deals, `c = 0.5`, with `greedy`, `indep`, `mc:samples=200,opp=greedy` and `random`: `indep` +0.98 and
  `mc` +0.99 per game, `greedy` −0.24, `random` −2.24.
- `exploit`, 400 hands, select 500 / eval 1,000 samples:

  | Player | `c = 0` | `c = 0.5` |
  |---|---|---|
  | `random` | | +5.09 ± 0.07 |
  | `greedy` | +0.85 ± 0.03 | +0.87 ± 0.03 |
  | `indep` | +0.20 ± 0.01 | +0.24 ± 0.02 |

  `indep` is far closer to an equilibrium than `greedy`, but a best response still gains about 0.2 per hand.
- The best response depends a lot on the opponents: for `Qh Ts Td 9s 9d 9c 8s 5c 4d 4c 3s 3d 2c` at `c = 0.5`, the best arrangement
  against `greedy` and the best against `indep` differ in every row.

## 3. Next steps

### 3.1 Evaluation

1. **Exploitability** (`exploit`, done) is the main measure of distance from equilibrium.
2. **Duplicate matches** (done) for comparing specific players, including 1v3 setups.
3. **Behavior against `c`.** For c in 0, 0.1, …, 1: dealer rate, average back strength, scoop rates. One of the
   things to look at once a net is conditioned on `c`.

### 3.2 Faster core

- Undominated candidates (~1 ms) are now the slowest step per decision. A tighter algorithm could help, but Monte
  Carlo over hundreds of samples costs far more.
- A cache of opponent hands and their arrangements for a fixed policy would let Monte Carlo reuse opponent work across
  decisions.

Sections 3.3 to 3.5 are implemented; see [training.md](training.md) for how to run them. They are kept here as the
original design notes. One change: training targets are each candidate's exact result against the actual opponents
of each deal, not Monte Carlo estimates, so Monte Carlo is not a ceiling on the network.

### 3.3 The network

**Input:** the 13 cards (52-dim), `c`, a fouls-allowed flag, and one candidate arrangement: per row, the category
(one-hot), the tie-break ranks, and the rank of the row among all hands of that row size (a percentile). Score each
candidate independently, or attend over the candidate set.

**Outputs per candidate:**

- `Q`: expected result against the current opponent population.
- Auxiliary heads, cheap and low-noise, in the spirit of KataGo's auxiliary targets: per opponent, P(I scoop them),
  P(they scoop me); P(I take the dealer cost); per row, P(my row beats a random opponent's row).

The policy is a softmax (or argmax) over candidates' `Q`. Candidates come from the dominance pruning, so the net never
needs to generate an arrangement; generating one card at a time is unnecessary.

**Fouls-allowed variant:** a possible side experiment only: score all 72,072 splits including fouls and track the
valid rate during training. Validity is trivial to enforce, so learning it says little about strength.

### 3.4 Training: iterated best response with Monte Carlo targets

A loop like fictitious play or PSRO:

1. **Population:** the latest net, earlier nets, `greedy`, `indep`, and later a model of human play.
2. **Data:** a random hand; `c` sampled from [0, 1] (more weight near the values in use); three opponents drawn from
   the population. Evaluate candidates with Monte Carlo: all undominated candidates, or the net's top k plus a few
   random ones. Targets are the Monte Carlo means and the auxiliary statistics, not single noisy outcomes.
3. **Train** `Q` by regression on those targets (weighted by their standard errors), plus the auxiliary heads.
4. **Add** the new net to the population and repeat.

The data generator needs net inference inside Monte Carlo, for opponents set by the net. The nets are small, so a
batched CPU forward pass in C++ (weights exported from PyTorch) is probably enough: batch all candidates of one hand,
or all opponent hands of many samples.

### 3.5 Human opponents

At the table the opponents are people, so the most useful player is a best response to a model of them. Write a few
human-like heuristics, or record real hands and arrangements and fit a net to them, then use `mc` against that model.

## 4. What to borrow from KataGo

| Part | Use here |
|---|---|
| `cpp/core` utilities | Copied: random numbers, hashing, timers, threads, string helpers, TCLAP. |
| NN evaluator, backends, multithreaded self-play | Little. There is no game tree and no long game; data generation is CPU-bound Monte Carlo. A small CPU inference path is simpler. |
| Training data window and shuffle (`python/shuffle.py`) | Copy the **power-law window size** (`compute_desired_num_rows`: the window grows like `rows^0.65`–`0.675` of the data so far). The rest of `shuffle.py` (sharding, two-phase merge across processes, md5 filtering) is built for very large Go datasets; rows here are small, so `.npz` shards plus random sampling from the window in the trainer is enough. |
| Training ideas | Auxiliary targets, SWA/EMA of weights, policy surprise weighting (weighting rows where Monte Carlo disagrees with the net), playout cap randomization (here: random sample counts per row, many cheap rows and some precise ones). |
