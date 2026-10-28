"""Extracts the first player's data of the guide (attack.json) from the winning graph.

Usage: python extract_attack.py <tetrano_win.bin>
"""
import json
import os
import sys
from wg import *

HERE = os.path.dirname(os.path.abspath(__file__))
book = load(sys.argv[1])
reps=[r for r,_ in replies(1,0)]
cls={}
for c in range(1,64):
    k=canon(1,1<<c)[:2]
    for r in reps:
        if canon(1,1<<r)[:2]==k: cls[c]=r
answers={c: decide(1,1<<c,book)[1] for c in range(1,64)}
# Graph positions and real decisions per branch, measured by walking the graph (see README).
nodes={1:489,3:359,5:446,6:349,7:390,15:252,21:649,22:649,23:446,27:349,31:489,63:252}
dec={1:5,3:6,5:5,6:5,7:5,15:5,21:9,22:9,23:5,27:5,31:5,63:5}
P=lambda s: cell('ABCD'.index(s[0]), int(s[1])-1, int(s[3])-1)
game='A1/1 D4/4 D1/1 B1/1 A4/1 B3/3 B2/2 A2/1 A1/4 A1/2 B3/2 C1/1 B1/2 B4/2 D1/4'.split()
json.dump({'answers':answers,'classes':{r:[c for c in range(1,64) if cls[c]==r] for r in reps},
           'nodes':nodes,'dec':dec,'game':[P(s) for s in game],
           'lines':LINES, 'strong':[c for c in range(64) if len(CELL_LINES[c])==7]},
          open(os.path.join(HERE, 'attack.json'), 'w'))
print('ok', len(answers))
