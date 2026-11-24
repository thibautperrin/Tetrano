# Réseau de neurones — plan d'amélioration pour l'analyse des parties

Rédigé le 2026-11-11, sur la base de `origin/main` (5.1, a66387f). Plan seulement : rien n'est
programmé ni lancé. Les mesures du § 2.3 ont été faites pour ce plan, sur les données de
`tools/nn/runs` (méthode en annexe).

## 0. Résumé

**But.** Les niveaux 102/103 garantissent le gain quand l'ordinateur commence et suit son graphe.
L'analyse, elle, doit juger **n'importe quelle position**, y compris après des erreurs des deux
joueurs : dire qui gagne (et avec quelle certitude) et quels coups conservent ce résultat.

**Constat principal.** Le Qubic est un jeu fini et résolu en principe : chaque position a une valeur
exacte (gagnée / nulle / perdue). Le réseau n'apprend aujourd'hui qu'une approximation bruitée de
cette vérité (l'issue de parties d'auto-jeu), et l'analyse l'utilise avec un biais mesurable. Le
gain n'est pas dans un réseau plus gros (déjà essayé, § 2.2) mais dans **plus de vérité exacte** :
dans la recherche, dans les cibles d'entraînement et dans la mesure.

**Les leviers, par rapport gain / coût décroissant :**

| # | Levier | Réentraînement | Gain attendu sur l'analyse |
|---|---|---|---|
| 1 | Corriger la valeur rendue par la recherche (biais pessimiste mesuré : −0,28) | non | verdicts de la revue justes, courbe sans dents de scie |
| 2 | Solveur exact (df-pn, déjà écrit pour `:wingraph`) dans la recherche et la revue | non | ~40 à 75 % des positions de milieu / fin de partie tranchées avec certitude |
| 3 | Un banc de positions à valeur exacte comme juge de paix (au lieu de l'Elo) | non | on sait enfin ce qu'on améliore |
| 4 | Cibles exactes + positions « humaines » dans l'entraînement | oui | réseau juste là où l'analyse en a besoin |
| 5 | Réseau v3 : tête Q par coup, graphe réduit aux cases libres et lignes vivantes | oui | meilleurs coups proposés, ~1,5× plus rapide en milieu de partie |

**Budget machine** (ce PC, Core Ultra 7 265H, 16 cœurs, pas de GPU CUDA) : ~50 h en version
minimale, 60 à 85 h avec les ablations d'architecture (§ 7), par nuits reprenables. Les leviers 1 à
3 ne demandent aucune heure d'entraînement.

---

## 1. Objectif et critères

Ce qu'on optimise n'est plus la force de jeu (les réseaux se valent : 77 % des paires de la battle
ont la même issue, tous les réseaux du tournoi à 50 % depuis le plateau vierge) mais la **justesse** :

1. **Valeur** : l'estimation V/N/D d'une position doit coller à sa valeur exacte.
2. **Coups** : le coup proposé doit conserver la valeur exacte (garder le gain, garder la nulle),
   de préférence par le chemin le plus court ; les coups qui la perdent doivent être signalés.
3. **Verdicts de la revue** : « erreur » = le coup a changé la valeur exacte ; ni faux positifs
   ni oublis.
4. **Coût** : rester utilisable sur un Pixel 4a (aujourd'hui 3,6 ms par simulation avec le 64 × 10).

Mesure : le banc de vérité du § 3. L'Elo en match (à temps égal) ne sert plus que de garde-fou
contre une régression du niveau 101.

---

## 2. État des lieux

### 2.1 Ce qui existe

| Brique | État |
|---|---|
| Réseau (`tools/nn/model.py`, `engine/nn/QubicNet.kt`) | passage de messages cases ↔ lignes, H = 64, 10 couches, 285 k paramètres, exactement équivariant (192 symétries) ; têtes : politique (1 logit par case), V/N/D, plis restants |
| Entrées | par case : vide / à moi / à l'adversaire + classe de degré ; par ligne : 8 états ; « je suis le premier joueur » |
| Cibles (`tools/nn/train.py`) | valeur = **issue de la partie** (entropie croisée) ; politique = politique améliorée de la recherche (si ≥ 256 simulations) ; plis restants. La valeur de recherche enregistrée (`value`) n'est pas utilisée |
| Auto-jeu (`selfplay/Main.kt`) | Gumbel MCTS adaptatif 64 → 2048, calendrier d'ouvertures (0 à 10 coups, 80 % « disputées »), 500 parties par génération (~25 min), 365 générations (`runs/big`) + 30 (`runs/proven`) |
| Positions prouvées (`tools/nn/proven.py`) | 4,9 M positions des deux graphes gagnants, 25 à 35 % de chaque lot |
| Recherche (`engine/nn/GumbelMcts.kt`) | tactique exacte à chaque nœud (gain immédiat, double menace, blocage forcé, VCF), MCTS-solver, classes de symétrie ; **arbre sans table de transposition** |
| Revue (`GameReview`, `GameReviewService`) | **64 simulations fixes** par position, sans bruit ; verdicts à partir de `Result.value` avant / après ; deux verdicts exacts seulement, quand la tactique (VCF) ou le MCTS-solver a prouvé un gain |
| Analyse en continu (`LiveAnalysisService`) | 1 000 à 20 000 simulations sur un seul fil, V/N/D affiché dérivé de `Result.value` |
| Solveur exact (`wingraph/DfPn.kt`) | df-pn « le premier joueur gagne-t-il ? », hors appli ; guidé par le réseau (`TopTree`, `Prover`) il a prouvé l'ouverture du coin en 16 min et celle de la case faible en ~56 h |
| Graphes gagnants | utilisés par `AiService` (niveaux 102/103) seulement, pas par l'analyse |

### 2.2 Ce que disent les runs existants

- **Le réseau a atteint un palier.** `runs/big` : perte de valeur sur parties fraîches 0,43 → 0,34
  et précision 83 → 86 % en 365 générations, plate depuis la génération ~200.
- **La taille n'est pas le levier.** `runs/explore` : H64 × 8 et H96 × 6 gagnent +12 et +10 Elo à
  simulations égales, perdent −28 et −38 à temps égal. Battle : 48 × 6 à 2048 simulations contre
  64 × 10 à 512 : −8 ± 3 Elo sur 19 178 parties. `capacity_gen46` : le 48 × 6 utilise presque tous
  ses neurones.
- **La recherche rapporte peu au-delà de 512** : +23 / +32 / +38 Elo pour 256 / 512 / 1024
  simulations contre 128.
- **L'issue des parties est bruitée en ouverture.** Dans la battle, les ouvertures disputées de 4 et
  5 coups n'ont la même issue dans les deux parties que 62 à 64 % du temps. Comme la position a une
  seule valeur exacte, de l'ordre de 20 % au moins de ces étiquettes sont fausses.
- **Les positions prouvées s'apprennent sans rien coûter** : 94,9 → 99,4 % de précision sur les
  positions des graphes, jeu égal avec gen 364. Mais elles couvrent une distribution étroite (la
  stratégie de l'attaquant, jamais de nulle, jamais de second joueur gagnant).

### 2.3 Mesures faites pour ce plan (2026-11-11)

Réseau : gen 29 de `runs/proven` (celui de l'appli). Positions : `runs/proven/data`.

**M1 — La valeur rendue par la recherche est pessimiste pour le joueur au trait.** Sur 100 946
paires de positions consécutives d'une même partie (gen 20 à 29), `v(t) + v(t+1)` devrait valoir 0
en moyenne (ce que l'un gagne, l'autre le perd) :

| Plis | 0–7 | 8–11 | 12–15 | 16–19 | 20–27 | 28+ | Tous |
|---|---|---|---|---|---|---|---|
| Moyenne de `v(t) + v(t+1)` | −0,47 | −0,46 | −0,32 | −0,24 | −0,18 | −0,08 | **−0,28** |

52 % des paires sont sous −0,1, 4 % au-dessus de +0,1. Cause : `GumbelMcts.buildResult` rend
`mixedValue(root)`, la moyenne des Q des coups visités **pondérée par la politique a priori**
(formule de complétion des Q de l'article Gumbel, pas une valeur de position) : elle inclut les
mauvais coups essayés. Conséquences : (a) le V/N/D affiché oscille d'un coup à l'autre ; (b) dans
`GameReview.judge`, la perte vaut `(v(t) + v(t+1)) / 2`, soit −0,14 en moyenne pour un coup
correct : une vraie perte de 0,20 s'affiche 0,06 et passe pour « bon » (seuil d'imprécision 0,08,
d'erreur 0,18). La revue **sous-détecte** les erreurs, d'autant plus en début de partie. Ces chiffres
viennent de recherches de ~470 simulations ; la revue n'en fait que 64 (chaque coup considéré visité
une fois au premier tour) : à mesurer, sans doute pire.

**M2 — df-pn tranche une grande part des positions, vite.** 960 positions non prouvées par la
recherche (120 par tranche de plis, gen 28–29), question « le premier joueur gagne-t-il ? », sans
réseau, un fil par position (8 en parallèle) :

| Budget (évaluations) | 4–7 | 8–11 | 12–15 | 16–19 | 20–23 | 24–27 | 28–35 | 36+ | Tous | Temps moyen |
|---|---|---|---|---|---|---|---|---|---|---|
| 3 000 | 2 % | 11 % | 41 % | 57 % | 72 % | 57 % | 47 % | 22 % | 39 % | 19 ms |
| 30 000 | 10 % | 30 % | 57 % | 72 % | 74 % | 62 % | 48 % | 23 % | 47 % | 270 ms |
| 300 000 | 23 % | 37 % | 62 % | 75 % | 74 % | 62 % | 48 % | 22 % | 51 % | médiane 2 à 8 ms entre 16 et 27 plis |

19 ms, c'est le prix d'environ 6 évaluations du réseau. Le taux plafonne avec le budget : les
positions restantes sont surtout celles où il faut prouver un gain du **second** joueur ou une
**nulle** (72 % des positions non prouvées après 28 plis finissent en nulle), ce que le df-pn actuel
ne sait pas faire.

**M3 — Le réseau seul se trompe encore sur des positions tranchées.** Sur les 486 positions
résolues à 300 000 : 6,4 % d'erreurs sur « le premier joueur gagne » (9 à 11 % entre 8 et 15 plis).
Échantillon biaisé vers les positions faciles : les non résolues sont celles où le réseau est le
moins sûr.

**M4 — En milieu de partie, l'issue de l'auto-jeu est fiable.** Sur ces mêmes positions, l'issue de
la partie ne contredit la preuve que dans 0,6 % des cas. Le bruit des étiquettes est donc concentré
avant le 12e pli (M5), là où df-pn seul ne conclut pas.

**M5 — La recherche hésite en début de partie.** Accord entre le signe de la valeur de recherche et
l'issue (parties décidées) : 82 % (plis 0–11), 90 % (12–15), 95 % (16–19), 97 % au-delà. Part des
positions déjà prouvées par la recherche : 0 %, 3 %, 11 %, 25 %, 41 %, 36 % par tranche.

**M6 — Une part croissante du plateau ne compte plus.** Une ligne portant les deux couleurs est
morte pour toujours ; une case occupée n'agit que par les lignes vivantes qui la traversent.

| Plis | 4–7 | 8–11 | 12–15 | 16–19 | 20–23 | 24–27 | 28–35 | 36+ |
|---|---|---|---|---|---|---|---|---|
| Cases libres | 58 | 55 | 51 | 47 | 43 | 39 | 33 | 17 |
| Lignes vivantes (sur 76) | 73 | 69 | 63 | 57 | 51 | 44 | 35 | 11 |
| Nœuds utiles / 140 | 0,94 | 0,88 | 0,81 | 0,74 | 0,67 | 0,59 | 0,48 | 0,20 |

### 2.4 Diagnostic

1. L'analyse affiche et juge avec une valeur biaisée (M1) : c'est le défaut le plus visible et le
   moins cher à corriger.
2. La vérité exacte est accessible pour une grande part des positions à partir du 12e pli (M2) et
   n'est utilisée ni par la recherche, ni par la revue, ni par l'entraînement (hors graphes).
3. Avant le 12e pli, ni la recherche (M5) ni l'issue des parties (§ 2.2) ne sont fiables : c'est là
   que le réseau doit progresser, et là qu'il manque des étiquettes exactes.
4. La distribution d'entraînement est celle de deux joueurs forts après une ouverture aléatoire ;
   une partie humaine contient des erreurs à tous les plis, et des positions très déséquilibrées.
5. Aucun outil ne mesure la justesse : on ne compare que des Elo entre réseaux équivalents.
6. Grossir le réseau ne paie pas à temps égal ; le rendre plus rapide et mieux informé, si.

---

## 3. Phase 0 — Banc de vérité (le juge de paix)

À faire en premier : toutes les décisions suivantes se prennent dessus.

### 3.1 Solveur exact à trois issues

- Déplacer `DfPn` de `:wingraph` vers `:engine` (`engine/nn/ExactSolver.kt`) et le généraliser :
  l'attaquant devient un paramètre (premier **ou** second joueur). `Board.attackerToMove` et
  `candidateMoves` prennent le rôle en argument ; `:wingraph` continue d'utiliser la même classe.
- `ExactSolver.solve(mine, theirs, budget)` → `WIN`, `LOSS`, `DRAW` ou `UNKNOWN` pour le joueur au
  trait : (1) « le joueur au trait gagne-t-il ? » ; si réfuté, (2) « l'adversaire gagne-t-il ? » ;
  deux réfutations = nulle prouvée. Le budget est partagé entre les deux questions.
- Variante guidée (ordre des coups par la politique, pn / dn initiaux par la valeur), en reprenant
  `TopTree` / `NetEval`, pour les positions de moins de 12 plis.
- Tests : accord avec `WinSolver` (gain en N coups), avec `Vcf`, avec les graphes (toute position du
  parcours doit sortir `WIN` / `LOSS`), symétrie des deux rôles sur positions miroir.

### 3.2 Positions du banc (~20 000, trois familles à parts égales)

| Famille | Source | Pourquoi |
|---|---|---|
| A — auto-jeu | 2 000 parties neuves de gen 29 (calendrier d'ouvertures actuel) | continuité avec les mesures existantes |
| B — « humaines » | parties entre niveaux de l'ancienne IA (`Ai.playLevel`, niveaux 10 à 100 tirés au hasard), ancienne IA contre niveau 101, et parties d'auto-jeu avec un coup faible injecté (§ 5.3) ; plus les parties sauvegardées de l'appli si on les exporte | c'est ce que l'analyse voit vraiment |
| C — ouvertures | les 35 positions à 2 coups, les 757 à 3 coups, un tirage à 4–8 coups | zone où tout est le moins fiable |

Étiquetage : `ExactSolver` avec un gros budget (jusqu'à ~2 M d'évaluations, soit ~20 s pour une
position qui résiste ; variante guidée pour les ouvertures). Les positions non résolues sont gardées dans une liste à part, avec la valeur d'une
recherche de 50 000 simulations comme référence « molle », et **toujours rapportées séparément** (le
banc exact est biaisé vers les positions résolubles : le dire dans chaque rapport).

Pour ~2 000 positions, étiqueter aussi **chaque coup légal** (résoudre chaque position fille) : c'est
ce qui permet de dire si un coup proposé conserve la valeur.

Nouvelle commande `selfplay truth` (génération + étiquetage, reprise possible) ; fichier
`runs/truth/bench.bin` au format v3 du § 5.1. Coût : ~6 à 10 h machine, une fois.

### 3.3 Métriques (commande `selfplay judge model=… sims=…`)

| Métrique | Définition |
|---|---|
| Erreur de valeur | part des positions où l'issue la plus probable n'est pas l'exacte ; et moyenne de `1 − P(issue exacte)` |
| Justesse du coup | part des positions où le coup proposé conserve la valeur exacte |
| Verdicts | sur des parties entières étiquetées : précision et rappel de « ce coup a changé la valeur » |
| Cohérence | moyenne et écart-type de `v(t) + v(t+1)` |
| Part prouvée | part des positions rendues avec un statut exact |
| Coût | ms par position, PC et téléphone |

Chacune par famille, par tranche de plis, pour : réseau seul, recherche à 64 / 512 / 2 000 / 10 000
simulations. **Première livraison de la phase : le tableau de référence du réseau et de la
recherche actuels.**

---

## 4. Phase 1 — Mieux utiliser le réseau actuel (sans réentraîner)

Chaque point est validé sur le banc avant de passer au suivant.

### 4.1 Valeur de la racine (corrige M1)

- `GumbelMcts.buildResult` : `Result.value` = Q complété du coup choisi (moyenne des simulations
  passées par ce coup, exact si l'enfant est résolu), et non plus `mixedValue(root)`, qui reste
  réservé à la complétion des Q des coups non visités.
- `GameReview.judge` : perte = Q du meilleur coup avant − Q du coup joué, ce dernier pris dans la
  recherche de la position suivante ; les deux sont alors des valeurs de même nature.
- `LiveAnalysisService.searchWdl` suit sans autre changement.
- Test : sur des parties d'auto-jeu, `v(t) + v(t+1)` de moyenne nulle à ±0,03 ; un coup égal au
  meilleur coup a une perte nulle.
- Effet de bord : le champ `value` des données devient une cible utilisable (§ 5.2). Celui des
  données déjà enregistrées reste biaisé : ne pas s'en servir.

### 4.2 Solveur exact dans la recherche (exploite M2)

- **À la racine**, avant toute simulation : `ExactSolver` avec un budget fixe en évaluations
  (jamais en temps, pour qu'un téléphone lent rende le même résultat), par exemple 30 000 en revue
  et 300 000 en analyse continue. Résolu → résultat exact sans simulation ; le coup vient de la
  table du solveur (un enfant prouvé perdant pour l'adversaire, ou nul pour garder la nulle). Dans
  une position prouvée perdue, la recherche choisit encore le coup le plus résistant.
- **Dans l'arbre** : quand un nœud atteint 8, 64, 512 visites, appel avec ~150 évaluations par
  visite (le solveur coûte alors moins d'un tiers du temps du réseau). Le statut remonte par le
  MCTS-solver existant (`updateStatus`).
- **Coups de la racine** : en revue, résoudre aussi la position après le coup joué et après le
  meilleur coup ; `MISSED_WIN` et `ALLOWED_WIN` reposent alors sur le solveur complet, plus
  seulement sur le VCF, et un verdict exact « a laissé échapper la nulle » devient possible.
- Tables : 2^18 à 2^20 entrées sur téléphone (7 à 29 Mo) au lieu de 2^22 ; à mesurer sur Pixel 4a
  et 7a (vitesse du df-pn inconnue sur ART).
- Déterminisme : mêmes précautions que `WinRules` (table vidée, taille fixe) pour les verdicts.

### 4.3 Revue : à rebours, avec mémoire, à budget variable

- Évaluer les positions **de la dernière à la première** en partageant la table du solveur et un
  cache des évaluations du réseau : ce qui est prouvé tard sert aux positions d'avant, et la position
  `t+1` est un enfant déjà cherché de la position `t`.
- Remplacer les 64 simulations fixes par `searchAdaptive` (64 → 1 024) : peu là où c'est tranché,
  plus là où ça hésite. Plafond en simulations totales par partie, calé sur le Pixel 4a.
- Après 4.2, une grande part des positions à partir du 16e pli ne coûtent plus aucune simulation.

### 4.4 Table de transposition

En Qubic les interversions de coups sont innombrables et `child()` crée toujours un nouveau nœud.
Version prudente : une table par recherche, indexée par `(mine, theirs)`, qui partage (a)
l'évaluation du réseau, (b) le statut exact, (c) le résultat du VCF. Les statistiques de visites
restent par chemin (pas de graphe partagé, donc pas de biais de remontée). À 10 000–20 000
simulations, mesurer la part d'évaluations économisées.

### 4.5 Graphes gagnants dans l'analyse

Tant que le premier joueur a suivi la stratégie d'un des deux graphes (`WinRules.decide` rend le
coup joué à chacun de ses tours), chaque position de la partie est exacte : gagnée pour lui, avec le
coup du graphe. Coût nul, et c'est précisément le début de partie, où le reste est le moins fiable.

### 4.6 Plusieurs fils en analyse continue

Un fil pour le MCTS, un second pour le solveur exact : racine d'abord, puis les positions de la
variante principale. Pas de parallélisation de l'arbre lui-même dans un premier temps.

**Sortie de phase** : tableau du banc avant / après, à réseau constant. C'est aussi le nouveau joueur
d'auto-jeu de la phase 4.

---

## 5. Phase 2 — Données et cibles

### 5.1 Format d'enregistrement v3

Les 152 octets actuels, plus : valeur de recherche corrigée (§ 4.1), V/N/D exact si connu (distinct
de l'issue de la partie), et **64 octets de valeurs par coup** (Q de recherche quantifié, codes
réservés pour gain / nulle / perte prouvés, « inconnu »). `data.py` et `selfplay` lisent toujours la
v2.

### 5.2 Cible de valeur

- Position à valeur exacte : cette valeur, quelle que soit l'issue de la partie.
- Sinon : mélange issue / recherche, `(1 − λ) · issue + λ · valeur de recherche`, λ = 0,5 avant le
  16e pli (là où l'issue est bruitée, § 2.2 et M5), 0,25 après. À régler par ablation supervisée
  sur le banc (famille C surtout).
- Adjudication : dès que le solveur tranche une position d'une partie d'auto-jeu, c'est ce résultat
  qui étiquette la suite ; la partie continue seulement pour la cible « plis restants ».

### 5.3 Distribution : des positions comme en produisent les humains

Ajouter au calendrier (`selfplay/Openings.kt`) une famille « partie entamée », 30 à 40 % des
parties, dont les coups d'ouverture ne sont pas appris (comme aujourd'hui) :

| Départ | Détail |
|---|---|
| Ancienne IA | préfixe d'une partie entre deux niveaux tirés entre 10 et 100, coupé entre 6 et 30 plis |
| Auto-jeu + faute | préfixe d'une partie d'auto-jeu coupé au hasard, suivi d'un ou deux coups tirés selon la politique du réseau à température 2 (un coup plausible mais pas le meilleur) |
| Auto-jeu + coup quelconque | idem avec un coup uniformément aléatoire (la bourde) |

Le filtre « disputée » ne s'applique qu'à la moitié : l'analyse voit aussi des positions décidées,
que le solveur traite, et des positions « presque décidées » que le réseau doit classer juste.

### 5.4 Ré-étiquetage exact des données existantes

Commande `selfplay relabel` : passer `ExactSolver` (3 000 évaluations, les deux questions) sur les
positions non prouvées de `runs/big` et `runs/proven` (~6 M sur 7,9 M). Coût mesuré : 19 ms par
position et par question, soit ~4 à 5 h sur 14 fils. Attendu d'après M2 : ~40 % de positions
supplémentaires à valeur exacte, davantage avec la preuve des nulles et des gains du second joueur.

### 5.5 Politique et valeurs par coup dans les positions exactes

- Position gagnée : la cible de politique se répartit sur **tous** les coups prouvés gagnants (en
  favorisant les plus courts), plus sur un seul ; un coup prouvé perdant reçoit 0.
- Position perdue : les coups qui résistent le plus longtemps.
- Tête Q (§ 6.2) : valeur exacte de chaque coup résolu. Une position donne alors jusqu'à 60
  étiquettes au lieu d'une.

### 5.6 Exemples difficiles

Après chaque génération, les positions où le réseau s'écarte le plus de la cible (surtout les
exactes) rejoignent un réservoir tiré à 10–15 % de chaque lot (même mécanisme que `proven_frac`).
Les positions des graphes descendent à 10 % : la vérité exacte vient maintenant de partout.

### 5.7 Preuves guidées en ouverture (extension, si le temps le permet)

Lancer le prouveur de `:wingraph` (généralisé aux deux rôles, racine quelconque) sur les 757
positions à 3 coups et sur un tirage de positions disputées à 4–6 coups, avec un budget par racine
(~10 min de calcul), des plus sûres selon le réseau aux moins sûres. Chaque preuve exporte son arbre
(généralisation de `ProvenExport`) : des milliers de positions exactes entre 4 et 14 plis, la zone
sans étiquettes. Ce qui n'est pas prouvé dans le budget reste « inconnu ». Sous-produit possible :
une petite table d'ouvertures exactes embarquée dans l'appli pour l'analyse.

---

## 6. Phase 3 — Réseau v3 (format `QNET` v3)

Taille de départ inchangée (H = 64, 10 couches). Trois changements, chacun validé par ablation
supervisée sur données fixes avant tout auto-jeu.

### 6.1 Graphe réduit aux cases libres et aux lignes vivantes

Une ligne morte ne peut plus être complétée par personne ; une case occupée n'influence la suite que
par les lignes vivantes qui la traversent, dont l'état (couleur, nombre d'anneaux) dit déjà tout.
Les retirer du passage de messages ne perd donc **aucune** information utile au jeu.

- Nœuds : cases libres et lignes vivantes seulement. État d'une ligne : vide, 1–3 à moi, 1–3 à
  l'adversaire (7 états). Une case libre agrège ses lignes vivantes par **somme et moyenne** (le
  nombre de lignes vivantes par type devient ainsi visible), plus la classe de degré.
- Têtes : politique sur les cases libres ; valeur et plis restants sur la mise en commun des cases
  libres et des lignes vivantes, plus le nombre de chacune.
- PyTorch : mêmes tenseurs denses avec masques (cases occupées et lignes mortes à zéro, exclues des
  agrégations et des mises en commun). Kotlin : boucles sur les seuls nœuds actifs.
- Gain de calcul d'après M6 : ~12 % à 8–11 plis, 26 % à 16–19, 41 % à 24–27, ~1,5× en moyenne sur
  les positions d'auto-jeu, davantage dans l'arbre (plus profond que la racine).
- Équivariance exacte conservée (les masques se transforment avec le plateau) : garder le test.

### 6.2 Tête Q par coup

Une sortie par case libre, valeur attendue après ce coup (tanh, perte de Huber, poids 0,5, masquée
aux coups sans cible). Cibles : Q de recherche des coups assez visités, valeurs exactes du § 5.5.

- Recherche : Q complété d'un coup non visité = sortie de la tête au lieu de `mixedValue` ; à
  valider sur le banc à 64–512 simulations.
- Analyse : une estimation pour chaque coup, même non exploré (`MoveHint.expectedScore` est
  aujourd'hui vide pour ces coups).

### 6.3 Contexte global dans le tronc

Toutes les 2 ou 3 couches, moyenne et maximum des cases libres → couche linéaire → ajoutés à chaque
case. Coût négligeable. Gain incertain (deux cases quelconques sont déjà à deux sauts l'une de
l'autre) : ne le garder que si l'ablation le justifie.

### 6.4 Taille : décider à temps égal

Candidats en v3 : 48 × 6, 64 × 8, 64 × 10, 80 × 10, entraînés à l'identique (12 000 pas chacun,
1,5 à 3 h). Choix sur l'erreur du banc **à temps égal** (ms par évaluation mesurées en Kotlin, PC et
téléphone), pas à simulations égales. Les expériences passées (§ 2.2) font attendre ≤ 64 × 10.

### 6.5 Portage

`model.py` reste la référence ; `QubicNet.kt` lit v1, v2, v3 ; nouvelles fixtures de parité
(`export.py --fixture`) ; `GumbelMcts` lit la tête Q si elle existe.

### 6.6 Non retenu

- **Grossir le réseau** : perd à temps égal (§ 2.2).
- **Traits d'entrée tactiques** (cases de menace, fourchettes) : calculables en une couche à partir
  des états de lignes, et la tactique exacte est déjà faite hors réseau.
- **Inférence native / quantifiée sur téléphone** : gain possible mais non chiffré ici ; à ne
  considérer qu'après mesure, une fois le graphe réduit en place.

---

## 7. Phase 4 — Entraînement (heures machine)

| Étape | Contenu | Heures |
|---|---|---|
| T0 | Banc de vérité (§ 3), une fois | 6–10 |
| T1 | Ré-étiquetage exact de `runs/big` + `runs/proven` (§ 5.4) | 4–5 |
| T2 | Ablations supervisées sur données fixes : graphe réduit, tête Q, contexte global, λ, taille (6 à 8 essais de 12 000 pas) | 12–18 |
| T3 | Pré-entraînement du réseau retenu sur tout l'existant ré-étiqueté + graphes : 24 000 à 30 000 pas de 1 024, lr 1e-3 → 2e-5 (comme `pretrain.py`), moyenne glissante des poids (EMA) pour le modèle exporté | 7–8 |
| T4 | Auto-jeu avec le joueur de la phase 1, les ouvertures du § 5.3, les cibles du § 5.2 : 80 à 100 générations de 500 parties ; fenêtre 1,5 M amorcée avec l'existant ré-étiqueté ; lr 1e-4 → 2e-5 en cosinus ; 10 % de positions des graphes, 10–15 % d'exemples difficiles | 30–40 |
| T5 | Banc de vérité, match contre gen 29 à temps égal (PC et deux téléphones, outillage de `battle.py`) | 3–4 |

Total ~60 à 85 h ; **version minimale ~50 h** en sautant T2 (64 × 10 v3 directement, λ = 0,5) et en
bornant T4 à 30 h. Seuls T3 et T4 (~40 à 50 h) sont de l'entraînement proprement dit. Tout reste reprenable comme aujourd'hui (`autotrain.py` : états écrits de façon
atomique, reprise à la partie près).

Points d'attention :

- T2 d'abord sur le 48 × 6 (2,5× moins cher) pour trier les idées, puis confirmation sur le 64 × 10.
- T4 : le solveur à la racine ajoute ~19 ms par coup mais évite la recherche des positions qu'il
  tranche ; mesurer le temps par génération dès la première et ajuster le nombre de parties.
- Suivre à chaque génération, en plus des pertes, l'erreur sur un extrait fixe du banc (2 000
  positions, ~1 min) : c'est la courbe qui compte, et le critère d'arrêt.
- Poids de la perte de valeur : essayer 1,5 dans T2 (la valeur est la sortie principale de
  l'analyse).

---

## 8. Phase 5 — Intégration et validation

1. `tetrano_nn.bin` v3 ; l'appli garde la lecture v2.
2. Écran d'analyse : distinguer « prouvé » d'« estimé » avec les textes existants (aucun composant
   nouveau, fidélité à l'appli d'origine) ; valeurs par coup pour tous les coups.
3. Vérifications : `./gradlew :engine:test` (parité, équivariance, solveur, revue), tableau du banc,
   match de non-régression, temps de revue d'une partie de 40 coups sur Pixel 4a.

## 9. Ordre, jalons et points de décision

| Jalon | Contenu | Décision à la sortie |
|---|---|---|
| J0 | Solveur à trois issues + banc + tableau de référence | Les ordres de grandeur du § 2.3 tiennent-ils sur les familles B et C ? |
| J1 | § 4.1 (valeur de racine) | Livrable seul : petite correction, gros effet sur la revue |
| J2 | § 4.2 à 4.6 | Part prouvée et coût sur téléphone ; si le df-pn est trop lent sur ART, le limiter à la racine |
| J3 | T1 + T2 | Quelles idées d'architecture passent ; taille retenue |
| J4 | T3 + T4 + T5 | Mise dans l'appli seulement si le banc s'améliore **et** si le match à temps égal ne régresse pas |
| J5 (option) | § 5.7 | Selon le temps machine restant |

J0 à J2 n'ont besoin d'aucun entraînement et améliorent déjà l'analyse du réseau actuel : à livrer
sans attendre la suite.

**Cibles indicatives**, à recaler sur le tableau de référence de J0 :

| Métrique | Aujourd'hui | Visé |
|---|---|---|
| Moyenne de `v(t) + v(t+1)` | −0,28 | \|·\| < 0,03 |
| Positions de revue rendues avec un statut exact (≥ 12 plis) | 11 à 41 % selon le pli | ≥ 60 % |
| Erreur du réseau seul sur positions tranchées par df-pn | 6,4 % (10 % entre 8 et 15 plis) | ≤ 3 % (≤ 5 %) |
| Erreur de la recherche à 1 000 simulations sur le banc exact | à mesurer (J0) | < 1 % |
| Match contre gen 29 à temps égal | — | ≥ 50 % |

## 10. Risques

| Risque | Parade |
|---|---|
| Le banc exact est biaisé vers les positions faciles | liste « non résolues » rapportée à part ; § 5.7 pour en résoudre davantage |
| Nulles et gains du second joueur chers à prouver | budget par position ; sinon cible mélangée du § 5.2 |
| df-pn lent ou gourmand en mémoire sur téléphone | budgets en évaluations, petites tables, racine seulement |
| Les positions exactes déforment la distribution (comme les graphes seuls) | toujours mélangées à l'auto-jeu ; suivi de l'erreur par famille |
| Le graphe réduit ou la tête Q n'apportent rien | décidés par ablation (T2) avant tout auto-jeu ; chacun est indépendant |
| Régression du niveau 101 | match à temps égal avant toute mise dans l'appli |

Question ouverte pour l'affichage : la valeur exacte dit qui gagne en jouant parfaitement, pas si
c'est difficile (plateau vierge = « gagné » pour le premier joueur). Les plis restants ou la taille
de la preuve peuvent servir d'indicateur de difficulté ; à trancher à la phase 5.

## Annexe — méthode des mesures du § 2.3

- M1, M5 : `runs/proven/data/gen_0020…0029.bin` (140 142 positions) ; paires consécutives =
  `mine(t+1) == theirs(t)` et `ply(t+1) == ply(t) + 1`, hors positions prouvées ; champ `value`.
- M2 à M4 : `gen_0028.bin` et `gen_0029.bin`, positions sans drapeau « exact », 120 tirées par
  tranche de plis ; forme canonique (`CanonicalForm`) puis `DfPn(22, 5000, 10).solve` (bibliothèque
  `wingraph` construite), 8 fils, une instance par fil, table gardée d'une position à l'autre.
  Réseau : `runs/proven/checkpoints/gen_0029.pt` via `model.evaluate_bits`.
- M6 : `qubic.encode` sur les mêmes fichiers ; ligne vivante = état différent de 7.
- Les scripts étaient jetables (hors dépôt) ; la phase 0 les remplace par `selfplay truth` / `judge`.

---

## Avancement (nuit du 24 au 25 novembre 2026)

Implémenté (branche `claude/neural-network-strategy-081200`) : phases 0 à 3 et l'outillage de la
phase 4 ; mode d'emploi dans `tools/nn/README.md`, « Valeurs exactes et réseau v3 ». Pas fait :
cible de politique répartie sur tous les coups gagnants (§ 5.5), réservoir d'exemples difficiles
(§ 5.6), preuves guidées en ouverture (§ 5.7), mesures sur téléphone.

### Banc de vérité (`runs/v3/truth`, 50 min)

9 340 positions (A : 4 299, B : 3 049, C : 1 992), dont 5 418 à valeur exacte (3 092 gains, 140 nulles,
2 186 pertes pour le joueur au trait ; 3 922 non résolues à 1 M d'évaluations, surtout avant le 8e pli),
400 positions avec la valeur exacte de chaque coup.

### Réseau actuel (gen 29) sur le banc, avant / après les changements de la recherche

| Configuration | Erreur de valeur | Prouvées | v(t)+v(t+1) | Erreur plis 8–11 | ms / position (PC) |
|---|---|---|---|---|---|
| Réseau seul | 8,1 % | 0 % | +0,012 | 15,5 % | 7 |
| 64 simulations, ancienne valeur de racine | 3,2 % | 28,6 % | **−0,173** | 4,7 % | 201 |
| 64 simulations, valeur = Q du coup choisi | 1,8 % | 28,7 % | +0,038 | 1,5 % | 204 |
| 256 simulations, ancienne valeur | 3,2 % | 28,8 % | **−0,202** | 4,6 % | 660 |
| 256 simulations, valeur = Q du coup choisi | 1,5 % | 28,8 % | +0,035 | 1,0 % | 673 |
| 256 simulations + solveur à la racine (30 000) | **1,3 %** | **41,4 %** | +0,067 | 1,0 % | 778 |

- La correction de la valeur de racine (§ 4.1) divise l'erreur par deux et supprime le biais (−0,20 →
  +0,04), sans aucun coût.
- Le solveur à la racine fait passer la part prouvée de 29 à 41 % (51 à 67 % entre 8 et 27 plis) pour
  +15 % de temps.
- Le réseau seul se trompe sur 8,1 % des positions exactes (11,8 % sur les parties « humaines » B,
  15,5 % entre 8 et 11 plis) : c'est la marge de l'entraînement.
- Fautes réelles (le coup change la valeur exacte) : rappel 99 %, mais précision ~30 % : la revue
  signale aussi des coups qui gardent la valeur exacte mais perdent beaucoup d'espérance estimée
  (une position gagnée devenue difficile). À trancher pour l'affichage (§ 10, question ouverte).

### Ré-étiquetage (4 h 22)

`runs/proven` (gen 0–29) et `runs/big` (gen 108–364) : 5,51 M positions, dont 1,41 M déjà exactes ;
le solveur (6 000 évaluations) en a prouvé 1,61 M de plus (790 k gains, 36 k nulles, 780 k pertes),
2,49 M restent inconnues : 55 % des positions ont maintenant une valeur exacte. `runs/big` gen 0–107
n'est pas fait (≈ 1 h 45 de plus).
