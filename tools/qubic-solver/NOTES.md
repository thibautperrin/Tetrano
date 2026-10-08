# Qubic solver — notes d'expérimentation (2026-10-08)

Objectif initial : un adversaire imbattable quand l'ordinateur commence (niveau 101).
Ce dossier contient la tentative « preuve exacte + livre d'ouvertures ». Elle n'a **pas abouti**
(pas de preuve trouvée depuis le plateau vide). La demande a ensuite évolué vers un réseau de
neurones (voir `docs/` / le plan correspondant).

## Faits établis

- Tetrano = **Qubic** (morpion 4×4×4) : 64 cases (`cell = x + 4y + 16z`, même numérotation que les
  anneaux de l'appli), 76 alignements, n'importe quelle case libre est jouable.
- Le Qubic est un **gain du premier joueur** (Patashnik 1980, Allis 1994).
- Groupe d'automorphismes d'ordre **192** (48 symétries du cube × 4 permutations uniformes des
  coordonnées commutant avec l'inversion `i → 3-i` : id, `(1 2)`, `(0 1)(2 3)`, …).
  L'appli n'en utilise que 48 dans `canonicalSignature`.
- Sous ces 192 symétries : **2 classes de cases** seulement — 16 « fortes » (8 coins + 8 centres,
  7 lignes chacune) et 48 « faibles » (4 lignes). Après un premier coup en coin (case 0), la
  défense a **12 réponses** distinctes : 1 3 5 6 7 15 21 22 23 27 31 63.

## Code (Java, sans dépendance)

| Fichier | Rôle |
|---|---|
| `Qubic.java` | géométrie bitboard (`long` = 64 cases), 76 masques de lignes, 192 symétries (tables par octet), forme canonique exacte, menaces |
| `Vcf.java` | *Victory by Continuous Forcing* : gain par suite de menaces (3 alignés) forçant l'adversaire, finissant sur une double menace ; table de transposition ; budget de nœuds |
| `Solver.java` | df-pn (proof-number search en profondeur, astuce 1+ε) sur formes canoniques exactes ; feuilles fermées par VCF |
| `Solver2.java` | même chose avec un hachage Zobrist invariant par symétrie (min sur les 192 images), incrémental ; options `nullPn`, `nullDn`, `topK` |
| `Strategy.java` | extraction d'un livre (position canonique → coup) depuis une preuve + vérification exhaustive du joueur « règles + livre + VCF » contre toutes les réponses (non utilisé faute de preuve) |
| `Bench.java` | positions de milieu de partie générées par heuristique, chronométrage du solveur |
| `Main.java` | `info`, `solve [coups…]`, `children [coups…]`, `bench` (options en `-Dnom=valeur`) |

Compilation / exécution :

```
javac -d out src/*.java
java -Xmx4g -DnullPn=10 -cp out Main solve 0 63      # coin puis réponse 63
java -Dplies=14 -Dcount=24 -DmaxMid=300000 -cp out Main bench
```

## Résultats

- Racine (plateau vide), df-pn simple : ~19 000 nœuds/s, aucune convergence en minutes.
- Heuristique `nullPn` (coup de l'attaquant qui ne menace aucun VCF ⇒ pn initial élevé) : divise
  par ~3 le coût des évaluations mais ne fait pas converger.
- Les 12 réponses au coin, en parallèle, 10 min chacune : **aucune résolue** ; les pn des fils
  restent tous entre 55 et 130 (recherche quasi en largeur : le début de partie n'a aucune menace,
  l'heuristique ne discrimine pas).
- Banc d'essai (positions heuristiques) : à 10 coups, résolu en < 12 s ; à 14 coups, la plupart en
  < 1 s mais certaines positions équilibrées restent non résolues à 300 000 nœuds.
- `topK` (l'attaquant ne considère que ses k meilleurs coups selon une heuristique de lignes) :
  plus rapide mais **fait rater des gains** (k = 4 : faux « pas de gain ») — l'heuristique de
  lignes est trop faible pour élaguer.

## Leçons

- Le goulot est le **début de partie** : il faut une bonne fonction d'orientation (politique /
  valeur) pour ordonner les coups de l'attaquant ; c'est exactement ce qu'un réseau de neurones
  peut apporter (preuve guidée par réseau = piste pour plus tard).
- La tactique (suites de menaces forcées) est, elle, résolue exactement et vite par VCF : à
  réutiliser tel quel dans tout joueur ou entraînement.
