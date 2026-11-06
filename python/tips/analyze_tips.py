"""The tips' numbers from family.json / taiwan.json (tipstats.py). Usage: python analyze_tips.py FILE"""
import json, sys
from collections import Counter
from statistics import median, mean

d = json.load(open(sys.argv[1]))
H = d['hands']
CAT = {1: 'high', 2: 'pair', 3: 'two pair', 4: 'trips', 5: 'straight', 6: 'flush', 7: 'full house', 8: 'quads', 9: 'str flush'}
RANK = '23456789TJQKA'
rank = lambda c: c // 4
suit = lambda c: c % 4
print('rules', d['rules'], 'hands', len(H))


def pct(x, n):
    return f'{100 * x / n:.1f}% ({x}/{n})' if n else '-'


def rows_of(a):
    where = {}
    for r, row in enumerate(a):
        for c in row:
            where[c] = r
    return where


def handcats(h):
    rc = Counter(rank(c) for c in h)
    sc = Counter(suit(c) for c in h)
    return rc, sc


nv = [x['num_valid'] for x in H]
print('valid arrangements: median', median(nv), 'min', min(nv), 'max', max(nv))
nc = [len(x['cands']) for x in H]
print('undominated candidates: median', median(nc), 'mean %.1f' % mean(nc))

best = [x['cands'][0] for x in H]
print('best invalid?', sum(not b['v'] for b in best))
for r, name in enumerate(['front', 'middle', 'back']):
    cnt = Counter(b['cat'][r] for b in best)
    print(name, ', '.join(f'{CAT[k]} {100 * v / len(H):.1f}%' for k, v in sorted(cnt.items())))

# The front's top card when it is high card.
top = Counter()
for x, b in zip(H, best):
    if b['cat'][0] == 1:
        top[max(rank(c) for c in b['a'][0])] += 1
n = sum(top.values())
print('front high card: its top card', ', '.join(f'{RANK[k]} {100 * v / n:.1f}%' for k, v in sorted(top.items(), reverse=True)))
pairfront = Counter()
for b in best:
    if b['cat'][0] == 2:
        rs = Counter(rank(c) for c in b['a'][0])
        pairfront[[k for k, v in rs.items() if v == 2][0]] += 1
n = sum(pairfront.values())
print('front pair: its rank', ', '.join(f'{RANK[k]} {100 * v / n:.1f}%' for k, v in sorted(pairfront.items(), reverse=True)))

# Greedy (strongest back, then middle, then front): its loss.
def q_of(x, a):
    for c in x['cands']:
        if c['a'] == a:
            return c['q']
    # The same row hands with other kickers: matched by keys in the engine; find by keys.
    return None

loss = []
same = 0
miss = 0
for x in H:
    gk = None
    for c in x['cands']:
        if c['x'] or c['a'] == x['greedy']:
            gk = c
    if gk is None:
        # greedy matched a candidate with the same keys; recompute: the candidate whose rows hold the same cards.
        g = [sorted(r) for r in x['greedy']]
        for c in x['cands']:
            if [sorted(r) for r in c['a']] == g:
                gk = c
    if gk is None:
        miss += 1
        continue
    l = x['cands'][0]['q'] - gk['q']
    loss.append(l)
    same += l < 1e-6
print('greedy: unmatched', miss, '; best', pct(same, len(loss)), '; loss mean %.3f median %.3f' % (mean(loss), median(loss)),
      '; loss > 0.1', pct(sum(l > 0.1 for l in loss), len(loss)), '; > 0.3', pct(sum(l > 0.3 for l in loss), len(loss)),
      '; > 0.5', pct(sum(l > 0.5 for l in loss), len(loss)))

# Gaps between the best and the next.
g2 = [x['cands'][0]['q'] - x['cands'][1]['q'] for x in H if len(x['cands']) > 1]
print('gap best-2nd: median %.3f; < 0.02 %s' % (median(g2), pct(sum(g < 0.02 for g in g2), len(g2))))
close = [sum(x['cands'][0]['q'] - c['q'] < 0.05 for c in x['cands']) for x in H]
print('arrangements within 0.05 of the best: median', median(close), '; only the best', pct(sum(c == 1 for c in close), len(close)))
qs = [x['cands'][0]['q'] for x in H]
print('best Q: mean %.3f median %.3f; > 0 %s' % (mean(qs), median(qs), pct(sum(q > 0 for q in qs), len(qs))))
worst = [x['cands'][0]['q'] - min(c['q'] for c in x['cands'] if c['v']) for x in H]
print('best - worst undominated valid: median %.3f mean %.3f' % (median(worst), mean(worst)))

# Is the best's back the strongest possible back (greedy's)?
back_max = 0
for x, b in zip(H, best):
    gb = max(c['k'][2] for c in x['cands'] if c['v'])
    back_max += b['k'][2] == gb
print('best back is the strongest possible back', pct(back_max, len(H)))
mid_max = 0
for x, b in zip(H, best):
    gm = max(c['k'][1] for c in x['cands'] if c['v'])
    mid_max += b['k'][1] == gm
print('best middle is the strongest possible middle', pct(mid_max, len(H)))
front_max = 0
for x, b in zip(H, best):
    gf = max(c['k'][0] for c in x['cands'] if c['v'])
    front_max += b['k'][0] == gf
print('best front is the strongest possible front', pct(front_max, len(H)))

# Pairs and trips kept together.
kept = Counter(); total = Counter()
for x, b in zip(H, best):
    rc, _ = handcats(x['hand'])
    w = rows_of(b['a'])
    for r, k in rc.items():
        if k >= 2:
            total[k] += 1
            rows = {w[c] for c in x['hand'] if rank(c) == r}
            kept[k] += len(rows) == 1
for k in sorted(total):
    print(f'rank held {k}x: all in one row', pct(kept[k], total[k]))
# Pairs split by the number of pairs in the hand.
for npairs in range(0, 7):
    sel = [(x, b) for x, b in zip(H, best) if sum(v >= 2 for v in handcats(x['hand'])[0].values()) == npairs]
    if not sel:
        continue
    split = 0
    for x, b in sel:
        rc, _ = handcats(x['hand'])
        w = rows_of(b['a'])
        split += any(len({w[c] for c in x['hand'] if rank(c) == r}) > 1 for r, k in rc.items() if k >= 2)
    fp = sum(b['cat'][0] >= 2 for x, b in sel)
    print(f'{npairs} ranks held 2+: {len(sel)} hands; some split {pct(split, len(sel))}; front pair+ {pct(fp, len(sel))}')

# Flushes: a hand with 5+ of a suit; does the best use a flush (or straight flush) row?
sel = [(x, b) for x, b in zip(H, best) if max(handcats(x['hand'])[1].values()) >= 5]
used = sum(any(c in (6, 9) for c in b['cat'][1:]) for x, b in sel)
print('hands with 5+ of a suit', pct(len(sel), len(H)), '; best has a flush row', pct(used, len(sel)))
# Straights: does the best use a straight when any candidate has one?
sel = [(x, b) for x, b in zip(H, best) if any(5 in c['cat'][1:] or 9 in c['cat'][1:] for c in x['cands'])]
used = sum(any(c in (5, 9) for c in b['cat'][1:]) for x, b in sel)
print('a straight possible', pct(len(sel), len(H)), '; best has one', pct(used, len(sel)))

# Constrained bests: what it costs to insist on the strongest back.
for name, keyrow in (('strongest back', 2), ('strongest front', 0)):
    ls = []
    for x in H:
        gb = max(c['k'][keyrow] for c in x['cands'] if c['v'])
        qb = max(c['q'] for c in x['cands'] if c['v'] and c['k'][keyrow] == gb)
        ls.append(x['cands'][0]['q'] - qb)
    print(f'best with the {name}: loss mean %.3f, > 0.1 %s' % (mean(ls), pct(sum(l > 0.1 for l in ls), len(ls))))

# The middle: what the best puts there when the back is a pair or two pair, etc.
mc = Counter((b['cat'][2], b['cat'][1]) for b in best)
print('back/middle pairs (top 15):', ', '.join(f'{CAT[a]}/{CAT[m]} {100 * v / len(H):.1f}%' for (a, m), v in mc.most_common(15)))

# Two pair in a hand with nothing better (two pairs exactly, no trips, no 5-card straight/flush possible): where do pairs go?
place = Counter(); n = 0
for x, b in zip(H, best):
    rc, sc = handcats(x['hand'])
    if sorted(rc.values(), reverse=True)[:3] != [2, 2, 1] or max(sc.values()) >= 5:
        continue
    if any(5 in c['cat'][1:] for c in x['cands']):
        continue
    n += 1
    w = rows_of(b['a'])
    ps = sorted((r for r, k in rc.items() if k == 2), reverse=True)
    place[tuple(sorted({w[c] for c in x['hand'] if rank(c) == r}).__repr__() for r in ps)] += 1
print('exactly two pairs, no straight/flush:', n, 'hands; rows of (high pair, low pair):',
      ', '.join(f'{k} {100 * v / n:.1f}%' for k, v in place.most_common(6)))
place = Counter(); n = 0
for x, b in zip(H, best):
    rc, sc = handcats(x['hand'])
    if sorted(rc.values(), reverse=True)[:2] != [2, 1] or max(sc.values()) >= 5:
        continue
    if any(5 in c['cat'][1:] for c in x['cands']):
        continue
    n += 1
    w = rows_of(b['a'])
    p = [r for r, k in rc.items() if k == 2][0]
    place[(RANK[p] if p >= 8 else 'low', ['front', 'middle', 'back'][w[[c for c in x['hand'] if rank(c) == p][0]]])] += 1
print('exactly one pair, no straight/flush:', n, 'hands; (pair, its row):', ', '.join(f'{k} {100 * v / n:.1f}%' for k, v in sorted(place.items())))
