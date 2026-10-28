"""SVG drawing of a Tetrano board (app geometry: BoardDrawing.kt) and of the Qubic layers."""
import html

RED = '#e0262b'
BLUE = '#1f5bd8'
HALO = '#ffd23f'
EMPTY = '#e2e2e2'

# app coefficients, side = 100
BORDER, SEP, DIST = 1.262, 0.9464, 23.6593
R0, DR, STROKE = 2.9, 2.8, 1.55
POS0 = BORDER + DIST / 2
STEP = DIST + SEP


def xyz(c):
    return c & 3, (c >> 2) & 3, c >> 4


def lab(c):
    x, y, z = xyz(c)
    return f"{'ABCD'[x]}{y + 1}/{z + 1}"


def center(c):
    x, y, _ = xyz(c)
    return POS0 + x * STEP, 100 - (POS0 + y * STEP)  # row 1 at the bottom


def radius(c):
    return R0 + DR * (c >> 4)


_ANGLES = [(0.71, -0.71), (-0.71, -0.71), (-0.71, 0.71), (0.71, 0.71)]


def defs():
    """Hidden SVG holding the empty board, referenced by every board."""
    out = ['<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs><g id="tb-base">',
           '<rect x="0" y="0" width="100" height="100" fill="#fcfcfc"/>']
    for c in range(64):
        cx, cy = center(c)
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{EMPTY}" '
                   f'stroke-width="{STROKE * 0.55:.1f}"/>')
    out.append(f'<rect x="{BORDER / 2}" y="{BORDER / 2}" width="{100 - BORDER}" height="{100 - BORDER}" '
               f'fill="none" stroke="#111" stroke-width="{BORDER}"/>')
    for i in range(1, 4):
        o = BORDER + i * DIST + SEP * (2 * i - 1) / 2
        out.append(f'<line x1="{o:.1f}" y1="0" x2="{o:.1f}" y2="100" stroke="#111" stroke-width="{SEP}"/>')
        out.append(f'<line x1="0" y1="{o:.1f}" x2="100" y2="{o:.1f}" stroke="#111" stroke-width="{SEP}"/>')
    out.append('</g></defs></svg>')
    return ''.join(out)


def board(rings=None, numbers=None, halo=(), targets=None, ghost=(), last=None, width=250, title=None,
          labels=True, fill_cells=None):
    """rings: cell -> 'A'|'D'; numbers: cell -> text; halo: cells to highlight; targets: cell -> 'A'|'D'
    (dashed: move to play); ghost: cells drawn pale blue (possible opponent rings); last: cell circled;
    fill_cells: (x, y) -> colour tint of the whole case."""
    rings = rings or {}
    numbers = numbers or {}
    targets = targets or {}
    fill_cells = fill_cells or {}
    m = 9 if labels else 2
    out = [f'<svg class="board" viewBox="{-m} -2 {102 + m} {104 + m}" width="{width}" role="img"'
           f' xmlns="http://www.w3.org/2000/svg">']
    if title:
        out.append(f'<title>{html.escape(title)}</title>')
    out.append('<use href="#tb-base"/>')
    for (x, y), col in fill_cells.items():
        out.append(f'<rect x="{BORDER + x * STEP:.1f}" y="{100 - BORDER - (y + 1) * STEP + SEP:.1f}" '
                   f'width="{DIST:.1f}" height="{DIST:.1f}" fill="{col}" fill-opacity="0.5"/>')
    if labels:
        for i in range(4):
            cx = POS0 + i * STEP
            out.append(f'<text x="{cx:.1f}" y="{100 + 6.5}" class="coord">{"ABCD"[i]}</text>')
            out.append(f'<text x="-4.5" y="{100 - cx + 1.6:.1f}" class="coord">{i + 1}</text>')
    for x, y in sorted({xyz(c)[:2] for c in halo}):  # light tint of the squares involved
        out.append(f'<rect x="{BORDER + x * STEP:.1f}" y="{100 - BORDER - (y + 1) * STEP + SEP:.1f}" '
                   f'width="{DIST:.1f}" height="{DIST:.1f}" fill="#fff3c4"/>')
        for z in range(4):
            c = x + 4 * y + 16 * z
            cx, cy = center(c)
            out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{EMPTY}" '
                       f'stroke-width="{STROKE * 0.55:.2f}"/>')
    same_square = len({xyz(c)[:2] for c in halo}) == 1  # the 4 sizes of one square: the tint says it all
    for c in ([] if same_square else halo):
        cx, cy = center(c)
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{HALO}" '
                   f'stroke-width="{STROKE * 1.8:.1f}"/>')
    for c in ghost:
        if c in rings or c in targets:
            continue
        cx, cy = center(c)
        col, w = '#9db8f0', STROKE * 1.1
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{col}" '
                   f'stroke-width="{w:.1f}"/>')
    for c, p in targets.items():
        cx, cy = center(c)
        col = RED if p == 'A' else BLUE
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{col}" '
                   f'stroke-width="{STROKE * 1.2:.1f}" stroke-dasharray="1.6 1.1"/>')
    if last is not None:  # the last move: a soft glow of its colour under the ring
        cx, cy = center(last)
        col = RED if rings.get(last) == 'A' else BLUE
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(last):.1f}" fill="none" stroke="{col}" '
                   f'stroke-opacity="0.28" stroke-width="{STROKE * 2.6:.1f}"/>')
    for c, p in rings.items():
        cx, cy = center(c)
        col = RED if p == 'A' else BLUE
        out.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius(c):.1f}" fill="none" stroke="{col}" '
                   f'stroke-width="{STROKE:.1f}"/>')
    for c, text in numbers.items():
        cx, cy = center(c)
        dx, dy = _ANGLES[c >> 4]
        r = radius(c)
        bx, by = cx + dx * r, cy + dy * r
        p = rings.get(c, targets.get(c, 'A'))
        col = RED if p == 'A' else BLUE
        if c >> 4 == 0:  # inside the smallest ring
            out.append(f'<text x="{cx:.1f}" y="{cy + 1.2:.1f}" class="num" fill="{col}">{html.escape(str(text))}</text>')
        else:
            out.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="2.7" fill="#fff" stroke="{col}" stroke-width="0.5"/>'
                       f'<text x="{bx:.1f}" y="{by + 1.2:.1f}" class="num" fill="{col}">{html.escape(str(text))}</text>')
    out.append('</svg>')
    return ''.join(out)


def layers(rings=None, halo=(), width=520, names=('1', '2', '3', '4')):
    """Qubic view: 4 layers (= ring sizes) side by side."""
    rings = rings or {}
    cell, gap = 10, 8
    w = 4 * (4 * cell) + 3 * gap
    out = [f'<svg class="board" viewBox="-6 -9 {w + 8} {4 * cell + 15}" width="{width}" '
           f'xmlns="http://www.w3.org/2000/svg">']
    for z in range(4):
        ox = z * (4 * cell + gap)
        out.append(f'<text x="{ox + 2 * cell}" y="-3" class="coord">{html.escape(names[z])}</text>')
        for y in range(4):
            for x in range(4):
                c = x + 4 * y + 16 * z
                fill = '#fff3c4' if c in halo else '#fcfcfc'
                X, Y = ox + x * cell, (3 - y) * cell
                out.append(f'<rect x="{X}" y="{Y}" width="{cell}" height="{cell}" fill="{fill}" stroke="#555" '
                           f'stroke-width="0.4"/>')
                if c in rings:
                    col = RED if rings[c] == 'A' else BLUE
                    out.append(f'<circle cx="{X + cell / 2}" cy="{Y + cell / 2}" r="3.2" fill="{col}"/>')
        for i in range(4):
            out.append(f'<text x="{ox + i * cell + cell / 2}" y="{4 * cell + 4.6}" class="coord small">'
                       f'{"ABCD"[i]}</text>')
        if z == 0:
            for i in range(4):
                out.append(f'<text x="-3.2" y="{(3 - i) * cell + cell / 2 + 1.4}" class="coord small">{i + 1}</text>')
    out.append('</svg>')
    return ''.join(out)
