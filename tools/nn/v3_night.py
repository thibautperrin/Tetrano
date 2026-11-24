"""Resumable chain of the heavy steps of docs/NN_IMPROVEMENT_PLAN.md (truth benchmark, exact
relabelling, judge, ablations, pre-training of the v3 network), between --start and --until.

    .venv/Scripts/python -u v3_night.py --runs <tools/nn/runs> --steps truth,judge0,relabel \
        --start 19:00 --until 07:30

Each step writes `<runs>/v3/<step>.done` when finished; re-running the same command skips the
finished steps and resumes the interrupted one (every step is itself resumable). The self-play
tool is copied to `<runs>/v3/app` at launch, so it can be rebuilt meanwhile. Log:
`<runs>/v3/log.txt`.
"""
import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

from winproc import keep_system_awake, run_child

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
LIB = REPO / "selfplay" / "build" / "install" / "selfplay" / "lib"
APP_NET = REPO / "app" / "src" / "main" / "res" / "raw" / "tetrano_nn.bin"
THREADS = max(1, (os.cpu_count() or 4) - 2)


def log(v3, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(v3 / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def at(text, after=None):
    """Epoch seconds of 'HH:MM' (next occurrence after `after`, default now) or of an ISO date."""
    if "T" in text or "-" in text:
        return dt.datetime.fromisoformat(text).timestamp()
    h, m = map(int, text.split(":"))
    base = dt.datetime.fromtimestamp(after or time.time())
    t = base.replace(hour=h, minute=m, second=0, microsecond=0)
    if t.timestamp() <= (after or time.time()):
        t += dt.timedelta(days=1)
    return t.timestamp()


class Night:
    def __init__(self, args):
        self.runs = Path(args.runs).resolve()
        self.v3 = self.runs / "v3"
        self.v3.mkdir(parents=True, exist_ok=True)
        self.until = args.until_ts
        self.app = self.v3 / "app"
        self.heap = args.heap

    def java(self, args, timeout=None):
        cmd = ["java", f"-Xmx{self.heap}", "-cp", str(self.app / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt"] + args
        left = self.until - time.time()
        if left <= 60:
            raise TimeoutError()
        log(self.v3, "java " + " ".join(args))
        try:
            proc = run_child(cmd, timeout=min(left, timeout or left))
        except subprocess.TimeoutExpired:
            raise TimeoutError()
        (self.v3 / "java_last.txt").write_text(proc.stdout + "\n" + proc.stderr, encoding="utf-8")
        if proc.returncode != 0:
            raise RuntimeError(f"java failed ({proc.returncode}): {proc.stderr[-2000:]}")
        results = [line for line in proc.stdout.splitlines() if line.startswith("RESULT ")]
        for r in results:
            log(self.v3, r)
        return results

    def deadline_ms(self, before=0):
        return str(int((self.until - before) * 1000))

    # ---- steps ----

    def truth(self):
        self.java(["truth", f"model={APP_NET}", f"out={self.v3 / 'truth'}", f"threads={THREADS}",
                   "games_a=150", "games_b=150", "openings_c=1200", "budget=1000000",
                   "move_positions=400", "move_budget=100000", f"deadline={self.deadline_ms(120)}"])
        labels = self.v3 / "truth" / "labels.csv"
        moves = self.v3 / "truth" / "moves.csv"
        positions = sum(1 for _ in open(self.v3 / "truth" / "positions.csv")) - 1
        done = sum(1 for line in open(labels) if line.rstrip().endswith(";")) - 1
        if done < positions:
            raise TimeoutError()
        return moves.exists()

    def judge0(self):
        bench = self.v3 / "truth"
        for label, extra in [("gen29_old_value", ["sims=0,64,256", "old_value=1"]),
                             ("gen29_new_value", ["sims=64,256"]),
                             ("gen29_root_solve", ["sims=256", "root_solve=30000"])]:
            out = bench / f"judge_{label}.md"
            if out.exists():
                continue
            self.java(["judge", f"bench={bench}", f"model={APP_NET}", f"threads={THREADS}", f"label={label}",
                       f"out={out}"] + extra)
        return True

    def relabel(self, files, budget, stop_before):
        """Relabels the files (newest first) into <runs>/v3/relabel/<run>/, until `stop_before`."""
        out_root = self.v3 / "relabel"
        for f in files:
            out = out_root / f.parent.parent.name / (f.stem + ".bin")
            if out.exists():
                continue
            out.parent.mkdir(parents=True, exist_ok=True)
            if time.time() > stop_before - 300:
                log(self.v3, f"relabel: stop time reached before {f}")
                return True
            self.java(["relabel", f"in={f}", f"out={out}", f"budget={budget}", f"threads={THREADS}",
                       f"deadline={int(stop_before * 1000)}"])
        return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", required=True)
    ap.add_argument("--steps", default="truth,judge0,relabel")
    ap.add_argument("--start", default=None, help="HH:MM: wait until then")
    ap.add_argument("--until", default="07:30")
    ap.add_argument("--relabel-until", default="00:45", help="HH:MM: no relabelling after this")
    ap.add_argument("--relabel-budget", type=int, default=6000)
    ap.add_argument("--heap", default="12g")
    args = ap.parse_args()
    keep_system_awake()
    start = at(args.start) if args.start else time.time()
    args.until_ts = at(args.until, after=start)
    night = Night(args)
    if time.time() < start:
        log(night.v3, f"waiting until {dt.datetime.fromtimestamp(start):%H:%M}")
        while time.time() < start:
            time.sleep(20)
    # A private copy of the tool: rebuilding it meanwhile does not disturb the running steps.
    if night.app.exists():
        shutil.rmtree(night.app)
    shutil.copytree(LIB, night.app)
    relabel_stop = at(args.relabel_until, after=start)
    runs = night.runs
    proven = sorted((runs / "proven" / "data").glob("gen_*.bin"), reverse=True)
    big = sorted((runs / "big" / "data").glob("gen_*.bin"), reverse=True)
    big = [f for f in big if re.fullmatch(r"gen_\d{4}", f.stem)]
    log(night.v3, f"steps {args.steps}, until {dt.datetime.fromtimestamp(night.until):%Y-%m-%d %H:%M}")
    for step in args.steps.split(","):
        done = night.v3 / f"{step}.done"
        if done.exists():
            continue
        t0 = time.time()
        try:
            if step == "truth":
                ok = night.truth()
            elif step == "judge0":
                ok = night.judge0()
            elif step == "relabel":
                ok = night.relabel(proven + big, args.relabel_budget, relabel_stop)
            else:
                raise SystemExit(f"unknown step {step}")
        except TimeoutError:
            log(night.v3, f"{step}: stopped by the time limit; re-run the same command to resume")
            return
        except Exception as e:  # keep going with the next steps
            log(night.v3, f"{step}: FAILED {e!r}")
            continue
        if ok:
            done.write_text(json.dumps({"seconds": round(time.time() - t0)}))
            log(night.v3, f"{step}: done in {(time.time() - t0) / 60:.0f} min")
    log(night.v3, "all steps done")


if __name__ == "__main__":
    main()
