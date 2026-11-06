"""The full house's pair, the front and middle pairs, and the best Q by the front's kind (python/tips). Usage: python full_house.py FILE"""
import json, sys
from collections import Counter
d = json.load(open(sys.argv[1])); H = d['hands']
rank = lambda c: c // 4
n = low = 0
for x in H:
    b = x['cands'][0]
    if b['cat'][2] != 7: continue
    back = Counter(rank(c) for c in b['a'][2])
    pr = [r for r, k in back.items() if k == 2][0]
    rest = Counter(rank(c) for c in b['a'][0] + b['a'][1])
    others = [r for r, k in rest.items() if k >= 2]
    if not others: continue
    n += 1; low += pr < min(others)
print('back full house with another pair left in front/middle:', n, '; its pair the lowest:', f'{100*low/n:.1f}%')
n = hi = 0
for x in H:
    b = x['cands'][0]
    if b['cat'][0] == 2 and b['cat'][1] == 2:
        f = [r for r, k in Counter(rank(c) for c in b['a'][0]).items() if k == 2][0]
        m = [r for r, k in Counter(rank(c) for c in b['a'][1]).items() if k == 2][0]
        n += 1; hi += m > f
print('front pair and middle pair:', n, '; middle pair higher:', f'{100*hi/n:.1f}%')
# Q by the best's front category
for cat in (1, 2, 4):
    qs = [x['cands'][0]['q'] for x in H if x['cands'][0]['cat'][0] == cat]
    print('front cat', cat, 'n', len(qs), 'mean best Q %.3f' % (sum(qs) / len(qs)))
