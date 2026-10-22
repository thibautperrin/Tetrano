# Tetrano — Plan de réécriture Kotlin / Compose

> Plan de travail de la réécriture 2026 (rédigé le 2026-09-17, réalisé depuis), conservé pour
> l’histoire du projet. Source : le dépôt Java de 2015 ; cible : ce dépôt.
> Depuis, en 5.0 : les étoiles ont été supprimées (indices du Challenge gratuits) et tous les
> écrans sont passés au noir et jaune.

---

## 1. Contexte

L'application d'origine (2015) : 124 fichiers, ~7 000 lignes de Java, `minSdk 8` / `targetSdk 23`,
AGP 1.5.0, support-v7 23.1.1, `applicationId thibautperrin.tetrano`, versionCode 18 / versionName 4.1.

Fonctionnalités existantes :

| Domaine | Classes d'origine | Devenir |
|---|---|---|
| Moteur de jeu + IA | `Intelligence2/` (`AnoAlig`, `Ensemble`, `Partie`, `Statique`, `StatiqueAlig`, `IA`) | **Porté fidèlement** en Kotlin pur |
| Gestion de partie | `packageGestionJeu/` | Réécrit (ViewModel + services domaine) |
| Rendu | `packageSurfaceGraphView/` (SurfaceView + Thread + Handler) | **100 % Compose Canvas** |
| Mode Challenge (245 niveaux) | `packageChallenge/` | Conservé, données migrées |
| Parties sauvegardées | `packageSavedGame/` (SQLite brut) | Room, données migrées |
| Bluetooth | `packageConnexions/packageBluetooth/` | **Supprimé** |
| Jeu en ligne | `packageConnexions/packageJeuEnLigne/` | **Reporté** (bouton conservé, désactivé) |
| Règles / exemples | `reglesDuJeu/` | Réécrit en Compose |
| Réglages | `SettingsActivity`, `TacheView` | Réécrit en Compose |

Règles du jeu (pour mémoire) : plateau 4×4 cases, 4 tailles d'anneaux → 64 emplacements
(`ring = x + 4y + 16·taille`), 76 alignements gagnants. Gagne qui aligne 4 anneaux de sa couleur
(concentriques dans une case, ou en ligne/colonne/diagonale à taille constante, croissante ou décroissante).

---

## 2. Décisions actées

| Sujet | Décision |
|---|---|
| Langue du code | **Tout en anglais** (identifiants, commentaires, KDoc). Glossaire FR→EN en §5 pour la traçabilité avec l'ancien moteur. |
| Données utilisateur | **Migration complète** : parties sauvegardées, niveau challenge, étoiles, statistiques, réglages. |
| minSdk | **26** (Android 8.0) |
| Historique Git | **Clone de l'ancien repo** + tag `v4.1-legacy` + commit « table rase » sur une branche `main`. |
| applicationId | **`thibautperrin.tetrano`** — inchangé (mise à jour Play Store). |
| namespace Kotlin | `fr.thibautperrin.tetrano` (le namespace n'a aucun impact sur le Play Store). |
| Version | versionCode **19**, versionName **"5.0"**. |
| Architecture | 2 modules : `:engine` (Kotlin/JVM pur) + `:app` (Android). |

---

## 3. Cible technique

Aligné sur le projet `TestCar` du même poste (versions installées et validées) :

```toml
# gradle/libs.versions.toml
agp        = "9.4.0"
kotlin     = "2.3.20"
ksp        = "2.3.12"
hilt       = "2.60.1"
composeBom = "2026.09.00"
# Gradle wrapper 9.7.1
```

Complément à **résoudre au moment de l'exécution** (versions stables du jour, à vérifier sur
`https://developer.android.com/jetpack/androidx/versions`) :
`androidx.room`, `androidx.navigation:navigation-compose`, `androidx.datastore:datastore-preferences`,
`androidx.lifecycle`, `androidx.core:core-splashscreen`, `kotlinx-coroutines`, `kotlinx-serialization`.

Réglages module `:app` :

```
compileSdk 37 / targetSdk 37 / minSdk 26
JavaVersion.VERSION_17, jvmTarget = "17"
buildFeatures { compose true }
buildTypes.release { minifyEnabled true ; shrinkResources true ; R8 full mode }
```

Réglages module `:engine` : plugin `org.jetbrains.kotlin.jvm` **uniquement** (aucune dépendance Android).
C'est ce qui permet d'exécuter les tests du moteur en JVM local (rapides) là où l'ancien projet devait
passer par `androidTest` (à cause de `android.os.Handler` et `LongSparseArray` dans `IA`).

`gradle.properties` :

```
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.caching=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

## 4. Arborescence cible

```
Tetrano/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── docs/
│   ├── PLAN.md                     ← ce document
│   ├── GLOSSARY.md                 ← correspondance FR (2015) → EN (2026)
│   ├── ENGINE_FIDELITY.md          ← contrat de fidélité du moteur
│   └── ONLINE_PROTOCOL.md          ← protocole du jeu en ligne, pour plus tard
├── engine/                         ← Kotlin/JVM pur, zéro dépendance Android
│   ├── src/main/kotlin/fr/thibautperrin/tetrano/engine/
│   │   ├── BoardTables.kt          (ex Statique)
│   │   ├── LineOps.kt              (ex StatiqueAlig)
│   │   ├── LineSet.kt              (ex Ensemble)
│   │   ├── BoardState.kt           (ex AnoAlig)
│   │   ├── Game.kt                 (ex Partie)
│   │   ├── ai/Ai.kt                (ex IA)
│   │   ├── ai/AiProgress.kt
│   │   └── util/LongByteMap.kt, util/IntScratch.kt
│   └── src/test/…                  (tests portés + différentiels + allocations)
└── app/
    └── src/main/kotlin/fr/thibautperrin/tetrano/
        ├── TetranoApplication.kt   (@HiltAndroidApp)
        ├── MainActivity.kt         (activité unique, @AndroidEntryPoint)
        ├── di/                     (AppModule, DatabaseModule, DispatcherModule)
        ├── data/
        │   ├── db/                 (TetranoDatabase, entities, DAOs, migrations)
        │   ├── prefs/              (DataStore : settings, stats, flags)
        │   ├── assets/             (ChallengeLevelsDataSource → challenge.bin)
        │   ├── legacy/             (LegacyDataImporter + lecteurs)
        │   └── repository/         (SavedGame, Settings, Challenge, Stats)
        ├── domain/
        │   ├── model/              (modèles métier immuables)
        │   └── service/            (GameSessionService, AiService, ChallengeService,
        │                             StarService, SavedGameService)
        └── ui/
            ├── theme/              (Color, Type, Theme)
            ├── navigation/         (TetranoNavHost, routes @Serializable)
            ├── components/         (BoardCanvas, RingTray, TetranoBackground,
            │                         ColorBlob, IconTextButton, StarBadge)
            ├── home/  play/  game/  challenge/
            └── savedgames/  settings/  rules/
                     chaque écran = Screen.kt + ViewModel.kt + UiModel.kt
```

Convention par écran :

- `XxxUiModel.kt` — data class immuable, **aucun type Android**, tout est prêt à afficher.
- `XxxViewModel.kt` — `@HiltViewModel`, expose `StateFlow<XxxUiModel>`, reçoit des `XxxAction`.
- `XxxScreen.kt` — `@Composable` sans état, prend `uiModel` + `onAction: (XxxAction) -> Unit`,
  plus un `XxxRoute` qui branche le ViewModel (facilite les `@Preview`).

---

## 5. Glossaire FR (2015) → EN (2026)

À écrire dans `docs/GLOSSARY.md` ; sert à relire l'ancien code en parallèle.

| 2015 | 2026 | Note |
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

---

## 6. Le moteur : contrat de fidélité + discipline anti-GC

C'est **le cœur du travail**. Deux exigences simultanées : *fidélité de comportement* et *sobriété mémoire*.
À documenter dans `docs/ENGINE_FIDELITY.md`.

### 6.1 Contrat de fidélité — ce qui ne doit surtout pas bouger

1. **Ordre de parcours de `LineSet.next()`.** L'ensemble est une pile : `next(-1)` renvoie le dernier
   inséré, puis on remonte par `posPrec`. L'IA dépend de cet ordre (dans plusieurs boucles, le premier
   résultat trouvé l'emporte). Portage **à l'identique**, y compris la logique de `remove` et le seuil
   `size > taille / 4` dans `clear()`.
2. **Arithmétique sur `byte`.** L'original écrit `(byte)(alig - 3 + 4*j)`, `(byte)(3*puissance3j)`,
   `tableau[i] += puissance3j * …` sur des `byte`. En Kotlin, `Byte + Byte → Int` : il faut **rétablir
   explicitement** chaque troncature avec `.toByte()` là où l'original en avait une, et nulle part ailleurs.
   Un test dédié compare `signature()` / `canonicalSignature()` sur 10 000 positions aléatoires.
3. **Tables littérales.** `tabAlignements` (76×4) et les 3 tableaux de poids
   (`{10,7,-12,40,40,6,10,10,40,0}`, `{10,6,10,10,40,7,-12,40,40,0}`,
   `{5,3,5,40,40,5,15,40,40,0}`) recopiés **valeur par valeur**.
4. **Seuils de niveau.** Toute la table de décision de `jouerNiveau` (bornes 10/20/30/35/40/50/60/70/80/90,
   tirages `rand = (int)(X + Math.random()*Y)`, comparaisons `niveau > rand`) reproduite telle quelle,
   y compris `jouerNiveauSuperFort` (ordre de fourchette 4→8 selon 89/92/96/100, blocage 2→4).
5. **Départages.** `choixAnneauPoints` : comparaison `>=`, buffer de 20 candidats, et la branche
   `if (pointsCourant == best && taille < 20)` conservée **telle quelle** : quand 20 candidats ex æquo
   sont atteints, le 21e réinitialise la liste. C'est le comportement historique, on ne le « corrige » pas.
6. **Encodage `getDejaJoue`.** Long : 4 bits de taille en position 60, puis N paires sur 10 bits,
   maintenues triées par ordre croissant. Identique au bit près.
7. **Symétries.** `getAnneauSymetrique` / `getAnneauSymetrieInverse` : les deux `switch` ne sont **pas**
   l'inverse exact l'un de l'autre (cas 3 et 4 permutés) — c'est voulu, recopier sans « corriger ».
8. **Bornes.** `if (nbAnneauxJoues > 61) return -1` et le ré-appel récursif avec
   `ordre = (64 - 3 - n) / 2`.

### 6.2 Discipline anti-GC

L'exigence porte **sur la recherche de fourchettes** (chemin chaud : des millions d'appels par coup à
niveau 100), pas sur l'UI. Objectif : **une recherche complète n'alloue qu'une quantité constante de
mémoire, indépendante de la profondeur**.

| Allocation d'origine | Problème | Remplacement |
|---|---|---|
| `Statique.getAlignements(int)` → `new byte[4 ou 7]` | **appelé 2× par `play`/`undo`**, soit des millions de fois | `BoardTables.lineCount(ring)` + `lineAt(ring, i)` lisant le tableau plat — 0 allocation |
| `listesAnneauxFourchette` → `new int[2n]` puis `new int[size]` | 2 allocations **par nœud** de l'arbre de recherche | pool `IntScratch` : un `IntArray` pré-alloué par profondeur (max 12) ; la fonction renvoie le `size` et écrit dans le buffer de sa profondeur |
| `StatiqueAlig.trov2anoIndice` → `new byte[3]` | 1 allocation par alignement visité | renvoie un `Int` empaqueté (`subIndex`, `ring1`, `ring2` sur 6 bits chacun) |
| `LongSparseArray<Byte>` ×6 | **autoboxing de chaque `Byte`** + croissance de tableaux | `LongByteMap` : adressage ouvert sur `LongArray`/`ByteArray` pré-alloués (capacité 1<<16), `clear()` en O(1) par compteur de génération (`IntArray stamps` + `generation++`), ce qui permet aussi de distinguer « absent » de la valeur `-1` |
| `fourchetteAvancee` → `superCopy` (byte[] par solution) | chemin `niveau < 90` uniquement | `ByteArray(24)` pré-alloué dans `Ai` + longueur explicite |
| `Statique.tabAlignements` (76 sous-tableaux) | 76 objets + indirection | `ByteArray(76*4)` plat + accesseurs |
| `BitSet` | objet + `long[]` | `LongArray(1)` pour les 64 anneaux, `LongArray(76)` pour la table d'appartenance |
| `Math.random()` | non déterministe, non testable | une instance `kotlin.random.Random` injectée dans `Ai` (graine fixable en test) |

Règles Kotlin à respecter dans `:engine` (à mettre en en-tête de module) :

- Types primitifs partout : `ByteArray` / `IntArray` / `LongArray`. **Jamais** `Array<Byte>`,
  `List<Byte>`, `Byte?`, `Sequence`, `Pair`, `Triple`.
- Aucune lambda capturante dans les boucles chaudes.
- Aucune destructuration ni `data class` dans le chemin de recherche ; valeurs composites empaquetées
  dans un `Int`/`Long`, éventuellement exposées via `@JvmInline value class`.
- Aucun `for (x in arrayOf(...))` (crée un tableau), aucun `.map`/`.filter`/`.forEach` sur collections.
- Pas de `String` ni de log dans le chemin chaud (l'original a des `System.out.println` : les retirer).
- `Ai` détient **tous** ses buffers en champs, alloués une seule fois dans le constructeur.

### 6.3 Concurrence

`ThreadIA` (Thread + `Thread.sleep(100)` de polling + `Handler`) est remplacé par :

```kotlin
class AiService @Inject constructor(
    @Dispatcher(Default) private val dispatcher: CoroutineDispatcher,
) {
    suspend fun chooseRing(
        moves: ByteArray,
        level: Int,
        onProgress: (phase: Int, current: Int, total: Int) -> Unit,
    ): Int
}
```

- exécuté sur `Dispatchers.Default`, annulable (`ensureActive()` dans les boucles de `blockAdvancedFork`
  et `bigAdvancedFork`, là où l'original envoyait un message de progression) ;
- **instance de `BoardState` dédiée** à l'IA, reconstruite depuis la liste des coups : on conserve
  l'isolation d'origine (`ThreadIA` avait sa propre `Partie`), aucun état mutable partagé avec l'UI ;
- la progression — utilisée pour dessiner l'arc partiel sur les anneaux de la réserve (0→180° pour la
  recherche de fourchette, 180→360° pour le blocage) — passe par un callback à paramètres primitifs,
  throttlé côté ViewModel avant d'atteindre l'état Compose.

---

## 7. Phases d'exécution

Chaque phase = un ou plusieurs commits. Ne pas passer à la suivante tant que la précédente ne compile
pas et que ses tests ne passent pas.

### P0 — Mise en place du dépôt

```bash
git clone <dépôt Java de 2015> Tetrano
cd Tetrano
git remote remove origin
git tag -a v4.1-legacy -m "Derniere version Java (2015), versionCode 18 / 4.1" f60fca2
git switch -c main
```

1. **Sauvegarder hors de l'arbre les éléments à conserver** avant la table rase :
   - `app/src/main/res/raw/challenge.bin` (3 430 o — **irremplaçable** : 245 niveaux × 14 octets) ;
   - `app/src/main/res/values*/strings.xml` (traductions en, de, es, fr, it, ru) ;
   - `ic_launcher.png` comme référence visuelle pour la future icône vectorielle.
2. **Commit « table rase »** : `git rm -r` de la totalité des 124 fichiers.
   Message : `chore!: remove legacy Java implementation (rewrite in Kotlin/Compose)`.
   Corps : rappeler que le code 2015 reste accessible via le tag `v4.1-legacy`
   (`git show v4.1-legacy:app/src/main/java/thibautperrin/tetrano/Intelligence2/IA.java`).
3. Commit : `.gitignore` moderne (Android + IntelliJ + `local.properties` + `.kotlin/`),
   `docs/PLAN.md` (ce fichier), `docs/GLOSSARY.md`, `docs/ONLINE_PROTOCOL.md`.

> La branche `master` reste figée sur `f60fca2` : l'état 2015 est consultable sans rien exhumer.
> `git log main` affiche les 8 commits de 2015 puis toute la réécriture.

### P1 — Squelette Gradle

- `settings.gradle.kts` (pluginManagement + `include(":app", ":engine")`, `rootProject.name = "Tetrano"`),
  `build.gradle.kts` racine, `gradle/libs.versions.toml`, wrapper Gradle 9.7.1.
- `:engine` : plugin `kotlin("jvm")`, dépendances `kotlin-test` / `junit` uniquement.
- `:app` : AGP + Kotlin + Compose + Hilt + KSP + serialization, `namespace fr.thibautperrin.tetrano`,
  `applicationId "thibautperrin.tetrano"`, versionCode 19 / versionName "5.0".
- `AndroidManifest.xml` minimal : une seule `MainActivity` exportée, `TetranoApplication`,
  **aucune permission** (Bluetooth et réseau supprimés ; `INTERNET` reviendra avec le jeu en ligne),
  `android:allowBackup="true"` + `dataExtractionRules`.
- `MainActivity` affichant « Hello Tetrano » → **premier build vert**.
- Commit : `build: bootstrap Gradle project (AGP 9.4, Kotlin 2.3, Compose, Hilt)`.

### P2 — Moteur (`:engine`) — portage fidèle

Ordre de portage (chaque classe = 1 commit, avec ses tests) :

1. `BoardTables` (ex `Statique`) — tables plates, accesseurs sans allocation, symétries.
2. `LineOps` (ex `StatiqueAlig`) — `ringCount1/2`, `lineKind`, `playLine`/`undoLine`,
   `firstFreeRing`/`lastFreeRing`/`lastRemainingRing`, `packedFreePair` (ex `trov2anoIndice` → `Int`).
3. `LineSet` (ex `Ensemble`).
4. `BoardState` (ex `AnoAlig`) — dont les constructeurs par signature et par symétrie,
   `signature(out)` / `canonicalSignature(out)` écrivant dans un buffer fourni.
5. `Game` (ex `Partie`).
6. `util/LongByteMap`, `util/IntScratch`.
7. `ai/Ai` (ex `IA`), dans l'ordre : `simpleFork`, `forkCandidates`, `advancedFork`, `chooseByWeights`,
   `chooseRandom`, `blockAdvancedFork`, `playLevel`, `playLevelExpert`, `bigAdvancedFork`,
   `isAdvancedFork`, `packMoves`, `playChallenge`, `playStar`.

Chaque fonction portée reçoit un commentaire `// ex IA.fourchetteAvancee()` pour la traçabilité.

### P3 — Tests du moteur

1. **Portage des tests existants** de `androidTest` vers `:engine/src/test` :
   `AnoAligTest`, `EnsembleTest`, `IATest` (360 l.), `PartieTest`, `StatiqueTest`, `StatiqueAligTest`.
   Ils utilisent `junit.framework.TestCase` : conversion mécanique. Ils tournent désormais en JVM
   (quelques secondes au lieu d'un émulateur).
2. **Test différentiel contre le moteur Java d'origine** — le garde-fou décisif :
   - source set de test `:engine/src/test/java/legacy/` contenant les 6 classes Java de 2015
     (récupérées via `git show v4.1-legacy:…`) **non modifiées**, plus deux stubs minimaux
     `android/os/Handler.java` (méthodes no-op) et `android/support/v4/util/LongSparseArray.java`
     (délégation à `HashMap<Long, Byte>`) pour les compiler hors Android ;
   - le test génère N positions pseudo-aléatoires légales (graine fixe) et compare **fonction par
     fonction** les sorties *déterministes* des deux moteurs : `isFourchetteSimple`,
     `fourchetteAvancee(ordre 0..4)`, `bigFourchetteAvancee(ordre 0..5)`,
     `bloqueFourchetteAvanceeSimplifiee(ordre 0..3)`, `playChallenge`, `getSignature`,
     `getLowestSignature`, `getDejaJoue`, et l'ordre de parcours de `Ensemble` ;
   - les fonctions à tirage aléatoire (`choixAnneauPoints`, `jouerNiveau`) sont testées séparément en
     injectant la même séquence de tirages des deux côtés ;
   - **critère d'acceptation : 0 divergence sur ≥ 50 000 positions.**
3. **Test d'allocation** : `ThreadMXBean.getThreadAllocatedBytes()` autour d'un `bigAdvancedFork(4)`
   sur une position dense → assertion `allocatedBytes < seuil` (à figer après la première mesure).
   Empêche toute régression GC future.
4. **Benchmark simple** : temps de `playLevel(level = 100)` sur 20 positions de référence, journalisé.
   Comparatif avant/après et garde-fou de performance.

> Une fois P3 vert, garder le source set `legacy/` dans le dépôt : il ne pèse rien, ne part pas dans
> l'APK, et documente l'origine. Le marquer `@Deprecated("reference implementation, do not use")`.

### P4 — Données (`:app/data`)

1. **Room** — conserver **exactement** le nom de fichier d'origine `savedGames.db` pour retrouver les
   données des utilisateurs mis à jour.
   - `@Database(version = 2)`, entités :
     - `SavedGameEntity` → table `saved_games` (`name` PK, `rings` BLOB, `userBegins` INT,
       `userColor` INT, `adversaryColor` INT, `createdAt` INT, `gameMode` INT) ;
     - `ChallengeProgressEntity` → table `challenge_progress`, **une seule ligne** (`id = 0`) :
       `level`, `symmetry`, `normallyPlayed` BLOB, `playedByStars` BLOB, `adFlag`.
   - `MIGRATION_1_2` : l'ancienne base (`SQLiteOpenHelperSavedGame`, `DATABASE_VERSION = 1`,
     table `savedgames` : `name TEXT PK, rings TEXT, begin INTEGER, ucolor INTEGER, acolor INTEGER`)
     est convertie — création de `saved_games`, `INSERT … SELECT`, `DROP TABLE savedgames`,
     création de `challenge_progress`. Si le fichier n'existe pas, Room crée directement en v2.
   - DAOs renvoyant des `Flow<List<…>>`.
2. **DataStore Preferences** (`settings.preferences_pb`) : couleurs joueur 1/2/utilisateur/adversaire,
   noms, niveau IA, nombre d'étoiles, compteurs de parties (2 joueurs, ordinateur, victoires, en ligne,
   bluetooth), flag `legacyImportDone`.
3. **`ChallengeLevelsDataSource`** : lit `res/raw/challenge.bin` une fois (245 × 14 octets) dans un
   `ByteArray` ; expose `signature(level)`, `forkOrder(level)` (= `dernierOctet / 2 - 1`),
   `isInvertPlayer(level)` (= `dernierOctet % 2 == 0`). Toute la logique de validation/récupération de
   `MemoryVarsChallenge.verifyCorrectAndSaveData()` est reprise, mais **sans lever d'exception** :
   en cas d'incohérence, réinitialisation propre + journalisation.
4. Tests Room (`MigrationTestHelper` avec un `savedGames.db` v1 de fixture, généré une fois avec
   l'ancien schéma).

### P5 — Migration des données legacy (une seule fois, au 1er lancement)

`LegacyDataImporter`, appelé au démarrage en coroutine `IO`, gardé par le flag `legacyImportDone` :

| Source 2015 | Contenu | Destination |
|---|---|---|
| `savedGames.db` (table `savedgames`) | parties sauvegardées | Room `MIGRATION_1_2` (automatique) |
| fichier interne `challengeVars.bin` | `levelChallenge` (int), `numberStars` (int), `numberSymetrieChallenge` (byte), `nextTimeAdvert` (bool), `normallyPlayedChallenge` (byte[] préfixé longueur), `playedByStarsChallenge` | Room `challenge_progress` + DataStore (étoiles) |
| SharedPreferences `preferences` | `player1Color`, `player2Color`, `userColor`, `adversaryColor`, `player1Name`, `player2Namme` *(sic — faute d'origine, à respecter à la lecture)*, `userName`, `level` | DataStore |
| SharedPreferences `gameLog` | `nbGames2P`, `nbGamesComp`, `nbWonGamesComp`, `nbGamesOnline`, `nbGamesBlt` | DataStore |
| `bluetoothDevices.db` | appareils Bluetooth | **supprimé** (les étoiles déjà gagnées sont conservées via `numberStars`) |

Le format binaire de `challengeVars.bin` est décrit par `BinaryFileReadingWriting` : entiers sur
4 octets little-endian avec bit de signe dans l'octet de poids fort, tableaux préfixés d'un octet de
longueur, tableau vide ≡ `null`. **Porter ce lecteur à l'identique** dans
`data/legacy/LegacyBinaryReader.kt`, avec le test `BinaryFileReadingWritingTest` porté lui aussi.

Après import réussi : `legacyImportDone = true`, puis suppression de `challengeVars.bin`,
`bluetoothDevices.db` et des deux SharedPreferences.

**Test d'acceptation** : fixtures réalistes (un `challengeVars.bin`, une base v1, deux XML de
SharedPreferences) dans `androidTest/assets`, import exécuté, vérification de l'état final.

### P6 — Domaine (`:app/domain`)

- `GameSessionService` — remplace `GestionJeu` et ses 4 sous-classes. Une seule implémentation
  paramétrée par `GameMode` (`TWO_PLAYERS`, `VS_COMPUTER`, `ONLINE` non implémenté), tenant l'instance
  `Game` du moteur, le joueur courant, couleurs/noms, l'état `busy`, et les actions `play(ring)`,
  `undo()`, `restart(changeStarter)`, `blink()`, `saveForLater(name)`.
  La logique d'annulation asymétrique (contre l'ordinateur on défait **2** coups, à 2 joueurs un seul)
  est reprise de `GestionJeuContreOrdinateur.cliqueAnnuler` / `GestionJeu2Joueurs.cliqueAnnuler`.
- `AiService` (§6.3).
- `ChallengeService` — reprend `Challenge` : `playComputer`, `userTriedPlaying`, `useAStar` (dont le
  rejeu complet de la partie quand l'utilisateur consomme une étoile), `gameFinished`, `isWonByUser`,
  `blinkingRings`.
- `StarService` — reprend `GameLog` : paliers `{5,10,20,50,100,200,500,1000}` par mode, étoile
  « tous les modes essayés », étoile « N victoires contre l'ordinateur ».
  ⚠️ **À trancher à l'implémentation** : les étoiles « les 4 modes » et « nouvel adversaire Bluetooth »
  n'ont plus de source. Proposition par défaut : « tous les modes » devient *2 joueurs + ordinateur +
  challenge*, l'étoile Bluetooth disparaît (les étoiles déjà acquises restent acquises via la migration),
  et on ajoute une source de compensation — par exemple une étoile tous les 10 niveaux de challenge réussis.
- `SavedGameService` — création/renommage (suffixe ` (2)`, ` (3)`… en cas de doublon, comme l'original),
  suppression, relecture.

### P7 — UI socle

1. **Thème** — Material 3, couleurs reprises de `colors.xml` (noir, gris, rouge, vert, bleu, vert clair,
   violet, marron, orange, + couleurs de boutons), thème **clair uniquement** (comme l'original : pages
   blanches, barre d'action sombre), typographie, et les `dimens.xml` d'origine par taille d'écran
   (`LegacyDimens`) utilisés comme tailles maximales avec texte auto-ajusté (rien n'est jamais coupé).
2. **`TetranoBackground`** — affiche `fond_inverse` (accueil et menu Jouer uniquement, comme l'original).
   ~~Régénéré par programme~~ : la version générative ne ressemblait pas à l'original et la vectorisation
   du collage (fines lignes de 1 px) ne le restitue pas fidèlement (1,2 Mo de tracés, lignes en
   pointillés). **Repli retenu (cf. risques)** : les deux originaux (portrait 450×800, paysage 800×450)
   en WebP sans perte, identiques au pixel près (245 Ko + 179 Ko), choisis selon la place disponible,
   `ContentScale.Crop`.
3. **`BoardCanvas`** — remplace `SurfaceGraphView` + `ThreadSurfaceGraphView` + `HandlerSurfaceGraphView`
   + `Dessin`. Un seul `Canvas` Compose :
   - coefficients de dessin **repris tels quels** de `StaticFields` (`thicknessBorderCoeff = 0.01262`,
     `thicknessSeparationLinesCoeff = 0.009464`, `thicknessRingsCoeff = 0.014`,
     `distanceLinesCoeff = 0.236593`, `shortestRadiusCoeff = 0.01893`, `diffRadiusCoeff = 0.02839`,
     `pos0Coeff`, `distanceRingsCentersCoeff`) → rendu identique au pixel près ;
   - mise en page : portrait = plateau carré + réserve de 4 anneaux en dessous (ratio 5/4) ;
     paysage = réserve à droite ; mode `square` (sans réserve) pour les écrans Règles/Exemples et la
     visualisation d'une partie sauvegardée — exactement les 3 cas de l'attribut `square` d'origine ;
   - `Paint` / `Path` hissés dans des `remember`, aucune allocation par frame.
4. **Interaction** — `pointerInput` + `detectDragGestures`, reproduisant `onTouchEvent` : on saisit un
   anneau en posant le doigt dans la réserve (taille = `posU * 4 / côté`), on le relâche sur une case →
   `ring = x + 4y + 16·taille` ; relâcher dans la réserve annule la saisie.
5. **Animations** — remplacent les `Handler.sendMessageDelayed` :
   - déplacement de l'anneau adverse : `Animatable(0f→1f)` en `tween(1500 ms)` (durées d'origine
     conservées : 1500 ms / 40 frames pour l'adversaire, 1200 ms / 30 frames pour une annulation simple,
     2 × 800 ms pour une double annulation) ;
   - clignotement : `repeat(5)` avec période 400 ms ;
   - arc de progression de l'IA sur les anneaux de la réserve (0→180° recherche, 180→360° blocage).
6. **Composants** : `RingTray`, `ColorBlob` (ex `TacheView` — la « tache » est déjà un `Path` de
   29 points normalisés, à recopier tel quel), `IconTextButton` (ex `BoutonsJouer`), `StarBadge`.
7. **Navigation** — Navigation Compose, routes `@Serializable` typées, activité unique.

### P8 — Écrans

| Écran | Remplace | Contenu |
|---|---|---|
| `HomeScreen` | `MainActivity` | Jouer / Règles du jeu / Réglages / À propos + compteur d'étoiles |
| `PlayMenuScreen` | `JouerActivity` | 2 joueurs, 1 joueur, **Jeu en ligne (désactivé, « bientôt »)**, Parties sauvegardées, Challenge. *Bouton Bluetooth supprimé.* |
| `TwoPlayersSetupScreen` | `JouerDeuxJoueurActivity` | noms + couleurs des 2 joueurs |
| `ComputerSetupScreen` | `JouerContreOrdiActivity` | slider niveau 0..100 (+ boutons ±1), qui commence ; gère aussi la reprise d'une partie sauvegardée (la logique d'inversion des rôles décrite dans le KDoc d'origine est à reprendre telle quelle) |
| `GameScreen` | `JeuActivity` | plateau + 3 boutons (Recommencer / Clignoter / Annuler) + menu (Réglages, Sauvegarder) + titre dynamique |
| `ChallengeScreen` | `ChallengeActivity` | plateau + « anneaux utilisés : n/m » + Abandonner + menu (Réglages, Explications, Utiliser une étoile) + dialogues de fin |
| `SavedGamesScreen` | `ListePartiesEnregistreesActivity` | liste + Voir / Renommer / Supprimer / Continuer (2 joueurs ou ordinateur) |
| `SavedGameViewerScreen` | `WatchSavedGameActivity` | rejeu coup par coup (boutons précédent/suivant, ex `ButtonNextPrevious`) |
| `SettingsScreen` | `SettingsActivity` | nom utilisateur + 2 palettes de 8 couleurs (`ColorBlob`), avec la contrainte d'origine « les deux couleurs doivent différer » |
| `RulesScreen` | `NoticeFormelleActivity` | texte des règles + accès aux exemples |
| `WinningExamplesScreen` | `ExamplesActivity` | 6 plateaux statiques (alignements 48/53/58/63 ; 40..43 ; 9/25/41/57 ; 2/22/42/62 ; 12/25/38/51 ; 1/5/9/13) |

Rotation : plus de `onConfigurationChanged` ni de `Parcelable` manuel (`SauvegardePartie`) — l'état vit
dans le ViewModel, et `SavedStateHandle` couvre la mort de process (sérialiser la liste des coups + le
joueur ayant commencé, exactement ce que faisait `SauvegardePartie`).

### P9 — Ressources & identité visuelle

- **Strings** : reprise des 6 langues (en par défaut, de, es, fr, it, ru), clés normalisées en
  `snake_case`, suppression des clés Bluetooth, ajout des nouvelles (« bientôt disponible », etc.).
  Vérifier les apostrophes typographiques déjà présentes et les `%1$d` des messages d'étoiles.
- **Vectorisation** :

  | PNG d'origine | Remplacement |
  |---|---|
  | `bouton_1_joueur`, `bouton_2_joueurs`, `bouton_challenge`, `bouton_partie_sauvegarde`, `bouton_jeu_online` | vector drawables **tracés depuis les PNG d'origine** (potrace, calques par niveau de gris) : même dessin, format 2:1 |
  | `star` | vector drawable redessiné (étoile dorée à dégradé radial, contour orangé) |
  | `apply`, `reseau` | supprimés (utilisés seulement par le Bluetooth) |
  | `fond_inverse` (×3) | WebP sans perte des originaux portrait/paysage (§P7.2) |
  | `bouton_jeu_bluetooth` | supprimé |
  | `ic_launcher` | **icône adaptative** reconstruite depuis l'original 150×150 : plateau incliné (−21,8°), lignes, cases et anneaux redessinés à partir de la géométrie mesurée, titre « Tetrano » et son ombre tracés depuis le bitmap ; placée dans la zone sûre de 66 dp sur fond blanc, + couche `monochrome` |

- **Splash** : `androidx.core:core-splashscreen`.
- Objectif : **aucun PNG/JPEG dans l'APK** (seul `fond_inverse` reste raster, en WebP), aucun qualifier
  `-small`/`-large`/`-land` de drawable.

### P10 — Finition, perf, release

1. Passe perf : vérifier qu'aucune allocation n'a lieu dans le dessin du plateau (Layout Inspector /
   trace), re-run du test d'allocation du moteur, Baseline Profile (optionnel).
2. R8 : `minifyEnabled true`, `shrinkResources true`, règles Room/Hilt (normalement automatiques) ;
   vérifier que le moteur survit à l'obfuscation (il n'utilise pas de réflexion).
3. Tests d'intégration : partie complète contre l'IA niveau 100 (durée de réflexion acceptable),
   challenge niveau 1, sauvegarde/restauration, rotation, mort de process.
4. **Génération d'un AAB signé avec la clé d'origine** (cf. §9).
5. Commit final + tag `v5.0`.

---

## 8. Critères d'acceptation

| # | Critère | Comment |
|---|---|---|
| A1 | Les 6 suites de tests 2015 passent | tests JVM `:engine` |
| A2 | **0 divergence** moteur Kotlin vs Java sur ≥ 50 000 positions | test différentiel P3.2 |
| A3 | Une recherche `bigAdvancedFork(4)` alloue moins que le seuil figé | `getThreadAllocatedBytes` |
| A4 | Migration : parties, niveau challenge, étoiles, stats et réglages retrouvés | `androidTest` sur fixtures |
| A5 | Rendu du plateau identique à l'original | comparaison visuelle sur captures 2015 / 2026 |
| A6 | Rotation + mort de process sans perte de partie | test manuel + `SavedStateHandle` |
| A7 | Aucun PNG dans l'APK (hors `fond_inverse` en WebP), taille < 3 Mo | inspection de l'AAB |
| A8 | `./gradlew build` vert, zéro warning Kotlin non justifié | build local |

---

## 9. Publication Play Store — points de vigilance

1. **Clé de signature** : la mise à jour n'est possible qu'avec la clé ayant signé la v4.1, *ou* via
   Play App Signing si l'upload key est disponible. **À vérifier avant même de commencer** — c'est le
   seul point réellement bloquant pour « mettre à jour sur le Play Store ». Sinon, la seule issue est
   une nouvelle fiche avec un autre `applicationId`.
2. `versionCode` **19** > 18. Obligatoire.
3. Format **AAB** (l'APK n'est plus accepté pour les nouvelles versions).
4. `targetSdk` conforme à l'exigence Play du moment (37 ici).
5. Alignement 16 Ko des pages natives : sans code natif, non concerné.
6. Déclaration « sécurité des données » : l'app ne collecte plus rien (réseau et Bluetooth retirés)
   → mettre la fiche à jour en conséquence.
7. Passer de `minSdk 8` à `26` retire de la distribution les très vieux appareils ; Play leur conserve
   automatiquement l'ancienne version.

---

## 10. Supprimé / reporté

**Supprimé** : tout `packageConnexions/packageBluetooth/` (9 classes, ~1 500 lignes), la base
`bluetoothDevices.db`, les permissions Bluetooth, les chaînes associées, `bouton_jeu_bluetooth.png`,
et le reliquat publicitaire `StaticFields.PUBLISHER_KEY_ADBUDDIZ` (la publicité avait déjà été retirée
au commit `a61c07c`).

**Reporté (jeu en ligne)** : bouton conservé mais désactivé. À préserver malgré tout pour préparer le
retour : la constante `GameMode.ONLINE` (code 4, pour la compatibilité des statistiques migrées) et le
protocole d'origine documenté dans `StaticFields` (octets 70/71, 90–93, 100–105) — **le recopier dans
`docs/ONLINE_PROTOCOL.md` avant la table rase**, il n'existe nulle part ailleurs.
⚠️ Le serveur d'origine (`88.187.4.43:4`) n'existe très probablement plus : la réimplémentation
supposera de toute façon une nouvelle infrastructure.

---

## 11. Risques identifiés

| Risque | Parade |
|---|---|
| Régression silencieuse de l'IA (jeu « moins bon » sans erreur visible) | test différentiel A2, obligatoire **avant** toute optimisation |
| Troncatures `byte` oubliées à la traduction Java→Kotlin | test dédié sur `signature`/`canonicalSignature` + revue ligne à ligne de chaque `(byte)` de l'original |
| Perte des données utilisateur à la mise à jour | import legacy testé sur fixtures réelles ; ne supprimer les anciens fichiers qu'après import confirmé |
| Clé de signature perdue | **à vérifier en tout premier** (§9.1) |
| Le fond génératif ne « ressemble » pas assez à l'original | repli : garder `fond_inverse` sous forme d'un unique WebP |
| Versions de Room / Navigation / DataStore non figées ici | les résoudre en P1 et les inscrire dans `libs.versions.toml` |

---

## 12. Séquence de commits prévue

```
(8 commits de 2015, tag v4.1-legacy sur f60fca2)
 1. chore!: remove legacy Java implementation (rewrite in Kotlin/Compose)
 2. docs: add rewrite plan, glossary and online protocol reference
 3. build: bootstrap Gradle project (AGP 9.4, Kotlin 2.3, Compose, Hilt)
 4. feat(engine): port board tables and line operations
 5. feat(engine): port line set and board state
 6. feat(engine): port game state
 7. perf(engine): allocation-free scratch buffers and primitive long→byte map
 8. feat(engine): port AI (forks, blocking, levels, challenge)
 9. test(engine): port 2015 test suites to JVM
10. test(engine): differential test against the 2015 Java engine
11. test(engine): allocation budget for fork search
12. feat(data): Room database, DAOs and 1→2 migration
13. feat(data): preferences DataStore and challenge levels source
14. feat(data): import legacy SQLite, challengeVars.bin and SharedPreferences
15. feat(domain): game session, AI, challenge, stars and saved game services
16. feat(ui): theme, generative background and board canvas
17. feat(ui): drag & drop interaction and move animations
18. feat(ui): home, play menu and setup screens
19. feat(ui): game screen
20. feat(ui): challenge screen
21. feat(ui): saved games and viewer screens
22. feat(ui): settings, rules and winning examples screens
23. feat(res): vector assets, adaptive icon and 6-language strings
24. build: R8, shrinking and release configuration
25. chore: release 5.0 (versionCode 19)
```
