#!/usr/bin/env python3
"""The fictitious-play loop split over two machines: self-play on a remote CPU host, training on the local GPU.

Run this on the local (GPU) machine. For each iteration k:
  1. On the remote host, `fp_loop.py --stage selfplay --iteration k` runs detached (setsid), so it survives a dropped
     connection. It writes REMOTE_ROOT/runs/RUN/data/iter_k and then a DONE (or FAILED) marker there.
  2. While it runs, finished .npz files are pulled into WORKDIR/data/iter_k with compressed rsync.
  3. Once DONE, the remote host starts iteration k + 1 right away (--overlap, the default), so its CPUs never wait for
     training. Its population then has the networks up to net_{k-1}: the newest network lags by one iteration.
  4. Locally, `fp_loop.py --stage train --iteration k`, then net_k.onnx is pushed to REMOTE_ROOT/runs/RUN/models.
  5. Optionally, an evaluation match on local CPU threads (fp_loop.py --stage eval).

The loop can be stopped and restarted at any time: finished stages are detected from the files (DONE markers,
net_k.onnx), and a remote self-play that is still running is waited for, not restarted. To stop after the current
iteration, create WORKDIR/STOP.

Login: a restricted key without a passphrase (--ssh-key, default ~/.ssh/thirteen_sync), so the loop works without the
ssh-agent. On the remote host the key may only run scripts/remote_gate.sh, which
allows rsync inside REMOTE_ROOT/runs and starting or querying one iteration's self-play. With --ssh-key none, ssh
uses its normal login (the agent) and runs the same script. BatchMode: a failed key login fails at once and never
falls back to a password prompt (repeated password failures can get the client banned).

Setup (once):
    ssh-keygen -t ed25519 -N "" -C thirteen_sync -f ~/.ssh/thirteen_sync
    # in the remote ~/.ssh/authorized_keys, one line (REMOTE_ROOT as an absolute path):
    command="REMOTE_ROOT/src/scripts/remote_gate.sh",restrict <contents of ~/.ssh/thirteen_sync.pub>
And on the remote host, once and after C++ changes (with the normal login):
    rsync -az --exclude .git --exclude 'build*/' --exclude cpp/external/onnxruntime ./ HOST:thirteen/src/
    ssh HOST 'cd thirteen/src && scripts/fetch_onnxruntime.sh && cd cpp &&
             cmake -S . -B build -DCMAKE_BUILD_TYPE=Release -DONNXRUNTIME_ROOT=$PWD/external/onnxruntime &&
             cmake --build build -j32 && build/thirteen runtests'

Example (the remote run is named after the local workdir, here run1):
    python python/remote_loop.py --host HOST --workdir runs/run1 --iterations 20 --deals 250000 --threads 32 \\
        --train-steps 10000 --device cuda --amp --train-args "--batch 512 --min-rows 1000000"
"""

import argparse
import glob
import os
import re
import shlex
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
MAX_WAIT = 1800


def log(msg):
    print(time.strftime("%Y-%m-%d %H:%M:%S ") + msg, flush=True)


def ssh_command(args):
    cmd = ["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=30", "-o", "ServerAliveInterval=30"]
    if args.ssh_key:
        # Without the agent: otherwise ssh offers the agent's copy of the IdentityFile in ~/.ssh/config first, logs in
        # with full access, and runs/ is no longer the root of the paths.
        cmd += ["-i", args.ssh_key, "-o", "IdentitiesOnly=yes", "-o", "IdentityAgent=none",
                "-o", "PreferredAuthentications=publickey"]
    return cmd


def backoff(args, attempt, what):
    """Waits after a failed connection, longer each time (up to 30 min), like share_sync.sh."""
    wait = min(args.poll * 2 ** min(attempt, 5), MAX_WAIT)
    log(f"{what} failed (attempt {attempt + 1}), retrying in {wait} s")
    time.sleep(wait)


def gate(args, *words):
    """Runs remote_gate.sh with these words (no spaces in them) and returns its stdout. Connection failures (exit 255)
    are retried, so a network outage does not stop the loop."""
    cmd = f"{args.remote_root}/src/scripts/remote_gate.sh " + " ".join(words)
    for attempt in range(10):
        proc = subprocess.run(ssh_command(args) + [args.host, cmd], stdout=subprocess.PIPE, text=True,
                              stdin=subprocess.DEVNULL)
        if proc.returncode != 255:
            break
        backoff(args, attempt, "ssh")
    if proc.returncode != 0:
        sys.exit(f"Remote command failed ({proc.returncode}): {cmd}")
    return proc.stdout.strip()


def remote_path(args, sub):
    """An rsync path inside the remote run: relative to REMOTE_ROOT/runs with the restricted key (rrsync's root), to
    the home directory otherwise."""
    return f"{args.host}:" + ("" if args.ssh_key else f"{args.remote_root}/runs/") + f"{args.remote_run}/{sub}"


def rsync(args, src, dst, extra=()):
    cmd = ["rsync", "-az", "--compress-choice=zstd", "-e", shlex.join(ssh_command(args))] + list(extra) + [src, dst]
    for attempt in range(10):
        if subprocess.run(cmd, stdin=subprocess.DEVNULL).returncode == 0:
            return
        backoff(args, attempt, "rsync")
    sys.exit("rsync failed: " + " ".join(cmd))


def selfplay_options(args):
    """The self-play options as --name=value words for remote_gate.sh, which allows only some of them."""
    words = [f"--deals={args.deals}", f"--threads={args.threads}", f"--cands={args.cands}", f"--topk={args.topk}"]
    tokens = shlex.split(args.selfplay_args)
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t.startswith("--") and "=" not in t and i + 1 < len(tokens) and not tokens[i + 1].startswith("--"):
            t += "=" + tokens[i + 1]
            i += 1
        if not t.startswith("--") or re.search(r"\s", t):
            sys.exit(f"--selfplay-args: cannot pass {t!r} (use --name=value without spaces)")
        words.append(t)
        i += 1
    return words


def remote_state(args, k):
    """'done', 'failed', 'running' or 'none' for the remote self-play of iteration k."""
    return gate(args, "state", args.remote_run, str(k))


def ensure_selfplay(args, k):
    state = remote_state(args, k)
    if state == "failed":
        sys.exit(f"Remote self-play of iteration {k} failed; see {args.remote_root}/runs/{args.remote_run}/logs/"
                 f"selfplay_{k}.out and iter_{k}.log on {args.host}")
    if state == "none":
        gate(args, "start", args.remote_run, str(k), *selfplay_options(args))
        log(f"Started remote self-play of iteration {k}")


def pull_data(args, k):
    local = os.path.join(args.workdir, "data", f"iter_{k}")
    os.makedirs(local, exist_ok=True)
    # Only finished files: the C++ writer renames .npz.tmp to .npz when a file is complete.
    rsync(args, remote_path(args, f"data/iter_{k}/"), local + "/",
          ["--include", "*.npz", "--include", "DONE", "--exclude", "*"])


def wait_selfplay(args, k):
    while True:
        state = remote_state(args, k)
        pull_data(args, k)
        if state == "done":
            pull_data(args, k)
            return
        if state == "failed":
            sys.exit(f"Remote self-play of iteration {k} failed")
        if state == "none":
            sys.exit(f"Remote self-play of iteration {k} is neither running nor done")
        time.sleep(args.poll)


def fp_loop(args, k, stage, extra):
    cmd = [sys.executable, os.path.join(HERE, "fp_loop.py"), "--workdir", args.workdir, "--binary", args.binary,
           "--stage", stage, "--iteration", str(k)] + extra
    print("+ " + " ".join(cmd), flush=True)
    if subprocess.run(cmd).returncode != 0:
        sys.exit(f"Local {stage} of iteration {k} failed")


def prune_local_data(args, k):
    """Deletes local copies of data older than --keep-iters iterations; the remote host keeps everything."""
    if args.keep_iters <= 0:
        return
    for path in glob.glob(os.path.join(args.workdir, "data", "*_*")):
        m = re.match(r"(iter|human)_(\d+)$", os.path.basename(path))
        if m and int(m.group(2)) <= k - args.keep_iters:
            shutil.rmtree(path)
            log(f"Removed local {path}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--workdir", required=True, help="Local run directory")
    ap.add_argument("--binary", default=os.path.join(HERE, "..", "cpp", "build", "thirteen"),
                    help="Local thirteen executable (for evaluation)")
    ap.add_argument("--host", required=True, help="The self-play host (an ssh destination)")
    ap.add_argument("--remote-root", default="thirteen",
                    help="Remote directory (relative to the remote home) with the code in src/")
    ap.add_argument("--remote-run", help="Remote run name, in REMOTE_ROOT/runs (default: the workdir's name)")
    ap.add_argument("--ssh-key", default=os.path.expanduser("~/.ssh/thirteen_sync"),
                    help="Restricted key for the remote host (see above); \"none\": the normal ssh login")
    ap.add_argument("--iterations", type=int, default=1)
    ap.add_argument("--iteration", type=int, help="First iteration (default: one past the newest local net)")
    ap.add_argument("--no-overlap", dest="overlap", action="store_false",
                    help="Wait for net_k before starting the self-play of iteration k + 1")
    ap.add_argument("--deals", type=int, default=250000)
    ap.add_argument("--threads", type=int, default=32)
    ap.add_argument("--cands", type=int, default=32)
    ap.add_argument("--topk", type=int, default=8)
    ap.add_argument("--selfplay-args", default="",
                    help='Extra arguments for the remote fp_loop.py, e.g. "--net-share 0.8 --human ..."')
    ap.add_argument("--train-steps", type=int, default=10000)
    ap.add_argument("--device", default=None)
    ap.add_argument("--amp", action="store_true")
    ap.add_argument("--train-args", default="", help="Extra arguments for train.py")
    ap.add_argument("--eval-deals", type=int, default=0, help="Local evaluation match after each training (0: none)")
    ap.add_argument("--eval-threads", type=int, default=8)
    ap.add_argument("--eval-args", default="", help='Extra arguments for the local evaluation, e.g. '
                    '"--eval-rules family,c=0.5;taiwan,c=0.5"')
    ap.add_argument("--init", help="Checkpoint for the first training when there is none yet (fp_loop.py --init)")
    ap.add_argument("--keep-iters", type=int, default=10,
                    help="Keep the local data of this many newest iterations (0: keep all)")
    ap.add_argument("--poll", type=int, default=60, help="Seconds between remote checks and data pulls")
    args = ap.parse_args()

    args.workdir = os.path.abspath(args.workdir)
    args.remote_run = args.remote_run or os.path.basename(args.workdir)
    if not re.match(r"^[A-Za-z0-9_-]+$", args.remote_run):
        ap.error("--remote-run: letters, digits, _ and - only")
    if args.ssh_key == "none":
        args.ssh_key = None
    elif not os.path.isfile(args.ssh_key):
        ap.error(f"no key {args.ssh_key}; create it (see the setup above) or pass --ssh-key none")
    args.binary = os.path.abspath(args.binary)
    for sub in ("data", "models", "checkpoints", "pops", "logs"):
        os.makedirs(os.path.join(args.workdir, sub), exist_ok=True)
    nets = sorted(int(m.group(1)) for m in (re.match(r"net_(\d+)\.onnx$", os.path.basename(p))
                                            for p in glob.glob(os.path.join(args.workdir, "models", "net_*.onnx"))) if m)
    first = args.iteration or (nets[-1] + 1 if nets else 1)
    last = first + args.iterations - 1
    stop_file = os.path.join(args.workdir, "STOP")

    # The remote host needs every local net (it may have been reset, or nets were trained elsewhere).
    if nets:
        rsync(args, os.path.join(args.workdir, "models") + "/", remote_path(args, "models/"),
              ["--include", "net_*.onnx", "--exclude", "*"])

    for k in range(first, last + 1):
        log(f"=== Iteration {k} ===")
        model = os.path.join(args.workdir, "models", f"net_{k}.onnx")
        if os.path.exists(model):
            log(f"{model} exists, skipping")
            continue
        ensure_selfplay(args, k)
        wait_selfplay(args, k)
        log(f"Self-play of iteration {k} pulled")
        if args.overlap and k < last and not os.path.exists(stop_file):
            ensure_selfplay(args, k + 1)

        train = ["--train-steps", str(args.train_steps), "--train-args", args.train_args]
        if args.device:
            train += ["--device", args.device]
        if args.amp:
            train.append("--amp")
        if args.init:
            train += ["--init", args.init]
        fp_loop(args, k, "train", train)
        rsync(args, model, remote_path(args, "models/"))
        log(f"Pushed net_{k}.onnx")
        prune_local_data(args, k)

        if args.eval_deals > 0:
            fp_loop(args, k, "eval", ["--eval-deals", str(args.eval_deals), "--threads", str(args.eval_threads)]
                    + shlex.split(args.eval_args))
        if os.path.exists(stop_file):
            log(f"{stop_file} exists, stopping")
            break


if __name__ == "__main__":
    main()
