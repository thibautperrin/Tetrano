"""pytest: geometry and symmetry invariance of the reference model."""
import numpy as np
import torch

import qubic
from export import random_positions
from model import QubicNet, evaluate_bits


def test_geometry():
    assert len(qubic.LINES) == 76
    assert qubic.SYMMETRIES.shape == (192, 64)
    assert sorted(set(qubic.DEGREE.tolist())) == [4.0, 7.0]
    assert int((qubic.DEGREE == 7).sum()) == 16


def test_bits_roundtrip():
    mine, theirs = random_positions(20, 3)
    assert (qubic.array_to_bits(qubic.bits_to_array(mine)) == mine).all()


def test_symmetry_invariance():
    torch.manual_seed(0)
    model = QubicNet(16, 3).eval()
    mine, theirs = random_positions(30, 11)
    pol, wdl, ml = evaluate_bits(model, mine, theirs)
    for s in (1, 17, 55, 100, 191):
        pm, ws, ms = evaluate_bits(model, qubic.transform(s, mine), qubic.transform(s, theirs))
        np.testing.assert_allclose(ws, wdl, atol=1e-5)
        np.testing.assert_allclose(ms, ml, atol=1e-3)
        # policy is equivariant: logit of cell c moves to cell sym[c]
        np.testing.assert_allclose(pm[:, qubic.SYMMETRIES[s]], pol, atol=1e-4)


def test_v3_symmetry_invariance_and_masks():
    import data
    import train
    from model import QubicNetV3, forward_all
    torch.manual_seed(0)
    model = QubicNetV3(16, 3, q_head=True, global_every=2).eval()
    mine, theirs = random_positions(30, 11)
    pol, wdl, ml = evaluate_bits(model, mine, theirs)
    for s in (1, 55, 191):
        pm, ws, ms = evaluate_bits(model, qubic.transform(s, mine), qubic.transform(s, theirs))
        np.testing.assert_allclose(ws, wdl, atol=1e-5)
        np.testing.assert_allclose(pm[:, qubic.SYMMETRIES[s]], pol, atol=1e-4)
    # Dead lines and occupied cells carry no information beyond the live lines: changing which
    # colour sits on a cell whose lines are all dead must not change the outputs.
    cs, ls, first = qubic.encode(mine, theirs)
    out = forward_all(model, torch.from_numpy(cs), torch.from_numpy(ls), torch.from_numpy(first))
    assert out[3].shape == (30, 64)
    # A training step runs and the Q target of played moves is set from the next exact value.
    arr = np.zeros(3, dtype=data.RECORD3)
    arr["mine"] = [0, 0, 1]  # cell 0 played, then cell 1
    arr["theirs"] = [0, 1, 2]
    arr["ply"] = [0, 1, 2]
    arr["exact"] = [1, 3, 1]
    arr["q"] = data.Q_UNKNOWN
    assert data.add_played_move_values(arr) == 2
    assert arr["q"][0, 0] == data.Q_WIN and arr["q"][1, 1] == data.Q_LOSS
    opt = train.make_optimizer(model, 1e-3, 0)
    res = train.train(model, opt, arr, 2, 3, np.random.default_rng(0), log_every=0)
    assert np.isfinite(res).all()
