# Training

The network, self-play, the fictitious-play loop, and the human model. See [plan.md](plan.md) for why things are
designed this way.

## 1. Setup

### C++ engine with ONNX Runtime

Net players need ONNX Runtime (CPU build, about 6 MB to download, no root needed):

```bash
scripts/fetch_onnxruntime.sh            # into cpp/external/onnxruntime
cd cpp
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DONNXRUNTIME_ROOT=$PWD/external/onnxruntime
cmake --build build -j
build/thirteen runtests
```

Without `-DONNXRUNTIME_ROOT` everything except `net:` players builds and works.

### Python

Python 3.10 or later with `pip install -r python/requirements.txt` (PyTorch, NumPy, ONNX). Training uses CUDA when
available (`--device cuda --amp`).

## 2. The network (`python/thirteen/model.py`)

It scores every undominated valid arrangement (candidate) of a hand. Inputs are the raw integers the engine already
has: the 13 card ids, the scoring rules (14 numbers including the dealer cost `c`, docs/rules.md section 11), a
population id, and per candidate the three rows as position masks and the three row keys.

- **Per hand (most of the compute):** a Transformer (default 4 layers, width 128) over 13 card tokens (rank and suit
  embeddings) plus a global token (the rules, population embedding).
- **Per candidate (cheap):** the first hidden layer is the sum over rows of per-card projections (computed once per
  hand, then gathered by the row masks), plus projections of each row's key features (category, tie-break ranks) and
  of the global token; then one more hidden layer (default 256) and the outputs. A hand can have a few thousand
  candidates, so this part has to stay small.
- **Outputs per candidate:** the probabilities of the scoring events (`Scoring::Target`): scooping, being scooped,
  home runs, the dealer-cost share, per-row win and loss rates, and per royalty type the rate at which an opponent's
  row of that type beats mine. Plus, per hand, the opponents' expected royalties of each type and dragons. None of
  these are about points, so the expected result under any rules is composed from them exactly (`compose_q`, the same
  as `Scoring::compose`). Under the family rules:

  `Q = 3 scoop - 3 scooped + 3 home_run - 3 opp_home_run + 3 bonus + 3 dragon - c dealer_share - opp_bonus`

  and in general `3 row (wins - losses)` per row, the royalties (always, or times the row's win rate with `on_win`)
  and the opponents' royalties are added. So the rules enter Q exactly, and the net only has to learn how opponents
  behave under them. The losses stay per event, so rules with large points (a dragon worth 36) do not dominate
  training.

About 1.0M parameters, a 4 MB ONNX file. One evaluation takes about 1 ms for 100 candidates and 10 ms for 2,750
candidates on one desktop core.

**Targets and losses.** The targets are exact, not estimates: after a deal, every seat's actual opponents are known,
so each candidate of each seat is scored exactly against them (`Scoring::Opponents::targetsOf`). Losses are binary
cross-entropy per event and squared error for the opponents' royalties. One deal is one noisy sample of the opponents;
averaging over many deals is the network's job. Because every candidate gets a target whatever the seat played, no
exploration is needed.

## 3. Self-play (`thirteen selfplay`)

```bash
build/thirteen selfplay --pop pop.txt --pop-id 0 -n 100000 -o data/iter_3 --ranker net:model=models/net_2.onnx -t 32
```

- **Population file:** lines of `WEIGHT SPEC`. Each seat of each deal draws a member by weight.
- **Rules:** `--rules mix` (the default) draws them per deal: with probability `--preset-prob` (0.5) one of the
  presets of docs/rules.md section 11, otherwise random values around them (rows count or not, scoop and home-run
  points, royalties, `on_win`, dragon), always with royalties that never decrease with the category. `--rules SPEC`
  fixes them (e.g. `--rules family` for the original game).
  The random half also draws strict rows (30%) and the ranking of A-2-3-4-5 (70% lowest, 15% second highest, 15% no
  straight).
- **`c`:** drawn from `--c-dist`, by default `0.7:0,0.1:0.5,0.1:1,0.1:u`: mostly 0, since the dealer cost is a side
  experiment, `u` being uniform in [0, 1]. c = 0 also prunes many more candidates, so those deals are faster. All four
  seats use the same rules and `c`.
- **Candidates per row:** up to `--cands` (32). When a hand has more, `--topk` (8) are the ranker's best and the rest
  a uniform sample. Seats played by the ranker's own net reuse its Q.
- **Output:** `.npz` files of `--rows-per-file` compact rows (`cpp/dataio/trainingrows.h`): the deal with all four
  arrangements, the rules and the candidates' position masks, about 245 bytes per row with 32 candidates.
  Like KataGo's packed inputs, nothing that can be recomputed is stored: the trainer keeps rows in this form and
  expands each batch, with the row keys and the exact targets from the C++ engine through ctypes
  (`cpp/build/libthirteen_data.so`, built with the binary; `train.py --data-lib`). The layout is in
  `python/thirteen/data.py`.

## 4. The loop (`python/fp_loop.py`)

Fictitious play over a growing population:

1. The population is the base players (default `greedy` and `indep`) plus all networks so far. The networks share
   `--net-share` (0.8) of the weight: `--latest-share` of it goes to the newest, the rest is uniform. Uniform alone is
   fictitious play (aims at an equilibrium); all on the newest is plain self-play.
2. Self-play into `WORKDIR/data/iter_k`.
3. Train from the previous checkpoint on the newest data window, export `net_k.onnx` (EMA weights).
4. Optional duplicate match of `net_k` against `indep`, `greedy` and `net_{k-1}`.

```bash
python python/fp_loop.py --workdir run --binary cpp/build/thirteen --iterations 20 \
    --deals 250000 --threads 32 --train-steps 20000 --device cuda --amp \
    --train-args "--batch 512 --min-rows 1000000"
```

**Rules.** `fp_loop.py --rules mix --preset-prob 0.5` (the defaults) pass the rules to self-play; `--eval-rules`
lists the rules of the evaluation matches, e.g. `"family,c=0.5;taiwan,c=0.5;pagat,c=0"`, and `--eval-baseline` sets
their fourth player.

**Networks from before the rules input** (an input `c` instead of `rules`, 11 targets):

- The C++ engine still plays them (`NetModel::legacy`), as under the family rules whatever the rules are.
- `--init CHECKPOINT` starts a new run's first training from one. `model.upgrade_legacy` gives the old dealer-cost
  weights to the `c` feature and zero weights to the other rules, so it plays exactly as before under the family
  rules, and the new outputs start untrained.
- Old data files load as the family rules with their `c`.
- Networks, checkpoints and data with 12 rules (before `strict` and `wheel`, like `rules1`) work too: the engine gives
  them the first 12 numbers, a checkpoint gets zero weights for the two new ones, and data loads with both 0.

A first check (2026-10-31, `rules1`): 3 iterations of 20,000 mixed-rules deals from run1's `net_10.pt`, 1,500 steps
each, about 17 minutes on the desktop alone. Matches of 1,000 deals with `indep`, `greedy` and the old `net_10`:

| Rules | new `net_3` | old `net_10` |
|---|---:|---:|
| `family`, c = 0.5 | +0.141 | +0.132 |
| `taiwan`, c = 0.5 | +1.50 | +1.26 |
| `pagat`, c = 0 | +1.47 | +1.33 |

Head to head under `family` (two of each), they are even (−0.125 each), so learning the other rules cost nothing
there.

**Training window.** `train.py` loads the newest rows into memory, with KataGo's power-law window size
(`--min-rows`, `--taper-exponent` 0.65, `--expand-per-row` 0.4). 1M rows take about 190 MB.

**Two machines (`python/remote_loop.py`).** Self-play on a CPU workstation (`--host`), training on a local GPU. Run
it on the GPU machine; it drives the workstation over ssh:

- The workstation runs `fp_loop.py --stage selfplay` detached (`setsid`), so it survives a dropped connection, and
  marks the iteration with `DONE` (or `FAILED`) in `REMOTE_ROOT/runs/RUN/data/iter_k`, where REMOTE_ROOT is
  `--remote-root` (relative to the remote home, default `thirteen`) and RUN is the local workdir's name
  (`--remote-run`).
- The GPU machine pulls finished `.npz` files every `--poll` seconds with `rsync -z` (zstd, about 1.7 times smaller on
  the wire), trains once the iteration is done, and pushes `net_k.onnx` back.
- With overlap (the default), the workstation starts iteration k + 1 as soon as k is done instead of waiting for `net_k`, so the
  population and ranker of iteration k + 1 are one network behind. `--no-overlap` waits.
- Restarting is safe: finished stages are found from the files, and a running remote self-play is waited for, not
  restarted. `touch WORKDIR/STOP` stops after the current iteration (the next iteration's remote self-play is not
  started then).
- The GPU machine keeps only the newest `--keep-iters` (10) iterations of data; the workstation keeps all of it.
- It logs in with a restricted key without a passphrase (`~/.ssh/thirteen_sync`), so it needs no ssh-agent. On the
  workstation that key may only run `scripts/remote_gate.sh`: rsync inside `REMOTE_ROOT/runs` (rrsync) and starting or querying one iteration's self-play with whitelisted options.
  `--selfplay-args` therefore takes only `--deals --threads --cands --topk --net-share --latest-share --human
  --human-deals --rules --preset-prob --c-dist`. `--ssh-key none` uses the normal login (the agent) instead.

The key, once (the second part is one line in the workstation's `~/.ssh/authorized_keys`, with the absolute path
of REMOTE_ROOT):

```bash
ssh-keygen -t ed25519 -N "" -C thirteen_sync -f ~/.ssh/thirteen_sync
```

```
command="REMOTE_ROOT/src/scripts/remote_gate.sh",restrict <contents of ~/.ssh/thirteen_sync.pub>
```

Setup on the workstation, once and after C++ changes (build there: the binary uses `-march=native`):

```bash
rsync -az --exclude .git --exclude 'build*/' --exclude cpp/external/onnxruntime ./ HOST:thirteen/src/
```

```bash
ssh HOST 'cd thirteen/src && scripts/fetch_onnxruntime.sh && cd cpp && cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DONNXRUNTIME_ROOT=$PWD/external/onnxruntime && cmake --build build -j32 && build/thirteen runtests'
```

Then on the GPU machine:

```bash
python python/remote_loop.py --host HOST --workdir runs/run2 --iterations 20 --deals 250000 --threads 32 \
    --train-steps 10000 --device cuda --amp --train-args "--batch 512 --min-rows 1000000" --eval-deals 2000
```

Logs on the workstation: `REMOTE_ROOT/runs/RUN/logs/selfplay_K.out` and `iter_K.log`. A single stage can still be run by hand
with `fp_loop.py --stage selfplay|train|eval --iteration K`.

**Measured throughput (2026-10-31).**

| | |
|---|---|
| Self-play, 32 threads, net players | ~210 deals/s, ~840 rows/s, ~160 KB/s of data; 250,000 deals in ~20 min; 1.2 GB RSS |
| Self-play, iteration 1 (`greedy`, `indep` only) | much faster (~2,000 deals/s on 10 desktop threads) |
| Transfer | data is ~110 B/row on the wire, ~90 KB/s at the rate above |
| Training on a desktop GPU, batch 512, bf16 | ~39,000 rows/s: 10,000 steps take ~2-3 min |

So self-play is the bottleneck; the GPU only works a few minutes per iteration, and the transfer keeps up easily
even on a slow link. One iteration of 1M rows takes ~190 MB on disk.

**Check an export.** `python python/check_onnx.py --binary cpp/build/thirteen --checkpoint run/checkpoints/net_5.pt
--onnx run/models/net_5.onnx --ema` compares C++ and PyTorch Q values.

**Half the file.** `python python/fp16_weights.py net_5.onnx net_5.fp16.onnx` stores the weights as float16 (the
network still computes in float32; Q moves by ~0.0001 on average). The app's built-in network is stored so; the
engine, the server and the app load either kind.

**Evaluate.** Net players work everywhere a player spec does:

```bash
build/thirteen match -p net:model=run/models/net_5.onnx -p indep -p greedy -p net:model=run/models/net_4.onnx -n 5000 -c 0.5
build/thirteen exploit -p net:model=run/models/net_5.onnx -n 200 -c 0.5
```

## 5. The human model

### Recording

Open `gui/collect.html` in a browser (it needs `gui/thirteen-eval.js` next to it). Each hand is dealt at random;
select cards and click a row (or press 1, 2, 3) to place them; click a placed card to take it back. Submit is enabled
once the rows are full and valid. Records stay in the browser's storage; **Export JSONL** downloads them, **Import**
merges files (duplicates are skipped by id). Set the dealer cost you have in mind at the top; it is saved with each
hand.

### Fitting

```bash
build/thirteen humanfeatures -i records.jsonl -o human.npz
python python/train_human.py --data human.npz --out human.txt
```

The model is a conditional logit over all valid arrangements of each hand: P(arrangement) is proportional to
exp(w · x), where x has 30 interpretable features (row categories, row strength percentiles and their squares, row
bonuses, and the dealer cost times the back's strength). With tens to hundreds of hands, this is about as much as the
data supports; the L2 penalty is chosen by cross-validation over hands, and the script reports held-out
log-likelihood, top-1 and top-5 accuracy. Arrangements with equal row keys are merged with their count, so suits do not
matter. Expect about 0.5 MB of features per hand.

Sanity check on a known player: fitted to 200 hands of `greedy` (`thirteen record -p greedy -n 200 -o greedy.jsonl`),
it predicts greedy's exact choice 66% of the time on held-out hands (80% within its top 5), and in a match it scores
like greedy.

### Using it

- As a player: `human:weights=human.txt` (samples like the fitted model) or `human:weights=human.txt,temp=0` (always
  the most likely arrangement).
- As a population to respond to: `fp_loop.py --human human:weights=human.txt --human-deals 50000` adds self-play
  against the human model with population id 1. Then `net:model=...,pop=1` is the network's best response to the
  human model, and `pop=0` its response to the league.
- With more data later: fine-tune a copy of the network to predict the recorded choices (a softmax over candidates'
  scores), starting from the self-play network's encoder.
