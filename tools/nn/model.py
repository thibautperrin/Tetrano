"""QubicNet: message passing on the cell <-> line hypergraph.

Reference implementation of the forward pass mirrored by the app's
engine/src/main/kotlin/.../engine/nn/QubicNet.kt. Any change here must be
reflected there (and in the export order of `export_weights`).
"""
import os
import struct
from pathlib import Path

import numpy as np
import torch
from torch import nn

import qubic

MAGIC = 0x54454E51  # "QNET" little endian
VERSION = 2
MOVES_LEFT_SCALE = 64.0  # the moves-left head predicts plies left / 64


class Block(nn.Module):
    """x += W2 relu(W1 LN(input) + b1) + b2."""

    def __init__(self, in_size, hidden):
        super().__init__()
        self.ln = nn.LayerNorm(in_size)
        self.fc1 = nn.Linear(in_size, hidden)
        self.fc2 = nn.Linear(hidden, hidden)
        # Small residual branches at init: the network starts close to its embeddings.
        with torch.no_grad():
            self.fc2.weight.mul_(0.2)

    def forward(self, x):
        return self.fc2(torch.relu(self.fc1(self.ln(x))))


class QubicNet(nn.Module):
    def __init__(self, hidden=48, layers=6):
        super().__init__()
        h = hidden
        self.hidden = h
        self.layers = layers
        self.cell_embed = nn.Parameter(torch.randn(3, h) * 0.5)
        self.degree_embed = nn.Parameter(torch.randn(2, h) * 0.5)
        self.first_embed = nn.Parameter(torch.randn(h) * 0.5)
        self.line_embed = nn.Parameter(torch.randn(8, h) * 0.5)
        self.line_blocks = nn.ModuleList([Block(2 * h, h) for _ in range(layers)])
        self.cell_blocks = nn.ModuleList([Block(2 * h, h) for _ in range(layers)])
        self.pol_ln = nn.LayerNorm(h)
        self.pol_fc1 = nn.Linear(h, h)
        self.pol_fc2 = nn.Linear(h, 1)
        v = 3 * h + 1
        self.val_ln = nn.LayerNorm(v)
        self.val_fc1 = nn.Linear(v, h)
        self.val_fc2 = nn.Linear(h, 3)
        self.ml_ln = nn.LayerNorm(v)
        self.ml_fc1 = nn.Linear(v, h)
        self.ml_fc2 = nn.Linear(h, 1)
        self.register_buffer("incidence", torch.from_numpy(qubic.INCIDENCE))
        self.register_buffer("inv_degree", torch.from_numpy(1.0 / qubic.DEGREE).float())
        self.register_buffer("degree_class", torch.from_numpy(qubic.DEGREE_CLASS))

    def forward(self, cell_state, line_state, first):
        """cell_state [B,64] long, line_state [B,76] long, first [B] float.

        Returns policy logits [B,64] (unmasked), WDL logits [B,3] and the moves-left
        prediction [B] (plies left / MOVES_LEFT_SCALE, before clamping at 0).
        """
        c = (self.cell_embed[cell_state] + self.degree_embed[self.degree_class][None]
             + first[:, None, None] * self.first_embed)
        l = self.line_embed[line_state]
        inc = self.incidence  # [76,64]
        for lb, cb in zip(self.line_blocks, self.cell_blocks):
            agg = torch.matmul(inc, c)  # [B,76,H]: sum of the line's 4 cells
            l = l + lb(torch.cat([l, agg], dim=-1))
            aggc = torch.matmul(inc.t(), l) * self.inv_degree[None, :, None]  # mean of lines
            c = c + cb(torch.cat([c, aggc], dim=-1))
        policy = self.pol_fc2(torch.relu(self.pol_fc1(self.pol_ln(c)))).squeeze(-1)
        pooled = torch.cat([c.mean(dim=1), c.max(dim=1).values, l.mean(dim=1), first[:, None]], dim=-1)
        wdl = self.val_fc2(torch.relu(self.val_fc1(self.val_ln(pooled))))
        moves_left = self.ml_fc2(torch.relu(self.ml_fc1(self.ml_ln(pooled)))).squeeze(-1)
        return policy, wdl, moves_left

    # ---- export ----

    def flat_parameters(self):
        """Parameters in the order QubicNet.kt reads them (Linear weights transposed)."""
        out = []

        def lin(fc):
            out.append(fc.weight.detach().t().contiguous())
            out.append(fc.bias.detach())

        out += [self.cell_embed.detach(), self.degree_embed.detach(), self.first_embed.detach(),
                self.line_embed.detach()]
        for lb, cb in zip(self.line_blocks, self.cell_blocks):
            for b in (lb, cb):
                out += [b.ln.weight.detach(), b.ln.bias.detach()]
                lin(b.fc1)
                lin(b.fc2)
        out += [self.pol_ln.weight.detach(), self.pol_ln.bias.detach()]
        lin(self.pol_fc1)
        out += [self.pol_fc2.weight.detach().reshape(-1), self.pol_fc2.bias.detach()]
        out += [self.val_ln.weight.detach(), self.val_ln.bias.detach()]
        lin(self.val_fc1)
        lin(self.val_fc2)
        out += [self.ml_ln.weight.detach(), self.ml_ln.bias.detach()]
        lin(self.ml_fc1)
        out += [self.ml_fc2.weight.detach().reshape(-1), self.ml_fc2.bias.detach()]
        return torch.cat([t.reshape(-1).float() for t in out]).numpy()

    def export_weights(self, path):
        """Writes the QNET file atomically (temporary file renamed at the end): a reader, or a
        resumed run after a hard kill, never sees a truncated model."""
        flat = self.flat_parameters().astype("<f4")
        path = Path(path)
        tmp = path.with_name(path.name + ".tmp")
        with open(tmp, "wb") as f:
            f.write(struct.pack("<iiiii", MAGIC, VERSION, self.hidden, self.layers, flat.size))
            f.write(flat.tobytes())
        os.replace(tmp, path)


VERSION3 = 3
FLAG_Q_HEAD = 1


class QubicNetV3(nn.Module):
    """Format v3: message passing on the free cells and the live lines only.

    A line holding rings of both colours can never be completed: it is dropped. An occupied cell
    acts only through the live lines crossing it, whose state (colour, rings) already says it all:
    it is dropped too. Nothing useful to the game is lost, and the work shrinks as the game goes
    on (about 1.5x less on average over self-play positions). The masks follow the board
    symmetries, so the network stays exactly equivariant.

    - cells (free only): degree class + "first player" embeddings; each cell block sees the cell,
      the sum (/4) and the mean of its live lines (the number of live lines stays visible);
    - lines (live only): 7 states (empty, 1-3 mine, 1-3 theirs); each line block sees the line and
      the sum of its free cells;
    - optional global context every `global_every` layers: mean and max of the free cells,
      projected and added to every free cell;
    - heads: policy (one logit per cell), optional Q (value of each move for the mover, tanh),
      WDL and plies left on [mean and max of free cells, mean of live lines, first, free cells / 64,
      live lines / 76].

    Mirrored by engine/.../nn/QubicNet.kt (version 3); keep `flat_parameters` in its read order.
    """

    def __init__(self, hidden=64, layers=10, q_head=True, global_every=0):
        super().__init__()
        h = hidden
        self.hidden = h
        self.layers = layers
        self.q_head = q_head
        self.global_every = global_every
        self.degree_embed = nn.Parameter(torch.randn(2, h) * 0.5)
        self.first_embed = nn.Parameter(torch.randn(h) * 0.5)
        self.line_embed = nn.Parameter(torch.randn(8, h) * 0.5)
        self.line_blocks = nn.ModuleList([Block(2 * h, h) for _ in range(layers)])
        self.cell_blocks = nn.ModuleList([Block(3 * h, h) for _ in range(layers)])
        self.global_fcs = nn.ModuleList([nn.Linear(2 * h, h) for k in range(layers)
                                         if global_every and (k + 1) % global_every == 0])
        for fc in self.global_fcs:
            with torch.no_grad():
                fc.weight.mul_(0.2)
        self.pol_ln = nn.LayerNorm(h)
        self.pol_fc1 = nn.Linear(h, h)
        self.pol_fc2 = nn.Linear(h, 1)
        if q_head:
            self.q_ln = nn.LayerNorm(h)
            self.q_fc1 = nn.Linear(h, h)
            self.q_fc2 = nn.Linear(h, 1)
        v = 3 * h + 3
        self.val_ln = nn.LayerNorm(v)
        self.val_fc1 = nn.Linear(v, h)
        self.val_fc2 = nn.Linear(h, 3)
        self.ml_ln = nn.LayerNorm(v)
        self.ml_fc1 = nn.Linear(v, h)
        self.ml_fc2 = nn.Linear(h, 1)
        self.register_buffer("incidence", torch.from_numpy(qubic.INCIDENCE))
        self.register_buffer("degree_class", torch.from_numpy(qubic.DEGREE_CLASS))

    def forward_all(self, cell_state, line_state, first):
        """Policy logits [B,64] (unmasked), WDL logits [B,3], plies left / 64 [B], Q [B,64] (or None)."""
        free = (cell_state == 0).float()  # [B,64]
        live = (line_state != 7).float()  # [B,76]
        fm = free[..., None]
        lm = live[..., None]
        inc = self.incidence
        c = (self.degree_embed[self.degree_class][None] + first[:, None, None] * self.first_embed) * fm
        l = self.line_embed[line_state] * lm
        n_live_cell = torch.matmul(live, inc)  # [B,64]: live lines through each cell
        inv_live = (1.0 / n_live_cell.clamp(min=1.0))[..., None]
        n_free = free.sum(dim=1, keepdim=True).clamp(min=1.0)  # [B,1]
        n_live = live.sum(dim=1, keepdim=True)
        g = 0
        for k, (lb, cb) in enumerate(zip(self.line_blocks, self.cell_blocks)):
            agg = torch.matmul(inc, c)  # sum of the line's free cells (occupied ones are 0)
            l = l + lb(torch.cat([l, agg], dim=-1)) * lm
            s = torch.matmul(inc.t(), l)  # sum of the cell's live lines (dead ones are 0)
            c = c + cb(torch.cat([c, s * 0.25, s * inv_live], dim=-1)) * fm
            if self.global_every and (k + 1) % self.global_every == 0:
                mean = c.sum(dim=1) / n_free
                mx = c.masked_fill(fm == 0, -1e4).max(dim=1).values
                c = c + self.global_fcs[g](torch.cat([mean, mx], dim=-1))[:, None, :] * fm
                g += 1
        policy = self.pol_fc2(torch.relu(self.pol_fc1(self.pol_ln(c)))).squeeze(-1)
        q = torch.tanh(self.q_fc2(torch.relu(self.q_fc1(self.q_ln(c)))).squeeze(-1)) if self.q_head else None
        mean_c = c.sum(dim=1) / n_free
        max_c = c.masked_fill(fm == 0, -1e4).max(dim=1).values
        mean_l = l.sum(dim=1) / n_live.clamp(min=1.0)
        pooled = torch.cat([mean_c, max_c, mean_l, first[:, None], n_free / 64.0, n_live / 76.0], dim=-1)
        wdl = self.val_fc2(torch.relu(self.val_fc1(self.val_ln(pooled))))
        moves_left = self.ml_fc2(torch.relu(self.ml_fc1(self.ml_ln(pooled)))).squeeze(-1)
        return policy, wdl, moves_left, q

    def forward(self, cell_state, line_state, first):
        policy, wdl, moves_left, _ = self.forward_all(cell_state, line_state, first)
        return policy, wdl, moves_left

    def flat_parameters(self):
        """Parameters in the order QubicNet.kt (version 3) reads them (Linear weights transposed)."""
        out = []

        def lin(fc):
            out.append(fc.weight.detach().t().contiguous())
            out.append(fc.bias.detach())

        def ln(m):
            out.extend([m.weight.detach(), m.bias.detach()])

        out += [self.degree_embed.detach(), self.first_embed.detach(), self.line_embed.detach()]
        g = 0
        for k, (lb, cb) in enumerate(zip(self.line_blocks, self.cell_blocks)):
            for b in (lb, cb):
                ln(b.ln)
                lin(b.fc1)
                lin(b.fc2)
            if self.global_every and (k + 1) % self.global_every == 0:
                lin(self.global_fcs[g])
                g += 1
        ln(self.pol_ln)
        lin(self.pol_fc1)
        out += [self.pol_fc2.weight.detach().reshape(-1), self.pol_fc2.bias.detach()]
        if self.q_head:
            ln(self.q_ln)
            lin(self.q_fc1)
            out += [self.q_fc2.weight.detach().reshape(-1), self.q_fc2.bias.detach()]
        ln(self.val_ln)
        lin(self.val_fc1)
        lin(self.val_fc2)
        ln(self.ml_ln)
        lin(self.ml_fc1)
        out += [self.ml_fc2.weight.detach().reshape(-1), self.ml_fc2.bias.detach()]
        return torch.cat([t.reshape(-1).float() for t in out]).numpy()

    def export_weights(self, path):
        """QNET version 3: magic, 3, H, L, parameter count, flags (1: Q head), global_every, parameters."""
        flat = self.flat_parameters().astype("<f4")
        path = Path(path)
        tmp = path.with_name(path.name + ".tmp")
        with open(tmp, "wb") as f:
            f.write(struct.pack("<iiiiiii", MAGIC, VERSION3, self.hidden, self.layers, flat.size,
                                FLAG_Q_HEAD if self.q_head else 0, self.global_every))
            f.write(flat.tobytes())
        os.replace(tmp, path)


def model_config(model):
    """What a checkpoint needs to rebuild `model`."""
    if isinstance(model, QubicNetV3):
        return {"version": 3, "hidden": model.hidden, "layers": model.layers, "q_head": model.q_head,
                "global_every": model.global_every}
    return {"version": 2, "hidden": model.hidden, "layers": model.layers}


def build_model(cfg):
    if cfg.get("version", 2) == 3:
        return QubicNetV3(cfg["hidden"], cfg["layers"], cfg.get("q_head", True), cfg.get("global_every", 0))
    return QubicNet(cfg["hidden"], cfg["layers"])


def forward_all(model, cs, ls, first):
    """(policy, wdl, moves_left, q or None) for either network version."""
    if isinstance(model, QubicNetV3):
        return model.forward_all(cs, ls, first)
    policy, wdl, ml = model(cs, ls, first)
    return policy, wdl, ml, None


def evaluate_bits(model, mine, theirs):
    """Numpy convenience: policy logits [N,64], WDL probabilities [N,3], plies left [N]."""
    cs, ls, first = qubic.encode(np.asarray(mine, dtype=np.uint64), np.asarray(theirs, dtype=np.uint64))
    with torch.no_grad():
        pol, wdl, ml = model(torch.from_numpy(cs), torch.from_numpy(ls), torch.from_numpy(first))
    return pol.numpy(), torch.softmax(wdl, dim=-1).numpy(), (torch.clamp(ml, min=0) * MOVES_LEFT_SCALE).numpy()


def load_checkpoint(path, strict=True):
    """A network (v2 or v3) from a training checkpoint; strict=False starts missing heads from scratch."""
    ckpt = torch.load(path, map_location="cpu", weights_only=False)
    model = build_model(ckpt.get("config", {"hidden": ckpt["hidden"], "layers": ckpt["layers"]}))
    missing, unexpected = model.load_state_dict(ckpt["model"], strict=strict)
    model.eval()
    return model, ckpt, missing
