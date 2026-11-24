"""Self-play training of the v3 network (plan T4), from the pre-trained one, resumable.

    .venv/Scripts/python -u v3_selfplay.py [--until 07:30] [--init <checkpoint>]

Run directory `runs/v3/selfplay` (inside the `runs` of this tools/nn, or --runs). On the first launch:
- its training window is seeded with the newest exact-relabelled positions (data/gen_-0001.bin,
  1.5 M positions, v3);
- generation 0 plays with the pre-trained network (`runs/v3/train/pretrain_v3/pretrain_v3.pt`).
Then `loop.py` generations: 500 games each, adaptive search up to 2048 simulations, every position
first solved exactly (20 000 evaluations), 35 % of the games from a "game under way" (half of them
disputed), the others from the opening schedule; records v3 (exact values, move values, chosen-move
Q); 10 % of each batch from the winning-graph positions; lr 1e-4 down to 2e-5 at generation 120.
Same files and stop/resume behaviour as loop.py (`runs/v3/selfplay/log.txt`, `metrics.csv`).
"""
import argparse
import sys
from pathlib import Path

import numpy as np

import data
import loop

HERE = Path(__file__).resolve().parent


def seed_window(run, runs, positions=1_500_000):
    target = run / "data" / "gen_-0001.bin"
    if target.exists():
        return
    relabel = runs / "v3" / "relabel"
    files = sorted((relabel / "proven").glob("gen_*.bin"), reverse=True) + sorted((relabel / "big").glob("gen_*.bin"), reverse=True)
    if not files:  # nothing relabelled: the original self-play files
        files = sorted((runs / "proven" / "data").glob("gen_*.bin"), reverse=True)
    chunks, total = [], 0
    for f in files:
        arr = data.load(f)
        data.add_played_move_values(arr)
        chunks.append(arr)
        total += len(arr)
        if total >= positions:
            break
    arr = np.concatenate(list(reversed(chunks)))[-positions:]
    target.parent.mkdir(parents=True, exist_ok=True)
    data.save_v3(target, arr)
    print(f"window seeded: {data.summary(arr)}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", default=str(HERE / "runs"))
    ap.add_argument("--init", default=None, help="checkpoint to start from (first launch)")
    ap.add_argument("--until", default=None, help="HH:MM or ISO date: clean stop")
    args, rest = ap.parse_known_args()
    runs = Path(args.runs).resolve()
    run = runs / "v3" / "selfplay"
    init = Path(args.init) if args.init else runs / "v3" / "train" / "pretrain_v3" / "pretrain_v3.pt"
    first = not (run / "state.json").exists()
    if first and not init.exists():
        sys.exit(f"no starting network: {init}")
    seed_window(run, runs)
    stop_at = None
    if args.until:
        import datetime as dt
        if "T" in args.until:
            stop_at = args.until
        else:
            h, m = map(int, args.until.split(":"))
            t = dt.datetime.now().replace(hour=h, minute=m, second=0, microsecond=0)
            if t <= dt.datetime.now():
                t += dt.timedelta(days=1)
            stop_at = t.isoformat(timespec="minutes")
    proven = ",".join(str(f) for f in sorted((runs / "proven_data").glob("*.bin")))
    argv = ["loop.py", "--run", str(run),
            "--version", "3", "--hidden", "64", "--layers", "10", "--q-head", "1",
            "--games", "500", "--stream", "1", "--sims", "2048", "--adaptive", "1", "--policy-min-sims", "256",
            "--openings", "schedule", "--disputed", "0.2,0.8", "--schedule-seed", "20261125",
            "--format", "v3", "--root-solve", "20000", "--human-share", "0.35",
            "--window-base", "1500000", "--window-per-gen", "0", "--window-max", "1500000",
            "--lr-peak", "1e-4", "--lr-final", "2e-5", "--lr-hold-gens", "20", "--lr-decay-gens", "120",
            "--keep-data", "1", "--proven", proven, "--proven-frac", "0.1", "--java-heap", "8g"]
    if first:
        argv += ["--init-checkpoint", str(init)]
    if stop_at:
        argv += ["--stop-at", stop_at]
    sys.argv = argv + rest
    loop.main()


if __name__ == "__main__":
    main()
