"""Diversity of recorded games (selfplay `arena`/`vslegacy` with games_out=...).

    python diversity.py runs/arena_175_vs_198_games.txt

Counts distinct positions after k plies and distinct whole games, up to the 192 symmetries,
separately for each "A plays first" side, plus how results are spread over the openings.
"""
import sys
from collections import Counter

import numpy as np

import qubic

# perms[s][c] = image of c under symmetry s
PERMS = qubic.SYMMETRIES


def canonical_position(moves):
    """Canonical (first player's cells, second player's cells) over the 192 symmetries."""
    def images(cells):
        cells = np.array(cells, dtype=np.int64)
        if cells.size == 0:
            return np.zeros(len(PERMS), dtype=np.uint64)
        bits = np.uint64(1) << PERMS[:, cells].astype(np.uint64)  # [192, n]
        return np.bitwise_or.reduce(bits, axis=1)

    a = images(moves[0::2])
    b = images(moves[1::2])
    i = np.lexsort((b, a))[0]  # smallest a, then smallest b
    return int(a[i]), int(b[i])


def canonical_sequence(moves):
    """Canonical move order over the 192 symmetries (a whole game, order included)."""
    arr = np.array(moves, dtype=np.int64)
    return min(tuple(perm[arr].tolist()) for perm in PERMS)


def load(path):
    games = []
    for line in open(path):
        parts = line.split()
        if len(parts) < 2:
            continue
        games.append((int(parts[0]), int(parts[1]), [int(x) for x in parts[2:]]))
    return games


def main(path):
    games = load(path)
    print(f"{len(games)} games from {path}")
    for a_first in (1, 0):
        subset = [g for g in games if g[0] == a_first]
        label = "A (1st arg) first" if a_first else "B (2nd arg) first"
        lengths = [len(m) for _, _, m in subset]
        results = Counter(r for _, r, _ in subset)
        print(f"\n== {label}: {len(subset)} games, results {dict(results)} (0 first wins, 1 second wins, -1 draw)")
        print(f"   length: mean {np.mean(lengths):.1f}, min {min(lengths)}, max {max(lengths)}")
        for k in (1, 2, 3, 4, 6, 8, 10, 14):
            keys = [canonical_position(m[:k]) for _, _, m in subset if len(m) >= k]
            counts = Counter(keys)
            top = counts.most_common(1)[0][1] if counts else 0
            print(f"   after {k:2d} plies: {len(counts):5d} distinct positions "
                  f"(most frequent: {top} games = {100 * top / max(1, len(keys)):.1f}%)")
        full = Counter(canonical_sequence(m) for _, _, m in subset)
        top = full.most_common(1)[0][1]
        print(f"   whole games: {len(full)} distinct (most frequent repeated {top} times)")
        # Are the second-player non-losses spread over many openings or one repeated line?
        nonloss = [m for _, r, m in subset if r != 0]
        if nonloss:
            lines = Counter(canonical_sequence(m) for m in nonloss)
            openings = Counter(canonical_position(m[:6]) for m in nonloss)
            print(f"   2nd-player wins/draws: {len(nonloss)} games, {len(lines)} distinct games, "
                  f"{len(openings)} distinct 6-ply openings")


if __name__ == "__main__":
    main(sys.argv[1])
