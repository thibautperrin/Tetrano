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

## Entraînement du week-end + tournoi (une seule commande)

```
cd tools/nn
.venv/Scripts/python -u weekend.py
```

- Entraîne jusqu'au `--train-until` (défaut 2026-10-11T18:00) dans `runs/weekend`, en partant
  de la génération 198 (`runs/main`), puis lance le tournoi jusqu'au `--tournament-until`
  (défaut 2026-10-11T20:00). Relancer la même commande reprend à la bonne étape.
- Auto-jeu : chaque partie commence par 0 à 5 coups aléatoires (non appris) et est jouée jusqu'au
  bout ; le réseau a une 3e tête (plis restants, format `QNET` v2) qui pousse le gagnant à
  conclure vite et le perdant à résister (désactivée pour la revue).
- Taux d'apprentissage : 5e-4 jusqu'à 40 % du temps, puis cosinus jusqu'à 5e-5 à l'heure d'arrêt.
- Aucun match pendant l'entraînement : le seul banc d'essai est le tournoi final (un contrôle Elo
  périodique reste possible avec `loop.py --elo-every N --elo-reference <modèle>`).
- Tournoi (`tournament.py`) : la 198 (Elo 1000), la dernière génération et 5 intermédiaires
  (1/6 … 5/6 du temps de calcul cumulé) ; mode A plateau vierge, mode B 5 coups aléatoires
  (mêmes ouvertures pour toutes les paires, chacune jouée dans les deux sens) ; 128 simulations,
  bruit sur les 4 premiers coups de chaque réseau. Rapport : `runs/weekend/tournament/report.md`.

## Nuit à 512 simulations sur ouvertures disputées (une seule commande)

```
cd tools/nn
.venv/Scripts/python -u night.py
```

- Attend `--start`, affine depuis le point de contrôle 480 du week-end (poids + état d'Adam)
  jusqu'au `--train-until` dans `runs/night512`, puis duel 480 contre la dernière génération
  jusqu'au `--duel-until`. Relancer la même commande reprend à la bonne étape.
- Auto-jeu : 512 simulations, 1000 parties par génération, toujours 5 coups aléatoires, gardés
  seulement si l'évaluation directe du réseau (sans recherche) donne au joueur au trait 20 à 80 %
  de victoire (option `disputed=0.2,0.8` de `selfplay`) ; sinon nouveau tirage.
- La fenêtre démarre avec les 5 dernières générations de données du week-end (pour ne pas
  sur-apprendre les premières parties), qui en sortent après une quinzaine de générations ;
  toutes les données sont gardées (`--keep-data 1`) pour entraîner d'autres réseaux plus tard.
- Duel (128 simulations, chaque ouverture jouée dans les deux sens) : mode B (mêmes ouvertures
  aléatoires que le tournoi du week-end) et mode C (ouvertures disputées selon la 480).
  Rapport : `runs/night512/duel/report.md`.

## Suivi

- `runs/main/log.txt` : journal ; `runs/main/metrics.csv` : une ligne par génération.
- Colonnes clés : `firstWins/secondWins/draws` (auto-jeu), `fresh_value_loss` (perte de valeur sur
  les nouvelles parties avant entraînement), `arena_score` (contre la génération −5, 0,5 = égalité),
  `legacy_score` (contre l'ancien niveau 100 ; `legacy_first`/`legacy_second` = V/N/D selon le rôle).

## Mettre un modèle dans l'appli

```
cp runs/<run>/models/gen_XXXX.bin ../../app/src/main/res/raw/tetrano_nn.bin
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
