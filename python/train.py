#!/usr/bin/env python3
"""Trains the network on the newest self-play data and exports it for the C++ engine.

    python train.py --data run/data --init run/checkpoints/latest.pt --out run/checkpoints/net_3.pt \
        --export run/models/net_3.onnx --steps 20000

Losses: binary cross-entropy of each event probability against its target (fractions over the three opponents,
exact per row), and squared error of the opponents' royalties. Q itself is composed from these by the scoring rules, so it
needs no loss; its squared error against the exact result is reported.
"""

import argparse
import copy
import json
import math
import os
import sys
import time

import numpy as np
import torch
import torch.nn.functional as F

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from thirteen.data import DEFAULT_LIB, DataWindow  # noqa: E402
from thirteen.model import (ModelConfig, ThirteenNet, TARGET_NAMES, export_onnx, load_checkpoint,  # noqa: E402
                            save_checkpoint)


def losses(model, b):
    q, probs, logits, opp_royalty = model(b["cards"], b["rules"], b["pop"], b["masks"], b["keys"])
    valid = b["valid"].unsqueeze(-1).to(logits.dtype)
    bce = F.binary_cross_entropy_with_logits(logits.float(), b["targets"], reduction="none")
    per_target = (bce * valid).sum(dim=(0, 1)) / valid.sum().clamp(min=1.0)
    bonus_mse = F.mse_loss(opp_royalty.float(), b["opp_royalty"], reduction="none").sum(-1).mean()
    v = b["valid"]
    q_mse = ((q.float() - b["result"]) ** 2)[v].mean()
    return per_target, bonus_mse, q_mse


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", nargs="+", required=True, help="Directories with .npz files (searched recursively)")
    ap.add_argument("--data-lib", default=DEFAULT_LIB, help="libthirteen_data.so, to expand the data")
    ap.add_argument("--init", help="Checkpoint to continue from; without it, a new model from the --model-* options")
    ap.add_argument("--out", required=True, help="Output checkpoint (.pt)")
    ap.add_argument("--export", help="Also export the EMA weights to this .onnx file")
    ap.add_argument("--steps", type=int, default=10000)
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--warmup", type=int, default=500)
    ap.add_argument("--weight-decay", type=float, default=1e-4)
    ap.add_argument("--ema", type=float, default=0.999, help="EMA decay of the exported weights (0 disables)")
    ap.add_argument("--bonus-weight", type=float, default=0.1)
    ap.add_argument("--device", default="cuda" if torch.cuda.is_available() else "cpu")
    ap.add_argument("--amp", action="store_true", help="bf16 autocast on CUDA")
    ap.add_argument("--min-rows", type=int, default=250_000)
    ap.add_argument("--max-rows", type=int)
    ap.add_argument("--taper-exponent", type=float, default=0.65)
    ap.add_argument("--expand-per-row", type=float, default=0.4)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--log-every", type=int, default=200)
    ap.add_argument("--log-file", help="Append JSON lines of losses here")
    for field, default in ModelConfig().to_dict().items():
        ap.add_argument(f"--model-{field.replace('_', '-')}", type=type(default), default=default)
    args = ap.parse_args()

    torch.manual_seed(args.seed)
    rng = np.random.default_rng(args.seed)
    device = torch.device(args.device)

    if args.init:
        model, state = load_checkpoint(args.init, device)
        total_steps = state.get("total_steps", 0)
        ema_state = state.get("ema")
    else:
        config = ModelConfig(**{f: getattr(args, "model_" + f) for f in ModelConfig().to_dict()})
        model = ThirteenNet(config).to(device)
        total_steps = 0
        ema_state = None
    print(f"Model: {model.config.to_dict()}, {sum(p.numel() for p in model.parameters())} parameters", flush=True)

    ema = copy.deepcopy(model).eval()
    if ema_state is not None:
        ema.load_state_dict(ema_state)
    for p in ema.parameters():
        p.requires_grad_(False)

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=args.weight_decay)
    if args.init and "optimizer" in state:
        try:
            opt.load_state_dict(state["optimizer"])
        except ValueError:
            print("Optimizer state does not match; starting fresh", flush=True)
    for group in opt.param_groups:
        group["lr"] = args.lr

    window = DataWindow(args.data, min_rows=args.min_rows, max_rows=args.max_rows,
                        taper_exponent=args.taper_exponent, expand_per_row=args.expand_per_row,
                        lib=args.data_lib)
    log = open(args.log_file, "a") if args.log_file else None
    use_amp = args.amp and device.type == "cuda"

    model.train()
    sums = None
    start = time.time()
    for step in range(1, args.steps + 1):
        lr = args.lr * min(1.0, step / max(1, args.warmup)) if not args.init else args.lr
        for group in opt.param_groups:
            group["lr"] = lr
        b = window.batch(args.batch, rng, device)
        with torch.autocast(device_type=device.type, dtype=torch.bfloat16, enabled=use_amp):
            per_target, bonus_mse, q_mse = losses(model, b)
        loss = per_target.sum() + args.bonus_weight * bonus_mse
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        opt.step()
        total_steps += 1
        if args.ema > 0:
            with torch.no_grad():
                for pe, pm in zip(ema.parameters(), model.parameters()):
                    pe.mul_(args.ema).add_(pm.detach(), alpha=1 - args.ema)
                for be, bm in zip(ema.buffers(), model.buffers()):
                    be.copy_(bm)

        vals = torch.cat([per_target.detach(), bonus_mse.detach().view(1), q_mse.detach().view(1)]).float().cpu()
        sums = vals if sums is None else sums + vals
        if step % args.log_every == 0 or step == args.steps:
            avg = (sums / (step % args.log_every or args.log_every)).tolist()
            rec = {"step": total_steps, "lr": lr, "loss": sum(avg[:len(TARGET_NAMES)]) + args.bonus_weight * avg[-2],
                   "q_mse": avg[-1], "opp_royalty_mse": avg[-2],
                   **{name: avg[i] for i, name in enumerate(TARGET_NAMES)},
                   "rows_per_s": step * args.batch / (time.time() - start)}
            print(" ".join(f"{k} {v:.4g}" if isinstance(v, float) else f"{k} {v}" for k, v in rec.items()), flush=True)
            if log:
                log.write(json.dumps(rec) + "\n")
                log.flush()
            sums = None

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    save_checkpoint(args.out, model, opt, {"total_steps": total_steps, "ema": ema.state_dict()})
    print(f"Saved {args.out}", flush=True)
    if args.export:
        os.makedirs(os.path.dirname(os.path.abspath(args.export)), exist_ok=True)
        export_onnx(ema if args.ema > 0 else model, args.export)
        print(f"Exported {args.export}", flush=True)


if __name__ == "__main__":
    main()
