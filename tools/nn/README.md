# Réseau de neurones du niveau 101

Le niveau 101 de Tetrano (Qubic 4×4×4) est un réseau de neurones guidant une recherche
arborescente (MCTS Gumbel) doublée d'une tactique exacte (suites de menaces forcées, VCF).
Il joue les deux rôles et sert à la revue de partie (visionneuse : menu « Analyser la partie »,
et bouton « Revoir la partie » en fin de partie).

## Pièces

| Où | Quoi |
|---|---|
| `engine/.../engine/nn/` | Kotlin, partagé par l'appli et l'auto-jeu : géométrie (192 symétries), tactique/VCF, `QubicNet` (inférence), `GumbelMcts`, `NeuralPlayer`, `GameReview` |
| `selfplay/` | outil JVM : `selfplay`, `arena`, `vslegacy`, `bench` |
| `tools/nn/` | Python/PyTorch : `model.py` (référence du réseau), `train.py`, `loop.py` (boucle de générations), `export.py`, `data.py`, `qubic.py` |
| `app/src/main/res/raw/tetrano_nn.bin` | le modèle embarqué (~390 Ko) |

Réseau : passage de messages cases ↔ lignes (76 lignes, 64 cases), poids partagés ⇒ invariant par
les 192 automorphismes du plateau ; sorties = un logit par case + P(victoire/nul/défaite) du joueur
au trait. H=48, 6 couches, ~97 000 paramètres.

## Installation (une fois)

```
cd tools/nn
python -m venv .venv
.venv/Scripts/python -m pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv/Scripts/python -m pip install numpy pytest
```

## Entraîner / reprendre

Depuis la racine du dépôt, construire l'outil d'auto-jeu (à refaire si le code Kotlin change) :

```
./gradlew :selfplay:installDist
```

Puis, depuis `tools/nn`, lancer ou **reprendre** (même commande : l'état est dans `runs/main/state.json`,
la génération interrompue est refaite) :

```
.venv/Scripts/python -u loop.py --run runs/main
```

Options utiles : `--stop-at 2026-10-10T23:00` (arrêt propre à l'heure dite, heure locale),
`--max-generations N`. Les autres réglages (parties par génération, simulations, fenêtre…) sont
mémorisés dans `runs/main/config.json` au premier lancement ; une option passée en ligne de
commande les remplace. Le processus empêche la mise en veille automatique de Windows tant qu'il
tourne (pas un capot fermé).

## Suivi

- `runs/main/log.txt` : journal ; `runs/main/metrics.csv` : une ligne par génération.
- Colonnes clés : `firstWins/secondWins/draws` (auto-jeu), `fresh_value_loss` (perte de valeur sur
  les nouvelles parties avant entraînement), `arena_score` (contre la génération −5, 0,5 = égalité),
  `legacy_score` (contre l'ancien niveau 100 ; `legacy_first`/`legacy_second` = V/N/D selon le rôle).

## Mettre un modèle dans l'appli

```
cp runs/main/models/gen_XXXX.bin ../../app/src/main/res/raw/tetrano_nn.bin
```

## Tests

- `.venv/Scripts/python -m pytest` : géométrie et invariance par symétrie du modèle de référence.
- `./gradlew :engine:test` : parité Kotlin ↔ PyTorch (fixtures dans
  `engine/src/test/resources/nn/`, régénérables par
  `python export.py --random --hidden 16 --layers 2 --out …/fixture_net.bin --fixture …/fixture_positions.bin`),
  invariance, tactique, recherche.

## Limites

Le réseau estime, il ne prouve pas : le niveau 101 n'est pas garanti imbattable. Le Qubic étant gagné
pour le premier joueur, une garantie quand l'ordinateur commence passerait par une preuve guidée par
le réseau (voir `tools/qubic-solver/NOTES.md`).
