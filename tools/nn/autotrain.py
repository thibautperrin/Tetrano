"""The 64 x 10 network, unattended: one command, resumable, stoppable at any moment.

    .venv/Scripts/python -u autotrain.py                 # or double-click autotrain.bat
    .venv/Scripts/python -u autotrain.py --until 07:30   # stops by itself (HH:MM or ISO datetime)
    .venv/Scripts/python autotrain.py --status
    .venv/Scripts/python autotrain.py --stop             # or double-click autotrain-stop.bat

Phases (each one skipped once done, so re-running resumes where it stopped):
1. pretrain.py: supervised pre-training of a fresh 64 x 10 network on the night512 + weekend games
   (<run>/pretrain/, about 6 h).
2. Check duel: pre-trained 64 x 10 against night512 gen 46 (48 x 6), 1000 games at 128 simulations
   on paired random openings (night.py's mode B); appended to <run>/report.md (--no-check: skipped).
3. Open-ended self-play training: loop.py on <run> from <run>/pretrain/pretrain.pt, 512 simulations
   on disputed openings, 500 games per generation in chunks of 100 (a stop loses at most one
   chunk, a few minutes), learning rate by generation (2e-4 until 60, cosine to 2e-5 at 300), Elo
   check against gen 46 every 10 generations.

The process runs at below-normal priority (inherited by its children), keeps Windows from
idle-sleeping, and writes <run>/autotrain.pid. Its children (pretrain/loop python, java) are in a
kill-on-close job object: they die with it however it ends (Ctrl+C, closed console, taskkill).
"""
import argparse
import contextlib
import datetime as dt
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import winproc

HERE = Path(__file__).resolve().parent
SELFPLAY_LIB = HERE.parents[1] / "selfplay" / "build" / "install" / "selfplay" / "lib"
NIGHT = Path("runs/night512")
REFERENCE = NIGHT / "models" / "gen_0046.bin"
WARM_FILES = 10
CHECK_SEED = 20261011  # the openings of night.py's mode B and of the weekend tournament

# Phase 3 (loop.py options). See README.md, section "Réseau 64 × 10", for the reasons.
LOOP = {
    "hidden": 64, "layers": 10,
    "sims": 512, "games": 500, "chunk_games": 100, "random_plies": 5, "disputed": "0.2,0.8",
    "keep_data": 1, "keep_checkpoint_every": 10,
    "lr_peak": 2e-4, "lr_final": 2e-5, "lr_hold_gens": 60, "lr_decay_gens": 300,
    "window_base": 300_000, "window_per_gen": 10_000, "window_max": 1_500_000,
    "elo_every": 10, "elo_reference": str(REFERENCE), "elo_games": 400, "elo_sims": 128,
}


def log(run, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] autotrain: {msg}"
    print(line, flush=True)
    with open(run / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def write_atomic(path, text):
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    os.replace(tmp, path)


def parse_until(text):
    """HH:MM (next occurrence) or an ISO datetime -> datetime."""
    if text is None:
        return None
    m = re.fullmatch(r"(\d{1,2})[:h](\d{2})", text.strip())
    if m:
        now = dt.datetime.now()
        t = now.replace(hour=int(m.group(1)), minute=int(m.group(2)), second=0, microsecond=0)
        return t if t > now else t + dt.timedelta(days=1)
    return dt.datetime.fromisoformat(text)


# ---- PID file ----

def read_pid(run):
    """(pid, info) of the running autotrain of this run, or (None, info) if none runs."""
    path = run / "autotrain.pid"
    try:
        info = json.loads(path.read_text())
    except (OSError, ValueError):
        return None, None
    alive = winproc.creation_time(info["pid"])
    return (info["pid"] if alive is not None and alive == info.get("created") else None), info


def write_pid(run):
    pid = os.getpid()
    write_atomic(run / "autotrain.pid", json.dumps({"pid": pid, "created": winproc.creation_time(pid),
                                                    "started": f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}"}))


def remove_pid(run):
    path = run / "autotrain.pid"
    try:
        if json.loads(path.read_text())["pid"] == os.getpid():
            path.unlink()
    except (OSError, ValueError, KeyError):
        pass


def stop(run):
    pid, info = read_pid(run)
    if pid is None:
        print("Tetrano autotrain: nothing is running" + (" (stale PID file removed)." if info else "."))
        (run / "autotrain.pid").unlink(missing_ok=True)
        return 0
    proc = subprocess.run(["taskkill", "/PID", str(pid), "/T", "/F"], capture_output=True, encoding="oem", errors="replace")
    print(proc.stdout.strip() or proc.stderr.strip())
    time.sleep(1)
    if winproc.creation_time(pid) is None:
        (run / "autotrain.pid").unlink(missing_ok=True)
        with open(run / "log.txt", "a", encoding="utf-8") as f:
            f.write(f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] autotrain: stopped by autotrain --stop\n")
        print(f"Tetrano autotrain stopped (PID {pid}, started {info.get('started')}). "
              "Run autotrain again to resume.")
        return 0
    print(f"PID {pid} is still running: taskkill /PID {pid} /T /F by hand?")
    return 1


# ---- status ----

def last_csv_rows(path):
    if not path.exists():
        return []
    import csv
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def status(run):
    pid, info = read_pid(run)
    print(f"Tetrano autotrain ({run}): " + (f"RUNNING (PID {pid}, since {info['started']})" if pid else "not running"))
    pre = run / "pretrain"
    if (pre / "done.json").exists():
        done = json.loads((pre / "done.json").read_text())
        print(f"1. pre-training: done ({done['steps']} steps, {done['compute_s'] / 3600:.1f} h, {done['finished']})")
    elif (pre / "progress.json").exists():
        p = json.loads((pre / "progress.json").read_text())
        eta = f", about {(p['steps'] - p['step']) * p['per_step'] / 3600:.1f} h left" if p.get("per_step") else ""
        print(f"1. pre-training: step {p['step']} / {p['steps']} ({100 * p['step'] / p['steps']:.0f} %){eta}")
    else:
        print("1. pre-training: not started")
    curve = last_csv_rows(pre / "curve.csv")
    if curve:
        c = curve[-1]
        print(f"   last evaluation (step {c['step']}): test value {c['test_value']} policy {c['test_policy']} "
              f"accuracy {100 * float(c['test_value_acc']):.1f} %")
    check = run / "check" / "result.json"
    if check.exists():
        r = json.loads(check.read_text())
        print(f"2. check duel vs gen 46: score {100 * r['score']:.1f} % => Elo {r['elo']:+.0f} ({r['openings']} openings)")
    else:
        print("2. check duel vs gen 46: not done")
    state = run / "state.json"
    if state.exists():
        s = json.loads(state.read_text())
        extra = ""
        if s["phase"] == "selfplay" and s.get("chunk_plan"):
            n = len(list((run / "data" / "partial").glob(f"gen_{s['generation']:04d}_c*.bin")))
            extra = f" (chunk {n} / {len(s['chunk_plan'])} done)"
        print(f"3. self-play training: generation {s['generation']}, phase {s['phase']}{extra}, "
              f"compute {s['compute_s'] / 3600:.1f} h")
        rows = last_csv_rows(run / "metrics.csv")
        if rows:
            r = rows[-1]
            print(f"   gen {r['generation']}: lr {r['lr']}, train value {r['value_loss']} policy {r['policy_loss']}, "
                  f"fresh value {r['fresh_value_loss']} policy {r['fresh_policy_loss']}, "
                  f"{r['games']} games in {r['selfplay_s']} s")
            elo = [x for x in rows if x.get("elo_vs_reference")]
            if elo:
                print("   Elo vs gen 46 (= 1000): " + ", ".join(f"gen {x['generation']} {x['elo_vs_reference']}"
                                                         for x in elo[-5:]))
    else:
        print("3. self-play training: not started")
    logf = run / "log.txt"
    if logf.exists():
        print("last log lines:")
        for line in logf.read_text(encoding="utf-8", errors="replace").splitlines()[-3:]:
            print("   " + line[:200])


# ---- phases ----

def child(run, cmd, deadline):
    """Runs a python step in the job object; returns its exit code (None if --until passed)."""
    try:
        return winproc.run_child(cmd, timeout=None if deadline is None else max(1.0, deadline - time.time()),
                                 capture=False).returncode
    except subprocess.TimeoutExpired:
        log(run, "stop time reached")
        return None


def pretrain(run, args, until_iso):
    if (run / "pretrain" / "done.json").exists():
        return True
    log(run, "phase 1: pre-training")
    cmd = [sys.executable, "-u", "pretrain.py", "--run", str(run)]
    if until_iso:
        cmd += ["--stop-at", until_iso]
    if args.extra_data:
        cmd += ["--extra-data", args.extra_data, "--extra-last", str(args.extra_last)]
    for failure in range(3):
        code = winproc.run_child(cmd, capture=False).returncode  # it stops itself at --stop-at
        if (run / "pretrain" / "done.json").exists():
            return True
        if code == 0:  # stop time reached
            return False
        log(run, f"pretrain.py exited with code {code}; retrying in 60 s")
        time.sleep(60)
    return False


def check_duel(run, deadline, n_games=1000):
    out = run / "check"
    if (out / "result.json").exists():
        return True
    out.mkdir(exist_ok=True)
    games = out / "games.txt"
    if not games.exists():
        log(run, f"phase 2: check duel, pre-trained 64x10 vs night512 gen 46 ({n_games} games, 128 simulations)")
        threads = max(1, (os.cpu_count() or 4) - 2)
        cmd = ["java", "-Xmx4g", "-cp", str(SELFPLAY_LIB / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt", "match",
               f"a={run / 'pretrain' / 'pretrain.bin'}", f"b={REFERENCE}", f"games={n_games}", "sims=128",
               f"threads={threads}", "openings=random", f"seed={CHECK_SEED}", "offset=0", f"games_out={games}.tmp"]
        t = time.time()
        try:
            proc = winproc.run_child(cmd, timeout=None if deadline is None else max(1.0, deadline - time.time()))
        except subprocess.TimeoutExpired:
            log(run, "stop time reached during the check duel (it will be redone)")
            return False
        if proc.returncode != 0:
            log(run, f"check duel failed: {proc.stdout}{proc.stderr}")
            return False
        os.replace(f"{games}.tmp", games)
        log(run, f"check duel played in {time.time() - t:.0f} s")
    import duel_analysis
    import numpy as np
    g = duel_analysis.load(games)
    buffer = io.StringIO()
    with contextlib.redirect_stdout(buffer):
        duel_analysis.mode_b(g, "big64x10", "gen46", np.random.default_rng(1))
    pairs = np.array([duel_analysis.score_a(g[k]) + duel_analysis.score_a(g[k + 1]) for k in range(0, len(g) - 1, 2)])
    score = float(pairs.mean() / 2)
    result = {"score": score, "elo": duel_analysis.elo(score), "openings": len(pairs),
              "date": f"{dt.datetime.now():%Y-%m-%d %H:%M}"}
    report = run / "report.md"
    head = "" if report.exists() else f"# Réseau 64 × 10 ({run})\n\n"
    with open(report, "a", encoding="utf-8") as f:
        f.write(head + f"## Contrôle après pré-entraînement ({result['date']})\n\n"
                "64 × 10 pré-entraîné (A, `pretrain/pretrain.bin`) contre night512 gen 46 (B, 48 × 6), "
                f"128 simulations chacun, {len(pairs)} ouvertures aléatoires de 5 coups jouées dans les deux sens "
                f"(graine {CHECK_SEED}, comme le mode B de night.py).\n\n```\n{buffer.getvalue().strip()}\n```\n\n")
    write_atomic(out / "result.json", json.dumps(result, indent=1))
    log(run, f"check duel: 64x10 score {100 * score:.1f} % => Elo {result['elo']:+.0f} vs gen 46 (report: {report})")
    return True


def self_play_training(run, until_iso, deadline):
    if not (run / "state.json").exists():
        (run / "data").mkdir(parents=True, exist_ok=True)
        # Named gen_-NNNN so that they sort before the run's own files (oldest first), as in night.py.
        night = [f for f in sorted((NIGHT / "data").glob("gen_*.bin")) if re.fullmatch(r"gen_\d+\.bin", f.name)]
        for f in night[-WARM_FILES:]:
            target = run / "data" / f"gen_-{f.stem.split('_')[1]}.bin"
            if not target.exists():
                shutil.copy(f, f"{target}.tmp")
                os.replace(f"{target}.tmp", target)
    log(run, "phase 3: self-play training (loop.py)")
    cmd = [sys.executable, "-u", "loop.py", "--run", str(run),
           "--init-checkpoint", str(run / "pretrain" / "pretrain.pt")]
    for key, value in LOOP.items():
        cmd += ["--" + key.replace("_", "-"), str(value)]
    if until_iso:
        cmd += ["--stop-at", until_iso]
    failures = 0
    while True:
        code = child(run, cmd, None if deadline is None else deadline + 120)  # loop.py stops itself first
        if code in (0, None):
            return
        failures += 1
        if failures >= 20 or (deadline is not None and time.time() > deadline):
            log(run, f"loop.py exited with code {code}; giving up")
            return
        log(run, f"loop.py exited with code {code} (failure {failures}); retrying in 60 s")
        time.sleep(60)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--run", default="runs/big")
    ap.add_argument("--until", help="stop by itself at this local time: HH:MM (next occurrence) or ISO datetime")
    ap.add_argument("--no-check", action="store_true", help="skip the check duel after pre-training")
    ap.add_argument("--check-games", type=int, default=1000, help=argparse.SUPPRESS)  # smoke tests
    ap.add_argument("--extra-data", help="pretrain.py --extra-data (first launch only), e.g. runs/main/data")
    ap.add_argument("--extra-last", type=int, default=0)
    ap.add_argument("--normal-priority", action="store_true")
    ap.add_argument("--status", action="store_true", help="print the progress and exit")
    ap.add_argument("--stop", action="store_true", help="stop the running autotrain (process tree) and exit")
    args = ap.parse_args()
    os.chdir(HERE)
    run = Path(args.run)
    if args.status:
        status(run)
        return 0
    if args.stop:
        return stop(run)

    run.mkdir(parents=True, exist_ok=True)
    pid, info = read_pid(run)
    if pid is not None:
        print(f"autotrain is already running on {run} (PID {pid}, since {info['started']})")
        return 1
    for f in (NIGHT / "checkpoints" / "gen_0046.pt", REFERENCE):
        if not f.exists():
            print(f"missing {f}")
            return 1
    if not SELFPLAY_LIB.exists():
        print("build the self-play tool first: ./gradlew :selfplay:installDist")
        return 1
    until = parse_until(args.until)
    until_iso = until.isoformat(timespec="seconds") if until else None
    deadline = until.timestamp() if until else None
    write_pid(run)
    try:
        winproc.keep_system_awake()
        if not args.normal_priority:
            winproc.below_normal_priority()
        log(run, f"start (PID {os.getpid()})" + (f", until {until_iso}" if until else ""))
        if not pretrain(run, args, until_iso):
            log(run, "pre-training not finished; run autotrain again to resume")
            return 0
        if not args.no_check and not check_duel(run, deadline, args.check_games):
            if deadline is not None and time.time() >= deadline:
                return 0
            log(run, "check duel not done (it will be retried at the next launch); going on")
        if deadline is not None and time.time() >= deadline:
            log(run, "stop time reached")
            return 0
        self_play_training(run, until_iso, deadline)
        log(run, "stopped; run autotrain again to resume")
        return 0
    except KeyboardInterrupt:
        log(run, "interrupted (Ctrl+C); run autotrain again to resume")
        return 130
    finally:
        remove_pid(run)


if __name__ == "__main__":
    sys.exit(main())
