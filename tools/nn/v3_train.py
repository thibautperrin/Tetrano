"""Supervised training of v2 / v3 networks on the exact-relabelled positions (plan phases 3-4:
T2 ablations, T3 pre-training).

    .venv/Scripts/python -u v3_train.py --runs <tools/nn/runs> --name ablate_v3q --hidden 48 --layers 6 \
        --version 3 --q-head 1 --steps 3000 [--until 07:10]

Data: the relabelled files of `<runs>/v3/relabel/<run>/` (v3, exact values), else the original
self-play files of `runs/proven` and `runs/big` (exact only where the search proved it), newest
first up to --max-positions; the last --test-files of runs/proven are held out; the winning-graph
positions (`runs/proven_data`) make --proven-frac of each batch. Each record whose next record is
the next position of the same game gets the played move's exact value as a Q target.

Resumable: `<runs>/v3/train/<name>/state.pt` every 250 steps; at the end `<name>.pt` (checkpoint
with its config), `<name>.bin` (QNET) and `report.json` (held-out losses, accuracy against exact
values, error on the truth benchmark).
"""
import argparse
import datetime as dt
import json
import math
import os
import re
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import build_model, evaluate_bits, model_config
from winproc import keep_system_awake


def log(out, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {out.name}: {msg}"
    print(line, flush=True)
    with open(out.parent / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def own(directory):
    return [f for f in sorted(Path(directory).glob("gen_*.bin")) if re.fullmatch(r"gen_\d{4}\.bin", f.name)]


def training_files(runs):
    """(train files newest first, test files): relabelled versions where they exist."""
    relabel = runs / "v3" / "relabel"
    proven = own(runs / "proven" / "data")
    big = own(runs / "big" / "data")
    test = proven[-2:]

    def best(f):
        r = relabel / f.parent.parent.name / f.name
        return r if r.exists() else f

    train_files = [best(f) for f in reversed(proven[:-2])] + [best(f) for f in reversed(big)]
    return train_files, [best(f) for f in test]


def load_set(files, max_positions):
    chunks, total = [], 0
    for f in files:
        arr = data.load(f)
        data.add_played_move_values(arr)
        chunks.append(arr)
        total += len(arr)
        if total >= max_positions:
            break
    return np.concatenate(chunks)[:max_positions] if chunks else np.zeros(0, dtype=data.RECORD3)


def bench_error(model, bench):
    """Network-alone error on the truth benchmark (positions with an exact value), by family."""
    pos = bench / "positions.csv"
    lab = bench / "labels.csv"
    if not (pos.exists() and lab.exists()):
        return {}
    labels = {}
    for line in open(lab):
        line = line.strip()
        if line.endswith(";") and not line.startswith("id"):
            f = line[:-1].split(",")
            if int(f[1]) > 0:
                labels[int(f[0])] = int(f[1])
    rows = [line.strip().split(",") for line in open(pos)][1:]
    rows = [r for r in rows if r and int(r[0]) in labels]
    if not rows:
        return {}
    first = np.array([int(r[4], 16) for r in rows], dtype=np.uint64)
    second = np.array([int(r[5], 16) for r in rows], dtype=np.uint64)
    ply = np.array([int(r[3]) for r in rows])
    fam = np.array([r[1] for r in rows])
    mine = np.where(ply % 2 == 0, first, second)
    theirs = np.where(ply % 2 == 0, second, first)
    truth = np.array([labels[int(r[0])] for r in rows]) - 1
    _, wdl, _ = evaluate_bits(model, mine, theirs)
    wrong = wdl.argmax(axis=1) != truth
    p = wdl[np.arange(len(truth)), truth]
    out = {"all": {"n": len(truth), "error": float(wrong.mean()), "p_exact": float(p.mean())}}
    for f in ("A", "B", "C"):
        m = fam == f
        if m.any():
            out[f] = {"n": int(m.sum()), "error": float(wrong[m].mean()), "p_exact": float(p[m].mean())}
    for lo, hi in ((0, 11), (12, 19), (20, 64)):
        m = (ply >= lo) & (ply <= hi)
        if m.any():
            out[f"ply{lo}-{hi}"] = {"n": int(m.sum()), "error": float(wrong[m].mean())}
    return out


def lr_at(step, steps, peak, final, warmup):
    if step < warmup:
        return peak * (step + 1) / warmup
    t = min(1.0, (step - warmup) / max(1, steps - warmup))
    return final + 0.5 * (peak - final) * (1 + math.cos(math.pi * t))


def at(text):
    if "T" in text:
        return dt.datetime.fromisoformat(text).timestamp()
    h, m = map(int, text.split(":"))
    t = dt.datetime.now().replace(hour=h, minute=m, second=0, microsecond=0)
    if t.timestamp() <= time.time():
        t += dt.timedelta(days=1)
    return t.timestamp()


def run(args):
    runs = Path(args.runs).resolve()
    out = runs / "v3" / "train" / args.name
    out.mkdir(parents=True, exist_ok=True)
    final = out / f"{args.name}.pt"
    if final.exists():
        log(out, "already done")
        return json.loads((out / "report.json").read_text())
    torch.set_num_threads(os.cpu_count() or 4)
    deadline = at(args.until) if args.until else None
    cfg = {"version": args.version, "hidden": args.hidden, "layers": args.layers, "q_head": bool(args.q_head),
           "global_every": args.global_every}
    train_files, test_files = training_files(runs)
    log(out, f"config {cfg}, {args.steps} steps, data: {len(train_files)} files "
             f"({sum(1 for f in train_files if 'relabel' in str(f))} relabelled)")
    train_set = load_set(train_files, args.max_positions)
    test_set = load_set(test_files, 10 ** 9)
    proven = None
    if args.proven_frac > 0:
        proven = np.concatenate([data.load(f) for f in sorted((runs / "proven_data").glob("*.bin"))])
    log(out, f"train {data.summary(train_set)}\n    test {data.summary(test_set)}")
    torch.manual_seed(args.seed)
    model = build_model(cfg)
    optimizer = train.make_optimizer(model, args.lr, 1e-4)
    state_path = out / "state.pt"
    step = 0
    if state_path.exists():
        st = torch.load(state_path, map_location="cpu", weights_only=False)
        model.load_state_dict(st["model"])
        optimizer.load_state_dict(st["optimizer"])
        step = st["step"]
        log(out, f"resumed at step {step}")
    rng = np.random.default_rng(args.seed * 1000 + step)
    started = time.time()
    first_step = step
    steps = args.steps
    while step < steps:
        if deadline is not None and time.time() > deadline - 300:
            # Out of time: shorten the schedule to what is done (the cosine ends here).
            log(out, f"deadline: stopping at step {step} of {steps}")
            steps = step
            break
        n = min(250 - step % 250, steps - step)
        for g in optimizer.param_groups:
            g["lr"] = lr_at(step, steps, args.lr, args.lr_final, args.warmup)
        if step < args.warmup:
            n = min(n, args.warmup - step)
        losses = train.train(model, optimizer, train_set, n, args.batch, rng, log_every=0, extra=proven,
                             extra_frac=args.proven_frac)
        step += n
        tmp = state_path.with_suffix(".tmp")
        torch.save({"model": model.state_dict(), "optimizer": optimizer.state_dict(), "step": step}, tmp)
        os.replace(tmp, state_path)
        if step % 1000 == 0 or step == steps:
            log(out, f"step {step}/{steps} lr {optimizer.param_groups[0]['lr']:.2e} value {losses[0]:.4f} "
                     f"policy {losses[1]:.4f} q {losses[3]:.4f} ({(time.time() - started) / max(1, step - first_step):.2f} s/step)")
    model.eval()
    test = train.evaluate_full(model, test_set)
    bench = bench_error(model, runs / "v3" / "truth")
    report = {"name": args.name, "config": cfg, "steps": step, "test": test, "bench": bench,
              "seconds_per_step": (time.time() - started) / max(1, step - first_step), "data_files": len(train_files),
              "train_positions": len(train_set)}
    torch.save({"hidden": model.hidden, "layers": model.layers, "config": model_config(model),
                "model": model.state_dict(), "optimizer": optimizer.state_dict(), "generation": 0}, out / "ckpt.tmp")
    os.replace(out / "ckpt.tmp", final)
    model.export_weights(out / f"{args.name}.bin")
    (out / "report.json").write_text(json.dumps(report, indent=1))
    log(out, f"done: test {json.dumps(test)} bench {json.dumps(bench.get('all', {}))}")
    return report


def parser():
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--version", type=int, default=3)
    ap.add_argument("--hidden", type=int, default=64)
    ap.add_argument("--layers", type=int, default=10)
    ap.add_argument("--q-head", type=int, default=1)
    ap.add_argument("--global-every", type=int, default=0)
    ap.add_argument("--steps", type=int, default=3000)
    ap.add_argument("--batch", type=int, default=1024)
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--lr-final", type=float, default=2e-5)
    ap.add_argument("--warmup", type=int, default=300)
    ap.add_argument("--max-positions", type=int, default=8_000_000)
    ap.add_argument("--proven-frac", type=float, default=0.1)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--until", default=None)
    return ap


def main():
    keep_system_awake()
    run(parser().parse_args())


if __name__ == "__main__":
    main()
