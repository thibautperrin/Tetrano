"""Where can level 101 still gain? Two experiments, run back to back until a hard deadline.

    python -u experiments.py --stop-at 2026-10-13T07:30

1. Search scaling: the shipped candidate (weekend gen 476) with 256, 512, 1024 simulations against
   itself with 128, on paired random openings (mode B). A large gain means the network leaves a
   lot to the search, so self-play with more simulations should produce better training targets.
2. Network size: S (H48 L6, current), M (H64 L8) and L (H96 L6) trained from scratch on the same
   self-play positions (weekend generations 538-568; 569-570 held out), same number of steps;
   held-out losses and accuracy, then duels in mode B at equal simulations and at equal time
   (simulations scaled by the measured cost of one simulation, the phone's constraint).

Writes runs/explore/report.md (updated after each step) and runs/explore/log.txt.
"""
import argparse
import datetime as dt
import json
import math
import re
import subprocess
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from duel_analysis import load as load_games, score_a
from loop import SELFPLAY_LIB, keep_system_awake

HERE = Path(__file__).resolve().parent
OUT = HERE / "runs" / "explore"
CANDIDATE = HERE / "runs" / "weekend" / "models" / "gen_0476.bin"
DATA = HERE / "runs" / "weekend" / "data"
SIZES = {"S": (48, 6), "M": (64, 8), "L": (96, 6)}
BATCH = 1024


class OutOfTime(Exception):
    pass


def log(msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(OUT / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def java(args, deadline):
    cmd = ["java", "-Xmx4g", "-cp", str(SELFPLAY_LIB / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt"] + args
    left = deadline - time.time()
    if left <= 0:
        raise OutOfTime()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=left)
    except subprocess.TimeoutExpired:
        raise OutOfTime()
    if proc.returncode != 0:
        raise RuntimeError(proc.stdout + proc.stderr)
    return proc.stdout


def duel(name, a, b, sims_a, sims_b, games, deadline, seed):
    """Mode B duel (paired random openings); returns a result dict (score of A, Elo, CI, sign test)."""
    games_file = OUT / f"duel_{name}.txt"
    if not games_file.exists():
        t = time.time()
        java(["match", f"a={a}", f"b={b}", f"games={games}", f"sims_a={sims_a}", f"sims_b={sims_b}",
              "openings=random", f"seed={seed}", "offset=0", f"games_out={games_file}.tmp"], deadline)
        Path(f"{games_file}.tmp").replace(games_file)
        log(f"duel {name} done in {time.time() - t:.0f} s")
    g = load_games(games_file)
    pairs = np.array([score_a(g[k]) + score_a(g[k + 1]) for k in range(0, len(g) - 1, 2)])
    score = pairs.mean() / 2
    rng = np.random.default_rng(1)
    boot = [pairs[rng.integers(0, len(pairs), len(pairs))].mean() / 2 for _ in range(2000)]
    lo, hi = np.percentile(boot, [2.5, 97.5])
    better, worse = int((pairs > 1).sum()), int((pairs < 1).sum())
    z = (better - worse) / math.sqrt(better + worse) if better + worse else 0.0
    elo = lambda s: 400 * math.log10(min(max(s, 1e-6), 1 - 1e-6) / (1 - min(max(s, 1e-6), 1 - 1e-6)))
    return {"name": name, "sims_a": sims_a, "sims_b": sims_b, "openings": len(pairs), "score": score,
            "elo": elo(score), "elo_lo": elo(lo), "elo_hi": elo(hi), "better": better, "worse": worse, "z": z}


def search_cost(model, deadline):
    out = java(["bench", f"model={model}", "sims=200"], deadline)
    return float(re.search(r"search: ([\d.,]+) ms/simulation", out).group(1).replace(",", "."))


def lr_at(step, total, peak=1e-3, final=2e-5, warmup=200):
    if step < warmup:
        return peak * (step + 1) / warmup
    t = (step - warmup) / max(1, total - warmup)
    return final + (peak - final) * 0.5 * (1 + math.cos(math.pi * t))


def distill(name, hidden, layers, train_set, test_set, steps, deadline, state):
    """Trains a network from scratch on train_set; returns held-out metrics along the way."""
    ckpt = OUT / f"distill_{name}.pt"
    if ckpt.exists():
        return json.loads((OUT / f"distill_{name}.json").read_text())
    model = train.new_model(hidden, layers, seed=7)
    opt = train.make_optimizer(model, 1e-3, 1e-4)
    rng = np.random.default_rng(7)
    curve = []
    running = np.zeros(3)
    count = 0
    t0 = time.time()
    for step in range(steps):
        if time.time() > deadline:
            raise OutOfTime()
        for group in opt.param_groups:
            group["lr"] = lr_at(step, steps)
        model.train()
        batch = train.batch_tensors(train_set[rng.integers(0, len(train_set), BATCH)])
        v, p, m = train.losses(model, batch)
        loss = v + p + train.MOVES_LEFT_WEIGHT * m
        opt.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        opt.step()
        running += (v.item(), p.item(), m.item())
        count += 1
        if (step + 1) % (steps // 6) == 0 or step + 1 == steps:
            hv, hp, hm, ha = train.evaluate(model, test_set)
            tv, tp, tm = running / count
            running[:] = 0
            count = 0
            curve.append({"step": step + 1, "epochs": round((step + 1) * BATCH / len(train_set), 2),
                          "train_value": round(tv, 4), "train_policy": round(tp, 4),
                          "test_value": round(hv, 4), "test_policy": round(hp, 4),
                          "test_moves_left": round(hm, 5), "test_value_acc": round(ha, 4)})
            log(f"distill {name} {curve[-1]} ({time.time() - t0:.0f} s)")
    model.eval()
    model.export_weights(OUT / f"distill_{name}.bin")
    torch.save({"hidden": hidden, "layers": layers, "model": model.state_dict()}, ckpt)
    result = {"name": name, "hidden": hidden, "layers": layers,
              "params": sum(p.numel() for p in model.parameters()), "steps": steps,
              "seconds": round(time.time() - t0), "curve": curve}
    (OUT / f"distill_{name}.json").write_text(json.dumps(result, indent=1))
    return result


def report(state):
    lines = ["# Exploration : recherche et taille du réseau", "",
             f"Mis à jour : {dt.datetime.now():%Y-%m-%d %H:%M}.", ""]
    lines += ["## 1. Recherche : gen 476 avec N simulations contre elle-même à 128 (mode B)", "",
              "| N | Ouvertures | Score | Elo (IC 95 %) | Ouvertures mieux / moins bien | z |", "|---|---|---|---|---|---|"]
    for r in state.get("search", []):
        lines.append(f"| {r['sims_a']} | {r['openings']} | {100 * r['score']:.1f} % | {r['elo']:+.0f} "
                     f"({r['elo_lo']:+.0f} – {r['elo_hi']:+.0f}) | {r['better']} / {r['worse']} | {r['z']:+.1f} |")
    lines += ["", "## 2. Taille : entraînement de zéro sur les mêmes positions", ""]
    if "costs" in state:
        lines += ["Coût d'une simulation (moteur Kotlin, ce PC) : " +
                  ", ".join(f"{k} {v:.2f} ms" for k, v in state["costs"].items()), ""]
    for d in state.get("distill", []):
        lines += [f"### {d['name']} : H{d['hidden']} × {d['layers']} couches, {d['params']} paramètres, "
                  f"{d['steps']} pas ({d['seconds'] // 60} min)", "",
                  "| Pas | Époques | Valeur (entr.) | Valeur (test) | Politique (entr.) | Politique (test) | Précision V/N/D (test) |",
                  "|---|---|---|---|---|---|---|"]
        for c in d["curve"]:
            lines.append(f"| {c['step']} | {c['epochs']} | {c['train_value']} | {c['test_value']} | {c['train_policy']} | "
                         f"{c['test_policy']} | {100 * c['test_value_acc']:.1f} % |")
        lines.append("")
    if state.get("duels"):
        lines += ["### Duels (mode B, ouvertures appariées)", "",
                  "| Duel | Simulations A / B | Ouvertures | Score de A | Elo de A (IC 95 %) | z |", "|---|---|---|---|---|---|"]
        for r in state["duels"]:
            lines.append(f"| {r['name']} | {r['sims_a']} / {r['sims_b']} | {r['openings']} | {100 * r['score']:.1f} % | "
                         f"{r['elo']:+.0f} ({r['elo_lo']:+.0f} – {r['elo_hi']:+.0f}) | {r['z']:+.1f} |")
    (OUT / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (OUT / "state.json").write_text(json.dumps(state, indent=1))


def main():
    keep_system_awake()
    ap = argparse.ArgumentParser()
    ap.add_argument("--stop-at", required=True)
    args = ap.parse_args()
    deadline = dt.datetime.fromisoformat(args.stop_at).timestamp()
    OUT.mkdir(parents=True, exist_ok=True)
    state = json.loads((OUT / "state.json").read_text()) if (OUT / "state.json").exists() else {}
    torch.set_num_threads(16)
    try:
        # ---- 1. search scaling ----
        state.setdefault("search", [])
        done = {r["sims_a"] for r in state["search"]}
        for sims, games in ((256, 4000), (512, 4000), (1024, 2000)):
            if sims in done:
                continue
            log(f"search scaling: {sims} vs 128 simulations, {games} games")
            state["search"].append(duel(f"search_{sims}", CANDIDATE, CANDIDATE, sims, 128, games, deadline, 9001))
            report(state)

        # ---- 2. network size ----
        files = data.files_in(DATA)
        test_set = np.concatenate([data.load(f) for f in files[-2:]])
        train_set = np.concatenate([data.load(f) for f in files[:-2]])
        log(f"distillation data: {len(train_set)} training positions ({files[0].name}..{files[-3].name}), "
            f"{len(test_set)} held out ({files[-2].name}, {files[-1].name})")
        if "costs" not in state:
            costs = {}
            for k, (h, l) in SIZES.items():
                m = train.new_model(h, l)
                m.export_weights(OUT / f"cost_{k}.bin")
                costs[k] = search_cost(OUT / f"cost_{k}.bin", deadline)
            state["costs"] = costs
            report(state)
        if "steps" not in state:
            # Same number of steps for every size, as many as the time allows (keeping 2 h for the
            # duels and 45 min of margin), at most 12 000 (about 6.4 epochs).
            per_step = {"S": 0.37, "M": 0.67, "L": 0.81}
            budget = deadline - time.time() - 2 * 3600 - 45 * 60
            state["steps"] = int(max(1500, min(12000, budget / sum(per_step.values()))))
            log(f"distillation: {state['steps']} steps per network")
        state.setdefault("distill", [])
        for k, (h, l) in SIZES.items():
            if any(d["name"] == k for d in state["distill"]):
                continue
            state["distill"].append(distill(k, h, l, train_set, test_set, state["steps"], deadline, state))
            report(state)

        # ---- duels between distilled networks, mode B ----
        state.setdefault("duels", [])
        names = {r["name"] for r in state["duels"]}
        base = 128
        plan = [("S distillé vs gen476", OUT / "distill_S.bin", CANDIDATE, base, base, 2000)]
        for k in ("M", "L"):
            equal_time = max(16, round(base * state["costs"]["S"] / state["costs"][k]))
            plan.append((f"{k} vs S, simulations égales", OUT / f"distill_{k}.bin", OUT / "distill_S.bin", base, base, 4000))
            plan.append((f"{k} vs S, temps égal", OUT / f"distill_{k}.bin", OUT / "distill_S.bin", equal_time, base, 4000))
        for name, a, b, sa, sb, games in plan:
            if name in names:
                continue
            log(f"duel {name}")
            r = duel(re.sub(r"[^A-Za-z0-9]+", "_", name), a, b, sa, sb, games, deadline, 9100)
            r["name"] = name
            state["duels"].append(r)
            report(state)
        log("all experiments done")
    except OutOfTime:
        log("deadline reached: report written with the experiments finished so far")
    report(state)


if __name__ == "__main__":
    main()
