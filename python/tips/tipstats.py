"""Statistics for the tutorial's tips, from the network's own analysis (thirteen gui, analyze with the model).

Usage: python tipstats.py RULES NUM_DEALS WORKERS OUT.json
Each deal gives four hands; each hand is analyzed (all undominated arrangements, each with its Q), greedy's
arrangement added as an extra. The answers are stored raw (trimmed) so the statistics can be redone without the engine.
"""
import json, os, subprocess, sys
from multiprocessing import Pool

BIN = os.environ.get('THIRTEEN_BINARY', 'cpp/build/thirteen')
MODEL = os.environ.get('THIRTEEN_MODEL', 'android/app/src/main/assets/run2_net10.onnx')
RULES = sys.argv[1]

proc = None


def ask(req):
    proc.stdin.write(json.dumps(req) + '\n')
    proc.stdin.flush()
    out = json.loads(proc.stdout.readline())
    if 'error' in out:
        raise RuntimeError(out['error'])
    return out


def init():
    global proc
    proc = subprocess.Popen([BIN, 'gui'], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)


def work(seed):
    hands = ask({'cmd': 'deal', 'seed': str(seed)})['hands']
    res = []
    for h in hands:
        g = ask({'cmd': 'arrange', 'player': 'greedy', 'hand': h, 'seed': '1', 'rules': RULES})['arrangement']
        a = ask({'cmd': 'analyze', 'hand': h, 'model': MODEL, 'rules': RULES, 'extra': [g]})
        cands = [{'a': c['arrangement'], 'k': c['info']['keys'], 'cat': c['info']['categories'],
                  'v': c['info']['valid'], 'q': c['q'], 'x': c['extra']} for c in a['cands']]
        res.append({'hand': h, 'greedy': g, 'num_valid': a['num_valid'], 'dragon': a['dragon'], 'cands': cands})
    return res


if __name__ == '__main__':
    n, workers, out = int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    with Pool(workers, initializer=init) as pool:
        data = [r for rs in pool.imap(work, range(1, n + 1), chunksize=4) for r in rs]
    json.dump({'rules': RULES, 'hands': data}, open(out, 'w'))
    print('hands', len(data))
