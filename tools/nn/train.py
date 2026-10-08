"""Training steps on self-play positions."""
import time

import numpy as np
import torch
import torch.nn.functional as F

import data
import qubic
from model import QubicNet


def new_model(hidden, layers, seed=0):
    torch.manual_seed(seed)
    return QubicNet(hidden, layers)


def make_optimizer(model, lr, weight_decay):
    return torch.optim.AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)


def batch_tensors(arr):
    cs, ls, first = qubic.encode(arr["mine"], arr["theirs"])
    occupied = qubic.bits_to_array(arr["mine"] | arr["theirs"])
    policy = arr["policy"].astype(np.float32) / 65535.0
    policy_sum = policy.sum(axis=1, keepdims=True)
    policy = policy / np.maximum(policy_sum, 1e-8)
    has_policy = ((arr["flags"] & data.FLAG_POLICY) > 0) & (policy_sum[:, 0] > 0.5)
    return (torch.from_numpy(cs), torch.from_numpy(ls), torch.from_numpy(first),
            torch.from_numpy(occupied), torch.from_numpy(policy),
            torch.from_numpy(has_policy.astype(np.float32)),
            torch.from_numpy(arr["outcome"].astype(np.int64)))


def losses(model, batch):
    cs, ls, first, occupied, policy_target, has_policy, outcome = batch
    policy_logits, wdl_logits = model(cs, ls, first)
    value_loss = F.cross_entropy(wdl_logits, outcome)
    masked = policy_logits.masked_fill(occupied, -1e9)
    log_p = torch.log_softmax(masked, dim=-1)
    per_sample = -(policy_target * log_p.masked_fill(occupied, 0.0)).sum(dim=-1)
    n_policy = has_policy.sum().clamp(min=1.0)
    policy_loss = (per_sample * has_policy).sum() / n_policy
    return value_loss, policy_loss


def train(model, optimizer, window, steps, batch_size, rng, deadline=None, log_every=100):
    """Runs `steps` optimizer steps on uniform samples of `window`. Returns mean losses, or None if
    the deadline (epoch seconds) passed first."""
    model.train()
    totals = np.zeros(2)
    count = 0
    started = time.time()
    for step in range(steps):
        if deadline is not None and time.time() > deadline:
            return None
        idx = rng.integers(0, len(window), size=batch_size)
        batch = batch_tensors(window[idx])
        value_loss, policy_loss = losses(model, batch)
        loss = value_loss + policy_loss
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 5.0)
        optimizer.step()
        totals += (value_loss.item(), policy_loss.item())
        count += 1
        if log_every and (step + 1) % log_every == 0:
            v, p = totals / count
            print(f"    step {step + 1}/{steps} value {v:.4f} policy {p:.4f} ({time.time() - started:.0f}s)",
                  flush=True)
    model.eval()
    return totals / max(count, 1)


@torch.no_grad()
def evaluate(model, arr, batch_size=4096):
    """Mean losses and value accuracy on arr (no gradient)."""
    model.eval()
    tot = np.zeros(3)
    n = 0
    for start in range(0, len(arr), batch_size):
        chunk = arr[start:start + batch_size]
        batch = batch_tensors(chunk)
        v, p = losses(model, batch)
        _, wdl = model(batch[0], batch[1], batch[2])
        acc = (wdl.argmax(dim=-1) == batch[6]).float().mean().item()
        tot += np.array([v.item(), p.item(), acc]) * len(chunk)
        n += len(chunk)
    return tot / max(n, 1)
