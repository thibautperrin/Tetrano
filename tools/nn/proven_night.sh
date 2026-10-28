#!/usr/bin/env bash
# Night of 2026-10-28: fine-tune gen 364 on proven positions, then self-play generations with
# proven positions in every batch, then a match against gen 364. Run from tools/nn of the checkout
# holding runs/ (see README.md, "Positions prouvées"). Resumable: each step is skipped once done.
set -e
W=$(cd "$(dirname "$0")" && pwd)
PY=.venv/Scripts/python
LIB="$(cygpath -w "$W/../../selfplay/build/install/selfplay/lib")\*"
sp() { java -Xmx6g -cp "$LIB" fr.thibautperrin.tetrano.selfplay.MainKt "$@"; }
PROVEN=runs/proven_data/corner.bin,runs/proven_data/weak.bin
STOP=${STOP:-2026-10-29T04:40}

[ -f runs/proven_ft/ft.pt ] || $PY -u $W/proven.py --checkpoint runs/big/checkpoints/gen_0364.pt \
    --selfplay runs/big/data --proven $PROVEN --out runs/proven_ft
[ -f runs/proven_ft/match_364.txt ] || sp match a=runs/proven_ft/ft.bin b=runs/big/models/gen_0364.bin \
    openings=random games=400 sims=256 threads=14 seed=20261028 > runs/proven_ft/match_364.txt
cat runs/proven_ft/match_364.txt

mkdir -p runs/proven/data
[ -f runs/proven/data/gen_-0001.bin ] || $PY -c "
import sys; sys.path.insert(0, r'$W'); import data
data.load_window(data.files_in('runs/big/data'), 1_500_000).tofile('runs/proven/data/gen_-0001.bin')"
$PY -u $W/loop.py --run runs/proven --init-checkpoint runs/proven_ft/ft.pt --stop-at $STOP \
    --hidden 64 --layers 10 --games 500 --stream 1 --sims 2048 --adaptive 1 --policy-min-sims 256 \
    --openings schedule --schedule-seed 20261028 --disputed 0.2,0.8 --threads 14 \
    --window-base 1500000 --window-per-gen 0 --window-max 1500000 \
    --lr-peak 5e-5 --lr-final 1e-5 --lr-hold 0.5 --keep-data 1 \
    --proven $PROVEN --proven-frac 0.25

LAST=$(ls runs/proven/models/gen_*.bin | tail -1)
echo "final model: $LAST"
[ -f runs/proven/match_364.txt ] || sp match a=$LAST b=runs/big/models/gen_0364.bin \
    openings=random games=800 sims=512 threads=14 seed=20261029 > runs/proven/match_364.txt
cat runs/proven/match_364.txt
