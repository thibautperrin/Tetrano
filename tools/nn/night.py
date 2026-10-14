"""An overnight fine-tuning run, in one command: waits until --start, trains from a checkpoint with
deeper self-play search on "disputed" openings until --train-until, then a duel between the
starting network and the last one until --duel-until. Re-running it resumes where it stopped.

    .venv/Scripts/python -u night.py

Self-play: 512 simulations per move, games starting with exactly 5 random moves, kept only when
the network (one forward pass, no search) gives the side to move 20-80 % of winning.
The first generations also train on a few generations of the weekend run's data, so that the
network does not over-fit the first small batches of new games; they leave the window after
about 15 generations.

Duel (128 simulations per move, each opening played twice, each network once the side to move),
alternating rounds of:
- B: the same random openings as the weekend tournament and the 476/570 duel;
- C: random openings that the starting network finds disputed (20-80 %).
Results: <run>/duel/report.md.
"""
import argparse
import contextlib
import datetime as dt
import io
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np

import duel_analysis
from loop import SELFPLAY_LIB, keep_system_awake

HERE = Path(__file__).resolve().parent


def log(path, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(path, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def last_model(run):
    models = sorted((run / "models").glob("gen_*.bin"))
    return models[-1] if models else None


def duel(args, run, reference, final, deadline):
    out = run / "duel"
    (out / "games").mkdir(parents=True, exist_ok=True)
    log(out / "log.txt", f"duel {reference} (A) vs {final} (B) until {args.duel_until}")
    threads = max(1, (os.cpu_count() or 4) - 2)
    rounds = {"B": len(list((out / "games").glob("B_*.txt"))), "C": len(list((out / "games").glob("C_*.txt")))}
    while time.time() < deadline:
        mode = "B" if rounds["B"] <= rounds["C"] else "C"
        r = rounds[mode]
        games_file = out / "games" / f"{mode}_{r:03d}.txt"
        cmd = ["java", "-Xmx4g", "-cp", str(SELFPLAY_LIB / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt", "match",
               f"a={reference}", f"b={final}", f"games={args.round_games}", f"sims={args.duel_sims}",
               f"threads={threads}", "openings=random", f"offset={r * args.round_games // 2}",
               f"games_out={games_file}.tmp"]
        if mode == "B":
            cmd.append("seed=20261011")
        else:
            cmd += ["seed=20261013", f"judge={reference}", f"disputed={args.disputed}"]
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=max(1.0, deadline - time.time()))
        except subprocess.TimeoutExpired:
            break  # unfinished round: dropped
        if proc.returncode != 0:
            log(out / "log.txt", f"match failed: {proc.stdout}{proc.stderr}")
            break
        os.replace(f"{games_file}.tmp", games_file)
        rounds[mode] += 1
        log(out / "log.txt", f"round {mode} {r} done")
    report_duel(out, reference, final)


def report_duel(out, reference, final):
    na, nb = "gen480", Path(final).stem.replace("gen_", "new")
    lines = [f"# Duel {na} ({reference}) contre {nb} ({final})", ""]
    for mode, title in (("B", "Mode B — ouvertures aléatoires (comme le tournoi du week-end)"),
                        ("C", "Mode C — ouvertures disputées (20-80 % selon gen480)")):
        files = sorted((out / "games").glob(f"{mode}_*.txt"))
        if not files:
            continue
        with open(out / f"{mode}.txt", "w") as f:
            for g in files:
                f.write(g.read_text())
        buffer = io.StringIO()
        with contextlib.redirect_stdout(buffer):
            duel_analysis.mode_b(duel_analysis.load(out / f"{mode}.txt"), na, nb, np.random.default_rng(1))
        text = buffer.getvalue().strip().replace("Mode B (random openings)", f"Mode {mode}")
        lines += [f"## {title}", "", "```", text, "```", ""]
    (out / "report.md").write_text("\n".join(lines), encoding="utf-8")
    log(out / "log.txt", f"report: {out / 'report.md'}")


def main():
    keep_system_awake()
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="runs/night512")
    ap.add_argument("--init-checkpoint", default="runs/weekend/checkpoints/gen_0480.pt")
    ap.add_argument("--reference-model", default="runs/weekend/models/gen_0480.bin")
    ap.add_argument("--warm-data", default="runs/weekend/data", help="its last --warm-files files start the window")
    ap.add_argument("--warm-files", type=int, default=5)
    ap.add_argument("--start", default="2026-10-13T19:00")
    ap.add_argument("--train-until", default="2026-10-14T06:20")
    ap.add_argument("--duel-until", default="2026-10-14T07:20")
    ap.add_argument("--disputed", default="0.2,0.8")
    ap.add_argument("--duel-sims", type=int, default=128)
    ap.add_argument("--round-games", type=int, default=1000)
    ap.add_argument("--games", type=int, default=1000, help="self-play games per generation")
    args = ap.parse_args()
    os.chdir(HERE)
    run = Path(args.run)
    for f in (args.init_checkpoint, args.reference_model):
        if not Path(f).exists():
            sys.exit(f"missing {f}")
    if not SELFPLAY_LIB.exists():
        sys.exit("build the self-play tool first: ./gradlew :selfplay:installDist")

    start = dt.datetime.fromisoformat(args.start).timestamp()
    if time.time() < start:
        print(f"waiting until {args.start} (keep this window open)", flush=True)
        while time.time() < start:
            time.sleep(min(60.0, start - time.time()))

    (run / "data").mkdir(parents=True, exist_ok=True)
    if not (run / "state.json").exists() and args.warm_files > 0:
        # Named gen_-NNNN so that they sort before the run's own files (oldest first).
        for f in sorted(Path(args.warm_data).glob("gen_*.bin"))[-args.warm_files:]:
            target = run / "data" / f"gen_-{f.stem.split('_')[1]}.bin"
            if not target.exists():
                shutil.copy(f, target)

    train_deadline = dt.datetime.fromisoformat(args.train_until).timestamp()
    failures = 0
    while time.time() < train_deadline:
        print(f"== training until {args.train_until}", flush=True)
        code = subprocess.run([sys.executable, "-u", "loop.py", "--run", str(run), "--stop-at", args.train_until,
                               "--init-checkpoint", args.init_checkpoint,
                               "--sims", "512", "--games", str(args.games), "--random-plies", "5",
                               "--disputed", args.disputed, "--lr-peak", "1.25e-4", "--lr-final", "2e-5",
                               "--lr-hold", "0.3", "--window-base", "300000", "--window-per-gen", "10000",
                               "--keep-data", "1", "--keep-checkpoint-every", "5", "--elo-every", "0"]).returncode
        if code == 0:
            break
        failures += 1
        print(f"== training exited with code {code} (failure {failures}); retrying in 60 s", flush=True)
        if failures >= 20:
            break
        time.sleep(60)

    final = last_model(run)
    if final is None:
        sys.exit("no trained generation")
    duel(args, run, args.reference_model, str(final), dt.datetime.fromisoformat(args.duel_until).timestamp())
    print(f"== done: see {run / 'duel' / 'report.md'}", flush=True)


if __name__ == "__main__":
    main()
