"""Training steps on self-play positions (v2 or v3 networks, records always as data.RECORD3).

Value target, for the player to move:
- the exact value when known (proven by the search, a winning graph or the exact solver);
- else the game outcome, mixed with the search value (weight VALUE_MIX_EARLY before ply 16,
  VALUE_MIX_LATE after) when that value is the chosen move's Q (FLAG_CHOSEN_Q: the older files
  store a biased mix of every visited move, never used);
Q head (v3 networks with one): the move values recorded (search Q, or exact codes).
"""
import time

import numpy as np
import torch
import torch.nn.functional as F

import data
import qubic
from model import MOVES_LEFT_SCALE, QubicNet, forward_all

MOVES_LEFT_WEIGHT = 0.5
Q_WEIGHT = 0.5
VALUE_WEIGHT = 1.0
VALUE_MIX_EARLY = 0.5
VALUE_MIX_LATE = 0.25


def new_model(hidden, layers, seed=0):
    torch.manual_seed(seed)
    return QubicNet(hidden, layers)


def make_optimizer(model, lr, weight_decay):
    return torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)


def value_targets(arr):
    """Target distribution over (win, draw, loss) for the player to move [N,3]."""
    n = len(arr)
    t = np.zeros((n, 3), dtype=np.float32)
    t[np.arange(n), arr["outcome"].astype(np.int64)] = 1.0
    mixed = (arr["flags"] & data.FLAG_CHOSEN_Q) > 0
    if mixed.any():
        v = np.clip(arr["value"][mixed].astype(np.float32), -1, 1)
        s = np.stack([np.maximum(v, 0), 1 - np.abs(v), np.maximum(-v, 0)], axis=1)
        lam = np.where(arr["ply"][mixed] < 16, VALUE_MIX_EARLY, VALUE_MIX_LATE).astype(np.float32)[:, None]
        t[mixed] = (1 - lam) * t[mixed] + lam * s
    exact = arr["exact"] > 0
    if exact.any():
        t[exact] = 0.0
        t[np.nonzero(exact)[0], arr["exact"][exact].astype(np.int64) - 1] = 1.0
    return t


def q_targets(arr):
    """Per-move value targets [N,64] in [-1, 1] and their mask."""
    q = arr["q"].astype(np.int16)
    mask = q != data.Q_UNKNOWN
    t = np.clip(q, -100, 100).astype(np.float32) / 100.0
    t[q == data.Q_WIN] = 1.0
    t[q == data.Q_DRAW] = 0.0
    t[q == data.Q_LOSS] = -1.0
    return t, mask


def batch_tensors(arr):
    cs, ls, first = qubic.encode(arr["mine"], arr["theirs"])
    occupied = qubic.bits_to_array(arr["mine"] | arr["theirs"])
    policy = arr["policy"].astype(np.float32) / 65535.0
    policy_sum = policy.sum(axis=1, keepdims=True)
    policy = policy / np.maximum(policy_sum, 1e-8)
    has_policy = ((arr["flags"] & data.FLAG_POLICY) > 0) & (policy_sum[:, 0] > 0.5)
    qt, qm = q_targets(arr)
    return (torch.from_numpy(cs), torch.from_numpy(ls), torch.from_numpy(first),
            torch.from_numpy(occupied), torch.from_numpy(policy),
            torch.from_numpy(has_policy.astype(np.float32)),
            torch.from_numpy(value_targets(arr)),
            torch.from_numpy(arr["moves_left"].astype(np.float32) / MOVES_LEFT_SCALE),
            torch.from_numpy(qt), torch.from_numpy(qm.astype(np.float32)),
            torch.from_numpy((arr["exact"] > 0).astype(np.float32)))


def losses(model, batch, outputs=None):
    """Value, policy, moves-left and Q losses (each 0 where no target was recorded)."""
    cs, ls, first, occupied, policy_target, has_policy, value_target, moves_left, qt, qm, _ = batch
    policy_logits, wdl_logits, ml_pred, q_pred = outputs or forward_all(model, cs, ls, first)
    value_loss = -(value_target * torch.log_softmax(wdl_logits, dim=-1)).sum(dim=-1).mean()
    masked = policy_logits.masked_fill(occupied, -1e9)
    log_p = torch.log_softmax(masked, dim=-1)
    per_sample = -(policy_target * log_p.masked_fill(occupied, 0.0)).sum(dim=-1)
    n_policy = has_policy.sum().clamp(min=1.0)
    policy_loss = (per_sample * has_policy).sum() / n_policy
    has_ml = (moves_left > 0).float()
    ml_loss = (F.huber_loss(ml_pred, moves_left, reduction="none", delta=0.25) * has_ml).sum() / has_ml.sum().clamp(min=1.0)
    if q_pred is not None:
        q_loss = (F.huber_loss(q_pred, qt, reduction="none", delta=0.5) * qm).sum() / qm.sum().clamp(min=1.0)
    else:
        q_loss = torch.zeros(())
    return value_loss, policy_loss, ml_loss, q_loss


def train(model, optimizer, window, steps, batch_size, rng, deadline=None, log_every=100, extra=None, extra_frac=0.0):
    """Runs `steps` optimizer steps on uniform samples of `window` (and, with `extra`, a fraction
    `extra_frac` of each batch drawn from `extra`). Returns mean losses (value, policy,
    moves-left, Q), or None if the deadline (epoch seconds) passed first."""
    n_extra = int(round(batch_size * extra_frac)) if extra is not None and len(extra) else 0
    model.train()
    totals = np.zeros(4)
    count = 0
    started = time.time()
    for step in range(steps):
        if deadline is not None and time.time() > deadline:
            return None
        idx = rng.integers(0, len(window), size=batch_size - n_extra)
        sample = window[idx]
        if n_extra:
            sample = np.concatenate([sample, extra[rng.integers(0, len(extra), size=n_extra)]])
        batch = batch_tensors(sample)
        value_loss, policy_loss, ml_loss, q_loss = losses(model, batch)
        loss = VALUE_WEIGHT * value_loss + policy_loss + MOVES_LEFT_WEIGHT * ml_loss + Q_WEIGHT * q_loss
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        totals += (value_loss.item(), policy_loss.item(), ml_loss.item(), q_loss.item())
        count += 1
        if log_every and (step + 1) % log_every == 0:
            v, p, m, q = totals / count
            print(f"    step {step + 1}/{steps} value {v:.4f} policy {p:.4f} moves-left {m:.4f} q {q:.4f} "
                  f"({time.time() - started:.0f}s)", flush=True)
    model.eval()
    return totals / max(count, 1)


@torch.no_grad()
def evaluate_full(model, arr, batch_size=4096):
    """Mean losses and accuracies on arr: value, policy, moves_left, q, value_acc (most likely
    outcome = target's), exact_acc and exact_loss (on positions with an exact value), n_exact."""
    model.eval()
    sums = {k: 0.0 for k in ("value", "policy", "moves_left", "q", "value_acc", "exact_acc", "exact_loss")}
    n = 0
    n_exact = 0
    for start in range(0, len(arr), batch_size):
        chunk = arr[start:start + batch_size]
        batch = batch_tensors(chunk)
        out = forward_all(model, batch[0], batch[1], batch[2])
        v, p, m, q = losses(model, batch, out)
        wdl = out[1]
        target = batch[6]
        acc = (wdl.argmax(dim=-1) == target.argmax(dim=-1)).float()
        exact = batch[10]
        logp = torch.log_softmax(wdl, dim=-1)
        k = len(chunk)
        sums["value"] += v.item() * k
        sums["policy"] += p.item() * k
        sums["moves_left"] += m.item() * k
        sums["q"] += q.item() * k
        sums["value_acc"] += acc.sum().item()
        sums["exact_acc"] += (acc * exact).sum().item()
        sums["exact_loss"] += (-(target * logp).sum(dim=-1) * exact).sum().item()
        n += k
        n_exact += exact.sum().item()
    out = {k: sums[k] / max(n, 1) for k in ("value", "policy", "moves_left", "q", "value_acc")}
    out["exact_acc"] = sums["exact_acc"] / max(n_exact, 1)
    out["exact_loss"] = sums["exact_loss"] / max(n_exact, 1)
    out["n_exact"] = int(n_exact)
    return out


def evaluate(model, arr, batch_size=4096):
    """Mean value, policy and moves-left losses and value accuracy on arr (no gradient)."""
    r = evaluate_full(model, arr, batch_size)
    return np.array([r["value"], r["policy"], r["moves_left"], r["value_acc"]])
