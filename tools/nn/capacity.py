"""Inside a trained QubicNet: dead or redundant neurons, how much each layer is used, and hints on
whether a wider or deeper network would help.

    python capacity.py --checkpoint runs/night512/checkpoints/gen_0046.pt \
        --data runs/night512/data --out runs/analysis/capacity_gen46.md

Neurons are the hidden ReLU units of the residual blocks (48 per block, one line block and one
cell block per layer) and of the three heads. Measured on self-play positions:
- activity: share of (position, cell or line) where the unit is > 0 (dead: never; always on:
  it behaves linearly);
- importance: increase of the training loss (value + policy + 0.5 moves-left) when the unit is
  replaced by its mean activation (= removed, its average folded into the bias);
- redundancy: correlations between units, effective dimension (PCA) of each block's hidden layer
  and of the residual streams, numerical rank of the weight matrices;
- depth: loss increase when a whole block is skipped;
- pruning curve: loss when the least important units are removed together.
"""
import argparse
from pathlib import Path

import numpy as np
import torch

import data
import train
from model import load_checkpoint

LOSS_WEIGHTS = (1.0, 1.0, train.MOVES_LEFT_WEIGHT)


class Probe(torch.nn.Module):
    """QubicNet forward with optional unit ablations (mean replacement), skipped blocks and
    statistics of hidden activations."""

    def __init__(self, net):
        super().__init__()
        self.net = net
        self.ablate = {}  # name -> (keep [H] float, mean [H])
        self.skip = set()  # block names
        self.stats = None  # name -> dict of accumulators, when recording

    def units(self, name, h):
        if self.stats is not None:
            x = h.reshape(-1, h.shape[-1]).double()
            s = self.stats.setdefault(name, {"n": 0, "sum": 0, "sq": 0, "active": 0})
            s["n"] += x.shape[0]
            s["sum"] = s["sum"] + x.sum(0)
            s["sq"] = s["sq"] + x.T @ x
            s["active"] = s["active"] + (x > 0).double().sum(0)
        if name in self.ablate:
            keep, mean = self.ablate[name]
            h = h * keep + mean * (1 - keep)
        return h

    def stream(self, name, x):
        if self.stats is not None:
            self.units(name, x)

    def block(self, name, b, x):
        h = self.units(name, torch.relu(b.fc1(b.ln(x))))
        return b.fc2(h)

    def forward(self, cell_state, line_state, first):
        n = self.net
        c = (n.cell_embed[cell_state] + n.degree_embed[n.degree_class][None]
             + first[:, None, None] * n.first_embed)
        l = n.line_embed[line_state]
        inc = n.incidence
        for i, (lb, cb) in enumerate(zip(n.line_blocks, n.cell_blocks)):
            agg = torch.matmul(inc, c)
            out = self.block(f"line{i}", lb, torch.cat([l, agg], dim=-1))
            if f"line{i}" not in self.skip:
                l = l + out
            self.stream(f"stream_l{i}", l)
            aggc = torch.matmul(inc.t(), l) * n.inv_degree[None, :, None]
            out = self.block(f"cell{i}", cb, torch.cat([c, aggc], dim=-1))
            if f"cell{i}" not in self.skip:
                c = c + out
            self.stream(f"stream_c{i}", c)
        hp = self.units("policy", torch.relu(n.pol_fc1(n.pol_ln(c))))
        policy = n.pol_fc2(hp).squeeze(-1)
        pooled = torch.cat([c.mean(dim=1), c.max(dim=1).values, l.mean(dim=1), first[:, None]], dim=-1)
        wdl = n.val_fc2(self.units("value", torch.relu(n.val_fc1(n.val_ln(pooled)))))
        ml = n.ml_fc2(self.units("moves_left", torch.relu(n.ml_fc1(n.ml_ln(pooled))))).squeeze(-1)
        return policy, wdl, ml


@torch.no_grad()
def loss(probe, batches):
    tot = np.zeros(3)
    n = 0
    for b in batches:
        parts = train.losses(probe, b)
        tot += np.array([p.item() for p in parts]) * len(b[0])
        n += len(b[0])
    tot /= n
    return float(np.dot(tot, LOSS_WEIGHTS)), tot


def spectrum(cov):
    ev = np.clip(np.linalg.eigvalsh(cov)[::-1], 0, None)
    total = ev.sum()
    if total <= 0:
        return 0.0, 0, 0
    pr = total ** 2 / (ev ** 2).sum()
    cum = np.cumsum(ev) / total
    return pr, int(np.searchsorted(cum, 0.95) + 1), int(np.searchsorted(cum, 0.99) + 1)


def summarize(s):
    mean = (s["sum"] / s["n"]).numpy()
    cov = (s["sq"] / s["n"]).numpy() - np.outer(mean, mean)
    rate = (s["active"] / s["n"]).numpy()
    std = np.sqrt(np.clip(np.diag(cov), 0, None))
    with np.errstate(invalid="ignore", divide="ignore"):
        corr = cov / np.outer(std, std)
    return mean, cov, rate, std, corr


def weight_rank(w):
    sv = torch.linalg.svdvals(w.detach()).numpy()
    energy = np.cumsum(sv ** 2) / (sv ** 2).sum()
    return len(sv), int(np.searchsorted(energy, 0.99) + 1), float(sv[-1] / sv[0])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--checkpoint", default="runs/night512/checkpoints/gen_0046.pt")
    ap.add_argument("--data", default="runs/night512/data")
    ap.add_argument("--files", type=int, default=7, help="last N data files")
    ap.add_argument("--positions", type=int, default=16384)
    ap.add_argument("--out", default="runs/analysis/capacity_gen46.md")
    args = ap.parse_args()
    torch.manual_seed(0)
    rng = np.random.default_rng(0)
    net, _, _ = load_checkpoint(args.checkpoint)
    probe = Probe(net).eval()
    files = [f for f in data.files_in(args.data) if "-" not in f.stem][-args.files:]
    arr = np.concatenate([data.load(f) for f in files])
    arr = arr[rng.choice(len(arr), size=min(args.positions, len(arr)), replace=False)]
    batches = [train.batch_tensors(arr[k:k + 2048]) for k in range(0, len(arr), 2048)]
    H, L = net.hidden, net.layers
    lines = [f"# Capacité du réseau `{args.checkpoint}`", "",
             f"H = {H}, {L} couches, {sum(p.numel() for p in net.parameters())} paramètres. "
             f"{len(arr)} positions tirées de {files[0].name} … {files[-1].name}.", ""]

    # ---- statistics ----
    probe.stats = {}
    base, parts = loss(probe, batches)
    stats, probe.stats = probe.stats, None
    lines += [f"Perte de référence : {base:.4f} (valeur {parts[0]:.4f}, politique {parts[1]:.4f}, "
              f"plis restants {parts[2]:.5f}).", ""]
    unit_names = [f"{k}{i}" for i in range(L) for k in ("line", "cell")] + ["policy", "value", "moves_left"]
    means = {}
    summary = {}
    for name in unit_names:
        mean, cov, rate, std, corr = summarize(stats[name])
        means[name] = torch.from_numpy(mean).float()
        off = np.abs(np.nan_to_num(corr)) - np.eye(len(mean))
        pr, d95, d99 = spectrum(cov)
        summary[name] = dict(rate=rate, std=std, dead=int((rate < 1e-3).sum()), always=int((rate > 0.999).sum()),
                             pairs=int((np.triu(off, 1) > 0.9).sum()), pr=pr, d95=d95, d99=d99)

    # ---- unit importance (mean ablation) ----
    importance = {}
    for name in unit_names:
        h = len(means[name])
        imp = np.zeros(h)
        for u in range(h):
            keep = torch.ones(h)
            keep[u] = 0
            probe.ablate = {name: (keep, means[name])}
            imp[u] = loss(probe, batches)[0] - base
        importance[name] = imp
        probe.ablate = {}
        print(f"importance {name}: min {imp.min():+.5f} median {np.median(imp):+.5f} max {imp.max():+.5f}", flush=True)

    lines += ["## Neurones des blocs et des têtes", "",
              "Actif = part des (position, case ou ligne) où le neurone est > 0. Importance = hausse de la perte "
              "quand on le retire (remplacé par sa moyenne). « Inutiles » = importance < 0,0005 "
              "(≈ 0,1 % de la perte).", "",
              "| Bloc | Morts | Toujours actifs | Actif médian | Inutiles | Importance médiane | max | "
              "Paires corrélées > 0,9 | Dim. effective (PR) | Dim. 95 % / 99 % |",
              "|---|---|---|---|---|---|---|---|---|---|"]
    for name in unit_names:
        s, imp = summary[name], importance[name]
        lines.append(f"| {name} | {s['dead']} | {s['always']} | {np.median(s['rate']):.0%} | "
                     f"{int((imp < 5e-4).sum())} / {len(imp)} | {np.median(imp):.4f} | {imp.max():.4f} | "
                     f"{s['pairs']} | {s['pr']:.1f} | {s['d95']} / {s['d99']} |")

    # ---- residual streams ----
    lines += ["", "## Flux résiduels (état des cases `c` et des lignes `l` après chaque couche)", "",
              f"Dimension effective sur {H} canaux : PR (participation ratio) et nombre de composantes "
              "pour 95 % / 99 % de la variance.", "", "| Couche | c : PR | c : 95 % / 99 % | l : PR | l : 95 % / 99 % |",
              "|---|---|---|---|---|"]
    for i in range(L):
        sc = spectrum(summarize(stats[f"stream_c{i}"])[1])
        sl = spectrum(summarize(stats[f"stream_l{i}"])[1])
        lines.append(f"| {i} | {sc[0]:.1f} | {sc[1]} / {sc[2]} | {sl[0]:.1f} | {sl[1]} / {sl[2]} |")

    # ---- weight ranks ----
    lines += ["", "## Rang des matrices de poids", "",
              "Valeurs singulières : nombre pour 99 % de l'énergie, et rapport plus petite / plus grande.", "",
              "| Bloc | fc1 (entrée 2H → H) | fc2 (H → H) |", "|---|---|---|"]
    for i in range(L):
        for kind, blocks in (("line", net.line_blocks), ("cell", net.cell_blocks)):
            b = blocks[i]
            r1, r2 = weight_rank(b.fc1.weight), weight_rank(b.fc2.weight)
            lines.append(f"| {kind}{i} | {r1[1]} / {r1[0]} ({r1[2]:.3f}) | {r2[1]} / {r2[0]} ({r2[2]:.3f}) |")

    # ---- depth: skip whole blocks ----
    lines += ["", "## Profondeur : bloc sauté", "", "| Bloc | Hausse de la perte | valeur | politique |", "|---|---|---|---|"]
    for i in range(L):
        for kind in ("line", "cell"):
            probe.skip = {f"{kind}{i}"}
            total, p = loss(probe, batches)
            lines.append(f"| {kind}{i} | {total - base:+.4f} | {p[0] - parts[0]:+.4f} | {p[1] - parts[1]:+.4f} |")
    probe.skip = set()
    for i in range(L):
        probe.skip = {f"line{i}", f"cell{i}"}
        total, _ = loss(probe, batches)
        lines.append(f"| couche {i} entière | {total - base:+.4f} | | |")
    probe.skip = set()

    # ---- pruning curve (hidden units of the residual blocks only) ----
    block_names = unit_names[:2 * L]
    order = sorted(((importance[n][u], n, u) for n in block_names for u in range(H)))
    lines += ["", "## Élagage : retrait simultané des neurones les moins importants des blocs", "",
              f"| Neurones retirés (sur {len(order)}) | Perte | Hausse |", "|---|---|---|"]
    for frac in (0.05, 0.1, 0.2, 0.3, 0.5):
        k = int(frac * len(order))
        keeps = {n: torch.ones(H) for n in block_names}
        for _, n, u in order[:k]:
            keeps[n][u] = 0
        probe.ablate = {n: (keeps[n], means[n]) for n in block_names}
        total, _ = loss(probe, batches)
        lines.append(f"| {k} ({frac:.0%}) | {total:.4f} | {total - base:+.4f} ({100 * (total - base) / base:+.1f} %) |")
    probe.ablate = {}

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    np.savez(out.with_suffix(".npz"), **{f"importance_{k}": v for k, v in importance.items()},
             **{f"rate_{k}": summary[k]["rate"] for k in unit_names})
    print(f"report: {out}")


if __name__ == "__main__":
    main()
