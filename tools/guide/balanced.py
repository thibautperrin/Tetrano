"""The balanced-game chapter: the first player must open on a weak place (data: handicap.json)."""
import json
import os

import build_guide as G
from boards import board
from wg import LINES, threats

HAND_PATH = os.path.join(G.HERE, 'handicap.json')
HAND = json.load(open(HAND_PATH)) if os.path.exists(HAND_PATH) else None


def t(fr, en):
    return G.t(fr, en)


def wilson(k, n, z=1.96):
    """95% Wilson interval of a proportion, in percent."""
    if n == 0:
        return 0.0, 0.0
    p = k / n
    den = 1 + z * z / n
    mid = (p + z * z / (2 * n)) / den
    half = z * ((p * (1 - p) / n + z * z / (4 * n * n)) ** 0.5) / den
    return max(0.0, 100 * (mid - half)), min(100.0, 100 * (mid + half))


def outcome_bar(first, draw, second):
    """A stacked bar of percentages: first player, draw, second player."""
    parts = [(first, 'seg-red'), (draw, 'seg-grey'), (second, 'seg-blue')]
    return ('<div class="stack">' + ''.join(
        f'<span class="{c}" style="width:{p:.1f}%">{G.pct(p) if p >= 9 else ""}</span>' for p, c in parts) + '</div>')


def commentary(moves):
    """Automatic comments for a game: threats, forced blocks, forks, wins; the line in question in yellow."""
    frames, rings = [], {}
    for i, c in enumerate(moves):
        p = 'A' if i % 2 == 0 else 'D'
        me = sum(1 << x for x, q in rings.items() if q == p)
        op = sum(1 << x for x, q in rings.items() if q != p)
        blocked = threats(op, me) >> c & 1
        me2 = me | 1 << c
        rings[c] = p
        won = [ln for ln in LINES if ln >> c & 1 and ln & me2 == ln]
        mine3 = [ln for ln in LINES if ln >> c & 1 and not ln & op and bin(ln & me2).count('1') == 3]
        hl = 0
        if won:
            text, hl = t('<b>Gagné</b> (en jaune).', '<b>Won</b> (in yellow).'), won[0]
        elif len(mine3) >= 2:
            text = t('<b>Fourchette</b> : deux menaces à la fois (en jaune).', '<b>Fork</b>: two threats at once (in yellow).')
            for ln in mine3:
                hl |= ln
        elif blocked and mine3:
            text, hl = (t('Il bloque une menace <b>en menaçant</b> à son tour (en jaune).',
                          'Blocks a threat <b>while threatening</b> in turn (in yellow).'), mine3[0])
        elif blocked:
            text = t('Forcé : il bloque une menace.', 'Forced: blocks a threat.')
        elif mine3:
            text, hl = t('Menace (en jaune).', 'Threat (in yellow).'), mine3[0]
        elif c in G.DATA['strong']:
            text = t('Coup de construction, sur une place forte.', 'Building move, on a strong place.')
        else:
            text = t('Coup de construction.', 'Building move.')
        if i == len(moves) - 1 and not won:
            text += t(' Le plateau est plein : <b>match nul</b>.', ' The board is full: <b>a draw</b>.')
        who = t('Premier joueur', 'First player') if p == 'A' else t('Second joueur', 'Second player')
        head = f'<b>{t("Coup", "Move")} {i + 1} · <span class="{"red" if p == "A" else "blue"}">{who}</span></b><br>'
        frames.append({'rings': dict(rings), 'num': {c: i + 1},
                       'hl': [x for x in range(64) if hl >> x & 1] or None, 'text': head + text})
    return frames


def _shares(r):
    n = r['games']
    return 100 * r['firstWins'] / n, 100 * r['draws'] / n, 100 * r['secondWins'] / n


def _row(name, r):
    n = r['games']
    f, d, s = _shares(r)
    cells = ''
    for k in ('firstWins', 'draws', 'secondWins'):
        lo, hi = wilson(r[k], n)
        cells += f'<td class="num">{G.pct(100 * r[k] / n)} <span class="muted">({lo:.0f}–{hi:.0f})</span></td>'
    return f'<tr><td>{name}</td><td>{outcome_bar(f, d, s)}</td>{cells}<td class="num">{n}</td></tr>'


def exact_section():
    ex = HAND.get('exact')
    if not ex:
        return ''
    first = HAND['weak']['first']
    rows = sorted(ex['replies'], key=lambda r: r['cell'])
    figs = [G.fig(board({first: 'A', r['cell']: 'D'}, width=110, labels=False)) for r in rows]
    fmt = (lambda x: f'{x:,}'.replace(',', ' ')) if G.LANG == 'fr' else (lambda x: f'{x:,}')
    ratio = round(ex['graph'] / 2519)
    return f"""
<h3>{t('Le calcul exact : le premier joueur gagne quand même', 'The exact computation: the first player still wins')}</h3>
<p>{t(f"Pour aller au-delà des statistiques, on a lancé sur cette position le même calcul que pour la stratégie gagnante sans handicap : une preuve exacte, qui examine toutes les défenses. Après un premier coup sur une place faible, le second joueur a {len(rows)} réponses vraiment différentes (au lieu de 12 après un coin), les voici. <b>Toutes perdent.</b> Le premier joueur a une stratégie gagnante malgré le handicap : avec un jeu parfait des deux côtés, il gagne 100 % des parties, comme sans handicap.",
      f"To go beyond statistics, the same computation as for the winning strategy without handicap was run on this position: an exact proof, examining every defence. After a first move on a weak place, the second player has {len(rows)} truly different replies (instead of 12 after a corner), shown here. <b>All of them lose.</b> The first player has a winning strategy despite the handicap: with perfect play on both sides, they win 100% of the games, as without the handicap.")}</p>
{G.grid(*figs, cls='figs tiny')}
<p>{t(f"Cette stratégie a été rejouée seule contre toutes les défenses possibles : {fmt(ex['verifiedPositions'])} positions examinées, des parties d'au plus {ex['maxRings']} anneaux, aucune défaite ni nulle. Elle compte <b>{fmt(ex['graph'])} positions</b> où il faut connaître le bon coup, contre 2 519 sans handicap : le handicap rend la victoire environ {ratio} fois plus difficile à mémoriser, et les parties bien plus longues, mais pas impossible.",
      f"This strategy was replayed on its own against every possible defence: {fmt(ex['verifiedPositions'])} positions examined, games of at most {ex['maxRings']} rings, no loss and no draw. It holds <b>{fmt(ex['graph'])} positions</b> where the right move must be known, against 2,519 without the handicap: the handicap makes the win about {ratio} times harder to memorise, and the games much longer, but not impossible.")}</p>
<p>{t("Il a fallu {h} heures de calcul. La dernière réponse à tomber a été le coin voisin du premier anneau : la recherche s'était enfoncée dans une seule ligne de plus de 40 coups ; une fois corrigée pour essayer d'abord les alternatives proches, elle a conclu en une minute.".format(h=ex['hours']),
      "It took {h} hours of computing. The last reply to fall was the corner next to the first ring: the search had dug into a single line over 40 moves deep; once corrected to try nearby alternatives first, it concluded in a minute.".format(h=ex['hours']))}</p>"""


def section():
    if not HAND:
        return ''
    w, c = HAND['weak'], HAND['corner']
    first = w['first']
    n = w['games']
    f_w, d_w, s_w = _shares(w)
    nw = HAND['netWeak']  # second player's view: win, draw, loss
    table = (f'<div class="table-wrap"><table class="results"><thead><tr><th>{t("Premier coup", "First move")}</th><th></th>'
             f'<th class="num">{t("1er gagne", "1st wins")}</th><th class="num">{t("Nul", "Draw")}</th>'
             f'<th class="num">{t("2e gagne", "2nd wins")}</th><th class="num">{t("Parties", "Games")}</th></tr></thead><tbody>'
             + _row(t('Place faible (handicap)', 'Weak place (handicap)'), w)
             + _row(t('Place forte (sans handicap)', 'Strong place (no handicap)'), c)
             + '</tbody></table></div>')
    reply_figs = []
    for k, r in enumerate(HAND['replies'][:4]):
        score = f'{r["value"]:+.2f}'
        if G.LANG == 'fr':
            score = score.replace('.', ',')
        rank = t('Meilleure réponse' if k == 0 else f'{k + 1}e', 'Best reply' if k == 0 else f'#{k + 1}')
        reply_figs.append(G.fig(board({first: 'A', r['cell']: 'D'}, {r['cell']: 2}, width=170), f'{rank} : {score}'
                                if G.LANG == 'fr' else f'{rank}: {score}'))
    by_rows = ''.join(
        f'<tr><td>{board({first: "A", b["cell"]: "D"}, width=96, labels=False)}</td><td class="num">{b["games"]}</td>'
        f'<td>{outcome_bar(*_shares(b))}</td></tr>'
        for b in HAND['byReply'] if b['games'] >= 10)
    by_table = (f'<div class="table-wrap"><table class="replies"><thead><tr><th>{t("Réponse du 2e joueur", "Second player’s reply")}</th>'
                f'<th class="num">{t("Parties", "Games")}</th><th>{t("Résultats", "Results")}</th></tr></thead>'
                f'<tbody>{by_rows}</tbody></table></div>')
    shown = [b for b in HAND['byReply'] if b['games'] >= 10]
    if len(shown) >= 2:
        by_text = by_table
    else:
        top = HAND['byReply'][0]
        by_text = '<p>' + t(f"Dans les parties jouées, le second joueur a choisi la meilleure réponse {top['games']} fois sur {n}.",
                            f"In the games played, the second player chose the best reply {top['games']} times out of {n}.") + '</p>'
    ex = HAND['example']
    res = {0: t('le premier joueur gagne', 'the first player wins'), 1: t('le second joueur gagne', 'the second player wins'),
           -1: t('match nul', 'a draw')}[ex['winner']]
    verdict = t(HAND['verdict_fr'], HAND['verdict_en'])
    return f"""
<section id="equilibre">
<h2>{t('4. Une partie équilibrée : le premier coup sur une place faible', '4. A balanced game: the first move on a weak place')}</h2>
<p>{t("Le premier joueur gagne toujours s'il joue parfaitement. Pour équilibrer une partie, on peut lui imposer d'ouvrir sur l'une des 48 places faibles : petits ou grands anneaux hors des coins, anneaux moyens hors du centre. Les 48 se valent à symétrie près ; voici celle que l'on utilise :",
      "The first player always wins with perfect play. To balance a game, you can require them to open on one of the 48 weak places: small or large rings off the corners, medium rings off the centre. All 48 are equivalent up to symmetry; here is the one used below:")}</p>
{G.grid(G.fig(board({first: 'A'}, {first: 1}, width=220),
              t('Le premier coup imposé : une place faible (4 alignements seulement).', 'The imposed first move: a weak place (only 4 lines).')))}
<div class="callout">{t(f"<b>Comment on estime les chances en pratique.</b> En théorie, la question est tranchée (voir le calcul exact plus bas). Mais aucun joueur ne retient 30 000 positions : pour savoir ce qui se passe entre deux bons joueurs, on a fait jouer {n} parties entre deux joueurs forts : le réseau de neurones de l'appli (celui du niveau le plus fort), guidé à chaque coup par une recherche arborescente (MCTS, {w['sims']} simulations par coup). Un peu d'aléa dans chaque choix fait varier les parties, comme entre deux bons joueurs. Pour comparer, on a refait l'expérience sans handicap (premier coup dans un coin). Entre parenthèses : l'intervalle de confiance à 95 %.",
                         f"<b>How the odds are estimated in practice.</b> In theory the question is settled (see the exact computation below). But no player memorises 30,000 positions: to see what happens between two good players, {n} games were played between two strong players: the app's neural network (the one of the strongest level), guided at every move by a tree search (MCTS, {w['sims']} simulations per move). A little randomness in each choice makes the games differ, as between two good players. For comparison, the experiment was repeated without the handicap (first move in a corner). In brackets: the 95% confidence interval.")}</div>
{table}
<p>{t(f"Avec le handicap, le premier joueur gagne {G.pct(f_w)} des parties et le second {G.pct(s_w)} ; {G.pct(d_w)} se terminent par un match nul (plateau plein). Le réseau seul, sans recherche, estimait la position à {G.pct(100 * nw[2])} de victoires pour le premier joueur, {G.pct(100 * nw[1])} de nulles et {G.pct(100 * nw[0])} pour le second.",
      f"With the handicap, the first player wins {G.pct(f_w)} of the games and the second {G.pct(s_w)}; {G.pct(d_w)} end in a draw (full board). The network alone, without search, rated the position at {G.pct(100 * nw[2])} wins for the first player, {G.pct(100 * nw[1])} draws and {G.pct(100 * nw[0])} for the second.")}</p>
<p>{verdict}</p>
{exact_section()}
<h3>{t('La meilleure réponse du second joueur', 'The second player’s best reply')}</h3>
<p>{t("Une recherche approfondie (20 000 simulations) classe les réponses par un score qui va de −1 (le premier joueur gagne à coup sûr) à +1 (le second gagne à coup sûr) : plus il est haut, mieux c'est pour le second joueur.",
      "A deep search (20,000 simulations) ranks the replies by a score from −1 (the first player surely wins) to +1 (the second surely wins): the higher, the better for the second player.")}</p>
{G.grid(*reply_figs, cls='figs small')}
<p>{t("Comme contre un coin, le second joueur répond au <b>centre</b>. Les réponses sur des places faibles sont nettement plus mauvaises (score proche de −1).",
      "As against a corner, the second player replies in the <b>centre</b>. Replies on weak places are clearly worse (score close to −1).")}</p>
{by_text}
<h3>{t('Une partie entre deux joueurs forts', 'A game between two strong players')}</h3>
<p>{t(f"Une des parties jouées ({res}). Les commentaires sont générés automatiquement.", f"One of the games played ({res}). The comments are generated automatically.")}</p>
{G.stepper(commentary(ex['moves']), 'game-balanced')}
</section>"""
