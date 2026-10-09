"""Round-robin tournament between networks of a training run, with two Elo ratings.

    python tournament.py --run runs/weekend --reference runs/main/models/gen_0198.bin \
        --stop-at 2026-10-11T20:00

Participants: the reference network (Elo fixed at 1000), the run's last network, and 5 networks
taken at 1/6 ... 5/6 of the run's cumulative compute time (`compute_s` in metrics.csv).

Two modes, played in alternating rounds until --stop-at (every pair plays the same number of
games in each completed round):
- A, empty board: colours alternate; 4 noisy moves per network, then deterministic (as level 101).
- B, random openings: 5 random moves (fixed set, identical for all pairs, distinct up to
  symmetry, no threat), each opening played twice so that each network is once the side to move.

Ratings: Bradley-Terry maximum likelihood (draw = half a win), reference = 1000, 95% bootstrap
intervals. Results: <out>/report.md, <out>/ratings.csv, games in <out>/games/. Resumable.
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
from collections import defaultdict
from itertools import combinations
from pathlib import Path

import numpy as np

from loop import SELFPLAY_LIB, keep_system_awake


def log(out, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}"
    print(line, flush=True)
    with open(out / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def participants(run, reference):
    """[(name, model path, hours of compute)] — reference first."""
    rows = list(csv.DictReader(open(run / "metrics.csv")))
    rows = [r for r in rows if r.get("compute_s") and (run / "models" / f"gen_{int(r['generation']):04d}.bin").exists()]
    if not rows:
        sys.exit("no trained generation in the run")
    total = float(rows[-1]["compute_s"])
    chosen = []
    for k in range(1, 6):
        target = total * k / 6
        best = min(rows, key=lambda r: abs(float(r["compute_s"]) - target))
        chosen.append(best)
    chosen.append(rows[-1])
    out = [("ref", str(reference), 0.0)]
    seen = set()
    for r in chosen:
        g = int(r["generation"])
        if g in seen:
            continue
        seen.add(g)
        out.append((f"gen{g}", str(run / "models" / f"gen_{g:04d}.bin"), float(r["compute_s"]) / 3600))
    return out


def play(args, a, b, mode, round_index, games_file, deadline):
    """One batch of games between a and b; True if completed before the deadline."""
    cmd = ["java", "-Xmx4g", "-cp", str(SELFPLAY_LIB / "*"), "fr.thibautperrin.tetrano.selfplay.MainKt", "match",
           f"a={a}", f"b={b}", f"games={args.round_games}", f"sims={args.sims}", f"threads={args.threads}",
           f"openings={'random' if mode == 'B' else 'empty'}", f"seed={args.seed + (0 if mode == 'B' else round_index)}",
           f"offset={round_index * args.round_games // 2 if mode == 'B' else 0}", f"games_out={games_file}.tmp"]
    left = deadline - time.time()
    if left <= 0:
        return False
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=left)
    except subprocess.TimeoutExpired:
        return False
    if proc.returncode != 0:
        raise RuntimeError(proc.stdout + proc.stderr)
    os.replace(f"{games_file}.tmp", games_file)
    return True


def load_games(out, names):
    """{mode: [(i, j, score of i)]} from the finished game files (names indexes)."""
    games = defaultdict(list)
    index = {n: k for k, n in enumerate(names)}
    for f in sorted((out / "games").glob("*.txt")):
        mode, _, a, b = f.stem.split("_", 3)
        if a not in index or b not in index:
            continue
        for line in open(f):
            parts = line.split()
            if len(parts) < 2:
                continue
            a_first, result = parts[0] == "1", int(parts[1])
            score = 0.5 if result == -1 else (1.0 if (result == 0) == a_first else 0.0)
            games[mode].append((index[a], index[b], score))
    return games


def bradley_terry(n, games, anchor=0, iterations=2000):
    """Elo ratings (anchor = 1000) by MM iterations on win counts, draws counted as half wins."""
    wins = np.zeros((n, n))
    for i, j, s in games:
        wins[i, j] += s
        wins[j, i] += 1 - s
    played = wins + wins.T
    gamma = np.ones(n)
    total_wins = wins.sum(axis=1) + 0.5  # +0.5: a small prior so a player who never scores stays finite
    for _ in range(iterations):
        denom = np.array([sum(played[i, j] / (gamma[i] + gamma[j]) for j in range(n) if j != i and played[i, j] > 0)
                          + 1.0 / (gamma[i] + 1.0) for i in range(n)])
        new = total_wins / np.maximum(denom, 1e-12)
        new /= new[anchor]
        if np.max(np.abs(np.log(new) - np.log(gamma))) < 1e-9:
            gamma = new
            break
        gamma = new
    return 1000 + 400 * np.log10(gamma / gamma[anchor])


def ratings(n, games, rng, boots=200):
    elo = bradley_terry(n, games)
    by_pair = defaultdict(list)
    for g in games:
        by_pair[(g[0], g[1])].append(g)
    samples = []
    for _ in range(boots):
        resampled = []
        for pair_games in by_pair.values():
            idx = rng.integers(0, len(pair_games), size=len(pair_games))
            resampled += [pair_games[k] for k in idx]
        samples.append(bradley_terry(n, resampled, iterations=300))
    samples = np.array(samples)
    return elo, np.percentile(samples, 2.5, axis=0), np.percentile(samples, 97.5, axis=0)


def diversity(out, mode, plies=8):
    """(games, distinct whole games, distinct positions after `plies` plies up to symmetry) of a mode."""
    from diversity import canonical_position
    sequences = []
    for f in (out / "games").glob(f"{mode}_*.txt"):
        for line in open(f):
            parts = line.split()
            if len(parts) > 2:
                sequences.append(tuple(int(x) for x in parts[2:]))
    prefixes = {canonical_position(list(seq[:plies])) for seq in sequences if len(seq) >= plies}
    return len(sequences), len(set(sequences)), len(prefixes)


def report(out, people, games, started, finished, final=False):
    names = [p[0] for p in people]
    rng = np.random.default_rng(1)
    lines = ["# Tournoi du week-end", "",
             f"Du {started} au {finished}. Référence : génération 198 (`ref`), Elo fixé à 1000.", "",
             "| Nom | Modèle | Heures de calcul |", "|---|---|---|"]
    for name, path, hours in people:
        lines.append(f"| {name} | `{Path(path).name}` | {hours:.1f} |")
    rows = []
    for mode, title in (("A", "Mode A — plateau vierge"), ("B", "Mode B — 5 premiers coups aléatoires")):
        g = games.get(mode, [])
        lines += ["", f"## {title}", "", f"{len(g)} parties."]
        if not g:
            continue
        if final:
            n, distinct, prefixes = diversity(out, mode)
            lines += ["", f"Diversité : {distinct} parties distinctes sur {n}, {prefixes} positions distinctes "
                          f"(aux 192 symétries près) après 8 coups."]
        elo, lo, hi = ratings(len(names), g, rng)
        order = np.argsort(-elo)
        lines += ["", "| Rang | Nom | Elo | IC 95 % |", "|---|---|---|---|"]
        for rank, k in enumerate(order, 1):
            lines.append(f"| {rank} | {names[k]} | {elo[k]:.0f} | {lo[k]:.0f} – {hi[k]:.0f} |")
            rows.append({"mode": mode, "name": names[k], "elo": round(elo[k], 1),
                         "ci_low": round(lo[k], 1), "ci_high": round(hi[k], 1)})
        score = defaultdict(float)
        count = defaultdict(int)
        for i, j, s in g:
            score[(i, j)] += s
            score[(j, i)] += 1 - s
            count[(i, j)] += 1
            count[(j, i)] += 1
        lines += ["", "Score de la ligne contre la colonne :", "",
                  "| | " + " | ".join(names) + " |", "|---" * (len(names) + 1) + "|"]
        for i in range(len(names)):
            cells = [f"{100 * score[(i, j)] / count[(i, j)]:.0f} %" if count[(i, j)] else "—" for j in range(len(names))]
            lines.append(f"| {names[i]} | " + " | ".join(cells) + " |")
    (out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    with open(out / "ratings.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["mode", "name", "elo", "ci_low", "ci_high"])
        w.writeheader()
        w.writerows(rows)


def main():
    keep_system_awake()
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", required=True)
    ap.add_argument("--reference", required=True)
    ap.add_argument("--stop-at", required=True)
    ap.add_argument("--out")
    ap.add_argument("--sims", type=int, default=128)
    ap.add_argument("--round-games", type=int, default=40)
    ap.add_argument("--threads", type=int, default=max(1, (os.cpu_count() or 4) - 2))
    ap.add_argument("--seed", type=int, default=20261011)
    args = ap.parse_args()
    run = Path(args.run)
    out = Path(args.out) if args.out else run / "tournament"
    (out / "games").mkdir(parents=True, exist_ok=True)
    deadline = dt.datetime.fromisoformat(args.stop_at).timestamp()

    state_path = out / "state.json"
    if state_path.exists():
        state = json.loads(state_path.read_text())
    else:
        state = {"participants": participants(run, Path(args.reference)), "started": f"{dt.datetime.now():%Y-%m-%d %H:%M}"}
        state_path.write_text(json.dumps(state, indent=1))
    people = [tuple(p) for p in state["participants"]]
    names = [p[0] for p in people]
    log(out, f"participants: {people}")
    pairs = list(combinations(range(len(people)), 2))

    round_index = {"A": 0, "B": 0}
    while time.time() < deadline:
        for mode in ("A", "B"):
            r = round_index[mode]
            complete = True
            for i, j in pairs:
                games_file = out / "games" / f"{mode}_{r:03d}_{names[i]}_{names[j]}.txt"
                if games_file.exists():
                    continue
                if not play(args, people[i][1], people[j][1], mode, r, games_file, deadline):
                    complete = False
                    break
            if not complete:
                # An unfinished round would give some pairs more games: drop it.
                for f in (out / "games").glob(f"{mode}_{r:03d}_*.txt"):
                    f.unlink()
                break
            round_index[mode] += 1
            log(out, f"mode {mode} round {r} done")
            report(out, people, load_games(out, names), state["started"], f"{dt.datetime.now():%Y-%m-%d %H:%M}")
            if time.time() >= deadline:
                break
    report(out, people, load_games(out, names), state["started"], f"{dt.datetime.now():%Y-%m-%d %H:%M}", final=True)
    log(out, f"done: {round_index} rounds; report in {out / 'report.md'}")


if __name__ == "__main__":
    main()
