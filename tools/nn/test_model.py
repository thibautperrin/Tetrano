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
    pol, wdl = evaluate_bits(model, mine, theirs)
    for s in (1, 17, 55, 100, 191):
        pm, ws = evaluate_bits(model, qubic.transform(s, mine), qubic.transform(s, theirs))
        np.testing.assert_allclose(ws, wdl, atol=1e-5)
        # policy is equivariant: logit of cell c moves to cell sym[c]
        np.testing.assert_allclose(pm[:, qubic.SYMMETRIES[s]], pol, atol=1e-4)
