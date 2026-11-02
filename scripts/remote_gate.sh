#!/bin/bash
# The remote host's side of python/remote_loop.py: the forced command of its restricted ssh key, so the loop needs
# neither the ssh-agent nor the personal key (which has a passphrase). In the remote ~/.ssh/authorized_keys:
#   command="ROOT/src/scripts/remote_gate.sh",restrict ssh-ed25519 AAAA... thirteen_sync
# The key has no passphrase, so it may only do what the loop needs:
#   rsync ...                  through rrsync, inside ROOT/runs (data, models)
#   state RUN K                prints done, failed, running or none for the self-play of iteration K
#   start RUN K [--opt=V ...]  starts that self-play detached (fp_loop.py --stage selfplay), with whitelisted options
# ROOT is the directory that holds src/; src/ (this script, the binary) stays out of the key's reach. With a full
# login the loop runs this script directly (remote_loop.py --ssh-key none), with the same commands.
set -u
ROOT=$(cd "$(dirname "$(realpath "$0")")/../.." && pwd)
RUNS=$ROOT/runs
CMD=${SSH_ORIGINAL_COMMAND:-$*}
read -r -a ARGS <<< "$CMD"
[[ ${#ARGS[@]} -gt 0 && ${ARGS[0]} == */remote_gate.sh ]] && ARGS=("${ARGS[@]:1}")
fail() { echo "remote_gate: $*" >&2; exit 1; }
check_run() {
  [[ ${#ARGS[@]} -ge 3 && ${ARGS[1]} =~ ^[A-Za-z0-9_-]+$ && ${ARGS[2]} =~ ^[0-9]+$ ]] || fail "expected: ${ARGS[0]} RUN K"
  RUN=$RUNS/${ARGS[1]}
  K=${ARGS[2]}
  DIR=$RUN/data/iter_$K
}

case "${ARGS[0]:-}" in
  rsync)
    mkdir -p "$RUNS"
    SSH_ORIGINAL_COMMAND=$CMD exec /usr/bin/rrsync -no-lock "$RUNS" ;;
  state)
    check_run
    if [[ -e $DIR/DONE ]]; then echo done
    elif [[ -e $DIR/FAILED ]]; then echo failed
    # The bracket keeps pgrep from matching this script's own command line.
    elif pgrep -u "$(id -u)" -f "[f]p_loop.py --workdir $RUN --binary .* --iteration $K( |$)" > /dev/null; then echo running
    else echo none
    fi ;;
  start)
    check_run
    FP=(python3 python/fp_loop.py --workdir "$RUN" --binary cpp/build/thirteen --stage selfplay --iteration "$K")
    for opt in "${ARGS[@]:3}"; do
      case "$opt" in
        --deals=*|--threads=*|--cands=*|--topk=*|--human-deals=*) [[ ${opt#*=} =~ ^[0-9]+$ ]] || fail "bad $opt" ;;
        --net-share=*|--latest-share=*|--preset-prob=*) [[ ${opt#*=} =~ ^[0-9]*\.?[0-9]+$ ]] || fail "bad $opt" ;;
        --rules=*) [[ ${opt#*=} =~ ^[A-Za-z0-9_.,=]+$ ]] || fail "bad $opt" ;;
        --c-dist=*) [[ ${opt#*=} =~ ^[0-9.:,u]+$ ]] || fail "bad $opt" ;;
        --human=*) [[ ${opt#*=} =~ ^human:[A-Za-z0-9_.,/=-]+$ ]] || fail "bad $opt" ;;
        *) fail "option not allowed: $opt" ;;
      esac
      FP+=("$opt")
    done
    mkdir -p "$DIR" "$RUN/logs" "$RUN/models" && rm -f "$DIR/FAILED" || fail "cannot create $DIR"
    cd "$ROOT/src" || fail "no $ROOT/src"
    INNER="$(printf '%q ' "${FP[@]}")>> $(printf '%q' "$RUN/logs/selfplay_$K.out") 2>&1 && touch $(printf '%q' "$DIR/DONE") || touch $(printf '%q' "$DIR/FAILED")"
    setsid -f nohup bash -c "$INNER" < /dev/null > /dev/null 2>&1
    echo started ;;
  *)
    fail "not allowed: ${CMD:-(nothing)}" ;;
esac
