"""Export a checkpoint to the app's QNET format, and parity fixtures for the Kotlin tests.

    python export.py --checkpoint runs/main/checkpoints/gen_0042.pt --out tetrano_nn.bin
    python export.py --random --hidden 16 --layers 2 --out fixture_net.bin --fixture fixture_positions.bin
"""
import argparse
import struct

import numpy as np
import torch

from model import QubicNet, evaluate_bits


def random_positions(n, seed):
    """Random positions from the point of view of the player to move."""
    rng = np.random.default_rng(seed)
    mine, theirs = [], []
    for _ in range(n):
        plies = int(rng.integers(0, 50))
        cells = rng.permutation(64)[:plies]
        first_moves = cells[0::2]
        second_moves = cells[1::2]
        a = sum(1 << int(c) for c in first_moves)
        b = sum(1 << int(c) for c in second_moves)
        # player to move: first player if plies is even
        if plies % 2 == 0:
            mine.append(a), theirs.append(b)
        else:
            mine.append(b), theirs.append(a)
    return np.array(mine, dtype=np.uint64), np.array(theirs, dtype=np.uint64)


def write_fixture(model, path, n=200, seed=7):
    mine, theirs = random_positions(n, seed)
    pol, wdl = evaluate_bits(model, mine, theirs)
    with open(path, "wb") as f:
        f.write(struct.pack("<i", n))
        for i in range(n):
            f.write(struct.pack("<QQ", int(mine[i]), int(theirs[i])))
            f.write(pol[i].astype("<f4").tobytes())
            f.write(wdl[i].astype("<f4").tobytes())


def load_checkpoint(path):
    ckpt = torch.load(path, map_location="cpu", weights_only=False)
    model = QubicNet(ckpt["hidden"], ckpt["layers"])
    model.load_state_dict(ckpt["model"])
    model.eval()
    return model


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint")
    ap.add_argument("--random", action="store_true")
    ap.add_argument("--hidden", type=int, default=48)
    ap.add_argument("--layers", type=int, default=6)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", required=True)
    ap.add_argument("--fixture")
    args = ap.parse_args()
    if args.random:
        torch.manual_seed(args.seed)
        model = QubicNet(args.hidden, args.layers)
        # Randomize LayerNorm affine params too, so the fixture exercises them.
        with torch.no_grad():
            for m in model.modules():
                if isinstance(m, torch.nn.LayerNorm):
                    m.weight.uniform_(0.5, 1.5)
                    m.bias.uniform_(-0.3, 0.3)
        model.eval()
    else:
        model = load_checkpoint(args.checkpoint)
    model.export_weights(args.out)
    if args.fixture:
        write_fixture(model, args.fixture)
    print(f"exported {sum(p.numel() for p in model.parameters())} parameters to {args.out}")


if __name__ == "__main__":
    main()
