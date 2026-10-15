"""Supervised pre-training of a fresh (bigger) network on the self-play games already played,
before self-play training (loop.py --init-checkpoint <run>/pretrain/pretrain.pt).

    .venv/Scripts/python -u pretrain.py                      # runs/big, H64 x 10 layers
    .venv/Scripts/python -u pretrain.py --extra-data runs/main/data --extra-last 50

Data: night512 (512 simulations on disputed openings, files gen_0000+ only: its gen_-05xx files
are copies of weekend files) + weekend; its last --test-files night512 files are held out.
AdamW, linear warm-up then cosine from --lr-peak to --lr-final over --steps; same losses as
train.py.

Resumable and stoppable at any moment: <run>/pretrain/state.pt (model, optimizer, step, RNG states,
running losses) is replaced atomically every --save-every steps; the schedule depends only on the
step and the batches only on the restored RNG, so a resumed run is the same as an uninterrupted
one. The configuration (including the file lists) is frozen in <run>/pretrain/config.json at the
first launch. Every --eval-every steps: held-out losses -> curve.csv, log.txt, report.md. At the
end: pretrain.bin (QNET) and pretrain.pt ({hidden, layers, model, optimizer}); re-running is then
a no-op.
"""
import argparse
import csv
import datetime as dt
import io
import json
import math
import os
import re
import sys
import time
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import load_checkpoint

HERE = Path(__file__).resolve().parent

CURVE_FIELDS = ["step", "epochs", "lr", "train_value", "train_policy", "train_moves_left", "test_value",
                "test_policy", "test_moves_left", "test_value_acc", "compute_s", "time"]


def log(run, msg):
    line = f"[{dt.datetime.now():%Y-%m-%d %H:%M:%S}] pretrain: {msg}"
    print(line, flush=True)
    with open(run / "log.txt", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def write_atomic(path, text):
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    os.replace(tmp, path)


def save_atomic(obj, path):
    tmp = path.with_name(path.name + ".tmp")
    torch.save(obj, tmp)
    os.replace(tmp, path)


def own_files(directory):
    """gen_NNNN.bin files of a run (not the gen_-NNNN.bin copies of another run's data)."""
    return [f for f in data.files_in(directory) if re.fullmatch(r"gen_\d+\.bin", f.name)]


def lr_at(cfg, step):
    peak, final, warmup, total = cfg["lr_peak"], cfg["lr_final"], cfg["warmup"], cfg["steps"]
    if step < warmup:
        return peak * (step + 1) / warmup
    t = min(1.0, (step - warmup) / max(1, total - warmup))
    return final + (peak - final) * 0.5 * (1.0 + math.cos(math.pi * t))


def make_config(args):
    night = own_files(args.night_data)
    if len(night) <= args.test_files:
        sys.exit(f"not enough files in {args.night_data}")
    test = night[-args.test_files:]
    training = night[:-args.test_files] + own_files(args.weekend_data)
    if args.extra_data:
        extra = own_files(args.extra_data)
        training += extra[-args.extra_last:] if args.extra_last > 0 else extra
    if args.max_files > 0:  # smoke tests
        training = training[-args.max_files:]
    cfg = {k: getattr(args, k) for k in ("hidden", "layers", "steps", "batch", "lr_peak", "lr_final", "warmup",
                                         "weight_decay", "eval_every", "save_every", "seed")}
    cfg["train_files"] = [str(f) for f in training]
    cfg["test_files"] = [str(f) for f in test]
    cfg["extra"] = (f"{args.extra_data} ({'last ' + str(args.extra_last) if args.extra_last > 0 else 'all'} files)"
                    if args.extra_data else "")
    cfg["reference_checkpoint"] = args.reference_checkpoint
    cfg["created"] = f"{dt.datetime.now():%Y-%m-%d %H:%M}"
    return cfg


def load_files(files):
    return np.concatenate([data.load(f) for f in files])


def write_curve(path, rows):
    out = io.StringIO()
    w = csv.DictWriter(out, fieldnames=CURVE_FIELDS, extrasaction="ignore", lineterminator="\n")
    w.writeheader()
    w.writerows(rows)
    write_atomic(path, out.getvalue())


def read_curve(path):
    if not path.exists():
        return []
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_report(out, cfg, st, curve, n_train, n_test, per_step):
    """Short French report (rewritten at each evaluation and at the end)."""
    step, total = st["step"], cfg["steps"]
    lines = [f"# Pré-entraînement H{cfg['hidden']} × {cfg['layers']} couches", "",
             f"Mis à jour : {dt.datetime.now():%Y-%m-%d %H:%M}. "
             + ("**Terminé.**" if st.get("done") else f"Pas {step} / {total} ({100 * step / total:.0f} %)."), "",
             f"- Réseau neuf : {st.get('params', '?')} paramètres ; AdamW, lots de {cfg['batch']}, "
             f"lr {cfg['lr_peak']:g} (montée sur {cfg['warmup']} pas) puis cosinus jusqu'à {cfg['lr_final']:g}, "
             f"weight decay {cfg['weight_decay']:g}.",
             f"- Entraînement : {n_train} positions ({len(cfg['train_files'])} fichiers : night512 sauf les "
             f"{len(cfg['test_files'])} derniers, week-end{', ' + cfg['extra'] if cfg.get('extra') else ''}) ; "
             f"{total * cfg['batch'] / max(1, n_train):.1f} époques au total.",
             f"- Test : {n_test} positions ({', '.join(Path(f).name for f in cfg['test_files'])} de night512, jamais vues).",
             f"- Temps de calcul : {st['compute_s'] / 3600:.1f} h"
             + (f", {per_step:.2f} s/pas, fin estimée dans {(total - step) * per_step / 3600:.1f} h" if per_step and not st.get("done") else "")
             + ".", ""]
    ref = st.get("reference")
    if ref:
        lines += [f"Référence : le 48 × 6 night512 gen 46 sur le même test : valeur {ref[0]:.4f}, politique {ref[1]:.4f}, "
                  f"précision V/N/D {100 * ref[3]:.1f} % (avantagé : ces deux fichiers étaient dans sa fenêtre "
                  "d'entraînement).", ""]
    lines += ["| Pas | Époques | lr | Valeur (entr.) | Valeur (test) | Politique (entr.) | Politique (test) | "
              "Plis restants (test) | Précision V/N/D (test) |", "|---|---|---|---|---|---|---|---|---|"]
    for r in curve:
        lines.append(f"| {r['step']} | {r['epochs']} | {r['lr']} | {r['train_value']} | {r['test_value']} | "
                     f"{r['train_policy']} | {r['test_policy']} | {r['test_moves_left']} | "
                     f"{100 * float(r['test_value_acc']):.1f} % |")
    lines += ["", "Pertes plus basses = mieux ; un écart entr./test qui se creuse = sur-apprentissage."]
    if st.get("done"):
        lines += ["", f"Modèle : `{out / 'pretrain.bin'}` ; point de départ de l'auto-jeu : `{out / 'pretrain.pt'}`."]
    write_atomic(out / "report.md", "\n".join(lines) + "\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run", default="runs/big")
    ap.add_argument("--hidden", type=int, default=64)
    ap.add_argument("--layers", type=int, default=10)
    ap.add_argument("--steps", type=int, default=24000)
    ap.add_argument("--batch", type=int, default=1024)
    ap.add_argument("--lr-peak", type=float, default=1e-3)
    ap.add_argument("--lr-final", type=float, default=2e-5)
    ap.add_argument("--warmup", type=int, default=500)
    ap.add_argument("--weight-decay", type=float, default=1e-4)
    ap.add_argument("--night-data", default="runs/night512/data")
    ap.add_argument("--weekend-data", default="runs/weekend/data")
    ap.add_argument("--extra-data", help="more training files, e.g. runs/main/data (older, weaker play)")
    ap.add_argument("--extra-last", type=int, default=0, help="only the last N files of --extra-data (0: all)")
    ap.add_argument("--test-files", type=int, default=2, help="last night512 files held out")
    ap.add_argument("--max-files", type=int, default=0, help="only the last N training files (smoke tests)")
    ap.add_argument("--eval-every", type=int, default=1000)
    ap.add_argument("--save-every", type=int, default=250)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--reference-checkpoint", default="runs/night512/checkpoints/gen_0046.pt",
                    help="evaluated once on the test set for comparison ('' to skip)")
    ap.add_argument("--stop-at", help="local time (ISO) at which to stop cleanly (resume later)")
    ap.add_argument("--threads", type=int, default=os.cpu_count() or 4)
    args = ap.parse_args()

    run = Path(args.run)
    out = run / "pretrain"
    out.mkdir(parents=True, exist_ok=True)
    cfg_path = out / "config.json"
    if cfg_path.exists():
        cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    else:
        cfg = make_config(args)
        write_atomic(cfg_path, json.dumps(cfg, indent=1))
    state_path = out / "state.pt"
    if (out / "done.json").exists():
        print(f"pre-training already done: {out / 'pretrain.pt'}", flush=True)
        return
    deadline = dt.datetime.fromisoformat(args.stop_at).timestamp() if args.stop_at else None
    torch.set_num_threads(args.threads)

    model = train.new_model(cfg["hidden"], cfg["layers"], seed=cfg["seed"])
    optimizer = train.make_optimizer(model, cfg["lr_peak"], cfg["weight_decay"])
    rng = np.random.default_rng(cfg["seed"])
    st = {"step": 0, "compute_s": 0.0, "running": [0.0, 0.0, 0.0], "count": 0,
          "params": sum(p.numel() for p in model.parameters())}
    if state_path.exists():
        saved = torch.load(state_path, map_location="cpu", weights_only=False)
        model.load_state_dict(saved["model"])
        optimizer.load_state_dict(saved["optimizer"])
        rng.bit_generator.state = saved["numpy_rng"]
        torch.set_rng_state(saved["torch_rng"])
        st = saved["progress"]

    t = time.time()
    train_set = load_files(cfg["train_files"])
    test_set = load_files(cfg["test_files"])
    log(run, f"H{cfg['hidden']} x {cfg['layers']} ({st['params']} parameters), step {st['step']}/{cfg['steps']}; "
             f"train {data.summary(train_set)} ({len(cfg['train_files'])} files), test {len(test_set)} positions "
             f"(loaded in {time.time() - t:.0f} s)")
    if "reference" not in st and cfg.get("reference_checkpoint") and Path(cfg["reference_checkpoint"]).exists():
        ref, _, _ = load_checkpoint(cfg["reference_checkpoint"])
        st["reference"] = [float(x) for x in train.evaluate(ref, test_set)]
        log(run, f"reference {cfg['reference_checkpoint']} on the test set: value {st['reference'][0]:.4f} "
                 f"policy {st['reference'][1]:.4f} accuracy {st['reference'][3]:.4f}")

    def save():
        save_atomic({"model": model.state_dict(), "optimizer": optimizer.state_dict(),
                     "numpy_rng": rng.bit_generator.state, "torch_rng": torch.get_rng_state(),
                     "progress": st}, state_path)
        write_atomic(out / "progress.json", json.dumps(
            {k: st[k] for k in ("step", "compute_s", "done", "per_step") if k in st} | {"steps": cfg["steps"]}))

    curve = read_curve(out / "curve.csv")
    session_start, session_step = time.time(), st["step"]
    last_tick = time.time()
    model.train()
    while st["step"] < cfg["steps"]:
        if deadline is not None and time.time() > deadline:
            save()
            log(run, f"stop time reached at step {st['step']}; resume with the same command")
            return
        lr = lr_at(cfg, st["step"])
        for group in optimizer.param_groups:
            group["lr"] = lr
        batch = train.batch_tensors(train_set[rng.integers(0, len(train_set), cfg["batch"])])
        v, p, m = train.losses(model, batch)
        loss = v + p + train.MOVES_LEFT_WEIGHT * m
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        st["running"] = [a + b for a, b in zip(st["running"], (v.item(), p.item(), m.item()))]
        st["count"] += 1
        st["step"] += 1
        now = time.time()
        st["compute_s"] += now - last_tick
        last_tick = now
        step = st["step"]
        if step > session_step:
            st["per_step"] = (now - session_start) / (step - session_step)
        if step % 50 == 0:
            r = [x / max(1, st["count"]) for x in st["running"]]
            print(f"    step {step}/{cfg['steps']} lr {lr:.2e} value {r[0]:.4f} policy {r[1]:.4f} "
                  f"moves-left {r[2]:.4f} ({st['per_step']:.2f} s/step)", flush=True)
        if step % cfg["eval_every"] == 0 or step == cfg["steps"]:
            hv, hp, hm, ha = train.evaluate(model, test_set)
            model.train()
            r = [x / max(1, st["count"]) for x in st["running"]]
            st["running"], st["count"] = [0.0, 0.0, 0.0], 0
            row = {"step": step, "epochs": round(step * cfg["batch"] / len(train_set), 2), "lr": f"{lr:.2e}",
                   "train_value": round(r[0], 4), "train_policy": round(r[1], 4), "train_moves_left": round(r[2], 5),
                   "test_value": round(hv, 4), "test_policy": round(hp, 4), "test_moves_left": round(hm, 5),
                   "test_value_acc": round(ha, 4), "compute_s": round(st["compute_s"]),
                   "time": f"{dt.datetime.now():%Y-%m-%d %H:%M:%S}"}
            curve = [c for c in curve if int(c["step"]) < step] + [{k: str(x) for k, x in row.items()}]
            write_curve(out / "curve.csv", curve)
            log(run, f"step {step}: train value {r[0]:.4f} policy {r[1]:.4f} | test value {hv:.4f} "
                     f"policy {hp:.4f} moves-left {hm:.5f} accuracy {ha:.4f} ({st['per_step']:.2f} s/step)")
            write_report(out, cfg, st, curve, len(train_set), len(test_set), st.get("per_step"))
        if step % cfg["save_every"] == 0:
            save()

    model.eval()
    model.export_weights(out / "pretrain.bin")
    save_atomic({"hidden": model.hidden, "layers": model.layers, "model": model.state_dict(),
                 "optimizer": optimizer.state_dict()}, out / "pretrain.pt")
    st["done"] = True
    save()
    write_report(out, cfg, st, curve, len(train_set), len(test_set), st.get("per_step"))
    write_atomic(out / "done.json", json.dumps({"steps": cfg["steps"], "compute_s": round(st["compute_s"]),
                                                "finished": f"{dt.datetime.now():%Y-%m-%d %H:%M}"}))
    log(run, f"done: {out / 'pretrain.bin'}, {out / 'pretrain.pt'}")


if __name__ == "__main__":
    main()
