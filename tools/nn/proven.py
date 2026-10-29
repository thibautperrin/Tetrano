"""Supervised fine-tuning of a network on proven positions mixed with self-play positions.

The proven positions come from the walks of the verified winning graphs (`wingraph export`, see
tools/wingraph/README.md): exact values (win for the attacker to move, loss for the defender to
move) and, at the graph's decision nodes, a proven winning move. Alone they would bias the network
(every one of them is decided, the first player always wins), so each batch also draws from the
latest self-play positions of the run the network comes from.

    python -u proven.py --checkpoint runs/big/checkpoints/gen_0364.pt --selfplay runs/big/data \
        --proven runs/proven_data/corner.bin,runs/proven_data/weak.bin --out runs/proven_ft

Writes out/ft.pt (checkpoint, same format as loop.py's), out/ft.bin (app model) and out/report.md
(losses and value accuracy on held-out proven and self-play positions, before and after).
Resumable: out/state.pt every 500 steps.
"""
import argparse
import math
import os
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import load_checkpoint
from winproc import keep_system_awake


def split(arr, test_frac, seed):
    rng = np.random.default_rng(seed)
    test = rng.random(len(arr)) < test_frac
    return arr[~test], arr[test]


def lr_at(step, steps, peak, final, warmup):
    if step < warmup:
        return peak * (step + 1) / warmup
    t = (step - warmup) / max(1, steps - warmup)
    return final + 0.5 * (peak - final) * (1 + math.cos(math.pi * t))


def describe(model, sets):
    rows = []
    for name, arr in sets:
        v, p, m, acc = train.evaluate(model, arr)
        rows.append(f"| {name} | {v:.4f} | {p:.4f} | {acc:.4f} |")
    return rows


def main():
    keep_system_awake()
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", required=True)
    ap.add_argument("--selfplay", required=True, help="self-play data directory (its gen_*.bin)")
    ap.add_argument("--proven", required=True, help="comma-separated proven position files")
    ap.add_argument("--out", required=True)
    ap.add_argument("--window", type=int, default=1_500_000)
    ap.add_argument("--test-gens", type=int, default=1, help="latest self-play files kept for the test")
    ap.add_argument("--steps", type=int, default=6000)
    ap.add_argument("--batch", type=int, default=1024)
    ap.add_argument("--proven-frac", type=float, default=0.35)
    ap.add_argument("--lr", type=float, default=1e-4)
    ap.add_argument("--lr-final", type=float, default=1e-5)
    ap.add_argument("--warmup", type=int, default=200)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(os.cpu_count() or 4)

    files = data.files_in(args.selfplay)
    sp_test = np.concatenate([data.load(f) for f in files[-args.test_gens:]])
    sp_train = data.load_window(files[:-args.test_gens], args.window)
    proven = np.concatenate([data.load(f) for f in args.proven.split(",")])
    pv_train, pv_test = split(proven, 0.03, 20261028)
    pv_test = pv_test[np.random.default_rng(1).permutation(len(pv_test))[:100_000]]
    print(f"self-play train {data.summary(sp_train)}\nself-play test {data.summary(sp_test)}\n"
          f"proven train {data.summary(pv_train)}\nproven test {data.summary(pv_test)}", flush=True)
    book_test = pv_test[(pv_test["flags"] & data.FLAG_POLICY) > 0]
    sets = [("self-play (test)", sp_test), ("prouvées (test)", pv_test), ("prouvées, nœuds du graphe (test)", book_test)]

    model, ckpt, _ = load_checkpoint(args.checkpoint)
    optimizer = train.make_optimizer(model, args.lr, 1e-4)
    if "optimizer" in ckpt:
        optimizer.load_state_dict(ckpt["optimizer"])
    state_path = out / "state.pt"
    step = 0
    before = None
    if state_path.exists():
        st = torch.load(state_path, map_location="cpu", weights_only=False)
        model.load_state_dict(st["model"])
        optimizer.load_state_dict(st["optimizer"])
        step = st["step"]
        before = st["before"]
        print(f"resumed at step {step}", flush=True)
    else:
        before = describe(model, sets)
    rng = np.random.default_rng(step + 7)
    started = time.time()
    while step < args.steps:
        n = min(500 - step % 500, args.steps - step)
        for group in optimizer.param_groups:
            group["lr"] = lr_at(step, args.steps, args.lr, args.lr_final, args.warmup)
        # The learning rate is set per chunk (up to the next multiple of 500 steps, where the state is
        # saved), and at every step of the warm-up.
        if step < args.warmup:
            n = 1
        losses = train.train(model, optimizer, sp_train, n, args.batch, rng, log_every=0,
                             extra=pv_train, extra_frac=args.proven_frac)
        step += n
        if step % 500 == 0 or step == args.steps:
            tmp = state_path.with_suffix(".tmp")
            torch.save({"model": model.state_dict(), "optimizer": optimizer.state_dict(), "step": step,
                        "before": before}, tmp)
            os.replace(tmp, state_path)
            print(f"step {step}/{args.steps} lr {optimizer.param_groups[0]['lr']:.2e} value {losses[0]:.4f} "
                  f"policy {losses[1]:.4f} ({time.time() - started:.0f}s)", flush=True)

    after = describe(model, sets)
    tmp = out / "ft.tmp"
    torch.save({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                "optimizer": optimizer.state_dict(), "generation": ckpt.get("generation", 0)}, tmp)
    os.replace(tmp, out / "ft.pt")
    model.export_weights(out / "ft.bin")
    head = "| Positions | perte valeur | perte politique | précision valeur |\n|---|---|---|---|"
    (out / "report.md").write_text(
        f"# Affinage sur positions prouvées\n\nDépart : `{args.checkpoint}`, {args.steps} pas de {args.batch}, "
        f"{args.proven_frac:.0%} de positions prouvées par lot, lr {args.lr:g} → {args.lr_final:g}.\n\n"
        f"## Avant\n\n{head}\n" + "\n".join(before) + f"\n\n## Après\n\n{head}\n" + "\n".join(after) + "\n",
        encoding="utf-8")
    print(f"done: {out / 'report.md'}", flush=True)


if __name__ == "__main__":
    main()
