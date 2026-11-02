# Thirteen (十三張)

An AI for Thirteen (Chinese poker), four players, as played in the author's family: each player sets 13 cards into
rows of 3, 5 and 5, and scores only by scooping opponents, plus a few bonus hands. The player with the largest back
row pays a dealer cost `c`.

- Rules: [docs/rules.md](docs/rules.md). Other common scoring rules (rows count, royalties, ...) are numbers the
  programs and the network take as input: section 11 and `--rules` below.
- Plan: [docs/plan.md](docs/plan.md).
- Training the network and the human model: [docs/training.md](docs/training.md).
- Battles (puzzle contests and table games between people, online or nearby), the server and its security: [docs/battle.md](docs/battle.md).

The players and tools follow the real rule that fouls are not allowed. The scoring code also implements the
"fouls allowed" variant of the rules, for experiments.

## Layout

| Path | What |
|---|---|
| `cpp/core/`, `cpp/external/tclap-1.2.5/` | Utilities copied unchanged from KataGo v1.18.2 (random numbers, hashing, timers, threads, command-line parsing). See `LICENSE-KataGo`. |
| `cpp/game/` | Cards, hand evaluation, arrangements (enumeration, validity, bonuses, dominance pruning), scoring. |
| `cpp/players/` | Players: random, greedy, independent-rows estimate, Monte Carlo best response. |
| `cpp/players/netplayer.*`, `cpp/players/human.*` | The network player (ONNX Runtime) and the human model player. |
| `cpp/dataio/` | `.npz` writer and the compact training rows; `libthirteen_data.so` expands them for the trainer. |
| `cpp/command/` | Subcommands; `thirteen help` lists them. |
| `cpp/tests/` | Unit tests. |
| `cpp/external/nlohmann_json/` | JSON parsing, copied from KataGo v1.18.2. |
| `python/` | The network (`thirteen/model.py`), data window, training, the fictitious-play loop, the human model fit. |
| `gui/` | `play.html`: play and analysis (with `python/play_gui.py`); `collect.html`: records how you set hands, for the human model. |
| `scripts/fetch_onnxruntime.sh` | Downloads ONNX Runtime for net players. |
| `protocol/` | Battle rooms and their messages (Kotlin), shared by the server and the app. |
| `server/` | The battle server (Kotlin, Ktor) and its Docker deployment. |
| `android/` | The Android app: [android/README.md](android/README.md). |

## Building

C++17, CMake 3.18 or later. Net players also need ONNX Runtime; see [docs/training.md](docs/training.md).

```bash
cd cpp
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j
build/thirteen runtests
```

`-DUSE_NATIVE=OFF` drops `-march=native`.

## Commands

### `analyze`: the best arrangements of one hand

```bash
build/thirteen analyze --hand "Qh Ts Td 9s 9d 9c 8s 5c 4d 4c 3s 3d 2c" -o greedy -n 2000 -c 0.5
```

Deals the 39 unseen cards `-n` times, lets three opponents of type `-o` set their hands, and reports the average
result of each undominated valid arrangement, with standard errors. It also shows where the arrangements of `greedy`
and `indep` (or the `-p` players) rank.

### `match`: duplicate deals between players

```bash
build/thirteen match -p mc:samples=200 -p greedy -p greedy -p greedy -n 2000 -c 0.5
```

Every deal is played 24 times, once for each way of giving the four hands to the four players, so luck of the deal
cancels out. With more than four `-p` players, each deal picks four at random. The table reports the mean result per
game with its standard error over deals, the dealer rate, scoops made and taken, home runs, bonuses and fouls.

### `exploit`: how much a best response gains against a player

```bash
build/thirteen exploit -p indep -n 400 -c 0.5
```

For random hands, a Monte Carlo best responder against three copies of the player picks an arrangement with
`--select-samples` deals, and then it and the player's own arrangement are compared on `--eval-samples` fresh deals.
The mean gain is an unbiased estimate for this responder and a lower bound on the player's exploitability.

### `benchmark`

Times hand evaluation, enumeration, the players and Monte Carlo on one thread.

### Play GUI

```bash
python python/play_gui.py --models run/models [--human human.txt]
```

Opens a local page (`gui/play.html`, served at http://127.0.0.1:8767/) backed by one `thirteen gui` engine process:

- **Play:** you (or any player, to watch) against three players of your choice, at any `c`. Hint shows the model's
  top 5; after the show down it scores the deal (scoops, home runs, dealer, bonuses) and tells you where your
  arrangement ranks by the model's Q. Session totals per player are kept in the browser.
- **Rules:** a preset and optional overrides (e.g. `scoop=2,on_win=1`) next to c apply to both tabs.
- **Analyze:** pick 13 cards (or type or deal them). It lists every undominated arrangement with the model's Q and
  event probabilities (scoop, scooped, home runs, dealer share, per-row win and loss), sortable; marks what other
  players or your own arrangement (`front | middle | back`) would play; checks the top rows by Monte Carlo against
  any opponent type; and shows how the model's choice changes with `c` (c sweep).

### Rules

`match`, `analyze`, `exploit`, `record` and `netq` take `--rules PRESET[,name=value,...]` (presets `family`, the
default, `family_mid2`, `taiwan`, `pagat`; names in docs/rules.md section 11, including `strict=1` for strictly
increasing rows and `wheel=1|2` for the ranking of A-2-3-4-5) and `-c` for the dealer cost:

```bash
build/thirteen match -p indep -p greedy -p greedy -p random -n 1000 --rules taiwan -c 0.5
```

### Player specs

| Spec | Player |
|---|---|
| `random` | A uniformly random valid arrangement. |
| `greedy` | The strongest back, then middle, then front. |
| `indep[:hands=N]` | Maximizes an estimate that treats the rows of each opponent as independent draws from the row distributions of greedy players (measured over `N` hands, default 4000). |
| `net:model=PATH[,pop=P,temp=T]` | A network: the best Q among undominated candidates, responding to population `P` (default 0); with `T > 0`, samples with probability proportional to exp(Q / T). |
| `human:weights=PATH[,temp=T]` | The human model fitted by `python/train_human.py`; samples from it (`temp=0`: its most likely choice). |
| `mc[:samples=N,opp=SPEC]` | Monte Carlo best response to three `SPEC` opponents (default greedy) over `N` deals of the unseen cards (default 500). `opp=` comes last and takes the rest of the spec, so it can have its own parameters. |

## License

GPLv3: see [LICENSE.md](LICENSE.md). The files copied from KataGo (`cpp/core/`, `cpp/external/`) keep their MIT
license, in `LICENSE-KataGo`.
