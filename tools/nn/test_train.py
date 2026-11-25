"""Hard-example reservoir (plan § 5.6) and batches drawn from several sources."""
import numpy as np
import torch

import data
import train
from export import random_positions
from model import build_model


def records(n, seed, exact=1):
    arr = np.zeros(n, dtype=data.RECORD3)
    arr["mine"], arr["theirs"] = random_positions(n, seed)
    arr["ply"] = [bin(int(m)).count("1") + bin(int(t)).count("1") for m, t in zip(arr["mine"], arr["theirs"])]
    arr["exact"] = exact
    arr["outcome"] = np.maximum(arr["exact"].astype(np.int64) - 1, 0)
    arr["q"] = data.Q_UNKNOWN
    return arr


def test_hard_examples_are_the_worst_exact_positions():
    torch.manual_seed(0)
    model = build_model({"version": 3, "hidden": 16, "layers": 2, "q_head": False, "global_every": 0})
    candidates = np.concatenate([records(200, 1), records(100, 2, exact=0)])
    loss = train.exact_value_losses(model, candidates)
    assert np.isnan(loss[200:]).all() and not np.isnan(loss[:200]).any()
    hard = train.update_hard(model, None, candidates, max_size=50, min_loss=0.0)
    assert len(hard) == 50 and (hard["exact"] > 0).all()
    worst = np.sort(loss[:200])[::-1][:50]
    assert np.allclose(np.sort(train.exact_value_losses(model, hard))[::-1], worst, atol=1e-5)
    # Fed again (a generation trained twice), nothing is duplicated.
    again = train.update_hard(model, hard, candidates, max_size=500, min_loss=0.0)
    distinct = len({(int(m), int(t)) for m, t in zip(candidates["mine"][:200], candidates["theirs"][:200])})
    assert len(again) == distinct
    # Above every loss: the reservoir empties.
    assert len(train.update_hard(model, hard, candidates, max_size=50, min_loss=1e9)) == 0


def test_batches_draw_from_every_source():
    torch.manual_seed(0)
    model = build_model({"version": 3, "hidden": 16, "layers": 2, "q_head": False, "global_every": 0})
    opt = train.make_optimizer(model, 1e-3, 0.0)
    window, extra, hard = records(64, 3), records(16, 4), records(16, 5)
    seen = []
    original = train.batch_tensors

    def spy(sample):
        seen.append(sample)
        return original(sample)

    train.batch_tensors = spy
    try:
        out = train.train(model, opt, window, 2, 32, np.random.default_rng(0), log_every=0,
                          extra=extra, extra_frac=0.25, extras=[(hard, 0.25), (None, 0.5)])
    finally:
        train.batch_tensors = original
    assert out is not None and len(seen) == 2
    for sample in seen:
        assert len(sample) == 32
        assert np.isin(sample[16:24]["mine"], extra["mine"]).all()
        assert np.isin(sample[24:]["mine"], hard["mine"]).all()
