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
        self.app = self.v3 / f"app_{os.getpid()}"
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

    # ---- training (python children) ----

    ABLATIONS = [
        ("abl_v2", ["--version", "2"]),
        ("abl_v3", ["--version", "3", "--q-head", "0"]),
        ("abl_v3q", ["--version", "3", "--q-head", "1"]),
        ("abl_v3qg", ["--version", "3", "--q-head", "1", "--global-every", "3"]),
    ]

    def python(self, args):
        cmd = [sys.executable, "-u", str(HERE / "v3_train.py"), "--runs", str(self.runs)] + args
        left = self.until - time.time()
        if left <= 120:
            raise TimeoutError()
        log(self.v3, "python v3_train.py " + " ".join(args))
        try:
            proc = run_child(cmd, timeout=left)
        except subprocess.TimeoutExpired:
            raise TimeoutError()
        (self.v3 / "python_last.txt").write_text(proc.stdout + "\n" + proc.stderr, encoding="utf-8")
        if proc.returncode != 0:
            raise RuntimeError(f"v3_train failed ({proc.returncode}): {proc.stderr[-2000:]}")

    def report(self, name):
        path = self.v3 / "train" / name / "report.json"
        return json.loads(path.read_text()) if path.exists() else None

    def ablate(self, steps):
        for name, extra in self.ABLATIONS:
            if self.report(name):
                continue
            self.python(["--name", name, "--hidden", "48", "--layers", "6", "--steps", str(steps),
                         "--until", dt.datetime.fromtimestamp(self.until - 4 * 3600).isoformat(timespec="minutes")] + extra)
        return True

    def choose(self):
        """The ablation whose network errs least against the exact values (truth benchmark, else the
        held-out exact positions), v3 and the Q head preferred within 0.3 / 0.2 points."""
        best, best_score = None, None
        for name, extra in self.ABLATIONS:
            r = self.report(name)
            if not r:
                continue
            err = r["bench"].get("all", {}).get("error")
            score = err if err is not None else 1 - r["test"]["exact_acc"]
            score += (0.003 if r["config"]["version"] == 2 else 0) + (0.002 if not r["config"].get("q_head") else 0)
            if best_score is None or score < best_score:
                best, best_score = (name, extra, r), score
        return best

    def pretrain(self, stop_margin):
        chosen = self.choose()
        if chosen is None:
            name, extra, sps = "abl_v3q", ["--version", "3", "--q-head", "1"], 0.5
        else:
            name, extra, r = chosen
            sps = r["seconds_per_step"]
        # 64 x 10 costs about 2.6 times 48 x 6 per step (measured on v2: 1.0 s against 0.39 s).
        sps64 = sps * 2.6
        left = self.until - time.time() - stop_margin
        steps = max(2000, int(left / sps64 * 0.92))
        log(self.v3, f"pre-training: chosen {name} ({extra}), {steps} steps of ~{sps64:.2f} s")
        until = dt.datetime.fromtimestamp(self.until - stop_margin + 600).isoformat(timespec="minutes")
        self.python(["--name", "pretrain_v3", "--hidden", "64", "--layers", "10", "--steps", str(steps),
                     "--lr", "1e-3", "--until", until] + extra)
        return True

    def judge1(self):
        bench = self.v3 / "truth"
        model = self.v3 / "train" / "pretrain_v3" / "pretrain_v3.bin"
        if not model.exists():
            return True
        out = bench / "judge_pretrain_v3.md"
        if not out.exists():
            self.java(["judge", f"bench={bench}", f"model={model}", f"threads={THREADS}", "label=pretrain_v3",
                       f"out={out}", "sims=0,64"])
        return True

    def summary(self):
        lines = ["# Nuit v3 — résumé", ""]
        for name, _ in self.ABLATIONS + [("pretrain_v3", [])]:
            r = self.report(name)
            if not r:
                continue
            b = r["bench"].get("all", {})
            t = r["test"]
            lines.append(f"- **{name}** {r['config']}, {r['steps']} pas ({r['seconds_per_step']:.2f} s/pas) : "
                         f"banc (réseau seul) erreur {b.get('error', float('nan')):.4f}, P(exacte) {b.get('p_exact', float('nan')):.4f} ; "
                         f"test : perte valeur {t['value']:.4f}, politique {t['policy']:.4f}, Q {t['q']:.4f}, "
                         f"précision sur positions exactes {t['exact_acc']:.4f} ({t['n_exact']})")
        for md in sorted((self.v3 / "truth").glob("judge_*.md")):
            lines += ["", f"## {md.stem}", "", md.read_text(encoding="utf-8").split("\n", 2)[-1]]
        (self.v3 / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
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
    ap.add_argument("--ablation-steps", type=int, default=3000)
    ap.add_argument("--judge-minutes", type=int, default=20, help="kept at the end for judge1 and the summary")
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
    for old in night.v3.glob("app_*"):
        shutil.rmtree(old, ignore_errors=True)  # copies still in use by another chain stay
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
            elif step == "ablate":
                ok = night.ablate(args.ablation_steps)
            elif step == "pretrain":
                ok = night.pretrain(stop_margin=args.judge_minutes * 60)
            elif step == "judge1":
                ok = night.judge1()
            elif step == "summary":
                ok = night.summary()
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
