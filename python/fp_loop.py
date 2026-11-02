#!/usr/bin/env python3
"""The training loop: fictitious play over a growing population of networks.

Iteration k (k = 1, 2, ...):
  1. Population: the base players (e.g. greedy, indep) and the networks net_1 .. net_{k-1}. The networks share
     --net-share of the weight: --latest-share of that goes to the newest network and the rest is uniform over all of
     them (uniform alone is fictitious play; all on the latest is plain self-play).
  2. Self-play: `thirteen selfplay` with that population (population id 0) into WORKDIR/data/iter_k. With --human,
     also deals against the human model (population id 1) into WORKDIR/data/human_k.
  3. Training: continue from the latest checkpoint on the newest data window; save net_k and export net_k.onnx.
  4. Evaluation (optional): a duplicate match of net_k against the base players and net_{k-1}.

net_k answers population 0 when it plays (pop=0); with --human it can also be asked for population 1, a best
response to the human model (spec net:model=...,pop=1).

Self-play and training can run on different machines: run with --stage selfplay on the CPU machine and --stage train
on the GPU machine for the same --iteration, syncing WORKDIR/data and WORKDIR/models/checkpoints between them.

Example (CPU workstation and GPU on one machine):
    python fp_loop.py --workdir run --binary ../cpp/build/thirteen --iterations 20 --deals 250000 --threads 32 \
        --train-steps 20000 --device cuda --amp
"""

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def run(cmd, log_path=None):
    print("+ " + " ".join(cmd), flush=True)
    if log_path:
        with open(log_path, "a") as log:
            log.write("+ " + " ".join(cmd) + "\n")
            log.flush()
            proc = subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT)
    else:
        proc = subprocess.run(cmd)
    if proc.returncode != 0:
        sys.exit(f"Command failed ({proc.returncode}): {' '.join(cmd)}")


def existing_nets(workdir):
    nets = []
    for path in glob.glob(os.path.join(workdir, "models", "net_*.onnx")):
        m = re.match(r"net_(\d+)\.onnx$", os.path.basename(path))
        if m:
            nets.append((int(m.group(1)), os.path.abspath(path)))
    return sorted(nets)


def write_population(path, base, nets, net_share, latest_share):
    lines = []
    base_total = sum(w for w, _ in base)
    base_scale = (1.0 - net_share) / base_total if nets and base_total > 0 else (1.0 / base_total if base_total else 0)
    for w, spec in base:
        lines.append(f"{w * base_scale:.6f} {spec}")
    if nets:
        uniform = net_share * (1.0 - latest_share) / len(nets)
        for i, (_, model) in enumerate(nets):
            w = uniform + (net_share * latest_share if i == len(nets) - 1 else 0.0)
            lines.append(f"{w:.6f} net:model={model},pop=0")
    with open(path, "w") as f:
        f.write("# weight spec\n" + "\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--workdir", required=True)
    ap.add_argument("--binary", required=True, help="The thirteen executable (built with ONNX Runtime)")
    ap.add_argument("--iterations", type=int, default=1, help="Iterations to run from --iteration on")
    ap.add_argument("--iteration", type=int, help="First iteration (default: one past the newest net)")
    ap.add_argument("--stage", choices=["all", "selfplay", "train", "eval"], default="all")
    ap.add_argument("--base", action="append", default=None,
                    help='Base population member "WEIGHT SPEC" (repeatable; default "1 greedy" and "1 indep")')
    ap.add_argument("--net-share", type=float, default=0.8)
    ap.add_argument("--latest-share", type=float, default=0.0)
    ap.add_argument("--deals", type=int, default=100000)
    ap.add_argument("--threads", type=int, default=0)
    ap.add_argument("--cands", type=int, default=32)
    ap.add_argument("--topk", type=int, default=8)
    ap.add_argument("--human", help='Human model player spec, e.g. "human:weights=human.txt"')
    ap.add_argument("--human-deals", type=int, default=0, help="Deals against the human model per iteration")
    ap.add_argument("--rules", default="mix", help="Self-play rules: a spec (e.g. family, taiwan,scoop=1) or mix, "
                    "drawn per deal (thirteen selfplay --rules)")
    ap.add_argument("--preset-prob", type=float, default=0.5, help="With --rules mix, the share of preset rules")
    ap.add_argument("--c-dist", help='Dealer cost distribution for self-play (thirteen selfplay --c-dist; default '
                    '"0.7:0,0.1:0.5,0.1:1,0.1:u")')
    ap.add_argument("--init", help="Checkpoint to start the first training from when there is no previous one, e.g. a "
                    "network of another run (one from before the rules input is upgraded, see model.upgrade_legacy)")
    ap.add_argument("--train-steps", type=int, default=10000)
    ap.add_argument("--device", default=None)
    ap.add_argument("--amp", action="store_true")
    ap.add_argument("--train-args", default="", help="Extra arguments for train.py, e.g. \"--batch 512 --min-rows 1000000\"")
    ap.add_argument("--eval-deals", type=int, default=2000)
    ap.add_argument("--eval-rules", default="family", help="Rules for the evaluation matches, separated by ';', each "
                    "with its c, e.g. \"family;taiwan,c=0.5\" (default family with c 0)")
    ap.add_argument("--eval-baseline", help="Player spec for the fourth seat of the evaluation (default: the previous "
                    "network, or greedy)")
    args = ap.parse_args()

    workdir = os.path.abspath(args.workdir)
    for sub in ("data", "models", "checkpoints", "pops", "logs"):
        os.makedirs(os.path.join(workdir, sub), exist_ok=True)
    base = [(float(s.split(None, 1)[0]), s.split(None, 1)[1]) for s in (args.base or ["1 greedy", "1 indep"])]
    first = args.iteration or (existing_nets(workdir)[-1][0] + 1 if existing_nets(workdir) else 1)

    c_dist = ["--c-dist", args.c_dist] if args.c_dist else []
    for k in range(first, first + args.iterations):
        print(f"=== Iteration {k} ===", flush=True)
        nets = [n for n in existing_nets(workdir) if n[0] < k]
        latest = nets[-1][1] if nets else None
        log = os.path.join(workdir, "logs", f"iter_{k}.log")

        if args.stage in ("all", "selfplay"):
            pop_path = os.path.join(workdir, "pops", f"iter_{k}.txt")
            write_population(pop_path, base, nets, args.net_share, args.latest_share)
            out = os.path.join(workdir, "data", f"iter_{k}")
            os.makedirs(out, exist_ok=True)
            cmd = [args.binary, "selfplay", "--pop", pop_path, "--pop-id", "0", "-n", str(args.deals), "-o", out,
                   "--cands", str(args.cands), "--topk", str(args.topk), "-s", f"iter{k}",
                   "--rules", args.rules, "--preset-prob", str(args.preset_prob)] + c_dist
            if args.threads:
                cmd += ["-t", str(args.threads)]
            if latest:
                cmd += ["--ranker", f"net:model={latest},pop=0"]
            run(cmd, log)
            if args.human and args.human_deals > 0:
                hpop = os.path.join(workdir, "pops", f"human_{k}.txt")
                with open(hpop, "w") as f:
                    f.write(f"1 {args.human}\n")
                hout = os.path.join(workdir, "data", f"human_{k}")
                os.makedirs(hout, exist_ok=True)
                cmd = [args.binary, "selfplay", "--pop", hpop, "--pop-id", "1", "-n", str(args.human_deals),
                       "-o", hout, "--cands", str(args.cands), "--topk", str(args.topk), "-s", f"human{k}",
                       "--rules", args.rules, "--preset-prob", str(args.preset_prob)] + c_dist
                if args.threads:
                    cmd += ["-t", str(args.threads)]
                if latest:
                    cmd += ["--ranker", f"net:model={latest},pop=1"]
                run(cmd, log)

        if args.stage in ("all", "train"):
            ck = os.path.join(workdir, "checkpoints", f"net_{k}.pt")
            model = os.path.join(workdir, "models", f"net_{k}.onnx")
            prev = os.path.join(workdir, "checkpoints", f"net_{k - 1}.pt")
            cmd = [sys.executable, os.path.join(HERE, "train.py"), "--data", os.path.join(workdir, "data"),
                   "--data-lib", os.path.join(os.path.dirname(os.path.abspath(args.binary)), "libthirteen_data.so"),
                   "--out", ck, "--export", model, "--steps", str(args.train_steps),
                   "--log-file", os.path.join(workdir, "logs", "train.jsonl")]
            if os.path.exists(prev):
                cmd += ["--init", prev]
            elif args.init:
                cmd += ["--init", args.init]
            if args.device:
                cmd += ["--device", args.device]
            if args.amp:
                cmd.append("--amp")
            cmd += args.train_args.split()
            run(cmd, log)
            shutil.copyfile(ck, os.path.join(workdir, "checkpoints", "latest.pt"))

        if args.stage in ("all", "eval") and args.eval_deals > 0:
            model = os.path.join(workdir, "models", f"net_{k}.onnx")
            for spec in filter(None, (s.strip() for s in args.eval_rules.split(";"))):
                if not os.path.exists(model):
                    break
                # c from the spec (default 0), passed with -c; match ignores the spec's own c.
                c = next((p.split("=", 1)[1] for p in spec.split(",") if p.strip().startswith("c=")), "0")
                cmd = [args.binary, "match", "-n", str(args.eval_deals), "--rules", spec, "-c", c, "-s", f"eval{k}",
                       "-p", f"net:model={model}", "-p", "indep", "-p", "greedy"]
                baseline = args.eval_baseline or (f"net:model={latest}" if latest else "greedy")
                cmd += ["-p", baseline]
                if args.threads:
                    cmd += ["-t", str(args.threads)]
                run(cmd, os.path.join(workdir, "logs", "eval.log"))
                with open(os.path.join(workdir, "logs", "eval.log")) as f:
                    print("".join(f.readlines()[-6:]), flush=True)


if __name__ == "__main__":
    main()
