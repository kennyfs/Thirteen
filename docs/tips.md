# The tutorial's tips: the numbers behind them

The tutorial's tips (`tut_tips` in the app's strings.xml, English and Chinese; the app's and the web page's tutorial)
come from the network's own analysis, not from intuition: 10000 random hands (2500 deals x 4) each under `family` and
`taiwan`, every undominated arrangement valued by run2_net10 (`thirteen gui` analyze, `python/tips`). "Best" is the
network's top Q. Numbers are family's; taiwan's in brackets where they differ.

| Tip | Evidence |
|---|---|
| The instinctive way (strongest back, then middle, the rest in front) is rarely best | greedy is the best in 11.6% (13.1%) of hands; median loss 0.46 (3.3) points a hand; loss > 0.3 in 66% (86%) |
| The back need not be your strongest five | the best back is the strongest possible back in 49.8% (49.7%); the middle the strongest possible in 17.8% |
| A pair in front is worth a lot | the best front is a pair or better in 59% (57%); best Q with a front pair +0.23 vs -0.25 with high card (+1.55 vs -1.34) |
| No pair for the front: an A or K | top card of a high-card front: A 54%, K 24%, Q 11% |
| Splitting a pair is normal | hands with 2-3 ranks held twice or more: some pair split in 54-59% (56-61%); a pair stays in one row 71% |
| A full house takes your smallest pair | back full house with another pair left: the lowest pair 89.7% (88.5%) |
| A flush is not always worth using | 65% of hands hold 5+ of a suit; the best uses a flush row in 48% (49%) of those |
| Often several arrangements are about as good | median gap best to 2nd 0.011 (0.088); median 4 (1) arrangements within 0.05 of the best |
| Most hands lose a little | best Q > 0 in 39% (45%) of hands; median best Q -0.16 (-0.62) |
| The choice matters | best minus worst undominated: median 1.07 (7.3) points |
| Typical rows | back: full house 29%, flush 28%, straight 22%, two pair 13%; middle: pair 39%, two pair 27%, straight 15%, trips 10.5% |

The tutorial's hand to try (`As Ad Kh Kd Qh Qd Qc Jh 7h 6s 6h 3h 2h`) is one where greedy (the aces with the queens'
full house in the back) is far behind the best (the aces in front, the sixes in the full house) under both rules,
found by `pick_hand.py`.

## Running them again

From the repository root, with `cpp/build/thirteen` built (README, Building); a few seconds on many cores.

```bash
for r in family taiwan; do python3 python/tips/tipstats.py $r 2500 32 /tmp/$r.json; done
python3 python/tips/analyze_tips.py /tmp/family.json
python3 python/tips/full_house.py /tmp/family.json
python3 python/tips/pick_hand.py /tmp/family.json /tmp/taiwan.json
```

`THIRTEEN_BINARY` and `THIRTEEN_MODEL` point elsewhere than `cpp/build/thirteen` and the app's built-in network.
