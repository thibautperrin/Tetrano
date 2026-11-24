"""Self-play position files written by `:selfplay` (see RECORD_SIZE in selfplay/Main.kt)."""
from pathlib import Path

import numpy as np

RECORD = np.dtype([
    ("mine", "<u8"),
    ("theirs", "<u8"),
    ("outcome", "u1"),  # 0 win, 1 draw, 2 loss for the player to move
    ("flags", "u1"),  # bit 0: policy target valid, bit 1: exact (proven) position
    ("ply", "u1"),
    ("moves_left", "u1"),  # plies until the end of the game (0 in files from before the moves-left head)
    ("policy", "<u2", (64,)),  # probability * 65535
    ("value", "<f4"),  # root search value for the player to move
])
assert RECORD.itemsize == 152

# Format v3 (selfplay Records.kt): a 16-byte header, then RECORD + these fields.
RECORD3 = np.dtype(RECORD.descr + [
    ("exact", "u1"),  # exact value for the player to move: 0 unknown, 1 win, 2 draw, 3 loss
    ("source", "u1"),  # 0 self-play, 1 winning graph, 2 truth bench
    ("reserved", "<u2"),
    ("solver_evals", "<u4"),
    ("q", "i1", (64,)),  # mover's value after each move x 100; 101 win, 102 draw, 103 loss proven; -128 unknown
])
assert RECORD3.itemsize == 224
V3_MAGIC = 0x33435254
Q_WIN, Q_DRAW, Q_LOSS, Q_UNKNOWN = 101, 102, 103, -128

FLAG_POLICY = 1
FLAG_EXACT = 2
FLAG_CHOSEN_Q = 4  # the value field is the chosen move's Q (not the biased mix of older files)
FLAG_MOVE_VALUES = 8


def is_v3(path):
    with open(path, "rb") as f:
        head = f.read(16)
    if len(head) < 16:
        return False
    magic, version, size, _ = np.frombuffer(head, dtype="<i4")
    return magic == V3_MAGIC and version == 3 and size == RECORD3.itemsize


def to_v3(arr):
    """v2 records as v3 ones: the "exact" flag of v2 files means the game outcome is exact."""
    out = np.zeros(len(arr), dtype=RECORD3)
    for name in RECORD.names:
        out[name] = arr[name]
    exact = (arr["flags"] & FLAG_EXACT) > 0
    out["exact"] = np.where(exact, arr["outcome"] + 1, 0)
    out["q"] = Q_UNKNOWN
    return out


def load(path):
    """Records of a v2 or v3 file, always as RECORD3."""
    if is_v3(path):
        return np.fromfile(path, dtype=RECORD3, offset=16)
    return to_v3(np.fromfile(path, dtype=RECORD))


def count(path):
    size = Path(path).stat().st_size
    return (size - 16) // RECORD3.itemsize if is_v3(path) else size // RECORD.itemsize


def save_v3(path, arr):
    path = Path(path)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "wb") as f:
        f.write(np.array([V3_MAGIC, 3, RECORD3.itemsize, 0], dtype="<i4").tobytes())
        f.write(arr.astype(RECORD3).tobytes())
    tmp.replace(path)


def load_window(files, max_positions):
    """The most recent positions of [files] (sorted oldest first), at most max_positions."""
    chunks = []
    total = 0
    for f in reversed(list(files)):
        arr = load(f)
        chunks.append(arr)
        total += len(arr)
        if total >= max_positions:
            break
    data = np.concatenate(list(reversed(chunks))) if chunks else np.zeros(0, dtype=RECORD3)
    return data[-max_positions:]


def summary(arr):
    n = len(arr)
    if n == 0:
        return "0 positions"
    out = np.bincount(arr["outcome"], minlength=3) / n
    exact = np.bincount(arr["exact"], minlength=4) / n
    return (f"{n} positions, outcome W/D/L {out[0]:.3f}/{out[1]:.3f}/{out[2]:.3f}, "
            f"policy {np.mean(arr['flags'] & FLAG_POLICY > 0):.3f}, exact {1 - exact[0]:.3f} "
            f"(W/D/L {exact[1]:.3f}/{exact[2]:.3f}/{exact[3]:.3f}), with move values {np.mean((arr['q'] != Q_UNKNOWN).any(axis=1)):.3f}")


def add_played_move_values(arr):
    """For consecutive positions of one game (record i+1 follows record i by one move), sets the
    played move's value in record i to the mover's exact value after it (from record i+1's exact
    value), where it is known and not already set. In place; returns the number set."""
    if len(arr) < 2:
        return 0
    cur, nxt = arr[:-1], arr[1:]
    follows = (nxt["mine"] == cur["theirs"]) & (nxt["ply"] == cur["ply"] + 1)
    moved = nxt["theirs"] ^ cur["mine"]
    follows &= (moved & cur["mine"]) == 0
    follows &= (moved != 0) & ((moved & (moved - np.uint64(1))) == 0)
    follows &= nxt["exact"] > 0
    idx = np.nonzero(follows)[0]
    if len(idx) == 0:
        return 0
    cells = np.log2(moved[idx].astype(np.float64)).round().astype(np.int64)
    # Mover's value after the move = the opponent's exact value, flipped: 1 win -> loss, 3 loss -> win.
    code = np.array([0, Q_LOSS, Q_DRAW, Q_WIN], dtype=np.int8)[nxt["exact"][idx]]
    q = arr["q"]
    keep = q[idx, cells] == Q_UNKNOWN
    q[idx[keep], cells[keep]] = code[keep]
    arr["q"] = q
    return int(keep.sum())


def files_in(directory):
    return sorted(Path(directory).glob("gen_*.bin"))
