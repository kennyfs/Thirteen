"""Compares two directories of screenshots (scripts/shots.py runs), pixel by pixel.

  python3 scripts/shotdiff.py BEFORE_DIR AFTER_DIR   (prints each png that differs, and the box where; exit 1 if any)
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops

a, b = Path(sys.argv[1]), Path(sys.argv[2])
bad = 0
for f in sorted(a.glob('*.png')):
    g = b / f.name
    if not g.exists():
        print('missing', f.name)
        bad += 1
        continue
    x, y = Image.open(f).convert('RGB'), Image.open(g).convert('RGB')
    if x.size != y.size:
        print('size', f.name, x.size, y.size)
        bad += 1
        continue
    box = ImageChops.difference(x, y).getbbox()
    if box:
        print('differs', f.name, box)
        bad += 1
print(f'{bad} of {len(list(a.glob("*.png")))} differ')
sys.exit(1 if bad else 0)
