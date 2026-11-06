"""Usage: python pick_hand.py FAMILY.json TAIWAN.json. Hands for the tutorial's practice page: the instinctive (greedy) arrangement clearly worse, the best one clear and
with a pair in front, under both family and taiwan."""
import json, sys
R = '23456789TJQKA'; S = 'cdhs'
name = lambda c: R[c // 4] + S[c % 4]
fam = json.load(open(sys.argv[1]))['hands']
tw = json.load(open(sys.argv[2]))['hands']
out = []
for x, y in zip(fam, tw):
    b, b2 = x['cands'][0], y['cands'][0]
    if [sorted(r) for r in b['a']] != [sorted(r) for r in b2['a']]:
        continue
    if b['cat'][0] != 2 or len(x['cands']) < 2:
        continue
    g = [c for c in x['cands'] if [sorted(r) for r in c['a']] == [sorted(r) for r in x['greedy']]]
    if not g:
        continue
    loss = b['q'] - g[0]['q']
    gap = b['q'] - x['cands'][1]['q']
    if loss > 0.5 and gap > 0.08 and len(x['cands']) < 30:
        out.append((gap, loss, x))
out.sort(key=lambda t: -t[0])
for gap, loss, x in out[:12]:
    b = x['cands'][0]
    print(f'gap {gap:.3f} loss {loss:.3f} n {len(x["cands"])} hand', ' '.join(name(c) for c in sorted(x['hand'], reverse=True)))
    print('   best ', ' | '.join(' '.join(name(c) for c in sorted(r, reverse=True)) for r in b['a']), b['cat'], f"q {b['q']:.3f}")
    print('   greedy', ' | '.join(' '.join(name(c) for c in sorted(r, reverse=True)) for r in x['greedy']))
    print('   hand ids', sorted(x['hand']))
