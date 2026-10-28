# Réseau de neurones du niveau 101

Le niveau 101 de Tetrano (Qubic 4×4×4) est un réseau de neurones guidant une recherche
arborescente (MCTS Gumbel) doublée d'une tactique exacte (suites de menaces forcées, VCF).
Il joue les deux rôles (nombre de variations testées par coup réglable dans les paramètres de
l'appli, 512 par défaut) et sert à l'analyse des parties (écran « Analyser les parties » : revue des
coups, analyse en continu de la position affichée, variantes, plateau vide). Les puzzles du mode
« Puzzles » sont tirés des parties d'auto-jeu (voir « Puzzles » plus bas).

## Pièces

| Où | Quoi |
|---|---|
| `engine/.../engine/nn/` | Kotlin, partagé par l'appli et l'auto-jeu : géométrie (192 symétries), tactique/VCF, `QubicNet` (inférence), `GumbelMcts`, `NeuralPlayer`, `GameReview` |
| `engine/.../engine/puzzle/` | `WinSolver` (gain forcé exact en N coups), `Puzzle` (format, arbre des solutions), `PuzzleAttempt` |
| `selfplay/` | outil JVM : `selfplay`, `arena`, `vslegacy`, `bench`, `match`, `puzzles` |
| `tools/nn/` | Python/PyTorch : `model.py` (référence du réseau), `train.py`, `loop.py` (boucle de générations), `pretrain.py`, `autotrain.py`, `export.py`, `data.py`, `qubic.py` |
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

## Réseau 64 × 10 : pré-entraînement et entraînement autonome

Un réseau plus gros (H=64, 10 couches, ~285 000 paramètres, fichier de 1,1 Mo) entraîné « quand le
PC ne sert pas », en une commande qui reprend toujours là où elle s'est arrêtée (`autotrain.py`,
dossier `runs/big`) :

1. **Pré-entraînement** (`pretrain.py`) : réseau neuf entraîné en supervisé sur les parties déjà
   jouées (night512 `gen_0000+` et week-end, ~3,4 M positions ; les 2 dernières générations night512
   sont gardées pour le test). AdamW, lots de 1024, lr 1e-3 (montée sur 500 pas) puis cosinus
   jusqu'à 2e-5 en 24 000 pas (~7 époques), mêmes pertes que `train.py`. `--extra-data runs/main/data
   --extra-last N` ajoute d'anciennes parties (plus faibles) — à donner au premier lancement.
2. **Auto-jeu sans fin** (`loop.py` sur `runs/big`, depuis `pretrain/pretrain.pt`), entraînement
   seulement (aucun match de test) : 512 simulations, 500 parties par génération jouées par tranches
   de 100 (jusqu'à la génération 336 ; depuis, voir « Recherche adaptative » ci-dessous), toutes les
   données gardées ; fenêtre 300 000 positions + 10 000 par génération
   (max 1,5 M), démarrée avec les 10 dernières générations de night512 (copiées en `gen_-00NN.bin`) ;
   lr 2e-4 jusqu'à la génération 60 puis cosinus jusqu'à 2e-5 à la génération 300 (puis constant).
   500 parties plutôt que 1000 : une génération du 64 × 10 coûte déjà ~3 fois plus cher que celle
   du 48 × 6, et le réseau est mis à jour deux fois plus souvent.

### Ouvertures de l'auto-jeu (`openings=schedule`)

Pour la diversité, les parties suivent un calendrier par cycles de 10 000 (`selfplay/.../Openings.kt`,
résumé d'un cycle : commande `selfplay openings`) :

| Parties / 10 000 | Départ |
|---|---|
| 200 | plateau vierge |
| 8 | un mauvais premier coup (une des 48 cases sur 4 alignements seulement) |
| 35 | 2 coups : chacune des 35 positions distinctes à symétrie près, une fois |
| 757 | 3 coups : chacune des 757 positions distinctes à symétrie près, une fois |
| 2000 / 2000 | 4 / 5 coups aléatoires |
| 2000 | 6 coups aléatoires, sans coup forcé au départ |
| 1000 / 1000 / 500 / 500 | 7 / 8 / 9 / 10 coups, sans coup forcé, ni partie finie ou gagnée d'avance (VCF) |

À partir de 4 coups, 80 % de chaque groupe sont « disputés » : l'évaluation directe du réseau donne
au premier joueur 20 à 80 % de victoire (sinon nouveau tirage). Les positions à 1, 2 et 3 coups sont
présentées sous une symétrie tirée au hasard. Chaque groupe est étalé régulièrement sur le cycle
(deux parties consécutives n'ont jamais le même nombre de coups d'ouverture ; une partie sur plateau
vierge toutes les ~50) et l'ordre change à chaque cycle. La partie n° i du run a toujours la case i du
calendrier (`schedule_games` dans `state.json`), quel que soit le découpage en tranches.

### Démarrer, arrêter

- **Démarrer / reprendre** : double-clic sur `tools/nn/autotrain.bat` (fenêtre « Tetrano autotrain »,
  priorité basse, pas de mise en veille automatique tant qu'elle tourne). En ligne de commande :
  `.venv/Scripts/python -u autotrain.py [--until 07:30]` (`--until` : arrêt propre à l'heure dite,
  `HH:MM` ou date ISO).
- **Arrêter** : fermer la fenêtre, Ctrl+C, ou double-clic sur `autotrain-stop.bat` (tue l'arbre de
  processus noté dans `runs/big/autotrain.pid` ; sans effet si rien ne tourne). Les processus
  java/python enfants sont dans un « job » Windows : ils meurent avec le processus principal, quelle
  que soit la façon dont il s'arrête.
- **Où en est-on ?** `.venv/Scripts/python autotrain.py --status` : phase, pas du pré-entraînement
  ou génération en cours, dernières pertes, avancement du calendrier d'ouvertures.

### Reprise

Relancer la même commande suffit, rien n'est perdu au-delà de quelques minutes :

- pré-entraînement : état complet (poids, Adam, pas, générateurs aléatoires) dans
  `runs/big/pretrain/state.pt` tous les 250 pas (~4 min) ; une reprise est équivalente à un
  entraînement sans interruption ; une fois fini (`pretrain/done.json`) il n'est plus refait ;
- auto-jeu : les tranches de 100 parties finies sont gardées (`data/partial/`), seule la tranche en
  cours est rejouée ; l'entraînement d'une génération (~1 min) est refait.

Tous les fichiers sont écrits dans un fichier temporaire puis renommés : un arrêt brutal (fenêtre
fermée, `taskkill /F`, coupure) laisse un état cohérent.

### Suivi

| Fichier | Contenu |
|---|---|
| `runs/big/log.txt` | journal de toutes les phases |
| `runs/big/pretrain/report.md`, `curve.csv` | pré-entraînement : pertes entraînement/test tous les 1000 pas (et celles de gen 46 sur le même test pour comparer) |
| `runs/big/metrics.csv` | une ligne par génération d'auto-jeu |
| `runs/big/models/gen_XXXX.bin` | le modèle de chaque génération |

### Mettre un modèle 64 × 10 dans l'appli

```
cp runs/big/models/gen_XXXX.bin ../../app/src/main/res/raw/tetrano_nn.bin
```

(`runs/big/pretrain/pretrain.bin` est aussi un modèle valide.) Le format `QNET` v2 porte H et le
nombre de couches : rien d'autre à changer, mais une simulation coûte ~3 fois plus cher sur le
téléphone qu'avec le 48 × 6.

### Durées estimées (ce PC)

- Pré-entraînement : 0,9 à 1,1 s par pas mesuré pour le 64 × 10 (0,39 s pour le 48 × 6) ⇒
  24 000 pas ≈ **7 h** (évaluations comprises, ~35 s chacune).
- Auto-jeu : une simulation MCTS Kotlin coûte ~3,0 ms pour le 64 × 10 (extrapolé de 0,97 ms en
  48 × 6 et 2,33 ms en 64 × 8 ; `selfplay bench` : 3,1 ms contre 0,96 ms pour gen 46), soit ~3,1 fois
  le 48 × 6. night512 jouait 1000 parties à 512 simulations en ~800 s ⇒ 500 parties du 64 × 10
  ≈ 21 min (une tranche de 100 ≈ 4 min), + ~1 min d'entraînement : **~22 min par génération**,
  ~2,7 générations par heure, ~65 par 24 h de calcul. Palier de lr (60 générations) ≈ 23 h de calcul, fin de la descente
  (300 générations) ≈ 4,7 jours.
- Disque : ~2,3 Mo de données et 1,1 Mo de modèle par génération (+ un point de contrôle de
  3,4 Mo toutes les 10).

### Recherche adaptative et symétries (depuis la génération 337)

- **Symétries** : `GumbelMcts` regroupe les coups équivalents par une des 192 symétries qui laissent
  la position inchangée (toutes à la racine, puis celles du parent qui fixent le coup joué) : un seul
  coup par classe dans l'arbre (prior sommé), le coup joué est une case de la classe tirée au hasard,
  et la cible de politique est répartie à égalité sur la classe. Le réseau est exactement
  équivariant (vérifié : écarts de 1e-7), donc rien n'est perdu.
- **Budget adaptatif** (`searchAdaptive`, `adaptive=1`) : 64 simulations, puis 128, 256… jusqu'à
  2048 tant que la recherche hésite, c'est-à-dire jusqu'à ce que le même coup (à symétrie près) soit
  choisi 4 tours de suite, ou 2 seulement si la position est décidée (|Q| ≥ 0,8). Règle choisie avec
  `selfplay rounds` + `adaptive_rules.py` sur 1000 positions de la battle (gros réseau), contre une
  recherche de 2048 : regret 0,0046 pour 468 simulations en moyenne, là où une recherche fixe en
  demande ~950 (0,0109 à 512, 0,0037 à 1024). Une règle « le meilleur coup devance les autres de X »
  était pire qu'un budget fixe (tout ou rien : arrêt à 64 à tort, ou 2048 quand plusieurs coups se
  valent).
- **Cibles de politique** (`policy_min_sims=256`) : seules les recherches d'au moins 256 simulations
  donnent une cible de politique ; toutes donnent la valeur.
- **Une génération = un appel java** (`stream=1`, `resume=1`) : les parties sont écrites une par
  une (`data/partial/gen_XXXX_stream.bin` + `.idx`), un arrêt ne perd que les parties en cours et la
  reprise joue les manquantes : plus d'attente en fin de tranche.
- Les 546 796 positions de la battle (`gen_0336_battle.bin`) sont dans la fenêtre d'entraînement.

### Options ajoutées à `loop.py`

`--stream 1` (une génération = un appel java repris partie par partie) ; `--adaptive 1
--policy-min-sims N` (recherche adaptative jusqu'à `--sims`) ; `--chunk-games N` (auto-jeu par
appels java de N parties, reprise par tranche ; 0 = un seul appel, comme avant) ; `--lr-hold-gens A --lr-decay-gens B` (lr par génération : palier jusqu'à A,
cosinus jusqu'à `lr_final` en B, quel que soit `--stop-at` ; 0 = comportement d'avant, lié à
l'heure d'arrêt). Les anciens runs et `config.json` restent valides.

## Positions prouvées (affinage de la nuit du 28 octobre 2026)

Les deux graphes gagnants (`tools/wingraph/graph/`, voir `tools/wingraph/README.md`) donnent des
positions dont la valeur est **certaine** : en rejouant la stratégie contre toutes les défenses,
chaque position où l'attaquant est au trait est gagnée pour lui, chaque position où le défenseur est
au trait est perdue pour lui, et aux nœuds du graphe le coup joué est un coup gagnant prouvé.

1. **Export** (`wingraph export`, drapeau « exact » du format) : toutes les positions du parcours du
   graphe du coin (taux 0,5) et 12 % de celles du graphe du premier coup faible, soit 4,9 M positions
   (`runs/proven_data/corner.bin`, `weak.bin`). Les positions à gain immédiat ou face à une double
   menace sont écartées ; pas de cible « plis restants ».
   ```
   java -cp "wingraph/build/install/wingraph/lib/*" fr.thibautperrin.tetrano.wingraph.MainKt export graph=tools/wingraph/graph/tetrano_win.bin out=…/corner.bin rate=0.5
   java -Xmx16g -cp "…" …MainKt export graph=tools/wingraph/graph/tetrano_win_weak.bin start=1 out=…/weak.bin rate=0.12
   ```
2. **Pourquoi les mélanger** : seules, ces positions biaiseraient le réseau (aucune nulle, le premier
   joueur gagne toujours ; il finirait par jouer n'importe quoi en second, toutes ses positions
   étant « perdues »). Chaque lot mêle donc des positions prouvées et les parties d'auto-jeu récentes
   (`train.train(..., extra=, extra_frac=)`).
3. **Affinage** (`proven.py`) : départ gen 364 (`runs/big`), 6 000 pas de 1024, 35 % de positions
   prouvées, lr 1e-4 → 1e-5 ; fenêtre = les 1,5 M dernières positions d'auto-jeu de `runs/big`
   (gen 364 gardée pour le test), 3 % des positions prouvées gardées pour le test. Rapport avant /
   après : `runs/proven_ft/report.md`.
4. **Auto-jeu** (`loop.py --proven … --proven-frac 0.25`, dossier `runs/proven`) : générations de
   500 parties comme `runs/big` (recherche adaptative jusqu'à 2048, calendrier d'ouvertures),
   25 % de positions prouvées dans chaque lot, fenêtre fixe de 1,5 M positions amorcée avec celle de
   `runs/big` (`gen_-0001.bin`), lr 5e-5 → 1e-5 jusqu'à l'heure d'arrêt.
5. **Match** contre gen 364 (`selfplay match`, ouvertures aléatoires jouées dans les deux sens).

Toute la chaîne, reprise étape par étape : `bash proven_night.sh` depuis le `tools/nn` qui contient
`runs/` (variable `STOP` : heure d'arrêt de l'auto-jeu).

## Battle petit réseau contre gros réseau, PC et téléphones (`battle.py`)

`battle.bat` (ou `.venv/Scripts/python -u battle.py [--until 05:00]`) enchaîne, en reprenant là où
il s'est arrêté (`runs/battle/state.json`) :

1. **Pré-entraînement du petit réseau** (48 × 6, gen 46 de l'appli) sur les parties à 512
   simulations du gros réseau (`runs/big/data` gen 0-334, gen 335-336 gardées pour le test) :
   6000 pas de 1024, lr 1e-4 → 1e-5 (≈ 40 min) → `runs/battle/small_ft/small_ft.{pt,bin}`.
2. **Battle** jusqu'à `--until` (fixé au premier lancement) : `selfplay battle` (voir
   `selfplay/.../Battle.kt`), A = petit réseau à 2048 simulations, B = gros réseau (gen 336) à
   512, recherche du niveau 101 de l'appli. Chaque ouverture du calendrier de l'auto-jeu
   (`Openings.kt`, même répartition sur 10 000, graine 20261020 ; « disputée » = moyenne des deux
   réseaux entre 20 et 80 %) est jouée deux fois (A premier joueur, puis B). PC : 14 parties en
   parallèle. Téléphones adb (y compris branchés en cours de route) : le tool selfplay en dex
   (`d8` sur les jars de `installDist` → `runs/battle/phone/classes.dex`) lancé par
   `app_process` dans `/data/local/tmp/tetrano_battle`, une partie à la fois comme dans l'appli
   (le téléphone doit rester allumé sur secteur : option développeur « Rester activé »). Après
   l'heure, plus de nouvelle paire ; celles en cours se terminent.
3. **Analyse** → `runs/battle/report.md` : score et Elo par appareil et par type d'ouverture,
   paires de même issue, temps par coup (moyenne, médiane, 90e centile, ms par simulation).
4. **Entraînement** des deux réseaux sur les positions des paires dont les deux parties ont la
   même issue (`same.bin`, 5 % gardés pour le test), 2 passages, lr 3e-5 (petit) / 2e-5 (gros)
   → `runs/battle/after/` ; rien n'est mis dans l'appli ni dans `runs/big`.

`battle.py --status` pour le suivi, `battle-stop.bat` (ou `--stop`) pour tout arrêter, téléphones
compris.

## Puzzles

`app/src/main/res/raw/puzzles.bin` est produit par la commande `puzzles` de l'outil d'auto-jeu, à
partir des parties enregistrées :

```
java -Xmx6g -cp "../../selfplay/build/install/selfplay/lib/*" fr.thibautperrin.tetrano.selfplay.MainKt puzzles     data=runs/night512/data,runs/weekend/data model=runs/night512/models/gen_0046.bin     out=runs/puzzles/puzzles.bin games=6000 threads=6 count=3000 seed=20261015 report=runs/puzzles/report.md
cp runs/puzzles/puzzles.bin ../../app/src/main/res/raw/puzzles.bin
```

1. Dans chaque partie, en remontant depuis la fin : les positions où le futur gagnant, au trait,
   gagne de force en au plus `max_moves` coups (5), prouvé par `WinSolver` (recherche exacte, coups
   « calmes » compris, élaguée par la monotonie : un anneau de plus ne nuit jamais à son camp).
2. L'arbre complet des solutions : tous les coups gagnants dans le nombre de coups annoncé, et pour
   chacun la défense la plus tenace (celle qui retarde le plus le gain, à égalité celle que le réseau
   préfère). Puzzle écarté s'il a plus de `max_lines` (4) solutions distinctes ou plus de
   `max_first` (2) premiers coups gagnants : l'appli stocke toutes les variantes correctes.
3. Elo du puzzle : probabilité exacte que des « solveurs » jouant au hasard selon la politique du
   réseau (températures ∞, 4, 2, 1, 0,5, 0,25) trouvent un bon coup à chaque étape ; ajustement
   logistique (modèle de Rasch) des solveurs et des puzzles, médiane des puzzles à 1500, bornes
   400–2800. Plus le gain est long ou le coup peu « naturel », plus l'Elo monte.
4. Un puzzle par partie au plus, aucun doublon à symétrie près, ~`count` puzzles répartis
   uniformément par tranches de 100 points d'Elo. Rapport : `runs/puzzles/report.md`.

Dans l'appli, le joueur part à 1200 ; on lui propose un puzzle non encore tenté parmi les 12 dont
l'Elo est le plus proche de (son Elo − 191), soit 75 % de réussite attendue ; seul le premier essai
compte (K = 40 pendant 20 puzzles, puis 20), sous une symétrie du plateau tirée au hasard.

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
