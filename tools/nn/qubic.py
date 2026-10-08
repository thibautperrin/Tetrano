"""Qubic (Tetrano) geometry on 64-bit bitboards: cell = x + 4*y + 16*z.

Same numbering as the app's rings. Line order may differ from the app's
BoardTables: nothing in the network depends on it (sums over lines).
"""
from itertools import product

import numpy as np

CELLS = 64


def _cell(x, y, z):
    return x + 4 * y + 16 * z


def _build_lines():
    lines = []
    dirs = [d for d in product((-1, 0, 1), repeat=3) if d != (0, 0, 0)]
    dirs = [d for d in dirs if next(c for c in d if c != 0) > 0]
    for dx, dy, dz in dirs:
        for x, y, z in product(range(4), repeat=3):
            end = (x + 3 * dx, y + 3 * dy, z + 3 * dz)
            before = (x - dx, y - dy, z - dz)
            if not all(0 <= c < 4 for c in end) or all(0 <= c < 4 for c in before):
                continue
            lines.append([_cell(x + i * dx, y + i * dy, z + i * dz) for i in range(4)])
    assert len(lines) == 76
    return lines


LINES = _build_lines()
LINE_MASKS = np.array([sum(1 << c for c in l) for l in LINES], dtype=np.uint64)
# incidence[l, c] = 1 iff cell c is on line l
INCIDENCE = np.zeros((76, CELLS), dtype=np.float32)
for _l, _cells in enumerate(LINES):
    INCIDENCE[_l, _cells] = 1.0
DEGREE = INCIDENCE.sum(axis=0)  # 4 or 7
DEGREE_CLASS = (DEGREE == 7).astype(np.int64)


def _coord_map(f):
    perm = []
    for c in range(CELLS):
        x, y, z = c & 3, (c >> 2) & 3, c >> 4
        perm.append(_cell(*f(x, y, z)))
    return tuple(perm)


def _build_symmetries():
    inside = (0, 2, 1, 3)
    pairs = (1, 0, 3, 2)
    gens = [
        _coord_map(lambda x, y, z: (y, x, z)),
        _coord_map(lambda x, y, z: (y, z, x)),
        _coord_map(lambda x, y, z: (3 - x, y, z)),
        _coord_map(lambda x, y, z: (inside[x], inside[y], inside[z])),
        _coord_map(lambda x, y, z: (pairs[x], pairs[y], pairs[z])),
    ]
    identity = tuple(range(CELLS))
    group = [identity]
    seen = {identity}
    i = 0
    while i < len(group):
        cur = group[i]
        i += 1
        for g in gens:
            comp = tuple(g[cur[c]] for c in range(CELLS))
            if comp not in seen:
                seen.add(comp)
                group.append(comp)
    line_set = {frozenset(l) for l in LINES}
    for perm in group:
        for l in LINES:
            assert frozenset(perm[c] for c in l) in line_set
    return np.array(group, dtype=np.int64)


SYMMETRIES = _build_symmetries()  # [192, 64]


def bits_to_array(bits):
    """uint64 array [N] -> bool array [N, 64]."""
    bits = np.asarray(bits, dtype=np.uint64)
    shifts = np.arange(CELLS, dtype=np.uint64)
    return ((bits[:, None] >> shifts[None, :]) & np.uint64(1)).astype(bool)


def array_to_bits(arr):
    """bool array [N, 64] -> uint64 array [N]."""
    weights = np.uint64(1) << np.arange(CELLS, dtype=np.uint64)
    return (arr.astype(np.uint64) * weights[None, :]).sum(axis=1, dtype=np.uint64)


def transform(sym, bits):
    """Image of bitboards under symmetry index sym."""
    arr = bits_to_array(bits)
    out = np.zeros_like(arr)
    out[:, SYMMETRIES[sym]] = arr
    return array_to_bits(out)


def encode(mine, theirs):
    """Network inputs from bitboards (player to move = mine).

    Returns cell_state [N,64] int64 (0 empty, 1 mine, 2 theirs),
    line_state [N,76] int64 (0 empty, 1..3 mine, 4..6 theirs, 7 dead),
    first [N] float32 (1 iff equal ring counts).
    """
    m = bits_to_array(mine)
    t = bits_to_array(theirs)
    cell_state = m.astype(np.int64) + 2 * t.astype(np.int64)
    mc = m.astype(np.float32) @ INCIDENCE.T  # [N,76]
    tc = t.astype(np.float32) @ INCIDENCE.T
    mc = mc.astype(np.int64)
    tc = tc.astype(np.int64)
    line_state = np.where((mc > 0) & (tc > 0), 7,
                          np.where(mc > 0, np.minimum(mc, 3),
                                   np.where(tc > 0, 3 + np.minimum(tc, 3), 0)))
    first = (m.sum(axis=1) == t.sum(axis=1)).astype(np.float32)
    return cell_state, line_state.astype(np.int64), first
