"""Flips an SVG path upside down in a 24x24 box (y -> 24 - y), as the app shows practice's icon (graphicsLayer scaleY = -1).

Usage: python3 scripts/flip_path.py 'M19 3L5 3c...'   (prints the flipped path)
"""
import re
import sys

ARGS = {'M': 2, 'L': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'T': 2, 'A': 7, 'Z': 0}


def fmt(x: float) -> str:
    s = ('%.3f' % x).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def flip(d: str, size: float = 24) -> str:
    out = []
    for cmd, body in re.findall(r'([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)', d):
        nums = [float(x) for x in re.findall(r'-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?', body)]
        n = ARGS[cmd.upper()]
        rel = cmd.islower()
        if n == 0:
            out.append(cmd)
            continue
        groups = [nums[i:i + n] for i in range(0, len(nums), n)]
        for g in groups:
            u = cmd.upper()
            if u == 'V':
                g = [-g[0] if rel else size - g[0]]
            elif u == 'H':
                pass
            elif u == 'A':
                # rx ry rotation large sweep x y: the sweep turns the other way, the rotation mirrors.
                g = [g[0], g[1], -g[2], g[3], 1 - g[4], g[5], -g[6] if rel else size - g[6]]
            else:
                g = [v if i % 2 == 0 else (-v if rel else size - v) for i, v in enumerate(g)]
            out.append(cmd + ' '.join(fmt(v) for v in g))
    return ''.join(out)


if __name__ == '__main__':
    print(flip(sys.argv[1]))
