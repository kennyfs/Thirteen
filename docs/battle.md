# Battles

Two modes, played in the app's Multiplayer tab against people on a server or on phones nearby:

- **Puzzle contest** (1 or more players; alone, a challenge): everyone sets the same hands, in the same order. Each hand costs what the
  player's arrangement loses against the network's best (its best Q minus the player's Q, under the room's rules);
  the least in total wins. No luck: only the choices count. Standings are shown live, after each hand closes.
- **Table game** (1 to 4 players): one table, networks in the empty seats, scored as usual; the most points wins.

The host sets the number of hands (1 to 30), the rules (the Rules page's, then fixed for the game), a time limit
per hand, and for a puzzle the **level** of its hands. After the game the hands are listed with their cards;
each opens the analysis of the player's hand, with the others' choices marked (a puzzle) or the table's result (a
table game). Every hand played also goes to the records, labelled `battle`.

## Puzzle levels

A puzzle deals at one of five levels: 1 easy, 2 random, 3 to 5 harder and harder. Levels change how hard a hand is
to arrange, not how good it is: harder hands chosen among random ones would otherwise lean to weak hands, which
have many shapes of about the same worth and none clearly best.

A hand's **strength** is the network's best Q. How **hard** it is (`Difficulty.score`) has two parts, in units of
the app's `notableQGap` g (a red mark's worth: 0.1 under family, scaled with the scoring):
- the other shapes close to the best, a shape being the rows' categories (pair / straight / flush, ...) and each
  counted by its best arrangement: 1 - gap / 5g, nothing from a gap of 5g;
- what the better of two simple strategies, greedy (the strongest back, then middle, then front) and `indep` (rows
  judged one by one), loses against the best, over 3g.

Strong hands have fewer close shapes but lose more to simple play, so the sum hardly depends on the strength
(correlation -0.02 over 40,000 random hands under family, against +0.22 for the loss alone and -0.31 for the
shapes alone). Level 2 deals a random hand. The others draw 3n random hands (`Levels`), keep the n closest in strength
to the first one drawn, and deal the easiest (level 1, n = 4) or the hardest (levels 3, 4, 5: n = 3, 6, 12). Over the
same 40,000 hands the median best Q stays at -0.11 to -0.20 at every level, and greedy or indep lose at least 0.1
in 7% (level 1), 34% (random), 58%, 68% and 68% of the hands.

A client before levels sends only `hard`, dealt as level 4; clients send `hard` = level >= 3 for servers before
levels, which deal hard hands as before.

## Structure

| Part | What |
|---|---|
| `protocol/` | The messages (`Messages.kt`), checks of everything a client sends (`Validation.kt`), a room (`Room.kt`), the rooms of a server (`Hub.kt`), and what a room asks of the engine (`GameEngine.kt`). Pure Kotlin, shared by the server and the app; `RoomTest` covers the rules of play and the limits. |
| `server/` | Ktor over WebSockets (`/ws`, plus `/health`), with a pool of `thirteen gui` processes as the engine. `server/Dockerfile`, `server/compose.yml` and `server/Caddyfile` deploy it. |
| `android/.../battle/` | The client: links to a server (OkHttp WebSocket) or to a phone nearby (Nearby Connections), and a room hosted on this phone (the same Hub and Room, with the app's engine). |

Messages are JSON, one per WebSocket frame or Nearby payload, with their type in `t`: a client says `hello` (with
the protocol version), then `create`, `join` (a 6-digit PIN) or `resume` (a room id and the secret token it was given
on joining, after a dropped connection); then `settings` and `start` (the host), `submit` and `ready`. The room sends
`state` (the room as everyone may see it), `deal` (the player's own hand only), `result` (a closed hand), `history`
(after a resume) and `error`.

## Fair play and security

The room (on the server, or on the hosting phone nearby) is the only authority:

- **Dealing** uses `SecureRandom`. A player is sent their own hand only (in a puzzle everyone's is the same), and
  nothing about the others' plays until the hand closes, except whether each has set it. The network's best and
  the losses are only sent once the hand is closed for everyone.
- **A play** is accepted once per hand, only for the open hand, only with exactly the dealt cards (checked in Kotlin
  before the engine sees it), and only if the engine finds it valid. Scores and losses are computed by the room, never
  taken from a client.
- **Running out of time** gives greedy's arrangement, never the network's, so waiting out the clock cannot pay. A
  hand without a time limit still closes after 10 minutes; a disconnected player is waited for 30 seconds once
  everyone else has set the hand, and can take the seat back with its token.
- **Only the player's current connection** acts for its seat; a resume elsewhere closes the old one.
- **Text from clients** is parsed in one place (`Hub.receive`) into typed messages. Rules are checked against a list
  of names and ranges and rebuilt as a spec before the engine sees them; names lose control and format characters
  (no bidirectional overrides) and are cut at 16 characters. Nothing a client sends reaches the engine as text.

What the server limits (environment variables in `server/compose.yml`):

| Limit | Default |
|---|---|
| Message size (WebSocket frame) | 4 KB |
| Messages per connection | 5 per second, bursts of 20; bad or refused messages drop the connection after 20 |
| Connections | 2,000 in all, 16 per client address (an IPv6 address counts by its /64) |
| Rooms | 200 in all; 3 open and 20 created per hour per address |
| Wrong PINs or tokens | 10 per address per 10 minutes, then joins from it are refused for that window |
| Players in a puzzle | 64 |
| Room lifetime | 30 minutes idle in the lobby, 10 minutes after the end, 6 hours at most |
| Messages waiting for a slow client | 256, then it is dropped |

**The model's cost.** The network runs only in the engine pool (`ENGINE_WORKERS` processes, 2 by default, each
with one ONNX Runtime thread), so it never takes more than that many cores whatever the rooms do. At most
`ENGINE_QUEUE` (64) requests wait; beyond that a request fails at once and its room closes instead of piling up. A
request that takes longer than `ENGINE_TIMEOUT_MS` (10 s) kills its process, which is started again. Only rooms ask
the engine anything, and only what the game needs: per hand a bounded number of requests (the puzzle level's search, the
networks' seats, greedy for timeouts, the score or the losses), and each room has a budget it cannot exceed. There
is no request a client can make that runs the network on its own; analyses after the game run on the phone. A
puzzle hand's analysis is one request for all its players. For a sense of scale: one hand's analysis takes about
1 to 10 ms on one core, the level search about 10 ms per hand drawn (36 at level 5).

**Memory.** About 70 MiB idle and about 160 MiB with 30 rooms playing at once (half table games, half hard
puzzles): the JVM (heap at most 128 MiB) about 110 MiB, and each engine process about 47 MiB. More load than the
engine queue takes is refused, not queued, so it does not grow much further; the compose file limits it to 384 MiB.

**Deployment.** Caddy gets the TLS certificate and is the only thing exposed (ports 80 and 443); it passes `/ws` and
`/health` and answers 404 to everything else. The game server is on an internal network with no way out, runs as
an unprivileged user on a read-only file system with all capabilities dropped and `no-new-privileges`, with limits
on CPU, memory and processes. The model is mounted read-only and checked against its SHA-256 at start (the server
refuses to start otherwise). The server keeps no records: rooms live in memory and are gone when they end; it logs
neither hands nor tokens. ONNX Runtime's download in the image build is checked against its SHA-256. For a fully
reproducible build, pin the base images by digest as well.

**What the server cannot see.** A player can still ask another phone (or this app's analysis on another phone)
for the best arrangement during a puzzle; the time limit makes that slower, nothing can make it impossible. A
modified app can claim any version: the app's version and signature reported by a client prove nothing. Making
sure clients are the published app would take Play Integrity (Google's signed verdict, checked by the server), once
the app is on the Play Store; it does not work offline.

**Nearby play.** The host's phone runs the room (the same code), and computes the networks' seats. The other
players get the same protections against each other, but the host's phone has every hand: a modified app on the
host could cheat. Nearby Connections encrypts the connections; joining still takes the room's PIN, and failed
PINs from nearby phones are limited together. Nearby needs Google Play services and the Bluetooth and nearby
devices permissions; it uses Bluetooth and Wi-Fi directly, without a network.

## Running a server

The image builds from the repository root. Put the network (`run2_net10.onnx` from the release) in
`server/models/`, point a DNS name at the host, then:

```bash
DOMAIN=thirteen.example.com docker compose -f server/compose.yml up -d --build
```

The app starts with the project's server, `thirteen.1li.tw`. In the app, the server's address is that name (`thirteen.example.com`; the app adds `wss://` and `/ws`). A server
on the same machine as an emulator, without TLS, is `ws://10.0.2.2:PORT` (only localhost, 127.0.0.1 and 10.0.2.2 may
go without TLS).

On Kubernetes, the `bundled` target has the network inside (fetched from the release and checked); behind
Cloudflare and then an ingress, set `TRUST_PROXY=2` so that limits apply to the client's address, not
Cloudflare's:

```bash
docker build -f server/Dockerfile --target bundled -t ghcr.io/kennyfs/thirteen-server:VERSION .
```

Without Docker, for development:

```bash
cd server && gradle installDist
THIRTEEN_BINARY=../cpp/build/thirteen THIRTEEN_MODEL=../android/app/src/main/assets/run2_net10.onnx PORT=18080 build/install/thirteen-server/bin/thirteen-server
```

`gradle test` in `server/` runs the room tests and a game over WebSockets with the real engine (skipped without
`cpp/build/thirteen` and the model).
