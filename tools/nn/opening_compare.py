"""Who plays which opening, and how the second player scores per opening, in a recorded arena.

    python opening_compare.py runs/arena_175_vs_198_games.txt 175 198

For plies 1..6: distinct positions and effective number of openings (exp of the entropy), per
colour arrangement, noting which network chose that ply. Then second-player non-losses grouped
by the second player's first reply (ply 2), and on 6-ply openings reached in both arrangements.
"""
import math
import sys
from collections import Counter, defaultdict

from diversity import canonical_position, load


def effective(counter):
    n = sum(counter.values())
    h = -sum(c / n * math.log(c / n) for c in counter.values())
    return math.exp(h)


def main(path, name_a, name_b):
    games = load(path)
    arrangements = {
        1: (name_a, name_b),  # A first
        0: (name_b, name_a),
    }
    per_opening = {}
    for a_first, (first, second) in arrangements.items():
        subset = [g for g in games if g[0] == a_first]
        print(f"\n== {first} first, {second} second: {len(subset)} games")
        for k in range(1, 7):
            mover = first if k % 2 == 1 else second
            keys = Counter(canonical_position(m[:k]) for _, _, m in subset)
            top = keys.most_common(1)[0][1]
            print(f"   ply {k} (chosen by {mover}): {len(keys):4d} distinct, effective {effective(keys):6.1f}, "
                  f"most frequent {100 * top / len(subset):5.1f}%")
        by_reply = defaultdict(lambda: [0, 0])
        for _, r, m in subset:
            key = canonical_position(m[:2])
            by_reply[key][0] += 1
            by_reply[key][1] += r != 0
        print(f"   {second}'s reply at ply 2 -> games, {second} wins+draws:")
        for key, (n, ok) in sorted(by_reply.items(), key=lambda kv: -kv[1][0]):
            print(f"      {n:5d} games, {ok:3d} non-losses ({100 * ok / n:.2f}%)")
        stats = defaultdict(lambda: [0, 0])
        for _, r, m in subset:
            key = canonical_position(m[:6])
            stats[key][0] += 1
            stats[key][1] += r != 0
        per_opening[second] = stats
    # Openings (6 plies) reached with each network playing second.
    sa, sb = per_opening[name_a], per_opening[name_b]
    common = set(sa) & set(sb)
    na = sum(sa[k][0] for k in common)
    nb = sum(sb[k][0] for k in common)
    oka = sum(sa[k][1] for k in common)
    okb = sum(sb[k][1] for k in common)
    print(f"\n6-ply openings reached in both arrangements: {len(common)}")
    print(f"   {name_a} second on them: {na} games, {oka} non-losses ({100 * oka / max(1, na):.2f}%)")
    print(f"   {name_b} second on them: {nb} games, {okb} non-losses ({100 * okb / max(1, nb):.2f}%)")
    # Same, weighting every common opening equally (removes frequency effects).
    ra = [sa[k][1] / sa[k][0] for k in common]
    rb = [sb[k][1] / sb[k][0] for k in common]
    print(f"   mean per-opening non-loss rate: {name_a} {100 * sum(ra) / max(1, len(ra)):.2f}%, "
          f"{name_b} {100 * sum(rb) / max(1, len(rb)):.2f}%")
    better = sum(1 for k in common if sb[k][1] / sb[k][0] > sa[k][1] / sa[k][0])
    worse = sum(1 for k in common if sb[k][1] / sb[k][0] < sa[k][1] / sa[k][0])
    print(f"   openings where {name_b} scores better as second: {better}, worse: {worse}, "
          f"equal: {len(common) - better - worse}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
