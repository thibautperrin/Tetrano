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

FLAG_POLICY = 1
FLAG_EXACT = 2


def load(path):
    return np.fromfile(path, dtype=RECORD)


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
    data = np.concatenate(list(reversed(chunks))) if chunks else np.zeros(0, dtype=RECORD)
    return data[-max_positions:]


def summary(arr):
    n = len(arr)
    if n == 0:
        return "0 positions"
    out = np.bincount(arr["outcome"], minlength=3) / n
    return (f"{n} positions, outcome W/D/L {out[0]:.3f}/{out[1]:.3f}/{out[2]:.3f}, "
            f"policy {np.mean(arr['flags'] & FLAG_POLICY > 0):.3f}, exact {np.mean(arr['flags'] & FLAG_EXACT > 0):.3f}")


def files_in(directory):
    return sorted(Path(directory).glob("gen_*.bin"))
