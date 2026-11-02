#!/usr/bin/env python3
"""Fits the human model: a conditional logit over all valid arrangements of each recorded hand.

    ../cpp/build/thirteen humanfeatures -i records.jsonl -o human.npz
    python train_human.py --data human.npz --out human.txt

Each arrangement class j of a hand has features x_j (cpp/players/human.cpp) and a count n_j of splits with the same
row keys; P(class j) = n_j exp(w . x_j) / sum_k n_k exp(w . x_k). With tens to hundreds of hands, a linear utility
with an L2 penalty is about as much as the data supports; the penalty is chosen by k-fold cross-validation over hands.
The output, lines of "feature weight", is read by the C++ player human:weights=FILE.
"""

import argparse

import numpy as np
import torch


def load(path):
    d = np.load(path)
    names = [line.strip() for line in open(path + ".features.txt") if line.strip()]
    return d["feats"].astype(np.float64), d["counts"].astype(np.float64), d["offsets"], d["chosen"], names


class Data:
    def __init__(self, feats, counts, offsets, chosen, records):
        idx = np.concatenate([np.arange(offsets[r], offsets[r + 1]) for r in records])
        self.x = torch.tensor(feats[idx])
        self.log_n = torch.tensor(np.log(counts[idx]))
        sizes = np.array([offsets[r + 1] - offsets[r] for r in records])
        self.group = torch.tensor(np.repeat(np.arange(len(records)), sizes))
        starts = np.concatenate([[0], np.cumsum(sizes)[:-1]])
        self.chosen = torch.tensor(starts + np.array([chosen[r] for r in records]))
        self.num = len(records)

    def log_probs(self, w):
        s = self.log_n + self.x @ w
        m = torch.zeros(self.num, dtype=s.dtype).scatter_reduce(0, self.group, s, reduce="amax", include_self=False)
        z = torch.zeros(self.num, dtype=s.dtype).index_add(0, self.group, torch.exp(s - m[self.group]))
        return s - (m + torch.log(z))[self.group]

    def metrics(self, w):
        with torch.no_grad():
            lp = self.log_probs(w)
            nll = -lp[self.chosen].mean().item()
            # The chosen class's rank by probability within its hand (0 = most likely).
            ranks = []
            g = self.group.numpy()
            lpn = lp.numpy()
            for r, c in enumerate(self.chosen.numpy()):
                hand = lpn[g == r]
                ranks.append(int((hand > lpn[c]).sum()))
            ranks = np.array(ranks)
            return {"nll": nll, "top1": float((ranks == 0).mean()), "top5": float((ranks < 5).mean()),
                    "median_rank": float(np.median(ranks))}


def fit(data, l2, w0=None):
    w = torch.zeros(data.x.shape[1], dtype=torch.float64) if w0 is None else w0.clone()
    w.requires_grad_(True)
    opt = torch.optim.LBFGS([w], max_iter=500, line_search_fn="strong_wolfe", tolerance_grad=1e-9)

    def closure():
        opt.zero_grad()
        loss = -data.log_probs(w)[data.chosen].mean() + l2 * (w ** 2).sum()
        loss.backward()
        return loss

    opt.step(closure)
    return w.detach()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", required=True, help=".npz from `thirteen humanfeatures`")
    ap.add_argument("--out", required=True, help="Weights file for human:weights=")
    ap.add_argument("--l2", type=float, nargs="*", default=[1e-4, 1e-3, 3e-3, 1e-2, 3e-2, 0.1, 0.3],
                    help="Penalties to cross-validate")
    ap.add_argument("--folds", type=int, default=5)
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    feats, counts, offsets, chosen, names = load(args.data)
    n = len(chosen)
    print(f"{n} hands, {len(counts)} arrangement classes, {feats.shape[1]} features")
    uniform = Data(feats, counts, offsets, chosen, list(range(n))).metrics(torch.zeros(feats.shape[1], dtype=torch.float64))
    print(f"Uniform over all valid splits: nll {uniform['nll']:.3f}")

    rng = np.random.default_rng(args.seed)
    order = rng.permutation(n)
    folds = np.array_split(order, min(args.folds, n))
    best = None
    if n >= 4:
        for l2 in args.l2:
            ms = []
            for f in range(len(folds)):
                test = folds[f].tolist()
                train = np.concatenate([folds[g] for g in range(len(folds)) if g != f]).tolist()
                w = fit(Data(feats, counts, offsets, chosen, train), l2)
                ms.append(Data(feats, counts, offsets, chosen, test).metrics(w))
            avg = {k: float(np.mean([m[k] for m in ms])) for k in ms[0]}
            print(f"l2 {l2:g}: held-out nll {avg['nll']:.3f}, top-1 {avg['top1']:.2f}, top-5 {avg['top5']:.2f}, "
                  f"median rank {avg['median_rank']:.1f}")
            if best is None or avg["nll"] < best[1]["nll"]:
                best = (l2, avg)
        l2 = best[0]
        print(f"Chosen l2 {l2:g}")
    else:
        l2 = args.l2[len(args.l2) // 2]
        print(f"Too few hands to cross-validate; l2 {l2:g}")

    all_data = Data(feats, counts, offsets, chosen, list(range(n)))
    w = fit(all_data, l2)
    m = all_data.metrics(w)
    print(f"Fit on all hands: nll {m['nll']:.3f}, top-1 {m['top1']:.2f}, top-5 {m['top5']:.2f}")
    with open(args.out, "w") as f:
        f.write(f"# Conditional logit fitted by train_human.py on {n} hands, l2 {l2:g}\n")
        for name, v in zip(names, w.tolist()):
            f.write(f"{name} {v:.6g}\n")
    print(f"Wrote {args.out}")


if __name__ == "__main__":
    main()
