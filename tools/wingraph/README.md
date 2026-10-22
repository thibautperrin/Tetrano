# Graphe de gain forcé (niveau Imbattable)

Calcule, pour l'ordinateur qui **commence**, un coup gagnant dans chaque position où il peut se
trouver, de sorte qu'il gagne quelle que soit la défense. Plan et choix : `docs/WINNING_GRAPH_PLAN.md`.
Code : module Gradle `:wingraph` (outil PC, hors appli) ; les règles de jeu du joueur imbattable et
le format du graphe sont dans `:engine` (`engine/.../wingraph/`), pour que l'appli les rejoue plus
tard à l'identique. Rien n'est branché dans l'appli.

## Une fois (et après chaque modification du code)

Depuis la racine du dépôt :

```
gradlew :wingraph:installDist
```

`wingraph.bat` copie le programme dans `runs\main\app` à chaque lancement : on peut reconstruire
pendant qu'un calcul tourne, la nouvelle version servira au prochain lancement.

## Démarrer, mettre en pause, reprendre

| Action | Comment |
|---|---|
| **Démarrer / reprendre** | double-clic sur `tools\wingraph\wingraph.bat` (fenêtre « Tetrano wingraph », priorité basse) |
| **Mettre en pause** | double-clic sur `wingraph-stop.bat` : le calcul sauvegarde tout puis s'arrête (quelques secondes) |
| **Suivre** | double-clic sur `wingraph-status.bat`, ou ouvrir `runs\main\status.txt` (réécrit toutes les 30 s) |
| Arrêt brutal | fermer la fenêtre : on perd au plus le travail depuis la dernière sauvegarde (10 min) |

Relancer `wingraph.bat` reprend toujours là où le calcul s'était arrêté, dans la même phase. Un
second lancement pendant qu'un calcul tourne est refusé (`run.lock`).

Réglages (au lancement, gardés pour les fois suivantes dans `runs\main\config.properties`) :
`wingraph.bat threads=8` (défaut : cœurs - 4, ici 12), `disk_limit_gb=10`, `checkpoint_minutes=10`,
`job_evals=500000`. Mémoire Java : variable `WINGRAPH_XMX` (défaut `16g`).

## Les phases

1. **Preuve** — un « arbre de pilotage » part de la position après le coin (case 0) et les
   12 réponses de l'adversaire. Chaque travail prend la feuille la plus prometteuse (nombres de
   preuve), tente de la résoudre par df-pn (recherche exacte, rapide, sans réseau) ; si elle
   résiste, le réseau 64 × 10 la développe : il ordonne nos coups (on n'ouvre d'abord que les
   meilleurs) et estime ce qui reste à prouver. Chaque feuille prouvée est aussitôt **extraite** :
   la stratégie au-dessous est parcourue contre toutes les réponses et ses coups notés dans le livre,
   en préférant les coups qui ne demandent aucun nouveau nœud. Fin : les 12 branches sont prouvées.
2. **Assemblage** — parcours complet depuis le plateau vide contre toutes les défenses, en ne
   gardant que les nœuds réellement utilisés ; écriture de `graph-v1.bin`, puis **vérification de ce
   fichier seul** (règles + graphe, rien d'autre).
3. **Réduction** — passes successives : à chaque nœud (des plus gros sous-arbres aux plus petits),
   on essaie les autres bons coups du réseau ; un essai qui prouve le gain avec strictement moins de
   nœuds remplace l'ancien coup. Chaque passe finit par un assemblage vérifié ; `graph-best.bin` est
   le plus petit graphe vérifié. La phase s'arrête quand une passe n'améliore plus rien.

Un nœud du graphe = une position (à symétrie près, 192 symétries) où c'est à l'ordinateur de jouer
et où ces règles ne suffisent pas : gagner tout de suite, bloquer un alignement de 3, ou gagner par
une suite de menaces (VCF, budget fixe de 20 000 nœuds). Ces cas-là ne coûtent aucun nœud.

## Lire `status.txt`

- **Phase** et temps de calcul cumulé (toutes sessions).
- **Branches** : une ligne par réponse de l'adversaire au coin (case 1, 3, 5, …, 63) : état
  (`à faire`, `en cours`, `PROUVÉE`), `pn` = estimation du nombre de feuilles encore à prouver
  (descend vers 0 quand la preuve avance, peut remonter quand une piste échoue), `dn` = idem pour
  réfuter (très grand = la branche est bien gagnante), gain estimé par le réseau à la création.
- **Session** : travaux faits, feuilles prouvées / réfutées / développées.
- **Livre** : coups prouvés trouvés jusqu'ici (toutes pistes, y compris abandonnées).
- **Graphe** : tailles de `graph-v1.bin` et de `graph-best.bin` une fois la phase 2 atteinte.
- **Mémoire / disque**.

La durée totale n'est pas connue d'avance (c'est l'objet même du calcul) : la tendance des `pn` et le
nombre de branches prouvées sont les meilleurs indicateurs.

## Disque : 10 Go au plus

Le calcul mesure son dossier à chaque sauvegarde : il se met en pause **avant** de dépasser
`disk_limit_gb` (défaut 10), et aussi au-delà de 95 %. Seuls des faits utiles sont écrits
(positions prouvées, coups du livre, arbre de pilotage), chacun en un fichier remplacé à chaque
sauvegarde (jamais d'historique qui s'accumule). Les tables de recherche restent en mémoire.

## Fichiers de `runs\main`

| Fichier | Contenu |
|---|---|
| `status.txt`, `log.txt` | état courant, journal |
| `config.properties`, `state.properties` | réglages, phase et compteurs |
| `net.bin` | le réseau utilisé (copié au premier lancement depuis l'appli : gen 364) |
| `tree.bin`, `book.bin`, `solved.bin`, `covered.bin` | état de la preuve |
| `graph-v1.bin`, `graph-best.bin` | les graphes vérifiés (format `WinBook`) |
| `app\` | copie du programme utilisée par le calcul |

## Vérifier un graphe

```
runs\main\app\bin\wingraph.bat verify graph=runs\main\graph-best.bin
```

Rejoue la stratégie (règles + ce fichier seulement) contre toutes les défenses depuis le plateau
vide. Contrôle par une autre voie, des parties réelles du joueur de l'appli (`UnbeatablePlayer`)
contre un adversaire aléatoire puis contre le réseau :

```
runs\mainppin\wingraph.bat play graph=runs\main\graph-best.bin model=..\..pp\src\mainesaw	etrano_nn.bin games=3000
```

## Divers

- `wingraph bench model=<réseau>` : vitesse du réseau et de df-pn sur cette machine.
- `start=0,a,b,…` (premier lancement d'un autre dossier, `dir=`) : part d'une position plus avancée,
  pour les essais ; les tests (`gradlew :wingraph:test`) s'en servent pour faire tout le parcours
  preuve → assemblage → réduction → vérification en une seconde.
