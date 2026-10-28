"""Builds the players' guide (French and English HTML) from attack.json and defence.json.

Positions are only ever shown on drawn boards: the text never names a place. Internally a place
is written 'A1/1' (square A1, ring size 1), the bottom-left square holding the smallest ring.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from boards import board, defs, xyz  # noqa: E402
from wg import threats, lowest  # noqa: E402

DATA = json.load(open(os.path.join(HERE, 'attack.json')))
DEF = json.load(open(sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'defence.json')))
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(HERE, '..', '..', 'docs', 'guide')
LANG = 'fr'


def t(fr, en):
    return fr if LANG == 'fr' else en


def cell(s):
    """'A1/1' -> place number."""
    return 'ABCD'.index(s[0]) + 4 * (int(s[1]) - 1) + 16 * (int(s[3]) - 1)


def cells(text):
    return [cell(s) for s in text.split()]


def line(*names):
    """The 4 places of the alignment through the given places."""
    want = [cell(n) for n in names]
    for m in DATA['lines']:
        if all(m >> c & 1 for c in want):
            return [c for c in range(64) if m >> c & 1]
    raise ValueError(names)


def fig(svg, caption=None):
    cap = f'<figcaption>{caption}</figcaption>' if caption else ''
    return f'<figure>{svg}{cap}</figure>'


def grid(*figs, cls='figs'):
    return f'<div class="{cls}">{"".join(figs)}</div>'


def num1(x):
    s = f'{x:.1f}'
    return s.replace('.', ',') if LANG == 'fr' else s


def pct(x):
    return f'{x:.0f} %' if LANG == 'fr' else f'{x:.0f}%'


# ---------------------------------------------------------------------------------------------
# Animations: one frame per move; only the last move carries a label (its number), and an
# alignment is shown in yellow only when the comment talks about it.

def stepper(frames, ident, width=330):
    """frames: list of dicts {rings, num, hl, text}: num = {place: label} (one label at most)."""
    out = []
    for i, f in enumerate(frames):
        svg = board(f['rings'], f.get('num'), halo=f.get('hl') or (), width=width)
        out.append(f'<div class="frame"{"" if i == len(frames) - 1 else " hidden"}>{svg}'
                   f'<p class="comment">{f["text"]}</p></div>')
    return (f'<div class="stepper" id="{ident}"><div class="controls">'
            f'<button type="button" class="first" aria-label="{t("Début", "Start")}">⏮</button>'
            f'<button type="button" class="prev" aria-label="{t("Coup précédent", "Previous move")}">◀</button>'
            f'<span class="counter"></span>'
            f'<button type="button" class="next" aria-label="{t("Coup suivant", "Next move")}">▶</button>'
            f'<button type="button" class="last" aria-label="{t("Fin", "End")}">⏭</button></div>'
            f'{"".join(out)}</div>')


def game(moves, notes, extra=None):
    """moves: list of (place, 'A'|'D'); notes: index -> (fr, en, hl) with hl a pair of place names
    (one alignment) or a list of pairs (several)."""
    extra = extra or {}
    frames, rings = [], {}
    for i, (c, p) in enumerate(moves):
        rings[c] = p
        fr, en, hl = notes.get(i, ('', '', None))[:3]
        who = t('Premier joueur', 'First player') if p == 'A' else t('Second joueur', 'Second player')
        text = f'<b>{t("Coup", "Move")} {i + 1} · <span class="{"red" if p == "A" else "blue"}">{who}</span></b><br>' + t(fr, en)
        text += extra.get(i, '')
        frames.append({'rings': dict(rings), 'num': {c: i + 1}, 'hl': lines_of(hl), 'text': text})
    return frames


def lines_of(hl):
    if not hl:
        return None
    pairs = hl if isinstance(hl, list) else [hl]
    return sorted({c for p in pairs for c in line(*p)})


def line_moves(entries):
    """Moves of an analysis line ('lost' entries: the defender blocks one threat)."""
    return [(m['cell'] if m['cell'] >= 0 else m['threats'][0], m['p']) for m in entries]


def legend():
    items = [('red-ring', t("premier joueur", "first player")), ('blue-ring', t("second joueur", "second player")),
             ('label', t("le coup dont on parle (son numéro)", "the move being discussed (its number)")),
             ('yellow', t("l’alignement dont on parle", "the line being discussed"))]
    return '<p class="legend">' + ''.join(f'<span class="item"><span class="sw {k}">{"3" if k == "label" else ""}</span>{v}</span>' for k, v in items) + '</p>'


# ---------------------------------------------------------------------------------------------
def section_intro():
    other = 'tetrano-guide-en.html' if LANG == 'fr' else 'tetrano-guide-fr.html'
    switch = '' if APP else f'<p class="lang"><a href="{other}">{t("English version", "Version française")}</a></p>'
    return f'''
<header>
  {switch}
  <h1>{t('Tetrano &amp; Qubic 4×4×4 : bien commencer, bien se défendre',
        'Tetrano &amp; Qubic 4×4×4: strong openings, stubborn defence')}</h1>
  <p class="lede">{t(
    "Quels coups jouer en début de partie, et pourquoi. Tout ce qui suit est tiré d'une <b>stratégie gagnante prouvée</b>, calculée par ordinateur et vérifiée contre toutes les défenses possibles.",
    "Which moves to play early in the game, and why. Everything here comes from a <b>proven winning strategy</b>, computed and checked against every possible defence.")}</p>
</header>
<div class="cards">
  <div class="card"><b>{t('Le premier joueur gagne', 'The first player wins')}</b>
    <p>{t("S'il ne se trompe jamais, quoi que fasse l'adversaire.", "If they never make a mistake, whatever the opponent does.")}</p></div>
  <div class="card"><b>{t('Le second joueur peut durer', 'The second player can hold out')}</b>
    <p>{t("Bien défendre, c'est repousser le moment où la victoire adverse devient mécanique, en gardant ses propres chances.",
          "Good defence pushes back the moment the opponent's win becomes mechanical, while keeping your own chances.")}</p></div>
</div>
<p>{t("Les schémas montrent le plateau de Tetrano vu de dessus : 16 cases, 4 tailles d'anneaux par case. Pour le Qubic 4×4×4, chaque taille d'anneau est un étage du cube.",
      "The diagrams show the Tetrano board from above: 16 squares, 4 ring sizes per square. For Qubic 4×4×4, each ring size is one layer of the cube.")}</p>
{legend()}'''


def section_strong():
    lines7 = [
        (('A1/1', 'A1/4'), t('les 4 tailles d’une même case', 'all 4 sizes of one square')),
        (('A1/1', 'D1/1'), t('une ligne, même taille', 'a row, same size')),
        (('A1/1', 'A4/1'), t('une colonne, même taille', 'a column, same size')),
        (('A1/1', 'D4/1'), t('une diagonale, même taille', 'a diagonal, same size')),
        (('A1/1', 'D1/4'), t('une ligne, tailles croissantes', 'a row, increasing sizes')),
        (('A1/1', 'A4/4'), t('une colonne, tailles croissantes', 'a column, increasing sizes')),
        (('A1/1', 'D4/4'), t('une diagonale, tailles croissantes', 'a diagonal, increasing sizes')),
    ]
    minis = [fig(board({0: 'A'}, halo=line(*n), width=150, labels=False), cap) for n, cap in lines7]
    return f'''
<section id="fortes">
<h2>{t('1. Places fortes, places faibles', '1. Strong places, weak places')}</h2>
<p>{t("Il y a 76 alignements gagnants. Un anneau dans un coin, petit ou grand, en fait partie de <b>7</b> : les voici, en jaune.",
      "There are 76 winning lines. A ring in a corner, small or large, belongs to <b>7</b> of them: here they are, in yellow.")}</p>
{grid(*minis, cls='figs small')}
<p>{t("Seules 16 places sont sur 7 alignements : les petits et grands anneaux des 4 coins, et les anneaux moyens des 4 cases du centre. Toutes les autres ne sont que sur 4 alignements.",
      "Only 16 places lie on 7 lines: the small and large rings of the 4 corners, and the medium rings of the 4 central squares. All the others lie on only 4 lines.")}</p>
{grid(fig(board(halo=DATA['strong'], width=260), t('Les 16 places fortes (en jaune).', 'The 16 strong places (in yellow).')))}
<p>{t("Le jeu a <b>192 symétries</b> : on peut tourner et retourner le plateau, inverser l'ordre des tailles, et même échanger coins et centres sans changer le jeu. Conséquence : à symétrie près, il n'y a que <b>deux</b> sortes de places, les fortes et les faibles. Dans la suite, on fixe votre premier anneau en bas à gauche, le plus petit, et on ne montre qu'une seule version de chaque position.",
      "The game has <b>192 symmetries</b>: you can rotate and flip the board, reverse the order of the sizes, and even swap corners and centres without changing the game. As a result, up to symmetry there are only <b>two</b> kinds of places, strong and weak. From here on, your first ring is fixed in the bottom-left corner, the smallest one, and only one version of each position is shown.")}</p>
</section>'''


def section_first():
    reply_text = {
        'D1/1': ('Un coin voisin, même taille, au bout de votre ligne.', 'A neighbouring corner, same size, at the end of your row.'),
        'D4/1': ('Le coin opposé de la grille, même taille, au bout de votre diagonale.', 'The opposite corner of the grid, same size, at the end of your diagonal.'),
        'D4/4': ('Le coin opposé, grand anneau : à l’autre bout de votre diagonale.', 'The opposite corner, large ring: at the other end of your diagonal.'),
        'B2/2': ('Un centre sur votre diagonale.', 'A centre on your diagonal.'),
        'C2/2': ('Un centre hors de vos alignements.', 'A centre off your lines.'),
        'B1/1': ('Juste à côté, sur votre ligne.', 'Right next to you, on your row.'),
        'B2/1': ('Sur votre diagonale, même taille.', 'On your diagonal, same size.'),
        'C2/1': ('Même taille, hors de vos alignements.', 'Same size, off your lines.'),
        'D2/1': ('Même taille, sur le bord opposé.', 'Same size, on the far edge.'),
        'D2/2': ('Taille 2, sur le bord opposé.', 'Size 2, on the far edge.'),
        'D3/2': ('Taille 2, sur le bord opposé, plus loin.', 'Size 2, on the far edge, further away.'),
        'D4/2': ('Taille 2, dans le coin opposé de la grille.', 'Size 2, in the opposite corner of the grid.'),
    }
    order = 'D1/1 D4/1 D4/4 B2/2 C2/2 B1/1 B2/1 C2/1 D2/1 D2/2 D3/2 D4/2'.split()
    twelve = []
    for name in order:
        r = cell(name)
        ans = DATA['answers'][str(r)]
        fr, en = reply_text[name]
        nodes = DATA['nodes'][str(r)]
        twelve.append(fig(board({0: 'A', r: 'D', ans: 'A'}, {ans: 3}, halo=line_by_cells(0, ans), width=190),
                          t(f'{fr}<br><span class="muted">{nodes} positions de la stratégie</span>',
                            f'{en}<br><span class="muted">{nodes} positions of the strategy</span>')))

    tri = [fig(board({cell('A1/1'): 'A', cell('D1/1'): 'A', cell('A4/1'): 'A'}, halo=line(*pair), width=150, labels=False))
           for pair in (('A1/1', 'D1/1'), ('A1/1', 'A4/1'), ('D1/1', 'A4/1'))]
    blocked = board({cell('A1/1'): 'A', cell('B1/1'): 'D', cell('A4/4'): 'A', cell('A2/2'): 'D', cell('D4/4'): 'A'},
                    {cell('D4/4'): 5}, width=230)

    # The annotated game: a defence that only takes strong places (analysis line, strong places only).
    g = next(r for r in DEF['replies'] if r['cell'] == cell('D4/4'))['strongLine']
    notes = {
        0: ('Un coin : place forte.', 'A corner: a strong place.', None, None),
        1: ('Le coin opposé : place forte aussi.', 'The opposite corner: a strong place too.', None, None),
        2: ('Deuxième coin, sur un alignement que l’adversaire n’a pas touché (en jaune).',
            'Second corner, on a line the opponent has not touched (in yellow).', ('A1/1', 'D1/1'), None),
        3: ('Il prend un centre, sans bloquer l’alignement.', 'They take a centre, without blocking the line.', None, None),
        4: ('Troisième coin, aligné avec les deux premiers. Le premier joueur a maintenant trois alignements à deux anneaux, dont celui en jaune.',
            'Third corner, in line with the first two. The first player now has three lines with two rings, including the yellow one.',
            ('A1/1', 'D4/1'), None),
        5: ('Encore une place forte, toujours sans bloquer.', 'Another strong place, still without blocking.', None, None),
        6: ('Quatrième coin, le grand anneau de la même case que le premier : six alignements à deux anneaux. Impossible de tous les bloquer.',
            'Fourth corner, the large ring in the same square as the first: six lines with two rings. They cannot all be blocked.', None, None),
        7: ('Encore une place forte.', 'Another strong place.', None, None),
        8: ('La victoire devient mécanique : une suite de menaces commence. Trois anneaux dans la même case (en jaune) : il menace de la compléter.',
            'The win becomes mechanical: a threat sequence starts. Three rings in the same square (in yellow): it threatens to complete it.',
            ('A1/1', 'A1/4')),
        9: ('Forcé.', 'Forced.', None, None),
        10: ('Nouvelle menace : il ne manque qu’un anneau sur l’alignement en jaune.', 'New threat: one ring is missing on the yellow line.', ('D1/1', 'A1/4')),
        11: ('Forcé.', 'Forced.', None, None),
        12: ('Nouvelle menace (en jaune).', 'New threat (in yellow).', ('A1/2', 'B1/2')),
        13: ('Forcé.', 'Forced.', None, None),
        14: ('Nouvelle menace (en jaune).', 'New threat (in yellow).', ('A1/1', 'D1/1')),
        15: ('Forcé.', 'Forced.', None, None),
        16: ('<b>Fourchette</b> : deux menaces à la fois, sur les deux alignements en jaune.', '<b>Fork</b>: two threats at once, on the two yellow lines.', [('C1/1', 'C1/4'), ('A1/1', 'D1/4')]),
        17: ('Il ne peut en parer qu’une.', 'They can only stop one.', None, None),
        18: ('Gagné (en jaune).', 'Won (in yellow).', ('A1/1', 'D1/4'), None),
    }
    frames = game(line_moves(g), notes)

    return f'''
<section id="premier">
<h2>{t('2. Quand vous commencez', '2. When you move first')}</h2>

<h3>{t('Coup 1 : une place forte', 'Move 1: a strong place')}</h3>
<p>{t("Il n'y a que deux premiers coups vraiment différents :", "There are only two truly different first moves:")}</p>
{grid(fig(board({0: 'A'}, width=200), t('Une place forte (7 alignements). <b>C’est celle-ci qu’il faut jouer.</b>', 'A strong place (7 lines). <b>This is the one to play.</b>')),
      fig(board({cell('B1/1'): 'A'}, width=200), t('Une place faible (4 alignements).', 'A weak place (4 lines).')))}
<p>{t("La stratégie prouvée commence par une place forte : presque deux fois plus d'alignements, donc plus de façons de les <b>croiser</b> plus tard, ce qui est la clé de la victoire.",
      "The proven strategy starts on a strong place: almost twice as many lines, hence more ways to make them <b>cross</b> later, which is the key to winning.")}</p>

<h3>{t('Coup 3 : un deuxième coin, sur un alignement libre', 'Move 3: a second corner, on a free line')}</h3>
<p>{t("L'adversaire a 12 réponses vraiment différentes (toutes les autres en sont des symétriques). Voici, pour chacune, le coup de la stratégie (étiquette 3). C'est <b>toujours un coin</b>, relié à votre premier anneau par un alignement que l'adversaire n'a pas touché (en jaune).",
      "The opponent has 12 truly different replies (all others are mirror images of these). Here is the strategy's move for each (label 3). It is <b>always a corner</b>, linked to your first ring by a line the opponent has not touched (in yellow).")}</p>
{grid(*twelve, cls='figs small')}
<p class="note">{t("« Positions de la stratégie » : combien des 2 519 positions où la stratégie doit choisir son coup se trouvent dans cette branche (une même position peut servir à plusieurs branches). Les réponses au centre demandent la plus grosse partie de la stratégie.",
                   "“Positions of the strategy”: how many of the 2,519 positions where the strategy has to choose its move lie in this branch (one position can serve several branches). The replies in the centre take up the largest part of the strategy.")}</p>
<p>{t("Pourquoi un coin ? Deux places fortes reliées par un alignement libre : l'adversaire devra tôt ou tard le bloquer, souvent sur une place faible. Et deux coins d'un même côté du cube préparent le coup suivant.",
      "Why a corner? Two strong places joined by a free line: sooner or later the opponent must block it, often on a weak place. And two corners on the same side of the cube prepare the next move.")}</p>

<h3>{t('Coup 5 : le triangle', 'Move 5: the triangle')}</h3>
<p>{t("Le troisième anneau cherche à être aligné avec <b>les deux premiers</b>. Trois coins d'un même côté du cube sont reliés deux à deux par trois alignements : l'adversaire ne peut en bloquer qu'un par coup.",
      "The third ring aims to be in line with <b>both</b> of the first two. Three corners on the same side of the cube are linked in pairs by three lines: the opponent can block only one per move.")}</p>
{grid(*tri, cls='figs small')}
<p>{t("Dans la stratégie, le troisième anneau est un coin dans 78 % des cas et un centre dans 15 % ; il est aligné avec vos deux premiers anneaux 8 fois sur 10. Si l'adversaire a déjà bloqué l'alignement entre vos deux coins, prenez un coin aligné avec chacun d'eux par d'autres alignements :",
      "In the strategy, the third ring is a corner in 78% of cases and a centre in 15%; it is in line with both of your first rings 8 times out of 10. If the opponent has already blocked the line between your two corners, take a corner in line with each of them along other lines:")}</p>
{grid(fig(blocked, t('Les deux anneaux bleus bloquent ; le bon coup porte l’étiquette 5.', 'The two blue rings block; the right move carries label 5.')))}

<h3>{t('Ensuite : la suite de menaces', 'Then: the threat sequence')}</h3>
<p>{t("Une <b>menace</b>, c'est trois anneaux sur un alignement dont la quatrième place est libre : l'adversaire est obligé de la bloquer. En enchaînant les menaces, on finit par en créer <b>deux à la fois</b> : c'est la <b>fourchette</b>, imparable. Dès que vous avez assez d'alignements à deux anneaux qui se croisent, la victoire devient <b>mécanique</b>.",
      "A <b>threat</b> is three rings on a line whose fourth place is free: the opponent must block it. Chaining threats, you end up creating <b>two at once</b>: the <b>fork</b>, which cannot be stopped. As soon as you have enough crossing lines with two rings, the win becomes <b>mechanical</b>.")}</p>
<p>{t("À chaque coup, dans l'ordre : <b>1.</b> gagner si possible ; <b>2.</b> sinon bloquer une menace adverse ; <b>3.</b> sinon chercher une suite de menaces qui finit en fourchette ; <b>4.</b> sinon seulement, un coup de construction.",
      "At every move, in this order: <b>1.</b> win if you can; <b>2.</b> otherwise block an opponent's threat; <b>3.</b> otherwise look for a threat sequence ending in a fork; <b>4.</b> only then, a building move.")}</p>

<h3>{t('Une partie commentée', 'An annotated game')}</h3>
<p>{t("Ici, le second joueur ne joue que des places fortes, sans jamais bloquer. Ça a l'air solide ; en réalité la victoire du premier joueur devient mécanique dès le coup 9. La bonne défense est au chapitre suivant.",
      "Here the second player only plays strong places and never blocks. It looks solid; in fact the first player's win becomes mechanical from move 9. The right defence is in the next chapter.")}</p>
{stepper(frames, 'game-first')}
</section>'''


def line_by_cells(a, b):
    for m in DATA['lines']:
        if m >> a & 1 and m >> b & 1:
            return [c for c in range(64) if m >> c & 1]
    return []


# ---------------------------------------------------------------------------------------------
def section_second():
    reps = DEF['replies']
    centre = next(r for r in reps if r['cell'] == cell('C2/2'))
    feats = DEF['features']

    def mech(entries):
        return next(i + 1 for i, m in enumerate(entries) if m['p'] == 'A' and m['rule'] in ('vcf', 'win'))

    def finds(entries):
        return sum(1 for m in entries if m['p'] == 'A' and m['rule'] == 'book') - 1  # move 1 excluded

    rows = []
    for r in sorted(reps, key=lambda r: (-mech(r['line']), -finds(r['line']), r['cell'])):
        best = r['cell'] == cell('C2/2') or r['cell'] == cell('B2/2')
        rows.append(f'<tr class="{"best" if best else ""}"><td>{board({0: "A", r["cell"]: "D"}, width=96, labels=False)}</td>'
                    f'<td class="num">{mech(r["line"])}</td><td class="num">{finds(r["line"])}</td>'
                    f'<td class="num">{mech(r["strongLine"])}</td>'
                    f'<td class="num">{DATA["nodes"][str(r["cell"])]}</td></tr>')
    table = (f'<div class="table-wrap"><table class="replies"><thead><tr><th>{t("Réponse", "Reply")}</th>'
             f'<th class="num">{t("Victoire mécanique à partir du coup", "Win mechanical from move")}</th>'
             f'<th class="num">{t("Coups que le premier joueur doit trouver", "Moves the first player must find")}</th>'
             f'<th class="num">{t("Si l’on ne joue que des places fortes", "Playing strong places only")}</th>'
             f'<th class="num">{t("Positions de la stratégie", "Positions of the strategy")}</th></tr></thead>'
             f'<tbody>{"".join(rows)}</tbody></table></div>')

    # the recommended line
    notes = {
        0: ('Un coin.', 'A corner.', None, None),
        1: ('<b>Un centre.</b>', '<b>A centre.</b>', None, None),
        2: ('Deuxième coin : un alignement à deux anneaux (en jaune).', 'Second corner: a line with two rings (in yellow).', ('A1/1', 'D1/1'), None),
        3: ('Il bloque cet alignement (en jaune). Au passage, ce nouvel anneau est aligné avec son centre.',
            'They block that line (in yellow). On the way, this new ring is in line with their centre.', ('A1/1', 'D1/1'), None),
        4: ('Troisième coin : deux nouveaux alignements à deux anneaux, dont celui en jaune.',
            'Third corner: two new lines with two rings, including the yellow one.', ('D1/1', 'D4/1'), None),
        5: ('Il bloque celui-là, là encore en s’alignant avec son centre.', 'They block that one, again in line with their centre.', ('D1/1', 'D4/1'), None),
        6: ('Un anneau aligné avec son troisième coin (en jaune).', 'A ring in line with their third corner (in yellow).', ('D4/1', 'B2/3'), None),
        7: ('Il bloque, depuis une autre place forte, et forme un alignement à deux anneaux avec son centre.',
            'They block, from another strong place, and make a line with two rings with their centre.', ('D4/1', 'B2/3'), None),
        8: ('Le premier joueur n’a plus de coup tranquille : il doit menacer (alignement en jaune).',
            'The first player has no quiet move left: they must threaten (yellow line).', ('A1/1', 'B2/1')),
        9: ('Forcé, mais utile : ce blocage lui donne deux alignements de plus. Il en a quatre à deux anneaux.',
            'Forced, but useful: this block gives them two more lines. They have four with two rings.', None, None),
        10: ('Menace (en jaune).', 'Threat (in yellow).', ('B2/1', 'B2/2')),
        11: ('Forcé.', 'Forced.', None, None),
        12: ('Menace (en jaune).', 'Threat (in yellow).', ('A1/1', 'B2/2')),
        13: ('Forcé ; il prend un coin au passage.', 'Forced; they pick up a corner on the way.', None, None),
        14: ('Menace (en jaune).', 'Threat (in yellow).', ('A1/3', 'C3/3')),
        15: ('Forcé.', 'Forced.', None, None),
        16: ('Menace (en jaune).', 'Threat (in yellow).', ('A1/1', 'A1/4')),
        17: ('Forcé.', 'Forced.', None, None),
        18: ('Dernier coup difficile : un coup calme qui crée trois alignements à deux anneaux.',
             'Last hard move: a quiet move creating three lines with two rings.', None, None),
        19: ('Trop tard : la victoire du premier joueur est maintenant mécanique.', 'Too late: the first player\'s win is now mechanical.', None, None),
        20: ('Menace (en jaune).', 'Threat (in yellow).', ('C1/3', 'C2/3')),
        21: ('Forcé.', 'Forced.', None, None),
        22: ('<b>Fourchette</b> : deux menaces, sur les deux alignements en jaune.', '<b>Fork</b>: two threats, on the two yellow lines.', [('D1/1', 'A1/4'), ('A1/3', 'D1/3')]),
        23: ('Il ne peut en parer qu’une.', 'They can only stop one.', None, None),
        24: ('Gagné, au 25e coup (en jaune).', 'Won, on move 25 (in yellow).', ('A1/3', 'D1/3'), None),
    }
    frames = game(line_moves(centre['line']), notes)

    f = feats
    n = f['positions']
    bs_b, bs_a = f['blocksTwoStrong']
    bw_b, bw_a = f['blocksTwo'][0] - bs_b, f['blocksTwo'][1] - bs_a
    sn_b, sn_a = f['strong'][0] - bs_b, f['strong'][1] - bs_a
    nn_b, nn_a = f['nBest'] - bs_b - bw_b - sn_b, f['nAll'] - bs_a - bw_a - sn_a
    fam = [
        (t('Bloque un alignement à deux anneaux, sur une place forte', 'Blocks a two-ring line, on a strong place'), bs_a, bs_b),
        (t('Bloque un alignement à deux anneaux, sur une place faible', 'Blocks a two-ring line, on a weak place'), bw_a, bw_b),
        (t('Place forte qui ne bloque rien', 'Strong place blocking nothing'), sn_a, sn_b),
        (t('Ni l’un ni l’autre', 'Neither'), nn_a, nn_b),
    ]
    famrows = ''.join(
        f'<tr><td>{name}</td><td class="num">{num1(all_ / n)}</td><td class="num">{pct(100 * best / all_)}'
        f' <span class="bar" style="width:{1.6 * 100 * best / all_:.0f}px"></span></td></tr>'
        for name, all_, best in fam)
    famtable = (f'<div class="table-wrap"><table><thead><tr><th>{t("Sorte de coup", "Kind of move")}</th>'
                f'<th class="num">{t("Combien, par position (moyenne)", "How many per position (average)")}</th>'
                f'<th class="num">{t("Part de ces coups qui sont parmi les meilleurs", "Share of these moves that are among the best")}</th>'
                f'</tr></thead><tbody>{famrows}</tbody></table></div>')
    avg_moves = f['nAll'] / n
    avg_best = f['nBest'] / n
    forcing_rate = 100 * f['forcing'][0] / f['forcing'][1]

    html = f'''
<section id="second">
<h2>{t('3. Quand vous jouez en second', '3. When you move second')}</h2>
<p>{t("Contre un joueur parfait, il n'y a pas de défense. La meilleure stratégie en second est donc celle qui <b>perd le plus tard possible</b>, pour laisser au premier joueur le temps de se tromper, et qui, s'il se trompe, vous donne de <b>vraies chances de gagner</b>.",
      "Against perfect play there is no defence. The best second-player strategy is therefore the one that <b>loses as late as possible</b>, giving the first player time to go wrong, and that, if they do, gives you <b>real winning chances</b>.")}</p>
<div class="callout">{t("<b>Ce que l'on mesure.</b> Pas le nombre d'anneaux joués : on récompenserait les menaces sans avenir posées pour retarder la fin. On mesure le <b>moment où la victoire du premier joueur devient mécanique</b> (une suite de menaces imparable), et le nombre de coups qu'il doit trouver lui-même avant. Une menace de votre part ne change rien à ce compte : sa réponse est forcée.",
                         "<b>What we measure.</b> Not the number of rings played: that would reward pointless threats played only to delay the end. We measure the <b>moment the first player's win becomes mechanical</b> (an unstoppable threat sequence), and how many moves they must find on their own before that. A threat of yours changes nothing on this count: their reply is forced.")}</div>

<h3>{t('Coup 2 : répondez au centre', 'Move 2: reply in the centre')}</h3>
{grid(fig(board({0: 'A', cell('C2/2'): 'D'}, width=220), t('Un anneau moyen dans une case du centre.', 'A medium ring in a central square.')))}
<p>{t("Voici les 12 réponses possibles au coin, chacune suivie de la meilleure défense ensuite. Avec un centre, la victoire du premier joueur ne devient mécanique qu'au coup 21, après 9 coups qu'il doit trouver seul. Avec toute autre réponse, elle l'est dès le coup 13 à 15.",
      "Here are the 12 possible replies to the corner, each followed by the best defence afterwards. With a centre, the first player's win only becomes mechanical on move 21, after 9 moves they must find on their own. With any other reply it is mechanical by move 13 to 15.")}</p>
{table}

<h3>{t('Faut-il rester sur les places fortes ?', 'Should you stay on strong places?')}</h3>
<p>{t("C'est l'intuition naturelle : garder ses anneaux sur des places à 7 alignements plutôt que d'aller se loger dans les alignements adverses. Elle ne tient pas. Une défense qui ne joue que des places fortes laisse la victoire devenir mécanique <b>dès le coup 9</b>, quelle que soit la réponse au coup 2 (avant-dernière colonne du tableau, et la partie commentée du chapitre précédent).",
      "It is the natural intuition: keep your rings on 7-line places rather than settle inside the opponent's lines. It does not hold. A defence that only plays strong places lets the win become mechanical <b>from move 9</b>, whatever the reply at move 2 (second-to-last column of the table, and the annotated game in the previous chapter).")}</p>
<p>{t(f"Pour le voir plus largement, on a passé en revue les <b>{n} positions</b> de la stratégie où le choix du second joueur compte. Dans chacune, il a en moyenne {avg_moves:.0f} coups possibles, dont {avg_best:.0f} environ sont « les meilleurs » (ceux qui repoussent le plus la victoire mécanique). On a rangé tous ces coups en quatre sortes :",
      f"To see it more broadly, we went through the <b>{n} positions</b> of the strategy where the second player's choice matters. In each, they have about {avg_moves:.0f} possible moves on average, of which about {avg_best:.0f} are “the best” (those that push back the mechanical win the most). We sorted all those moves into four kinds:")}</p>
{famtable}
<p>{t(f"Lecture : un coup qui bloque un alignement à deux anneaux est parmi les meilleurs <b>une fois sur deux</b>, qu'il soit sur une place forte ou faible. Une place forte qui ne bloque rien ne l'est que <b>{100 * sn_b / sn_a:.0f} fois sur 100</b>. Les places fortes ne sont pas inutiles : quand rien n'est à bloquer, la meilleure place forte vaut souvent le meilleur coup ({f['strongEqual']} positions sur {n}) ; mais dès qu'un alignement à deux anneaux est ouvert, il faut le bloquer. Quant à faire une menace sans suite, c'est rarement utile : seuls {forcing_rate:.0f} % de ces coups sont parmi les meilleurs.",
      f"How to read it: a move that blocks a two-ring line is among the best <b>one time in two</b>, whether on a strong or a weak place. A strong place that blocks nothing is among the best only <b>{100 * sn_b / sn_a:.0f} times in 100</b>. Strong places are not useless: when there is nothing to block, the best strong place is often as good as the best move ({f['strongEqual']} positions out of {n}); but as soon as a two-ring line is open, it must be blocked. As for making a threat that leads nowhere, it rarely helps: only {forcing_rate:.0f}% of such moves are among the best.")}</p>

<h3>{t('La meilleure défense, coup par coup', 'The best defence, move by move')}</h3>
<p>{t("L'idée centrale : chaque blocage du second joueur fait <b>aussi</b> un alignement avec son centre. Au coup 9, le premier joueur n'a plus de coup tranquille et doit menacer à chaque coup ; chaque menace doit être exacte, sinon les alignements du second joueur prennent le dessus.",
      "The core idea: each of the second player's blocks <b>also</b> lines up with their centre. By move 9 the first player has no quiet move left and must threaten every move; each threat has to be exact, or the second player's lines take over.")}</p>
{stepper(frames, 'game-second')}
'''
    html += f'''
<h3>{t('Les principes', 'The principles')}</h3>
<ol>
<li>{t('<b>Prenez un centre</b> au coup 2.', '<b>Take a centre</b> at move 2.')}</li>
<li>{t('<b>Bloquez chaque alignement à deux anneaux du premier joueur dès qu’il apparaît.</b> Ne vous contentez pas des places fortes.', '<b>Block every two-ring line of the first player as soon as it appears.</b> Don’t just sit on strong places.')}</li>
<li>{t('<b>Choisissez le blocage qui vous aligne avec vos propres anneaux</b>, idéalement votre centre : chaque coup défend et prépare la contre-attaque.', '<b>Pick the block that lines up with your own rings</b>, ideally your centre: each move defends and prepares the counter-attack.')}</li>
<li>{t('<b>Gardez vos menaces</b> pour une fourchette, ou pour bloquer en menaçant.', '<b>Save your threats</b> for a fork, or to block while threatening.')}</li>
<li>{t('<b>Quand il force, construisez</b> : chaque blocage forcé peut aussi prolonger vos alignements ; si sa suite de menaces n’est pas exacte, la contre-attaque est prête.', '<b>When they force, keep building</b>: each forced block can also extend your lines; if their threat sequence is not exact, your counter-attack is ready.')}</li>
</ol>
</section>'''
    summary = (f'<ol><li>{t("Coup 2 : un centre.", "Move 2: a centre.")}</li>'
               f'<li>{t("Bloquez chaque alignement à deux anneaux dès qu’il apparaît.", "Block every two-ring line as soon as it appears.")}</li>'
               f'<li>{t("Bloquez en vous alignant avec vos anneaux.", "Block while lining up with your own rings.")}</li>'
               f'<li>{t("Pas de menace gratuite.", "No free threats.")}</li>'
               f'<li>{t("Quand il force, bloquez en construisant.", "When they force, block while building.")}</li></ol>')
    return html, summary


def section_summary(def_summary):
    return f'''
<section id="resume">
<h2>{t('5. En résumé', '5. Summary')}</h2>
<div class="cols">
<div><h3>{t('En premier', 'Moving first')}</h3><ol>
<li>{t('Une place forte.', 'A strong place.')}</li>
<li>{t('Coup 3 : un coin relié au premier par un alignement libre.', 'Move 3: a corner linked to the first by a free line.')}</li>
<li>{t('Coup 5 : le triangle.', 'Move 5: the triangle.')}</li>
<li>{t('Ensuite : une suite de menaces jusqu’à la fourchette.', 'Then: a threat sequence up to the fork.')}</li>
<li>{t("Toujours d'abord : gagner si possible, sinon bloquer.", 'Always first: win if you can, otherwise block.')}</li></ol></div>
<div><h3>{t('En second', 'Moving second')}</h3>{def_summary}</div>
</div>
<p>{t("Pour vous entraîner dans l'appli, choisissez « Ordinateur » pour qui commence : le <b>niveau 102</b> joue exactement cette stratégie (premier anneau sur une place forte), le <b>niveau 103</b> celle prouvée après un premier anneau sur une place faible (chapitre 4). Aucun des deux ne perd ni ne fait match nul : à vous de durer le plus longtemps possible.",
      "To practise in the app, choose “Computer” as the one who begins: <b>level 102</b> plays exactly this strategy (first ring on a strong place), <b>level 103</b> the one proven after a first ring on a weak place (chapter 4). Neither ever loses or draws: try to hold out as long as you can.")}</p>
</section>'''


def section_method():
    return f'''
<section id="methode" class="method">
<h2>{t("D'où viennent ces conseils", 'Where this advice comes from')}</h2>
<p>{t("Du <b>graphe de gain forcé</b> de Tetrano (fichier <code>tools/wingraph/graph/tetrano_win.bin</code> du dépôt) : 2 519 positions avec le coup à jouer, vérifiées en rejouant la stratégie contre toutes les défenses. Partout ailleurs, la stratégie applique « gagner, bloquer, suite de menaces ».",
      "From Tetrano's <b>forced-win graph</b> (file <code>tools/wingraph/graph/tetrano_win.bin</code> in the repository): 2,519 positions with the move to play, checked by replaying the strategy against every defence. Everywhere else the strategy applies “win, block, threat sequence”.")}</p>
<p>{t("Pour le second joueur, chaque position de cette stratégie a été notée par le nombre de coups que le premier joueur doit encore trouver avant que sa victoire ne devienne mécanique ; la meilleure défense maximise ce nombre (à égalité : un coup qui ne menace pas, puis l'estimation du réseau de l'appli). Ces mesures valent contre cette stratégie gagnante ; un premier joueur qui gagne autrement peut demander d'autres réponses.",
      "For the second player, every position of this strategy was scored by how many moves the first player still has to find before their win becomes mechanical; the best defence maximises that number (on ties: a non-threatening move, then the app network's estimate). These measures hold against this winning strategy; a first player winning another way may call for other replies.")}</p>
</section>'''


CSS = open(os.path.join(HERE, 'guide.css'), encoding='utf-8').read()
JS = open(os.path.join(HERE, 'guide.js'), encoding='utf-8').read()


# The app's Strategy screen: the same guide, in the app's black and yellow, without the language switch.
APP = False
APP_CSS = """
:root[data-theme="dark"].app {
  --bg: #000000;
  --surface: #141414;
  --text: #ffffff;
  --muted: #b4b4b4;
  --border: #5a5a5a;
  --accent: #ffff32;
  --accent-soft: #33330f;
}
.app h1, .app h2, .app h3 { color: var(--accent); }
.app main { padding-top: 8px; }
"""


def build(lang, app=False):
    global LANG, APP
    LANG = lang
    APP = app
    second, def_summary = section_second()
    import balanced
    balanced.G = sys.modules[__name__]
    body = (section_intro() + section_strong() + section_first() + second + balanced.section() + section_summary(def_summary)
            + section_method())
    title = t('Tetrano et Qubic : guide des débuts de partie', 'Tetrano and Qubic: opening guide')
    return f'''<!doctype html>
<html lang="{lang}"{' data-theme="dark" class="app"' if app else ''}>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>{CSS}{APP_CSS if app else ''}</style>
</head>
<body>
{defs()}<main>{body}</main>
<script>{JS}</script>
</body>
</html>'''


if __name__ == '__main__':
    for lang in ('fr', 'en'):
        open(os.path.join(OUT, f'tetrano-guide-{lang}.html'), 'w', encoding='utf-8').write(build(lang))
        app_dir = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'strategy')
        os.makedirs(app_dir, exist_ok=True)
        open(os.path.join(app_dir, f'strategy-{lang}.html'), 'w', encoding='utf-8').write(build(lang, app=True))
    print('ok')
