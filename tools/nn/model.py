"""QubicNet: message passing on the cell <-> line hypergraph.

Reference implementation of the forward pass mirrored by the app's
engine/src/main/kotlin/.../engine/nn/QubicNet.kt. Any change here must be
reflected there (and in the export order of `export_weights`).
"""
import struct

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
        flat = self.flat_parameters().astype("<f4")
        with open(path, "wb") as f:
            f.write(struct.pack("<iiiii", MAGIC, VERSION, self.hidden, self.layers, flat.size))
            f.write(flat.tobytes())


def evaluate_bits(model, mine, theirs):
    """Numpy convenience: policy logits [N,64], WDL probabilities [N,3], plies left [N]."""
    cs, ls, first = qubic.encode(np.asarray(mine, dtype=np.uint64), np.asarray(theirs, dtype=np.uint64))
    with torch.no_grad():
        pol, wdl, ml = model(torch.from_numpy(cs), torch.from_numpy(ls), torch.from_numpy(first))
    return pol.numpy(), torch.softmax(wdl, dim=-1).numpy(), (torch.clamp(ml, min=0) * MOVES_LEFT_SCALE).numpy()


def load_checkpoint(path, strict=True):
    """A QubicNet from a training checkpoint; strict=False starts missing heads from scratch."""
    ckpt = torch.load(path, map_location="cpu", weights_only=False)
    model = QubicNet(ckpt["hidden"], ckpt["layers"])
    missing, unexpected = model.load_state_dict(ckpt["model"], strict=strict)
    model.eval()
    return model, ckpt, missing
