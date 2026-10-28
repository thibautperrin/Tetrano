"""Decode the Tetrano winning graph and walk its opening."""
import struct, sys
from functools import lru_cache

M64 = (1 << 64) - 1

def cell(x, y, z): return x + 4 * y + 16 * z
def xyz(c): return c & 3, (c >> 2) & 3, c >> 4

# --- lines (76) -------------------------------------------------------------
def build_lines():
    lines = set()
    dirs = []
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dy, dz) != (0, 0, 0):
                    dirs.append((dx, dy, dz))
    for c in range(64):
        x, y, z = xyz(c)
        for dx, dy, dz in dirs:
            pts = [(x + i * dx, y + i * dy, z + i * dz) for i in range(4)]
            if all(0 <= a < 4 and 0 <= b < 4 and 0 <= e < 4 for a, b, e in pts):
                m = 0
                for p in pts: m |= 1 << cell(*p)
                lines.add(m)
    return sorted(lines)

LINES = build_lines()
assert len(LINES) == 76
CELL_LINES = [[l for l in LINES if l >> c & 1] for c in range(64)]

# --- symmetries (192) -------------------------------------------------------
def cmap(f):
    return tuple(cell(*f(*xyz(c))) for c in range(64))

def build_syms():
    inside = (0, 2, 1, 3); pairs = (1, 0, 3, 2)
    gens = [cmap(lambda x, y, z: (y, x, z)), cmap(lambda x, y, z: (y, z, x)),
            cmap(lambda x, y, z: (3 - x, y, z)),
            cmap(lambda x, y, z: (inside[x], inside[y], inside[z])),
            cmap(lambda x, y, z: (pairs[x], pairs[y], pairs[z]))]
    ident = tuple(range(64)); group = [ident]; seen = {ident}; i = 0
    while i < len(group):
        cur = group[i]; i += 1
        for g in gens:
            comp = tuple(g[cur[k]] for k in range(64))
            if comp not in seen: seen.add(comp); group.append(comp)
    return group

SYMS = build_syms()
assert len(SYMS) == 192
TABLES = []
for s in SYMS:
    t = []
    for b in range(8):
        row = []
        for v in range(256):
            img = 0
            for bit in range(8):
                if v >> bit & 1: img |= 1 << s[8 * b + bit]
            row.append(img)
        t.append(row)
    TABLES.append(t)

def transform(si, bits):
    t = TABLES[si]; r = 0; b = 0
    while bits:
        r |= t[b][bits & 0xFF]; bits >>= 8; b += 1
    return r

@lru_cache(maxsize=None)
def canon(a, d):
    best = (a, d, 0)
    for si in range(1, 192):
        ta = transform(si, a)
        if ta > best[0]: continue
        td = transform(si, d)
        if ta < best[0] or td < best[1]: best = (ta, td, si)
    return best

# --- book -------------------------------------------------------------------
def load(path):
    data = open(path, 'rb').read()
    magic, ver, n = struct.unpack('>iii', data[:12])
    assert magic == 0x5157494E and ver == 1
    book = {}
    off = 12
    for _ in range(n):
        a, d, m = struct.unpack('>QQb', data[off:off + 17]); off += 17
        book[(a, d)] = m
    return book

def threats(me, other):
    """Cells completing a line of 3 of `me` free of `other`."""
    r = 0
    for l in LINES:
        if l & other: continue
        own = l & me
        if bin(own).count('1') == 3: r |= l & ~own
    return r

def lowest(b): return (b & -b).bit_length() - 1

def decide(a, d, book):
    """Attacker move in real coords and the rule used; ('vcf', None) if none of 1-3."""
    w = threats(a, d)
    if w: return 'win', lowest(w)
    b = threats(d, a)
    if b: return 'block', lowest(b)
    ca, cd, si = canon(a, d)
    m = book.get((ca, cd))
    if m is not None:
        return 'book', SYMS[si].index(m)
    return 'vcf', None

def replies(a, d):
    """One real representative per class of defender reply (lowest cell), as (cell, canon key)."""
    seen = {}
    occ = a | d
    for c in range(64):
        if occ >> c & 1: continue
        k = canon(a, d | 1 << c)[:2]
        if k not in seen: seen[k] = c
    return [(c, k) for k, c in seen.items()]
