"""Compares stopping rules for GumbelMcts.searchAdaptive offline, from `selfplay rounds` output.

    .venv/Scripts/python adaptive_rules.py runs/battle/rounds.jsonl

Each line holds a position's rounds (64, 128, ... simulations of one growing search, never stopped)
and a separate reference search of 2048 simulations. A rule picks the first round where it stops;
the move is that round's choice. Per rule: agreement with the reference's move (up to symmetry),
mean regret (reference Q of the reference's move minus reference Q of the move) and mean
simulations. Fixed budgets are the rounds at that budget.
"""
import json
import sys

import numpy as np


def load(path):
    out = []
    for line in open(path, encoding="utf-8"):
        if not line.strip():
            continue
        p = json.loads(line)
        ref_q = {c: q for c, q, v, m in p["ref"]["classes"]}
        out.append((p["ref"]["chosen"], ref_q, p["rounds"]))
    return out


def stats(r):
    """Chosen class's Q, best other visited Q, chosen policy mass, chosen visit share."""
    qs = {c: q for c, q, v, m in r["classes"]}
    visits = {c: v for c, q, v, m in r["classes"]}
    mass = {c: m for c, q, v, m in r["classes"]}
    ch = r["chosen"]
    q = qs.get(ch)
    others = [qs[c] for c in qs if c != ch and visits[c] > 0 and qs[c] is not None]
    second = max(others) if others else None
    total = sum(visits.values()) or 1
    return q, second, mass.get(ch, 0.0), visits.get(ch, 0) / total


def evaluate(data, rule):
    agree, regret, sims = 0, 0.0, 0
    for ref_chosen, ref_q, rounds in data:
        prev = []
        pick = rounds[-1]
        for r in rounds:
            if rule(r, prev):
                pick = r
                break
            prev.append(r)
        if pick["chosen"] == ref_chosen:
            agree += 1
        best, mine = ref_q.get(ref_chosen), ref_q.get(pick["chosen"])
        if best is not None and mine is not None:
            regret += max(0.0, best - mine)
        sims += pick["sims"]
    n = len(data)
    return agree / n, regret / n, sims / n


def fixed(budget):
    return lambda r, prev: r["sims"] >= budget


def margin(delta):
    def rule(r, prev):
        q, second, _, _ = stats(r)
        return q is not None and (second is None or q - second >= delta)
    return rule


def stable(k, min_sims=0):
    """The same choice in k consecutive rounds."""
    def rule(r, prev):
        if r["sims"] < min_sims or len(prev) < k - 1:
            return False
        return all(p["chosen"] == r["chosen"] for p in prev[-(k - 1):]) if k > 1 else True
    return rule


def mass(p):
    return lambda r, prev: stats(r)[2] >= p


def stable_and_mass(k, p):
    s = stable(k)
    return lambda r, prev: s(r, prev) and stats(r)[2] >= p


def stable_or_margin(k, delta):
    s, m = stable(k), margin(delta)
    return lambda r, prev: m(r, prev) or s(r, prev)


def main():
    data = load(sys.argv[1] if len(sys.argv) > 1 else "runs/battle/rounds.jsonl")
    rules = [(f"fixed {b}", fixed(b)) for b in (64, 128, 256, 512, 1024, 2048)]
    rules += [(f"margin {d}", margin(d)) for d in (0.1, 0.25, 0.4)]
    rules += [(f"stable {k}", stable(k)) for k in (2, 3, 4)]
    rules += [(f"policy mass >= {p}", mass(p)) for p in (0.5, 0.7, 0.85, 0.95)]
    rules += [(f"stable {k} & mass >= {p}", stable_and_mass(k, p)) for k in (2, 3) for p in (0.5, 0.7, 0.85)]
    print(f"{len(data)} positions")
    print(f"{'rule':28s} {'agree':>6s} {'regret':>7s} {'sims':>6s}")
    for name, rule in rules:
        a, g, s = evaluate(data, rule)
        print(f"{name:28s} {100 * a:5.1f}% {g:7.4f} {s:6.0f}")


if __name__ == "__main__":
    main()
