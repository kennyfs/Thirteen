#!/usr/bin/env python3
"""Checks that the C++ engine (ONNX Runtime) and PyTorch give the same Q for a checkpoint and its export.

    python check_onnx.py --binary ../cpp/build/thirteen --checkpoint run/checkpoints/net_3.pt --onnx run/models/net_3.onnx
"""

import argparse
import os
import subprocess
import sys

import numpy as np
import torch

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from thirteen.model import load_checkpoint  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--binary", required=True)
    ap.add_argument("--checkpoint", required=True)
    ap.add_argument("--onnx", required=True)
    ap.add_argument("--ema", action="store_true", help="Compare with the checkpoint's EMA weights (what train.py exports)")
    ap.add_argument("--hands", type=int, default=5)
    args = ap.parse_args()

    model, state = load_checkpoint(args.checkpoint)
    if args.ema and "ema" in state:
        model.load_state_dict(state["ema"])
    model.eval()
    worst = 0.0
    for i in range(args.hands):
        for rules, c in (("family", 0.0), ("taiwan,wheel=1", 0.7), ("pagat,wheel=2", 0.3)):
            out = subprocess.run([args.binary, "netq", "-m", args.onnx, "--rules", rules, "-c", str(c),
                                  "-s", f"check{i}"], check=True, capture_output=True, text=True).stdout.split("\n")
            cards = [int(x) for x in out[0].split()[1:]]
            features = [float(x) for x in out[1].split()[1:]]
            rows = np.array([[float(x) for x in line.split()] for line in out[3:] if line.strip()])
            masks = torch.tensor(rows[:, 0:3], dtype=torch.int64).unsqueeze(0)
            keys = torch.tensor(rows[:, 3:6], dtype=torch.int64).unsqueeze(0)
            with torch.no_grad():
                q = model(torch.tensor([cards]), torch.tensor([features], dtype=torch.float32), torch.tensor([0]),
                          masks, keys)[0][0].numpy()
            diff = np.abs(q - rows[:, 6]).max()
            worst = max(worst, diff)
            print(f"hand {i} {rules} c {c}: {len(rows)} candidates, max |Q diff| {diff:.2e}")
    print("OK" if worst < 1e-3 else "MISMATCH", f"(worst {worst:.2e})")
    sys.exit(0 if worst < 1e-3 else 1)


if __name__ == "__main__":
    main()
