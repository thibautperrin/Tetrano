"""AlphaZero-style training loop for the level-101 network. Resumable.

Each generation: self-play with the latest network (Kotlin, `:selfplay`) -> train on a sliding
window of recent positions -> export -> occasional matches. Everything lives in the run directory;
re-running the same command resumes where it stopped.

    python loop.py --run runs/main --stop-at 2026-10-09T09:00
"""
import argparse
import csv
import datetime as dt
import json
import math
import os
import re
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import QubicNet

ROOT = Path(__file__).resolve().parents[2]  # the Tetrano repository
SELFPLAY_LIB = ROOT / "selfplay" / "build" / "install" / "selfplay" / "lib"

DEFAULTS = {
    "hidden": 48,
    "layers": 6,
    "games": 2000,
    "sims": 48,
    "threads": max(1, (os.cpu_count() or 4) - 2),
    "batch": 1024,
    "reuse": 4.0,
    "window_base": 250_000,
    "window_per_gen": 25_000,
    "window_max": 2_000_000,
    "lr": 1e-3,
    "weight_decay": 1e-4,
    "arena_every": 5,
    "arena_games": 100,
    "arena_sims": 64,
    "legacy_every": 10,
    "legacy_games": 20,
    "legacy_sims": 64,
    "java_heap": "6g",
}


class DeadlineReached(Exception):
    pass


def log(run, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(run / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def remaining(deadline):
    if deadline is None:
        return None
    left = deadline - time.time()
    if left <= 0:
        raise DeadlineReached()
    return left


def java(cfg, args, deadline):
    cmd = ["java", f"-Xmx{cfg['java_heap']}", "-cp", str(SELFPLAY_LIB / "*"),
           "fr.thibautperrin.tetrano.selfplay.MainKt"] + args
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=remaining(deadline))
    except subprocess.TimeoutExpired:
        raise DeadlineReached()
    if proc.returncode != 0:
        raise RuntimeError(f"selfplay failed ({proc.returncode}):\n{proc.stdout}\n{proc.stderr}")
    results = {}
    for line in proc.stdout.splitlines():
        if line.startswith("RESULT "):
            for key, value in re.findall(r"(\w+)=(\S+)", line[len("RESULT "):]):
                results[key] = value
    return results


def lr_for(cfg, gen):
    if gen < 30:
        return cfg["lr"]
    if gen < 80:
        return cfg["lr"] / 2
    return cfg["lr"] / 4


def save_checkpoint(path, model, optimizer, gen):
    tmp = path.with_suffix(".tmp")
    torch.save({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                "optimizer": optimizer.state_dict(), "generation": gen}, tmp)
    os.replace(tmp, path)


def load_state(run):
    path = run / "state.json"
    if path.exists():
        return json.loads(path.read_text())
    return {"generation": 0, "phase": "selfplay"}


def save_state(run, state):
    tmp = run / "state.json.tmp"
    tmp.write_text(json.dumps(state, indent=1))
    os.replace(tmp, run / "state.json")


def append_metrics(run, row):
    path = run / "metrics.csv"
    new = not path.exists()
    fields = ["generation", "time", "games", "positions", "firstWins", "secondWins", "draws", "avgPlies",
              "selfplay_s", "window", "steps", "lr", "fresh_value_loss", "fresh_policy_loss", "fresh_value_acc",
              "value_loss", "policy_loss", "train_s", "arena_vs", "arena_score", "legacy_score",
              "legacy_first", "legacy_second"]
    with open(path, "a", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        if new:
            w.writeheader()
        w.writerow(row)


def model_file(run, gen):
    return run / "models" / f"gen_{gen:04d}.bin"


def keep_system_awake():
    """Windows: ask the OS not to idle-sleep while this process runs (released when it exits)."""
    if sys.platform == "win32":
        import ctypes
        es_continuous, es_system_required = 0x80000000, 0x00000001
        ctypes.windll.kernel32.SetThreadExecutionState(es_continuous | es_system_required)


def main():
    keep_system_awake()
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--stop-at", help="local time, e.g. 2026-10-09T09:00")
    ap.add_argument("--max-generations", type=int, default=10_000)
    for key, value in DEFAULTS.items():
        ap.add_argument("--" + key.replace("_", "-"), type=type(value), default=None)
    args = ap.parse_args()

    run = Path(args.run)
    for sub in ("data", "models", "checkpoints"):
        (run / sub).mkdir(parents=True, exist_ok=True)
    cfg_path = run / "config.json"
    cfg = dict(DEFAULTS)
    if cfg_path.exists():
        cfg.update(json.loads(cfg_path.read_text()))
    for key in DEFAULTS:
        v = getattr(args, key)
        if v is not None:
            cfg[key] = v
    cfg_path.write_text(json.dumps(cfg, indent=1))
    deadline = dt.datetime.fromisoformat(args.stop_at).timestamp() if args.stop_at else None
    torch.set_num_threads(os.cpu_count() or 4)
    if not SELFPLAY_LIB.exists():
        sys.exit(f"build the self-play tool first: ./gradlew :selfplay:installDist ({SELFPLAY_LIB} missing)")

    state = load_state(run)
    gen = state["generation"]
    model = train.new_model(cfg["hidden"], cfg["layers"])
    optimizer = train.make_optimizer(model, cfg["lr"], cfg["weight_decay"])
    last_trained = gen if state["phase"] == "eval" else gen - 1
    if last_trained >= 0:
        ckpt = torch.load(run / "checkpoints" / f"gen_{last_trained:04d}.pt", map_location="cpu", weights_only=False)
        model.load_state_dict(ckpt["model"])
        optimizer.load_state_dict(ckpt["optimizer"])
    model.eval()
    rng = np.random.default_rng(int(time.time()))
    log(run, f"start at generation {gen}, phase {state['phase']}, config {cfg}")

    try:
        while gen < args.max_generations:
            row = {"generation": gen, "time": f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}"}
            out = run / "data" / f"gen_{gen:04d}.bin"
            prev_model = str(model_file(run, gen - 1)) if gen > 0 else "none"

            if state["phase"] == "selfplay":
                t = time.time()
                res = java(cfg, ["selfplay", f"model={prev_model}", f"games={cfg['games']}", f"sims={cfg['sims']}",
                                 f"threads={cfg['threads']}", f"out={out}", f"seed={gen + 1}"], deadline)
                row.update(res)
                row["selfplay_s"] = round(time.time() - t)
                log(run, f"gen {gen} self-play: {res}")
                state["phase"] = "train"
                state["selfplay"] = res
                save_state(run, state)
            else:
                row.update(state.get("selfplay", {}))

            if state["phase"] == "train":
                t = time.time()
                fresh = data.load(out)
                if gen > 0:
                    fv, fp, fa = train.evaluate(model, fresh)
                    row.update(fresh_value_loss=round(fv, 4), fresh_policy_loss=round(fp, 4),
                               fresh_value_acc=round(fa, 4))
                window_size = min(cfg["window_max"], cfg["window_base"] + cfg["window_per_gen"] * gen)
                window = data.load_window(data.files_in(run / "data"), window_size)
                steps = max(50, math.ceil(len(fresh) * cfg["reuse"] / cfg["batch"]))
                lr = lr_for(cfg, gen)
                for group in optimizer.param_groups:
                    group["lr"] = lr
                log(run, f"gen {gen} training {steps} steps on {data.summary(window)}, lr {lr}")
                result = train.train(model, optimizer, window, steps, cfg["batch"], rng, deadline)
                if result is None:
                    raise DeadlineReached()
                save_checkpoint(run / "checkpoints" / f"gen_{gen:04d}.pt", model, optimizer, gen)
                model.export_weights(model_file(run, gen))
                row.update(window=len(window), steps=steps, lr=lr, value_loss=round(result[0], 4),
                           policy_loss=round(result[1], 4), train_s=round(time.time() - t))
                log(run, f"gen {gen} trained: value {result[0]:.4f} policy {result[1]:.4f}")
                state["phase"] = "eval"
                save_state(run, state)

            if state["phase"] == "eval":
                if gen > 0 and cfg["arena_every"] > 0 and gen % cfg["arena_every"] == 0:
                    vs = gen - cfg["arena_every"]
                    res = java(cfg, ["arena", f"a={model_file(run, gen)}", f"b={model_file(run, vs)}",
                                     f"games={cfg['arena_games']}", f"sims={cfg['arena_sims']}",
                                     f"threads={cfg['threads']}"], deadline)
                    row.update(arena_vs=vs, arena_score=res.get("score"))
                    log(run, f"gen {gen} arena vs gen {vs}: {res}")
                if gen > 0 and cfg["legacy_every"] > 0 and gen % cfg["legacy_every"] == 0:
                    res = java(cfg, ["vslegacy", f"model={model_file(run, gen)}", "level=100",
                                     f"games={cfg['legacy_games']}", f"sims={cfg['legacy_sims']}",
                                     f"threads={cfg['threads']}"], deadline)
                    row.update(legacy_score=res.get("score"),
                               legacy_first=f"{res.get('aFirstWin')}/{res.get('aFirstDraw')}/{res.get('aFirstLoss')}",
                               legacy_second=f"{res.get('aSecondWin')}/{res.get('aSecondDraw')}/{res.get('aSecondLoss')}")
                    log(run, f"gen {gen} vs legacy level 100: {res}")
                append_metrics(run, row)
                gen += 1
                state = {"generation": gen, "phase": "selfplay"}
                save_state(run, state)
    except DeadlineReached:
        log(run, f"deadline reached during generation {gen} ({state['phase']}); stopped, resume with the same command")


if __name__ == "__main__":
    main()
