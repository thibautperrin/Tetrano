# Guide des débuts de partie (HTML)

Génère `docs/guide/tetrano-guide-fr.html` et `docs/guide/tetrano-guide-en.html` : un guide pour les
joueurs de Tetrano et de Qubic 4×4×4 (meilleurs coups en premier et en second, plateaux dessinés,
parties rejouables coup par coup). Chaque page est autonome (CSS, JS et SVG intégrés). Le même
script écrit la version de l'appli (écran « Stratégie ») : `app/src/main/assets/strategy/strategy-{fr,en}.html`,
en noir et jaune, sans le lien vers l'autre langue.

```
python tools/guide/build_guide.py
```

## Fichiers

| Fichier | Rôle |
|---|---|
| `build_guide.py` | textes FR / EN et mise en page |
| `boards.py` | dessin SVG du plateau (géométrie de `BoardDrawing.kt`, anneaux plus grands pour la lisibilité) et vue Qubic par étages |
| `guide.css`, `guide.js` | style (clair / sombre, impression) et navigation des parties |
| `wg.py` | lecture du graphe gagnant, 76 alignements, 192 symétries, forme canonique (en Python) |
| `extract_attack.py` | produit `attack.json` (premier joueur) à partir du graphe |
| `attack.json` | réponses au coup 2, classes de réponses, partie commentée, alignements |
| `defence.json` | analyse du second joueur (voir ci-dessous) |
| `balanced.py`, `handicap.json` | chapitre « partie équilibrée » : parties entre deux joueurs forts après un premier coup sur une place faible |

## Les données

Le graphe gagnant vit sur la branche `claude/tetrano-winning-strategy-graph-7e09e9`
(`tools/wingraph/graph/tetrano_win.bin`) :

```
git show origin/claude/tetrano-winning-strategy-graph-7e09e9:tools/wingraph/graph/tetrano_win.bin > win.bin
python tools/guide/extract_attack.py win.bin
```

`defence.json` vient d'un outil Kotlin lancé sur cette branche (module `:wingraph`, réseau
gen 364 de l'appli) : parcours complet de la stratégie gagnante (1,6 million de positions), note de
chaque position par le nombre maximal de **décisions réelles** qui restent au premier joueur
(positions du graphe : ni gain immédiat, ni parade forcée, ni suite de menaces), ligne de défense
qui maximise cette note, et, à chaque décision de cette ligne, preuve df-pn (un million de
positions au plus) de chaque coup possible du premier joueur : toujours gagnant, perdant (plus de
gain forcé), ou inconnu.

`handicap.json` vient de `selfplay` (commande `HandicapMain`, fichier
`selfplay/src/main/kotlin/fr/thibautperrin/tetrano/selfplay/Handicap.kt`) : parties réseau + MCTS
contre lui-même à partir d'un premier coup imposé, avec du bruit de Gumbel à la racine pour varier
les parties, plus une recherche profonde de la position après le premier coup :

```
gradlew :selfplay:installDist
java -cp "selfplay/build/install/selfplay/lib/*" fr.thibautperrin.tetrano.selfplay.HandicapMain model=app/src/main/res/raw/tetrano_nn.bin first=1 games=400 sims=400 threads=14 out=weak_games.txt
```
