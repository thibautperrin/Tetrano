"""AlphaZero-style training loop for the level-101 network. Resumable.

Each generation: self-play with the latest network (Kotlin, `:selfplay`, games starting with
0..5 random moves and played to the end) -> train on a sliding window of recent positions ->
export -> every few generations, an Elo check against a reference network on paired random
openings. Everything lives in the run directory; re-running the same command resumes where it
stopped (the generation in progress is redone).

    python loop.py --run runs/weekend --init-checkpoint runs/main/checkpoints/gen_0198.pt \
        --stop-at 2026-10-11T18:00 --elo-reference runs/main/models/gen_0198.bin
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
from model import load_checkpoint

ROOT = Path(__file__).resolve().parents[2]  # the Tetrano repository
SELFPLAY_LIB = ROOT / "selfplay" / "build" / "install" / "selfplay" / "lib"

DEFAULTS = {
    "hidden": 48,
    "layers": 6,
    "games": 2000,
    "sims": 48,
    "random_plies": 5,
    "threads": max(1, (os.cpu_count() or 4) - 2),
    "batch": 1024,
    "reuse": 4.0,
    "window_base": 250_000,
    "window_per_gen": 25_000,
    "window_max": 2_000_000,
    # Learning rate: lr_peak until lr_hold of the run's duration, then cosine down to lr_final
    # at --stop-at (without --stop-at: lr_peak throughout).
    "lr_peak": 5e-4,
    "lr_final": 5e-5,
    "lr_hold": 0.4,
    "weight_decay": 1e-4,
    "elo_every": 10,
    "elo_games": 200,
    "elo_sims": 64,
    "elo_reference": "",
    "keep_checkpoint_every": 10,
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
    """Runs the self-play tool; returns its RESULT key=value pairs."""
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


def lr_for(cfg, state, deadline):
    if deadline is None:
        return cfg["lr_peak"]
    start = state["start_time"]
    f = min(1.0, max(0.0, (time.time() - start) / max(1.0, deadline - start)))
    if f < cfg["lr_hold"]:
        return cfg["lr_peak"]
    t = (f - cfg["lr_hold"]) / (1.0 - cfg["lr_hold"])
    return cfg["lr_final"] + (cfg["lr_peak"] - cfg["lr_final"]) * 0.5 * (1.0 + math.cos(math.pi * t))


def elo_from_score(score):
    s = min(max(score, 1e-3), 1 - 1e-3)
    return 400.0 * math.log10(s / (1.0 - s))


def save_checkpoint(path, model, optimizer, gen):
    tmp = path.with_suffix(".tmp")
    torch.save({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                "optimizer": optimizer.state_dict(), "generation": gen}, tmp)
    os.replace(tmp, path)


def load_state(run):
    path = run / "state.json"
    if path.exists():
        return json.loads(path.read_text())
    return {"generation": 0, "phase": "selfplay", "start_time": time.time(), "compute_s": 0.0}


def save_state(run, state):
    tmp = run / "state.json.tmp"
    tmp.write_text(json.dumps(state, indent=1))
    os.replace(tmp, run / "state.json")


FIELDS = ["generation", "time", "compute_s", "games", "positions", "firstWins", "secondWins", "draws",
          "avgPlies", "selfplay_s", "window", "steps", "lr", "fresh_value_loss", "fresh_policy_loss",
          "fresh_moves_left_loss", "fresh_value_acc", "value_loss", "policy_loss", "moves_left_loss",
          "train_s", "elo_vs_reference", "elo_score"]


def append_metrics(run, row):
    path = run / "metrics.csv"
    new = not path.exists()
    with open(path, "a", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS, extrasaction="ignore")
        if new:
            w.writeheader()
        w.writerow(row)


def model_file(run, gen):
    return run / "models" / f"gen_{gen:04d}.bin"


def window_files(files, max_positions):
    """The newest files holding at least max_positions positions (oldest first)."""
    chosen, total = [], 0
    for f in reversed(files):
        chosen.append(f)
        total += f.stat().st_size // data.RECORD.itemsize
        if total >= max_positions:
            break
    return list(reversed(chosen))


def clean_up(run, cfg, gen, kept_data):
    """Deletes data files out of the window and most checkpoints (one in N and the last are kept)."""
    for f in data.files_in(run / "data"):
        if f not in kept_data:
            f.unlink(missing_ok=True)
    every = cfg["keep_checkpoint_every"]
    for f in (run / "checkpoints").glob("gen_*.pt"):
        g = int(f.stem.split("_")[1])
        if g < gen and g % every != 0:
            f.unlink(missing_ok=True)


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
    ap.add_argument("--stop-at", help="local time, e.g. 2026-10-11T18:00")
    ap.add_argument("--init-checkpoint", help="weights to start from (first launch only)")
    ap.add_argument("--max-generations", type=int, default=100_000)
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
    save_state(run, state)
    gen = state["generation"]
    model = train.new_model(cfg["hidden"], cfg["layers"])
    optimizer = train.make_optimizer(model, cfg["lr_peak"], cfg["weight_decay"])
    last_trained = gen if state["phase"] == "eval" else gen - 1
    if last_trained >= 0:
        ckpt = torch.load(run / "checkpoints" / f"gen_{last_trained:04d}.pt", map_location="cpu", weights_only=False)
        model.load_state_dict(ckpt["model"])
        optimizer.load_state_dict(ckpt["optimizer"])
    elif args.init_checkpoint:
        start, _, missing = load_checkpoint(args.init_checkpoint, strict=False)
        model.load_state_dict(start.state_dict())
        log(run, f"warm start from {args.init_checkpoint} (new parameters: {missing})")
    model.eval()
    if gen == 0 and args.init_checkpoint and not (run / "models" / "init.bin").exists():
        # The warm-start network plays generation 0's self-play.
        model.export_weights(run / "models" / "init.bin")
    rng = np.random.default_rng(int(time.time()))
    log(run, f"start at generation {gen}, phase {state['phase']}, config {cfg}")

    try:
        while gen < args.max_generations:
            row = {"generation": gen, "time": f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}"}
            out = run / "data" / f"gen_{gen:04d}.bin"
            if gen > 0:
                prev_model = str(model_file(run, gen - 1))
            elif (run / "models" / "init.bin").exists():
                prev_model = str(run / "models" / "init.bin")
            else:
                prev_model = "none"
            phase_start = time.time()

            if state["phase"] == "selfplay":
                res = java(cfg, ["selfplay", f"model={prev_model}", f"games={cfg['games']}", f"sims={cfg['sims']}",
                                 f"random_plies={cfg['random_plies']}", f"threads={cfg['threads']}",
                                 f"out={out}", f"seed={gen + 1}"], deadline)
                row.update(res)
                row["selfplay_s"] = round(time.time() - phase_start)
                log(run, f"gen {gen} self-play: {res}")
                state.update(phase="train", selfplay=res, compute_s=state["compute_s"] + time.time() - phase_start)
                save_state(run, state)
            else:
                row.update(state.get("selfplay", {}))

            if state["phase"] == "train":
                t = time.time()
                fresh = data.load(out)
                fv, fp, fm, fa = train.evaluate(model, fresh)
                row.update(fresh_value_loss=round(fv, 4), fresh_policy_loss=round(fp, 4),
                           fresh_moves_left_loss=round(fm, 5), fresh_value_acc=round(fa, 4))
                window_size = min(cfg["window_max"], cfg["window_base"] + cfg["window_per_gen"] * gen)
                files = window_files(data.files_in(run / "data"), window_size)
                window = data.load_window(files, window_size)
                steps = max(50, math.ceil(len(fresh) * cfg["reuse"] / cfg["batch"]))
                lr = lr_for(cfg, state, deadline)
                for group in optimizer.param_groups:
                    group["lr"] = lr
                log(run, f"gen {gen} training {steps} steps on {data.summary(window)}, lr {lr:.2e}")
                result = train.train(model, optimizer, window, steps, cfg["batch"], rng, deadline)
                if result is None:
                    raise DeadlineReached()
                save_checkpoint(run / "checkpoints" / f"gen_{gen:04d}.pt", model, optimizer, gen)
                model.export_weights(model_file(run, gen))
                row.update(window=len(window), steps=steps, lr=f"{lr:.2e}", value_loss=round(result[0], 4),
                           policy_loss=round(result[1], 4), moves_left_loss=round(result[2], 5),
                           train_s=round(time.time() - t))
                log(run, f"gen {gen} trained: value {result[0]:.4f} policy {result[1]:.4f} moves-left {result[2]:.5f}")
                clean_up(run, cfg, gen, set(files))
                state.update(phase="eval", train_row={k: row[k] for k in row if k not in ("generation", "time")},
                             compute_s=state["compute_s"] + time.time() - t)
                save_state(run, state)
            else:
                row.update(state.get("train_row", {}))

            if state["phase"] == "eval":
                t = time.time()
                if cfg["elo_reference"] and cfg["elo_every"] > 0 and gen % cfg["elo_every"] == 0:
                    res = java(cfg, ["match", f"a={model_file(run, gen)}", f"b={cfg['elo_reference']}",
                                     "openings=random", f"games={cfg['elo_games']}", f"sims={cfg['elo_sims']}",
                                     "seed=20261011", "offset=0", f"threads={cfg['threads']}"], deadline)
                    score = float(res["aScore"]) / int(res["games"])
                    row.update(elo_score=round(score, 3), elo_vs_reference=round(1000 + elo_from_score(score)))
                    log(run, f"gen {gen} vs reference on random openings: score {score:.3f} "
                             f"=> Elo ~{row['elo_vs_reference']}")
                state["compute_s"] += time.time() - t
                row["compute_s"] = round(state["compute_s"])
                append_metrics(run, row)
                gen += 1
                state.update(generation=gen, phase="selfplay")
                state.pop("selfplay", None)
                state.pop("train_row", None)
                save_state(run, state)
    except DeadlineReached:
        log(run, f"deadline reached during generation {gen} ({state['phase']}); stopped, resume with the same command")


if __name__ == "__main__":
    main()
