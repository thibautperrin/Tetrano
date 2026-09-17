# Glossaire FR (2015) → EN (2026)

Sert à relire l'ancien code (module Java, tag `v4.1-legacy`) en le mettant en regard du nouveau
moteur Kotlin (`:engine`).

| 2015 (FR) | 2026 (EN) | Note |
|---|---|---|
| anneau | `ring` | 0..63, `ring = x + 4·y + 16·size` |
| taille d'anneau | `ringSize` | 0..3, = coordonnée z |
| alignement | `line` | 0..75 |
| `Ensemble` | `LineSet` | ensemble d'octets, add/remove/contains en O(1) |
| `AnoAlig` | `BoardState` | état brut (rings, lines, sets, playedCount) |
| `Partie` | `Game` | état + historique + tour de jeu |
| `Statique` | `BoardTables` | tables statiques anneaux ↔ alignements |
| `StatiqueAlig` | `LineOps` | opérations sur l'octet d'un alignement |
| `IA` | `Ai` | |
| `jouer` / `dejouer` | `play` / `undo` | |
| fourchette simple | `simple fork` | double menace immédiate |
| fourchette avancée d'ordre n | `advanced fork of order n` | n coups forcés avant la fourchette |
| `bloqueFourchetteAvanceeSimplifiee` | `blockAdvancedFork` | |
| `reglages` (tableau de poids) | `weights` | `byte[10]`, indexé par n° d'ensemble |
| `numEnsemble` | `lineKind` | 0..9 |
| `signature` | `signature` | `byte[13]`, base 3, 5 anneaux par octet |
| `getLowestSignature` | `canonicalSignature` | min sur les 48 symétries |
| `prochainJoueur` | `nextPlayer` | 1 ou 2 |
| `getJoueurCommencant` | `startingPlayer` | |
| anneaux clignotants | `blinkingRings` | ligne gagnante ou dernier coup |
| `GestionJeu` | `GameSessionService` | |
| `SauvegardePartie` | `GameSnapshot` | |
| `SurfaceGraphView` / `Dessin` | `BoardCanvas` / `BoardDrawing` | |
| `TacheView` | `ColorBlob` | |
| `BoutonsJouer` | `IconTextButton` | |
| `MemoryVarsChallenge` | `ChallengeRepository` + `ChallengeState` | |
| `StarUsedBean` | `StarHint` | |
| `GameLog` | `StatsRepository` | |
| niveau IA (0..100) | `aiLevel` | |
| niveau challenge (1..245) | `challengeLevel` | |
