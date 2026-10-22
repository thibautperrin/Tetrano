# Graphe de gain forcé — plan (niveau « Imbattable »)

But : une structure de données qui donne, pour l'ordinateur qui **commence**, le coup à jouer dans
chaque position atteignable, de sorte qu'il gagne **quel que soit** le jeu de l'adversaire. Elle
servira de niveau imbattable dans l'appli.

Priorité de la première version : **une stratégie qui marche à coup sûr, avec le moins de nœuds
possible**. La longueur des parties n'est pas un critère : entre un coup qui gagne vite mais demande
plus de nœuds et un coup qui gagne lentement par une suite forcée avec moins de nœuds, on prend le
second.

Ce travail tourne **en parallèle du plan de livraison** : outil hors appli (module Gradle `:wingraph`),
priorité basse, nombre de threads réglable, pause / reprise sans perte ; il vit dans sa **propre
branche** (`claude/tetrano-winning-strategy-graph-*`) et n'est pas intégré à la branche principale de
l'appli. Mode d'emploi : `tools/wingraph/README.md`.

Décisions (2026-10-22) :

- Au niveau Imbattable, **l'ordinateur commence toujours**.
- **Disque : 10 Go au plus** pour tout le calcul (le programme mesure son dossier et s'arrête
  proprement avant de dépasser).
- Structure « nœuds de décision seulement » (§ 2) validée.

## Résultat (2026-10-22)

Premier calcul complet en **~16 min** sur ce PC (12 threads) : le coin gagne contre les 12 réponses.

- **graph-v1** : 4 149 nœuds, vérifié seul contre toutes les défenses (1 762 197 positions, parties
  d'au plus 56 anneaux).
- Réduction, passe 1 : **3 239 nœuds** (−22 %), vérifié ; passe 2 en cours (reprendre avec
  `tools/wingraph/wingraph.bat`).
- Contrôle par une autre voie : `UnbeatablePlayer` (code de l'appli) a gagné 6 000 parties sur
  6 000 contre un adversaire aléatoire et contre le réseau 64 × 10 (tirage selon sa politique).
- Taille du fichier : ~17 octets par nœud, soit ~55 Ko pour 3 239 nœuds. L'étape « moins de coups »
  (§ 4 bis) est donc envisageable.

---

## 1. Point de départ (déjà établi, voir `tools/qubic-solver/NOTES.md`)

- Tetrano = Qubic 4×4×4, 64 cases, 76 lignes ; **gain du premier joueur** (Patashnik 1980, Allis
  1994). Il n'existe donc de stratégie imbattable **que si l'ordinateur commence** : quand l'humain
  commence et joue parfaitement, il gagne. Voir la question ouverte § 9.
- Groupe d'automorphismes d'ordre **192** (déjà dans `QubicGeometry.symmetries`).
- 2 classes de cases seulement : 16 « fortes » (coins + centres, 7 lignes) et 48 « faibles ».
  ⇒ le premier coup est « une case forte » (case 0), et la défense a **12 réponses** distinctes :
  1 3 5 6 7 15 21 22 23 27 31 63.
- Tentative d'octobre (df-pn Java sans réseau) : **pas de preuve** depuis le plateau vide ni pour
  les 12 réponses en 10 min chacune. Cause identifiée : en début de partie aucune menace, les
  nombres de preuve initiaux sont tous à 1, la recherche part en largeur. Le banc montre qu'à
  10–14 coups le solveur conclut vite.
- La tactique est résolue : `Vcf` (gain par menaces continues) est exact quand il répond « oui »,
  rapide, et déjà porté en Kotlin dans `:engine`.
- Ce qui manquait est exactement ce qu'on a maintenant : un **réseau 64 × 10** (gen 364, celui de
  l'appli) qui sait quels coups gagnent et quelles positions sont gagnées.

---

## 2. Structure du graphe — et le challenge de la proposition

### Proposition initiale

Couches alternées : 1 nœud « plateau vide → notre coup », 12 nœuds « réponse de l'adversaire »,
12 nœuds « notre réponse », etc., avec l'idée que séparer nœuds adverses et nœuds à nous rend plus
facile de changer d'avis et de couper une branche.

### Recommandation : ne stocker que les nœuds de décision

**Un nœud = une position canonique où c'est à nous de jouer + le coup choisi.** Les réponses de
l'adversaire ne sont pas des nœuds mais des **arêtes**, et elles ne sont même pas stockées : c'est
« toutes les cases libres, à symétrie près de la position », une fonction pure du plateau.

Correspondance avec le comptage proposé :

| Proposé | Ici |
|---|---|
| 1 nœud plateau vide (meilleur coup) | 1 nœud : plateau vide → case 0 |
| 12 nœuds « réponses de l'adversaire » | 12 arêtes (calculées) |
| 12 nœuds « 2 anneaux + notre réponse » | 12 nœuds : (case 0 + réponse) → notre coup |
| … | chaque nœud a ensuite *k* arêtes, *k* = réponses distinctes à symétrie près |

Pourquoi ce n'est pas plus difficile de changer d'avis :

- Changer d'avis à un endroit = **modifier le coup d'un seul nœud**. Les réponses adverses
  sous l'ancien coup disparaissent d'elles-mêmes puisqu'elles sont calculées.
- Le sous-graphe devenu inaccessible est retiré par un **parcours d'accessibilité depuis la racine**
  (un « ramasse-miettes »), pas par une suppression manuelle. C'est obligatoire de toute façon :
  le graphe est un **DAG**, pas un arbre (deux ordres de coups différents mènent à la même position
  canonique, qui n'est qu'un seul nœud), donc on ne peut jamais supprimer une branche « à la main »
  sans risquer de casser une autre branche qui la partage.
- Les nœuds coupés ne sont pas perdus : ils restent dans un **cache de preuves** sur disque (hors
  livrable) et sont repris gratuitement si une autre branche y retombe.
- Un nœud adverse séparé ne porterait aucune information (pas de choix à mémoriser) : il doublerait
  la taille sans rien apporter.

En interne, le solveur manipule bien les deux sortes de positions (nœuds OU = à nous, nœuds ET =
à l'adversaire) dans sa table de transposition ; c'est seulement le **livrable** qui n'en garde
qu'une.

### Ce qui ne coûte aucun nœud

Le joueur de l'appli applique, dans l'ordre (comme `Strategy.choose` dans `tools/qubic-solver`) :

1. compléter une ligne s'il peut (gagner) ;
2. bloquer un « trois » adverse ;
3. **jouer le coup du graphe** si la position (canonique) y est ;
4. sinon jouer le premier coup du **VCF** trouvé (budget fixé, cf. § 5).

Donc les coups forcés et toute la fin de partie par menaces continues **ne coûtent rien**. C'est ce
qui rend le critère « une suite forcée, quelle que soit sa longueur » naturel : une feuille VCF de 15
coups vaut 0 nœud. **Taille du graphe = nombre de nœuds de décision accessibles depuis la racine**,
c'est-à-dire les positions où une règle 1/2/4 ne suffit pas.

Ordre de grandeur attendu : 1 → 12 → quelques dizaines de réponses par nœud dès que la position perd
ses symétries ; toute la taille dépend de **la profondeur à laquelle le VCF prend le relais**.
Minimiser les nœuds, c'est donc choisir des coups qui amènent vite toutes les réponses adverses dans
une position à VCF.

---

## 3. Étape A — trouver une preuve (correction d'abord)

Recherche **df-pn** (proof-number en profondeur, déjà écrite en Java, à porter en Kotlin) guidée
par le réseau. df-pn plutôt que le `GumbelMcts` existant : le nombre de preuve est précisément une
estimation du **nombre de feuilles restant à prouver**, donc l'algorithme vise par construction les
petites preuves, ce qui colle au critère « moins de nœuds ».

- **Nœuds à nous (OU)** : coups regroupés par classe de symétrie, triés par la politique du
  réseau ; **élargissement progressif** (on n'ouvre que les *k* meilleurs, *k* grandit si ceux-ci
  s'avèrent chers ou perdus). Sans risque : on ne cherche qu'une preuve, et une « réfutation »
  obtenue sous élagage n'est jamais tenue pour vraie (l'échec de `topK` dans les notes venait de là).
- **Nœuds adverses (ET)** : **toutes** les réponses à symétrie près, sans exception (c'est la
  garantie). Triées par la politique du réseau vue du défenseur, pour attaquer d'abord la défense la
  plus dangereuse et échouer vite.
- **Initialisation pn/dn par le réseau** : au lieu de 1/1, pn et dn tirés de P(gain) du réseau.
  C'est le levier principal contre la recherche « en largeur » observée en octobre.
- **Feuilles** : gain immédiat, ou VCF trouvé → prouvé ; position pleine / adversaire gagnant →
  réfuté.
- Table de transposition sur forme canonique 192 (hachage Zobrist invariant de `Solver2.java`) ;
  évaluations du réseau mises en cache par position canonique.
- **Découpage** : les 12 sous-problèmes après la case 0 sont indépendants ⇒ résolus en parallèle,
  du plus facile (valeur réseau la plus haute) au plus dur. Une branche trop chère peut être
  rouverte plus tard avec un autre coup sans toucher les autres.
- Le premier coup n'est pas rediscuté : sous les 192 symétries il n'y a que « fort » ou « faible »,
  et le fort est le bon.

## 4. Étape B — réduire le nombre de nœuds

Le minimum exact est hors de portée (problème combinatoire sur un DAG énorme) ; on fait une
amélioration **à tout instant interruptible** et **monotone** : chaque version est une stratégie
gagnante complète, plus petite que la précédente.

1. **Pendant la preuve** (glouton) : à un nœud à nous, essayer d'abord les coups dont toutes les
   réponses adverses tombent immédiatement en VCF (sous-graphe de taille 1), puis ceux qui mènent
   à des positions **déjà présentes** dans le graphe (coût marginal 0 grâce au partage).
2. **Après la preuve** (amélioration locale itérée) :
   - calculer pour chaque nœud la taille de son sous-graphe (nœuds partagés comptés une fois) ;
   - parcourir les nœuds du plus gros sous-graphe au plus petit ; pour chaque coup alternatif
     (meilleurs coups du réseau + coups menant à des positions connues), tenter une preuve avec un
     **budget plafonné à la taille actuelle** (inutile de chercher plus gros) ;
   - si strictement plus petit : remplacer le coup, ramasse-miettes ; sinon garder ;
   - recommencer jusqu'à stabilité ou échéance.
   - À taille égale : le moins de coups au total (critère secondaire seulement).

C'est exactement « changer d'avis et couper une branche », de façon systématique.

## 4 bis. Étape ultérieure, peut-être un jour — gagner en moins de coups

Seulement si le graphe obtenu au § 4 n'est pas trop gros. Le critère s'inverse : **gagner avec le
moins de coups possible** (au pire des défenses), quitte à ajouter des nœuds. Même mécanique
d'amélioration locale que le § 4 (essayer d'autres coups à un nœud, garder si la profondeur
maximale — puis moyenne — du sous-graphe baisse, toujours après preuve), avec un plafond de taille
pour respecter le budget disque et APK. Les fins par VCF utiliseraient alors `Vcf.shortest`. Non
programmé pour l'instant.

## 5. Étape C — vérification exhaustive indépendante

Aucune version n'est livrée sans :

- **Simulation complète du joueur de l'appli** (règles 1–4 du § 2, *même code Kotlin que l'appli*)
  contre **toutes** les réponses adverses à symétrie près, jusqu'à la fin de chaque partie. Échec si
  une branche finit nulle ou perdue, ou si un nœud manque.
- **Contre-vérification** avec le code Java de `tools/qubic-solver` (implémentation indépendante des
  lignes et des symétries). *Pas encore écrite : à faire avant l'intégration dans l'appli (§ 7).*
  Déjà fait : la vérification relit le fichier du graphe seul (`wingraph verify`), et les tests
  rejouent `UnbeatablePlayer` (le code de l'appli) contre des défenses aléatoires.
- **Piège à éviter — déterminisme du VCF** : `Vcf` garde une table de résultats ; son résultat avec
  un budget limité dépend donc de ce qu'elle contient déjà et de sa taille (collisions). La règle 4
  doit être une fonction figée : **instance neuve à chaque appel, taille de table fixée, budget en
  nœuds (jamais en temps)**, identique au générateur, au vérificateur et à l'appli. Ainsi un
  téléphone lent joue exactement comme le PC. De plus le VCF est relancé à chaque coup : la
  vérification par simulation (et non par « un VCF existe à la feuille ») couvre le cas où la
  relance choisirait une autre suite.

## 6. Où vit le code (réalisé)

- `:engine`, paquet `engine.wingraph` : ce que l'appli rejouera — `CanonicalForm` (192 symétries),
  `WinRules` (règles 1–4, VCF figé : table vidée, 2^16 cases, 20 000 nœuds), `WinBook` (format du
  graphe), `UnbeatablePlayer`. Seul autre changement du moteur : `Vcf.clear()`.
- Module Gradle `:wingraph` (outil JVM, hors appli) : `DfPn` (port Kotlin de `Solver.java`),
  `TopTree` (arbre de pilotage best-first, coups en attente = enfants virtuels), `Prover` (travaux en
  parallèle), `Walker` + `SmallGraphChooser` (extraction = vérification), `Assembler` (graphe +
  vérification du fichier seul), `Reducer` (§ 4), `Run` (sauvegardes, plafond disque, pause/reprise,
  `status.txt`).
- `tools/wingraph/` : `wingraph.bat` (démarrer / reprendre, priorité basse), `wingraph-stop.bat`
  (pause propre), `wingraph-status.bat`, `README.md` (mode d'emploi), `runs/` ignoré par git.
- Réseau figé sur **gen 364** (celui de l'appli), copié dans le dossier du calcul. Changer de réseau
  ne remet pas en cause une preuve (elle est exacte), seulement la vitesse.
- Mesures (ce PC) : une évaluation du 64 × 10 = 3,3 ms ; df-pn ≈ 150 000 à 500 000 positions/s par
  thread.

## 7. Étape D — intégration dans l'appli (après validation)

- Fichier `res/raw/tetrano_win.bin` : entrées triées (position canonique : 2 × `Long`, coup :
  1 octet), recherche dichotomique ; ~17 octets par nœud (100 000 nœuds ≈ 1,7 Mo, compressible
  si besoin).
- Joueur `UnbeatablePlayer` dans `:engine` : règles 1–4 ; si la position sort du graphe (ne doit
  jamais arriver), repli sur le niveau 101 et trace dans les logs.
- Nouveau niveau (102 ?) dans `AiService`, chaînes dans les 6 langues, présentation fidèle à
  l'appli d'origine (pas de composants Material de substitution).
- Bonus éventuel plus tard : l'écran d'analyse signale « gain forcé » quand la position est dans le
  graphe.

## 8. Jalons (avec point d'arrêt à chacun)

| Jalon | Contenu | Résultat attendu |
|---|---|---|
| J0 — mesure | Sur les parties d'auto-jeu du 64 × 10 gagnées par le 1er joueur depuis le plateau vide : à quel pli le VCF apparaît. df-pn guidé vs df-pn nu sur le banc de positions à 10–14 coups. | Estimation de la taille du graphe et du gain de vitesse dû au réseau |
| J1 — une branche | Prouver la plus facile des 12 réponses, extraire et vérifier son sous-graphe | Nœuds et heures par branche ⇒ projection pour les 12 |
| J2 — preuve complète | Les 12 branches + racine, vérification § 5 | **Première stratégie imbattable** (taille non optimisée) |
| J3 — réduction | Étape B jusqu'à stabilité ou échéance | Courbe taille / temps, version la plus petite vérifiée |
| J4 — appli | § 7, test sur téléphone | Niveau imbattable livrable |

Risques et parades :

- **Preuve trop coûteuse** malgré le réseau (Patashnik et Allis l'ont obtenue avec beaucoup
  d'expertise) → règle de feuille plus forte (VCF + menaces à 2 coups), plus de temps machine sur
  les branches dures, ou ouvrir d'autres coups à la racine des branches qui résistent.
- **Graphe trop gros pour l'APK** → l'étape B réduit d'abord ; sinon compression ou téléchargement.
- **Concurrence avec le plan de livraison** sur le CPU → threads et priorité réglables, arrêt/reprise
  sans perte.
- **Disque** → plafond de 10 Go vérifié à chaque sauvegarde ; tables de travail en mémoire (taille
  fixe), seuls les faits utiles (positions prouvées, coups du livre, arbre de pilotage) sont écrits.

## 9. Questions ouvertes

1. **Qui commence au niveau imbattable ?** Proposition : l'ordinateur commence toujours à ce niveau
   — **tranché : l'ordinateur commence toujours.**
2. Faut-il, une fois le graphe minimal obtenu, le rendre plus « humain » (varier entre coups
   gagnants équivalents) ? Hors périmètre de la première version.
