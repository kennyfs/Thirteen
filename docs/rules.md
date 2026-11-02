# Rules of Thirteen (十三張)

These are the rules this project implements: the house rules of the author's family, for four players. Where
anything else conflicts with this file, this file wins. Section 11 describes the other scoring rules the programs also play.

Terms used throughout:

| Term | Chinese | Meaning |
|---|---|---|
| Front / middle / back | 前墩 / 中墩 / 後墩 | The three rows a player sets: 3, 5 and 5 cards. |
| Row hand | 牌組 | The cards a player puts in one row. |
| Scoop | 打槍 | Beating one opponent in all three rows. |
| Home run | 全壘打 | Scooping all three opponents. |
| Foul | 倒水 | An arrangement whose rows are not in non-decreasing order. |
| Dealer cost | 洗牌成本 | The cost of having to shuffle and deal the next hand. |

## 1. Players and cards

1. There are exactly 4 players.
2. A standard 52-card deck is used, with no jokers. Each player is dealt 13 cards, so the whole deck is dealt.
3. Ranks from lowest to highest: 2, 3, 4, 5, 6, 7, 8, 9, 10 (T), J, Q, K, A.
4. **Suits never rank.** Suits matter only for deciding whether cards form a flush or a straight flush. No comparison
   is ever decided by suit.

## 2. Setting the hand

Each player splits their 13 cards into three rows, without seeing anyone else's cards or arrangement:

- **front**: 3 cards,
- **middle**: 5 cards,
- **back**: 5 cards.

All players reveal their arrangements at the same time. There is no further play.

## 3. Hand categories

### 3.1 Five-card hands (middle and back)

From highest to lowest:

| Rank | Category | Chinese | Definition |
|---|---|---|---|
| 9 | Straight flush | 同花順 | A straight whose five cards are all of one suit. |
| 8 | Four of a kind | 鐵支 | Four cards of one rank, plus any fifth card. |
| 7 | Full house | 葫蘆 | Three cards of one rank and two cards of another rank. |
| 6 | Flush | 同花 | Five cards of one suit that are not a straight flush. |
| 5 | Straight | 順子 | Five cards of consecutive ranks, not all of one suit. |
| 4 | Three of a kind | 三條 | Three cards of one rank and two cards of two other, different ranks. |
| 3 | Two pair | 兩對 | Two cards of one rank, two cards of another rank, and a fifth card of a third rank. |
| 2 | One pair | 一對 | Two cards of one rank and three cards of three other, different ranks. |
| 1 | High card | 單張 (烏龍) | None of the above. |

Straights:

- The ace may be high (10-J-Q-K-A) or low (A-2-3-4-5).
- Straights do not wrap around: Q-K-A-2-3 is not a straight.
- A straight is ranked by its highest card, with **A-2-3-4-5 counted as 5-high**. So the order from lowest to highest
  is A-2-3-4-5, 2-3-4-5-6, …, 9-10-J-Q-K, 10-J-Q-K-A. The same order applies to straight flushes.

### 3.2 Three-card hands (front)

The front row has only three categories, from highest to lowest:

| Rank | Category | Definition |
|---|---|---|
| 4 | Three of a kind | Three cards of one rank. |
| 2 | One pair | Two cards of one rank and one card of another rank. |
| 1 | High card | Three different ranks. |

Three cards of one suit, or three consecutive ranks, do not form any special category in the front row. The rank
numbers are shared with §3.1 so that a front hand and a five-card hand can be compared (§5).

## 4. Comparing two hands

A hand of a higher category beats a hand of a lower category. Two hands of the same category are compared by
their **tie-break sequence**, a list of ranks compared one entry at a time from the first entry: the first
difference decides, and the higher rank wins.

| Category | Tie-break sequence |
|---|---|
| Straight flush, straight | The highest card of the straight (5 for A-2-3-4-5). |
| Four of a kind | The rank of the four cards. |
| Full house | The rank of the three cards. |
| Flush | All five ranks, from highest to lowest. |
| Three of a kind | The rank of the three cards. |
| Two pair | The higher pair's rank, then the lower pair's rank, then the fifth card's rank. |
| One pair | The pair's rank, then the other cards' ranks from highest to lowest (three in a five-card hand, one in a front hand). |
| High card | All ranks, from highest to lowest (five in a five-card hand, three in a front hand). |

If the whole sequences are equal, the two hands are **equal (a tie)**. Because suits never rank, ties are possible
for straight flushes, flushes, straights, two pair, one pair and high card. Ties are impossible for four of a kind,
full house and three of a kind, since only one player can hold three or four cards of a rank.

Examples:

- `K♠ K♥ 9♣ 7♦ 3♠` beats `K♦ K♣ 9♠ 6♥ 4♥` (pairs equal, 9 = 9, then 7 > 6).
- `A♥ 9♥ 7♥ 4♥ 2♥` and `A♣ 9♣ 7♣ 4♣ 2♣` tie.
- `A♠ 2♦ 3♣ 4♥ 5♠` loses to `2♣ 3♦ 4♠ 5♥ 6♣`.

## 5. Valid arrangements and fouls

An arrangement is **valid** when

    front ≤ middle ≤ back

where ≤ means "loses to or ties", using §4. Equal rows are allowed.

Comparing the front (3 cards) with the middle (5 cards) uses the same procedure: compare categories, then the
tie-break sequences entry by entry. When the front's sequence runs out with all entries equal, the two rows count
as equal, and the arrangement is valid. For example, front `A K Q` against middle `A K Q 5 3`, or front `8 8 K`
against middle `8 8 K 7 3`, count as equal.

An arrangement that is not valid is a **foul** (倒水).

- **Real games:** fouls are not allowed. A player must choose a valid arrangement. One always exists: for example,
  sort the cards and put the 5 highest in the back, the next 5 in the middle and the lowest 3 in the front.
- **Variant for programs (`fouls allowed`):** a player may submit a foul, which is scored as in §8. This lets a
  learning program discover validity by itself. Everything else in these rules is the same with or without this
  variant.

## 6. Scoring between players

Points move between players; every point one player receives is paid by another. Each of the 6 pairs of players is
compared separately.

### 6.1 Scoop (打槍)

Player A **scoops** player B when A's front beats B's front, A's middle beats B's middle **and** A's back beats B's
back, each **strictly**. A tie in any row means no scoop.

When A scoops B, B pays A **1 point**. Otherwise no points change hands between A and B for row comparisons; winning
only one or two rows earns nothing.

### 6.2 Home run (全壘打)

When a player scoops all three opponents, each of the three additionally pays them **1 point**. So the home-run
player receives 2 points from each opponent, 6 in total.

### 6.3 Special bonuses

Each item below earns its holder **1 point from each of the other three players** (3 points in total):

| Bonus | Chinese | Condition |
|---|---|---|
| Front trips | 衝三 | The front row is three of a kind. |
| Middle four of a kind or straight flush | 鐵支 / 同花順 | The middle row is four of a kind or a straight flush. |
| Back four of a kind or straight flush | 鐵支 / 同花順 | The back row is four of a kind or a straight flush. |
| Dragon | 一條龍 | The 13 cards contain all 13 ranks, 2 through A. A dragon of one suit (同花一條龍) is also worth only 1. |

Details:

- A bonus counts only for a row as it is set. Four cards of a rank that are split across rows earn nothing.
- Bonuses are counted per row and add up. For example, a straight flush in the middle and four of a kind in the back
  earn 2 bonuses, 6 points in total.
- A straight flush earns one bonus, not one for being a straight flush and another for anything else.
- The dragon depends only on the 13 cards, not on how they are set. A dragon holder must still set a valid
  arrangement, and is compared and scored normally in addition to the dragon bonus.
- Bonuses do not depend on winning or losing any row, and add to any scoop and home-run points.

## 7. Dealer cost

In real games, the player with the largest back row shuffles and deals the next hand. This is modeled as a cost:

- Let `c` be the dealer cost, with `0 ≤ c ≤ 1`. All players use the same `c`.
- Among the players **who did not foul**, find the largest back row (§4). Every player whose back row equals it
  takes an equal share of the cost: with `k` such players, each loses `c / k`.
- If every player fouled, nobody takes the cost.
- The cost is not paid to anyone; it only lowers the dealer's result.

## 8. Fouls (only with `fouls allowed`)

- A fouled player is treated as scooped by every player who did not foul: they pay each such player 1 point.
- Two fouled players do not score against each other.
- A player who did not foul and scoops all three opponents, whether by beating their rows or because they fouled, gets
  a home run (§6.2).
- A fouled player earns no special bonuses, not even a dragon, and can never take the dealer cost.

## 9. Result of a hand

A player's **result** for one hand is

    (points received) − (points paid) − (their share of the dealer cost)

The points part sums to zero over the four players; the dealer cost does not. Programs aim to **maximize the
expected result**. Placement among the four players does not matter.

## 10. Worked example

Assume no fouls, `c = 0.5`, and:

- Player 1 scoops players 2, 3 and 4: a home run.
- Player 2 scoops player 4. Players 2 and 3 do not scoop each other, and neither do players 3 and 4.
- Player 3 holds a dragon. No other bonuses.
- Player 1's back row is the largest; nobody ties it.

| Item | P1 | P2 | P3 | P4 |
|---|---:|---:|---:|---:|
| P1 scoops P2, P3, P4 | +3 | −1 | −1 | −1 |
| P1's home run | +3 | −1 | −1 | −1 |
| P2 scoops P4 | | +1 | | −1 |
| P3's dragon | −1 | −1 | +3 | −1 |
| Dealer cost (P1) | −0.5 | | | |
| **Result** | **4.5** | **−2** | **1** | **−4** |

The points sum to 0 over the four players. The results sum to −0.5, the dealer cost.

## 11. Other scoring rules

Sections 6 to 9 are the house rules. Other tables score differently, mostly in how much each thing is worth, so the
programs take the scoring as numbers (`Rules` in `cpp/game/rules.h`; the spec syntax is `PRESET,name=value,...`, e.g.
`--rules taiwan,scoop=1`). Between each pair of valid players A and B, B pays A:

| Name | Meaning | `family` | `family_mid2` | `taiwan` | `pagat` |
|---|---|---:|---:|---:|---:|
| `row` | per row A wins, minus per row B wins (ties 0) | 0 | 0 | 1 | 1 |
| `scoop` | if A scoops B | 1 | 1 | 3 | 3 |
| `home_run` | more from each opponent when A scoops all three | 1 | 1 | 6 | 0 |
| `front_trips` | royalty for three of a kind in front | 1 | 1 | 3 | 3 |
| `middle_full_house` | royalty for a full house in the middle | 0 | 0 | 0 | 2 |
| `middle_quads` | royalty for four of a kind in the middle | 1 | 2 | 8 | 8 |
| `middle_straight_flush` | royalty for a straight flush in the middle | 1 | 2 | 10 | 10 |
| `back_quads` | royalty for four of a kind in the back | 1 | 1 | 4 | 4 |
| `back_straight_flush` | royalty for a straight flush in the back | 1 | 1 | 5 | 5 |
| `on_win` | 1: a royalty is paid only by opponents whose row it beats; 0: by every opponent | 0 | 0 | 0 | 1 |
| `dragon` | for a dragon, from each opponent | 1 | 1 | 36 | 13 |
| `strict` | 1: rows must be strictly increasing, front < middle < back (section 5 allows equal rows) | 0 | 0 | 0 | 1 |
| `wheel` | A-2-3-4-5: 0 the lowest straight (section 3.1), 1 the second highest (below 10-J-Q-K-A), 2 not a straight (high card A 5 4 3 2, or a flush) | 0 | 0 | 0 | 0 |
| `c` | the dealer cost (section 7) | any | any | any | any |

- `family` is sections 6 and 7. `family_mid2` is the common Taiwanese "only scoops count" game where a monster in the
  middle pays double.
- `taiwan` counts rows (per-row 1 point, 打槍 doubles the three rows, 全壘打 doubles again), with the royalty table found
  on Taiwanese sites: 衝三 3, middle 鐵支 8 and 同花順 10, back 鐵支 4 and 同花順 5, 一條龍 36.
- `pagat` is the English "Chinese poker" 1-6 scoring with royalties paid only on won rows (pagat.com, Hawaiian
  values).
- Royalties never decrease with a row's category (a middle full house is worth at most quads, quads at most a straight
  flush), so the dominance pruning stays exact.
- With `strict`, a hand that has no strictly increasing arrangement (very rare) may use equal rows.
- `wheel` applies to straight flushes too: under 2, a suited A-2-3-4-5 is a flush.
- The dragon is still compared and scored like any hand. Declared special hands (報到: six pairs, three straights,
  three flushes, ...) are left out on purpose: they are fixed points for their holder and rare enough to barely
  change anyone else's decisions.
- The dealer cost is a house rule but applies under any of them.
