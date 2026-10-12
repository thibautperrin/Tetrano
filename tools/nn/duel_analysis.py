"""Analysis of a two-network duel recorded by `selfplay match` (games_out files).

    python duel_analysis.py runs/duel_476_570 gen476 gen570

Expects A.txt (empty board, colours alternating) and/or B.txt (random openings, game 2k and
2k+1 on the same opening with the side to move swapped). Line format: "<A to move first: 1|0>
<result: 0 first wins, 1 second wins, -1 draw> <moves...>".
"""
import math
import sys
from collections import Counter
from pathlib import Path

import numpy as np

from diversity import canonical_position


def load(path):
    games = []
    for line in open(path):
        p = line.split()
        if len(p) >= 2:
            games.append((p[0] == "1", int(p[1]), [int(x) for x in p[2:]]))
    return games


def score_a(game):
    """Score of network A in one game."""
    a_first, result, _ = game
    if result == -1:
        return 0.5
    return 1.0 if (result == 0) == a_first else 0.0


def elo(score):
    s = min(max(score, 1e-6), 1 - 1e-6)
    return 400 * math.log10(s / (1 - s))


def interval(samples):
    return np.percentile(samples, 2.5), np.percentile(samples, 97.5)


def mode_a(games, na, nb, rng):
    scores = np.array([score_a(g) for g in games])
    first = Counter()
    for g in games:
        first[("A" if g[0] else "B", {0: "first wins", 1: "second wins", -1: "draw"}[g[1]])] += 1
    boot = [scores[rng.integers(0, len(scores), len(scores))].mean() for _ in range(2000)]
    lo, hi = interval(boot)
    print(f"\n== Mode A (empty board): {len(games)} games")
    for who, name in (("A", na), ("B", nb)):
        n = sum(v for (w, _), v in first.items() if w == who)
        print(f"   {name} moving first ({n} games): " +
              ", ".join(f"{k} {first[(who, k)]}" for k in ("first wins", "draw", "second wins")))
    print(f"   {na} score {scores.mean():.4f} (95% CI {lo:.4f} – {hi:.4f}) => Elo {na} - {nb} = "
          f"{elo(scores.mean()):+.1f} ({elo(lo):+.1f} – {elo(hi):+.1f})")
    seqs = {tuple(g[2]) for g in games}
    prefixes = {canonical_position(g[2][:8]) for g in games if len(g[2]) >= 8}
    print(f"   diversity: {len(seqs)} distinct games, {len(prefixes)} distinct positions after 8 plies")


def mode_b(games, na, nb, rng):
    pairs = [(games[k], games[k + 1]) for k in range(0, len(games) - 1, 2)]
    pair_scores = np.array([score_a(x) + score_a(y) for x, y in pairs])  # A's points over the two games: 0..2
    kinds = Counter()
    for (x, y), s in zip(pairs, pair_scores):
        if s == 1.0:
            # same total: either the opening decides (side to move wins both), or the other side, or draws
            if x[1] == y[1] == 0:
                kinds["opening won by the side to move (each wins once)"] += 1
            elif x[1] == y[1] == 1:
                kinds["opening won by the other side (each wins once)"] += 1
            elif x[1] == y[1] == -1:
                kinds["two draws"] += 1
            else:
                kinds["other split"] += 1
        elif s > 1.0:
            kinds[f"{na} better"] += 1
        else:
            kinds[f"{nb} better"] += 1
    total = pair_scores.mean() / 2
    boot = [pair_scores[rng.integers(0, len(pair_scores), len(pair_scores))].mean() / 2 for _ in range(2000)]
    lo, hi = interval(boot)
    better_a = int((pair_scores > 1).sum())
    better_b = int((pair_scores < 1).sum())
    # sign test on the openings where the networks differ
    n = better_a + better_b
    z = (better_a - n / 2) / math.sqrt(n / 4) if n else 0.0
    print(f"\n== Mode B (random openings): {len(pairs)} openings x 2 games")
    for k, v in kinds.most_common():
        print(f"   {k}: {v} openings ({100 * v / len(pairs):.1f} %)")
    print(f"   {na} score {total:.4f} (95% CI {lo:.4f} – {hi:.4f}) => Elo {na} - {nb} = "
          f"{elo(total):+.1f} ({elo(lo):+.1f} – {elo(hi):+.1f})")
    print(f"   openings where they differ: {na} better on {better_a}, {nb} better on {better_b} "
          f"(sign test z = {z:+.2f})")
    side = Counter(g[1] for g in games)
    print(f"   all games: side to move after the opening wins {side[0]}, draws {side[-1]}, other side wins {side[1]}")
    prefixes = {canonical_position(g[2][:5]) for g in games[::2]}
    print(f"   diversity: {len(prefixes)} distinct openings (up to symmetry)")


def main():
    folder, na, nb = Path(sys.argv[1]), sys.argv[2], sys.argv[3]
    rng = np.random.default_rng(1)
    if (folder / "A.txt").exists():
        mode_a(load(folder / "A.txt"), na, nb, rng)
    if (folder / "B.txt").exists():
        mode_b(load(folder / "B.txt"), na, nb, rng)


if __name__ == "__main__":
    main()
