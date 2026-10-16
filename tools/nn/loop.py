"""AlphaZero-style training loop for the level-101 network. Resumable.

Each generation: self-play with the latest network (Kotlin, `:selfplay`, games starting with
0..5 random moves and played to the end) -> train on a sliding window of recent positions ->
export -> every few generations, an Elo check against a reference network on paired random
openings. Everything lives in the run directory; re-running the same command resumes where it
stopped (the generation in progress is redone, except the self-play chunks already finished
with --chunk-games).

    python loop.py --run runs/weekend --init-checkpoint runs/main/checkpoints/gen_0198.pt \
        --stop-at 2026-10-11T18:00 --elo-reference runs/main/models/gen_0198.bin

A hard kill (closed console, taskkill /F) at any moment leaves a resumable run: every file is
written to a temporary file then renamed (state, config, metrics, checkpoints, models, self-play
data), and the java child dies with this process (winproc.run_child).
"""
import argparse
import csv
import datetime as dt
import io
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import load_checkpoint
from winproc import keep_system_awake, run_child  # keep_system_awake: also imported from here

ROOT = Path(__file__).resolve().parents[2]  # the Tetrano repository
SELFPLAY_LIB = ROOT / "selfplay" / "build" / "install" / "selfplay" / "lib"

DEFAULTS = {
    "hidden": 48,
    "layers": 6,
    "games": 2000,
    # > 0: a generation's self-play runs as successive java calls of chunk_games games each
    # (data/partial/), so that stopping loses at most one chunk; 0: one call.
    "chunk_games": 0,
    "sims": 48,
    "random_plies": 5,
    # "0.2,0.8": always random_plies random moves, kept only if the network's direct evaluation
    # gives the side to move a win probability in that range (drawn again otherwise).
    "disputed": "",
    # "schedule": the selfplay tool's opening schedule (cycles of 10 000 games: empty board, every
    # 2- and 3-move position up to symmetry, 4 to 10 random moves...; see selfplay Openings.kt),
    # disputed then bounding the first player's win probability; random_plies is then unused.
    # Game i of the run gets slot i of the schedule (state.json counts the games: schedule_games).
    "openings": "",
    "schedule_seed": 1,
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
    # By generations instead (when lr_decay_gens > 0, whatever --stop-at): lr_peak until
    # generation lr_hold_gens, cosine down to lr_final at generation lr_decay_gens, then lr_final.
    "lr_hold_gens": 0,
    "lr_decay_gens": 0,
    "weight_decay": 1e-4,
    "elo_every": 0,  # > 0: Elo check against elo_reference every N generations (off: benchmark at the end)
    "elo_games": 200,
    "elo_sims": 64,
    "elo_reference": "",
    "keep_checkpoint_every": 10,
    "keep_data": 0,  # 1: never delete self-play data (to train other networks on it later)
    "java_heap": "6g",
}


class DeadlineReached(Exception):
    pass


def log(run, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(run / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def write_atomic(path, text):
    path = Path(path)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    os.replace(tmp, path)


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
        proc = run_child(cmd, timeout=remaining(deadline))
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


def lr_for(cfg, state, deadline, gen):
    peak, final = cfg["lr_peak"], cfg["lr_final"]
    if cfg.get("lr_decay_gens", 0) > 0:
        hold, end = cfg.get("lr_hold_gens", 0), cfg["lr_decay_gens"]
        if gen <= hold:
            return peak
        if gen >= end:
            return final
        t = (gen - hold) / max(1, end - hold)
        return final + (peak - final) * 0.5 * (1.0 + math.cos(math.pi * t))
    if deadline is None:
        return peak
    start = state["start_time"]
    f = min(1.0, max(0.0, (time.time() - start) / max(1.0, deadline - start)))
    if f < cfg["lr_hold"]:
        return peak
    t = (f - cfg["lr_hold"]) / (1.0 - cfg["lr_hold"])
    return final + (peak - final) * 0.5 * (1.0 + math.cos(math.pi * t))


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
    write_atomic(run / "state.json", json.dumps(state, indent=1))


FIELDS = ["generation", "time", "compute_s", "games", "positions", "firstWins", "secondWins", "draws",
          "avgPlies", "selfplay_s", "window", "steps", "lr", "fresh_value_loss", "fresh_policy_loss",
          "fresh_moves_left_loss", "fresh_value_acc", "value_loss", "policy_loss", "moves_left_loss",
          "train_s", "elo_vs_reference", "elo_score"]


def append_metrics(run, row):
    """Adds the generation's row, replacing any row of this generation or a later one (left by a
    kill between this write and the state's). The whole file is rewritten atomically; existing
    lines are kept verbatim."""
    path = run / "metrics.csv"
    gen = int(row["generation"])
    lines = []
    if path.exists():
        with open(path, encoding="utf-8", newline="") as f:
            lines = f.read().splitlines(keepends=True)
    out = io.StringIO()
    if lines:
        out.write(lines[0] if lines[0].endswith("\n") else lines[0] + "\n")
        for line in lines[1:]:
            first = line.split(",", 1)[0].strip()
            if line.endswith("\n") and re.fullmatch(r"-?\d+", first) and int(first) < gen:
                out.write(line)
    w = csv.DictWriter(out, fieldnames=FIELDS, extrasaction="ignore", lineterminator="\r\n")
    if not lines:
        w.writeheader()
    w.writerow(row)
    write_atomic(path, out.getvalue())


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
        if f not in kept_data and not cfg["keep_data"]:
            f.unlink(missing_ok=True)
    every = cfg["keep_checkpoint_every"]
    for f in (run / "checkpoints").glob("gen_*.pt"):
        g = int(f.stem.split("_")[1])
        if g < gen and g % every != 0:
            f.unlink(missing_ok=True)


# ---- self-play ----

def selfplay_args(cfg, model, games, out, seed, offset=0):
    return (["selfplay", f"model={model}", f"games={games}", f"sims={cfg['sims']}",
             f"random_plies={cfg['random_plies']}", f"threads={cfg['threads']}", f"out={out}", f"seed={seed}"]
            + ([f"disputed={cfg['disputed']}"] if cfg["disputed"] else [])
            + ([f"openings={cfg['openings']}", f"offset={offset}", f"schedule_seed={cfg['schedule_seed']}"]
               if cfg["openings"] else []))


SUMMED = ("games", "positions", "firstWins", "secondWins", "draws", "evals", "openingDraws", "openingsKept")


def aggregate(results):
    """One RESULT for the whole generation from the chunks' ones."""
    total = {}
    for key in SUMMED:
        if any(key in r for r in results):
            total[key] = sum(int(r.get(key, 0)) for r in results)
    games = total.get("games", 0)
    total["avgPlies"] = round(sum(float(r["avgPlies"]) * int(r["games"]) for r in results) / max(1, games), 1)
    total["seconds"] = round(sum(float(r["seconds"]) for r in results), 1)
    total["chunks"] = len(results)
    return total


def partial_dir(run):
    return run / "data" / "partial"


def remove_partials(run, upto):
    """Deletes the chunk files of generations <= upto."""
    for f in partial_dir(run).glob("gen_*"):
        m = re.match(r"gen_(-?\d+)_", f.name)
        if m and int(m.group(1)) <= upto:
            f.unlink(missing_ok=True)


def chunked_selfplay(run, cfg, state, gen, prev_model, out, deadline):
    """The generation's self-play as successive java calls of chunk_games games. Each finished
    chunk is data/partial/gen_XXXX_cNNN.bin (+ .json, its RESULT); a resumed generation only plays
    the missing chunks. Returns the aggregated RESULT once out (all chunks, in order) is written."""
    part = partial_dir(run)
    part.mkdir(parents=True, exist_ok=True)
    plan = state.get("chunk_plan")
    if not plan:  # frozen for the generation, so that a config change cannot mix chunk sizes
        n, c = cfg["games"], cfg["chunk_games"]
        plan = [min(c, n - k) for k in range(0, n, c)]
        state["chunk_plan"] = plan
        save_state(run, state)
    results, files = [], []
    for i, games in enumerate(plan):
        stem = part / f"gen_{gen:04d}_c{i:03d}"
        chunk, meta = Path(f"{stem}.bin"), Path(f"{stem}.json")
        if not (chunk.exists() and meta.exists()):
            tmp = Path(f"{stem}.bin.tmp")  # the java tool itself writes <out>.tmp, then renames it to <out>
            offset = state.get("schedule_games", 0) + sum(plan[:i])
            res = java(cfg, selfplay_args(cfg, prev_model, games, tmp, gen * 1000 + i + 1, offset), deadline)
            write_atomic(meta, json.dumps(res))
            os.replace(tmp, chunk)
            log(run, f"gen {gen} self-play chunk {i + 1}/{len(plan)}: {res}")
        results.append(json.loads(meta.read_text()))
        files.append(chunk)
    tmp = Path(f"{out}.tmp")
    with open(tmp, "wb") as dst:
        for f in files:
            with open(f, "rb") as src:
                shutil.copyfileobj(src, dst, 1 << 22)
    os.replace(tmp, out)
    return aggregate(results)


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
    write_atomic(cfg_path, json.dumps(cfg, indent=1))
    deadline = dt.datetime.fromisoformat(args.stop_at).timestamp() if args.stop_at else None
    torch.set_num_threads(os.cpu_count() or 4)
    if not SELFPLAY_LIB.exists():
        sys.exit(f"build the self-play tool first: ./gradlew :selfplay:installDist ({SELFPLAY_LIB} missing)")

    state = load_state(run)
    save_state(run, state)
    gen = state["generation"]
    # Chunks of finished generations (a kill between the state's update and their deletion).
    remove_partials(run, gen if state["phase"] != "selfplay" else gen - 1)
    model = train.new_model(cfg["hidden"], cfg["layers"])
    optimizer = train.make_optimizer(model, cfg["lr_peak"], cfg["weight_decay"])
    last_trained = gen if state["phase"] == "eval" else gen - 1
    if last_trained >= 0:
        ckpt = torch.load(run / "checkpoints" / f"gen_{last_trained:04d}.pt", map_location="cpu", weights_only=False)
        model.load_state_dict(ckpt["model"])
        optimizer.load_state_dict(ckpt["optimizer"])
    elif args.init_checkpoint:
        start, ckpt, missing = load_checkpoint(args.init_checkpoint, strict=False)
        model.load_state_dict(start.state_dict())
        if not missing and "optimizer" in ckpt:
            optimizer.load_state_dict(ckpt["optimizer"])  # same network: continue with its Adam moments
        log(run, f"warm start from {args.init_checkpoint} (new parameters: {missing})")
    model.eval()
    if gen == 0 and args.init_checkpoint and not (run / "models" / "init.bin").exists():
        # The warm-start network plays generation 0's self-play (export_weights is atomic).
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
                if cfg["chunk_games"] > 0:
                    res = chunked_selfplay(run, cfg, state, gen, prev_model, out, deadline)
                else:
                    res = java(cfg, selfplay_args(cfg, prev_model, cfg["games"], out, gen + 1,
                                                  state.get("schedule_games", 0)), deadline)
                row.update(res)
                row["selfplay_s"] = round(time.time() - phase_start)
                log(run, f"gen {gen} self-play: {res}")
                state.pop("chunk_plan", None)
                if cfg["openings"]:
                    state["schedule_games"] = state.get("schedule_games", 0) + int(res["games"])
                state.update(phase="train", selfplay=res, compute_s=state["compute_s"] + time.time() - phase_start)
                save_state(run, state)
                remove_partials(run, gen)
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
                lr = lr_for(cfg, state, deadline, gen)
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
                state.update(phase="eval", train_row={k: row[k] for k in row if k not in ("generation", "time")},
                             compute_s=state["compute_s"] + time.time() - t)
                save_state(run, state)
                # Only now: a kill before the state's update must still find checkpoint gen - 1.
                clean_up(run, cfg, gen, set(files))
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
