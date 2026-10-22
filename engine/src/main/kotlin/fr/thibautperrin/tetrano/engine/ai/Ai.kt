package fr.thibautperrin.tetrano.engine.ai

import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.BoardTables
import fr.thibautperrin.tetrano.engine.LineOps
import fr.thibautperrin.tetrano.engine.util.IntScratch
import fr.thibautperrin.tetrano.engine.util.LongByteMap
import kotlin.random.Random

/**
 * The Tetrano AI: fork detection, fork blocking, and the per-level move
 * selection policy. Ports `thibautperrin.tetrano.Intelligence2.IA` (see
 * docs/GLOSSARY.md) as closely as behavior allows — see
 * docs/PLAN.md §6.1 for the specific quirks that are preserved on purpose,
 * and §6.2 for why every method below that runs during a real search is
 * written to avoid allocating.
 *
 * One [Ai] instance owns a private [board] it freely plays/undoes moves on
 * while searching (mirroring `ThreadIA`'s own private `Partie`): it must
 * not be shared with anything that mutates the board concurrently.
 *
 * @param random source of randomness for move selection among ties. Ports
 *   the legacy code's direct `Math.random()` calls; injecting it (instead
 *   of a global default) makes the deterministic parts of the search
 *   testable against a fixed seed — see docs/PLAN.md §6.2.
 */
class Ai(
    private val board: BoardState,
    private val random: Random = Random.Default,
) {
    // Shared scratch state — see the class doc for why reuse across calls
    // (recursive or sequential) is safe here.
    private val bits = LongArray(1)
    private val scratch = IntScratch(maxDepth = MAX_SEARCH_DEPTH, capacityPerLevel = 2 * BoardTables.LINE_COUNT)
    private val forkChainBuffer = ByteArray(MAX_CHAIN_LENGTH)
    private val bestRingsScratch = ByteArray(MAX_TIED_CANDIDATES)
    private val packMovesScratch = LongArray(MEMO_DEPTH + 2)
    private val memo: Array<LongByteMap> = Array(MEMO_DEPTH) { LongByteMap(1 shl 16) }

    // ---- Simple (one-move) forks ----

    /**
     * If [player] can force a win in one more move by playing the
     * intersection of two of their own 2-ring lines, the ring to play
     * there; otherwise [NOT_FOUND]. Ports `isFourchetteSimple`.
     */
    fun simpleFork(player: Int): Int {
        if (board.playedCount > 61) return NOT_FOUND
        bits[0] = 0L
        val twoLineKind = if (player == 1) 2 else 6
        var line = board.lineSets[twoLineKind].next(-1)
        while (line != NONE) {
            val lineIndex = line.toInt()
            val ring1 = firstFreeRingOf(lineIndex)
            val ring2 = lastFreeRingOf(lineIndex)
            if (bitsGet(ring1)) return ring1
            bitsSet(ring1)
            if (bitsGet(ring2)) return ring2
            bitsSet(ring2)
            line = board.lineSets[twoLineKind].next(line)
        }
        return NOT_FOUND
    }

    // ---- Advanced fork candidate generation ----

    /**
     * Fills `scratch.bufferAt(depth)` with every ring [player] could play
     * that forces the opponent into a specific reply, without that reply
     * itself letting the opponent force *us* to block in turn. Returns how
     * many candidates were written.
     *
     * Each candidate packs `ring1` (the move [player] plays) in bits 0-5,
     * `ring2` (the opponent's forced reply) in bits 6-11, and — for
     * [isAdvancedFork]'s memoization key only — `12*line + 2*subIndex(+1)`
     * from bit 12 up. Ports `listesAnneauxFourchette`.
     */
    private fun forkCandidates(player: Int, depth: Int): Int {
        bits[0] = 0L
        val opponentTwoLineKind = if (player == 1) 6 else 2
        val ownTwoLineKind = if (player == 1) 2 else 6

        var line = board.lineSets[opponentTwoLineKind].next(-1)
        while (line != NONE) {
            val lineIndex = line.toInt()
            bitsSet(firstFreeRingOf(lineIndex))
            bitsSet(lastFreeRingOf(lineIndex))
            line = board.lineSets[opponentTwoLineKind].next(line)
        }

        var size = 0
        val buffer = scratch.bufferAt(depth)
        line = board.lineSets[ownTwoLineKind].next(-1)
        while (line != NONE) {
            val lineIndex = line.toInt()
            val packed = LineOps.packedFreePair(board, lineIndex)
            val ring1 = LineOps.freePairRing1(packed).toInt()
            val ring2 = LineOps.freePairRing2(packed).toInt()
            val subIndex = LineOps.freePairSubIndex(packed).toInt()
            if (!bitsGet(ring2)) {
                buffer[size] = ring1 or (ring2 shl 6) or ((12 * lineIndex + 2 * subIndex) shl 12)
                size++
            }
            if (!bitsGet(ring1)) {
                buffer[size] = ring2 or (ring1 shl 6) or ((12 * lineIndex + 2 * subIndex + 1) shl 12)
                size++
            }
            line = board.lineSets[ownTwoLineKind].next(line)
        }
        return size
    }

    // ---- Advanced forks (undiscounted search, used below AI level 90) ----

    /**
     * If [player] has an advanced fork of order [order] or less, the full
     * forced sequence: forced `(player, opponent)` pairs, then the final
     * move that completes a simple fork — an odd-length array. Returns
     * null if there is none. Ports the public `fourchetteAvancee(byte, int)`.
     *
     * Allocates once, for the returned copy — everything under the hood
     * ([advancedFork] with a depth and a reusable buffer) does not.
     */
    fun advancedFork(player: Int, order: Int): ByteArray? {
        val pairs = advancedFork(player, order, depth = 0, resultBuffer = forkChainBuffer)
        if (pairs == NOT_FOUND) return null
        return forkChainBuffer.copyOf(pairs * 2 + 1)
    }

    /**
     * @return the number of forced `(player, opponent)` pairs before
     *   [player]'s fork completes (>=0), with the pairs written into
     *   [resultBuffer] at `[0,1), [2,3), ...` and the final move at
     *   `2*result`; or [NOT_FOUND]. Ports the recursive body of
     *   `fourchetteAvancee`.
     */
    private fun advancedFork(player: Int, order: Int, depth: Int, resultBuffer: ByteArray): Int {
        if (board.playedCount > 61) return NOT_FOUND
        if (order * 2 + 3 + board.playedCount > BoardTables.RING_COUNT) {
            val adjustedOrder = (BoardTables.RING_COUNT - 3 - board.playedCount) / 2
            return advancedFork(player, adjustedOrder, depth, resultBuffer)
        }

        val simple = simpleFork(player)
        if (simple != NOT_FOUND) {
            resultBuffer[depth * 2] = simple.toByte()
            return 0
        }
        if (order == 0) return NOT_FOUND

        val opponent = 3 - player
        var searchBound = order - 1
        var best = NOT_FOUND
        val count = forkCandidates(player, depth)
        val candidates = scratch.bufferAt(depth)
        for (i in 0 until count) {
            val packed = candidates[i]
            val ring1 = packed and RING_MASK
            val ring2 = (packed ushr 6) and RING_MASK
            board.play(ring1, player)
            board.play(ring2, opponent)
            val subOrder = advancedFork(player, searchBound, depth + 1, resultBuffer)
            board.undo(ring2, opponent)
            board.undo(ring1, player)
            if (subOrder != NOT_FOUND) {
                resultBuffer[depth * 2] = ring1.toByte()
                resultBuffer[depth * 2 + 1] = ring2.toByte()
                best = subOrder + 1
                if (subOrder == 0) break
                searchBound = subOrder - 1
            }
        }
        return best
    }

    /**
     * Finds a ring for [player] to block the opponent's advanced fork
     * (searched up to order [order]) in one move; if none blocks it
     * outright, the ring that leaves the opponent's fork at the highest
     * possible order. Returns [NOT_FOUND] only if every free ring is
     * itself part of one of [player]'s own 2-ring lines (so playing
     * anywhere would force *us* to block next). Ports
     * `bloqueFourchetteAvanceeSimplifiee`.
     */
    fun blockAdvancedFork(player: Int, order: Int, progress: AiProgress? = null): Int {
        val weights = if (player == 1) WEIGHTS_BLOCK_PLAYER1 else WEIGHTS_BLOCK_PLAYER2
        val ownTwoLineKind = (if (player == 1) 2 else 6).toByte()

        val candidateCount = BoardTables.RING_COUNT - board.playedCount
        val rings = ByteArray(candidateCount)
        val points = ByteArray(candidateCount)
        var size = 0
        for (ring in 0 until BoardTables.RING_COUNT) {
            if (board.rings[ring] == EMPTY && !BoardTables.ringHasLineOfKind(ring, board.lines, ownTwoLineKind)) {
                rings[size] = ring.toByte()
                points[size] = BoardTables.pointsFor(ring, board.lines, weights)
                size++
            }
        }
        if (size == 0) return NOT_FOUND

        val opponent = 3 - player
        var bestRing = NOT_FOUND
        var bestOrder = -1
        for (i in 0 until size) {
            var max = Byte.MIN_VALUE
            var maxPos = -1
            for (j in 0 until size) {
                if (rings[j] != REMOVED && points[j] > max) {
                    max = points[j]
                    maxPos = j
                }
            }
            val ring = rings[maxPos].toInt()
            rings[maxPos] = REMOVED
            board.play(ring, player)
            val opponentOrder = advancedFork(opponent, order, depth = 0, resultBuffer = forkChainBuffer)
            board.undo(ring, player)
            if (opponentOrder == NOT_FOUND) return ring
            if (opponentOrder > bestOrder) {
                bestRing = ring
                bestOrder = opponentOrder
            }
            progress?.onProgress(AiSearchPhase.BLOCKING_FORK, i, size)
            if (progress?.isCancelled() == true) break
        }
        return bestRing
    }

    // ---- Advanced forks with memoization (used at AI level 90+) ----

    /**
     * Like [advancedFork], but only returns the ring to play right now —
     * not the full sequence — and searches with memoization across the
     * whole subtree (bounded by [order]), making deeper searches (order
     * 5-8) practical. Ports `bigFourchetteAvancee`.
     */
    fun bigAdvancedFork(player: Int, order: Int, progress: AiProgress? = null): Int {
        if (board.playedCount > 61) return NOT_FOUND
        if (order * 2 + 3 + board.playedCount > BoardTables.RING_COUNT) {
            return bigAdvancedFork(player, (BoardTables.RING_COUNT - 3 - board.playedCount) / 2, progress)
        }

        val simple = simpleFork(player)
        if (simple != NOT_FOUND) return simple
        if (order == 0) return NOT_FOUND

        val opponent = 3 - player
        var bound = order
        var bestRing = NOT_FOUND
        val count = forkCandidates(player, depth = 0)
        val candidates = scratch.bufferAt(0)
        for (i in 0 until count) {
            val packed = candidates[i]
            val ring1 = packed and RING_MASK
            val ring2 = (packed ushr 6) and RING_MASK
            val indice = packed ushr 12
            board.play(ring1, player)
            board.play(ring2, opponent)
            val res = isAdvancedFork(player, depth = 1, remainingOrder = bound - 1, dejaJoue = packMoves(indice))
            board.undo(ring2, opponent)
            board.undo(ring1, player)
            if (res != NOT_FOUND) {
                bound = res
                bestRing = ring1
                if (res == 0) break
            }
            progress?.onProgress(AiSearchPhase.SEARCHING_FORK, i, count)
            if (progress?.isCancelled() == true) break
        }
        for (table in memo) table.clear()
        return bestRing
    }

    /**
     * Recursive worker behind [bigAdvancedFork]: does [player] have an
     * advanced fork within [remainingOrder] more forced pairs, given the
     * pairs already played are encoded in [dejaJoue] (see [packMoves])?
     * Returns [NOT_FOUND], or the order found (0 = an immediate simple
     * fork). [depth] is both the recursion depth (matching the legacy
     * `profondeur`, 1-based) and the memoization table index (`depth - 1`,
     * only used while `< `[MEMO_DEPTH]`). Ports `isFourchetteAvancee`.
     */
    private fun isAdvancedFork(player: Int, depth: Int, remainingOrder: Int, dejaJoue: Long): Int {
        val simple = simpleFork(player)
        if (simple != NOT_FOUND) return 0
        if (remainingOrder == 0) return NOT_FOUND

        val opponent = 3 - player
        var bound = remainingOrder
        var best = NOT_FOUND
        val memoTable = if (depth - 1 < MEMO_DEPTH) memo[depth - 1] else null
        // If memoization is disabled at this depth, the "already played"
        // key is never recomputed and every recursive call below gets 0 —
        // this matches a quirk of the legacy code exactly (see
        // docs/PLAN.md §6.1): harmless, since memoization is then also
        // disabled for every deeper call, so the key is never looked up.
        var nextDejaJoue = 0L

        val count = forkCandidates(player, depth)
        val candidates = scratch.bufferAt(depth)
        for (i in 0 until count) {
            val packed = candidates[i]
            val ring1 = packed and RING_MASK
            val ring2 = (packed ushr 6) and RING_MASK

            var result: Int
            var cached = LongByteMap.NOT_FOUND
            if (memoTable != null) {
                nextDejaJoue = packMoves(dejaJoue, packed ushr 12)
                cached = memoTable.get(nextDejaJoue)
            }
            if (cached != LongByteMap.NOT_FOUND) {
                result = cached
            } else {
                board.play(ring1, player)
                board.play(ring2, opponent)
                result = isAdvancedFork(player, depth + 1, bound - 1, nextDejaJoue)
                board.undo(ring2, opponent)
                board.undo(ring1, player)
                memoTable?.put(nextDejaJoue, result.toByte())
            }

            if (result != NOT_FOUND) {
                if (result == 0) return 1
                if (result < bound) {
                    bound = result
                    best = result + 1
                }
            }
        }
        return best
    }

    /**
     * Encodes one more fork-candidate [indice] into [dejaJoue], keeping the
     * encoded sequence in ascending order — the memoization key for
     * [isAdvancedFork]. Bits 60+ hold the count of encoded indices; each is
     * stored in its own 10-bit group starting at bit 0. Reuses a scratch
     * buffer instead of allocating. Ports the 2-argument `getDejaJoue`.
     */
    internal fun packMoves(dejaJoue: Long, indice: Int): Long {
        val oldSize = (dejaJoue / (1L shl 60)).toInt()
        var result = (oldSize + 1).toLong() shl 60
        val entries = packMovesScratch
        entries[0] = indice.toLong()
        for (i in 0 until oldSize) {
            val extracted = (dejaJoue / (1L shl (10 * i))) % (1L shl 10)
            entries[i + 1] = extracted
            if (indice > extracted) {
                entries[i] = extracted
                entries[i + 1] = indice.toLong()
            }
        }
        for (i in 0..oldSize) {
            result += entries[i] shl (10 * i)
        }
        return result
    }

    /** The initial `dejaJoue` encoding a single [indice]. Ports the 1-argument `getDejaJoue`. */
    internal fun packMoves(indice: Int): Long = (1L shl 60) + indice

    // ---- Weighted / random ring choice ----

    /** [chooseByWeights] using the standard per-player weights. Ports `choixAnneauPoints(byte)`. */
    private fun chooseByWeights(player: Int): Int =
        chooseByWeights(if (player == 1) WEIGHTS_PLAYER1 else WEIGHTS_PLAYER2)

    /**
     * The free ring with the highest [BoardTables.pointsFor] under
     * [weights], breaking ties at random among up to 20 candidates (a tie
     * beyond the 20th candidate restarts the tied list — see
     * docs/PLAN.md §6.1.5, preserved on purpose). Ports the `byte[]`
     * overload of `choixAnneauPoints`.
     */
    private fun chooseByWeights(weights: ByteArray): Int {
        val best = bestRingsScratch
        var count = 0
        var bestPoints = Byte.MIN_VALUE
        for (ring in 0 until BoardTables.RING_COUNT) {
            if (board.rings[ring] == EMPTY) {
                val points = BoardTables.pointsFor(ring, board.lines, weights)
                if (points >= bestPoints) {
                    if (points == bestPoints && count < MAX_TIED_CANDIDATES) {
                        best[count] = ring.toByte()
                        count++
                    } else {
                        bestPoints = points
                        best[0] = ring.toByte()
                        count = 1
                    }
                }
            }
        }
        return when {
            count == 0 -> NOT_FOUND
            count == 1 -> best[0].toInt()
            else -> best[random.nextInt(count)].toInt()
        }
    }

    /** A uniformly random free ring, or [NOT_FOUND] if the board is full. Ports `choixAnneauRandom`. */
    private fun chooseRandom(): Int {
        if (board.playedCount >= BoardTables.RING_COUNT) return NOT_FOUND
        val target = random.nextInt(BoardTables.RING_COUNT - board.playedCount)
        var seen = 0
        for (ring in 0 until BoardTables.RING_COUNT) {
            if (board.rings[ring] == EMPTY) {
                if (seen == target) return ring
                seen++
            }
        }
        return NOT_FOUND
    }

    // ---- Per-level move selection ----

    /**
     * Chooses [player]'s move at difficulty [level] (0..100). See the
     * legacy KDoc ported alongside `playLevelExpert` below for the exact
     * behavior at each level range. Ports `jouerNiveau`.
     */
    fun playLevel(player: Int, level: Int, progress: AiProgress? = null): Int {
        val ownThreeKind = if (player == 1) 3 else 7
        val opponentThreeKind = if (player == 1) 7 else 3

        var line = board.lineSets[ownThreeKind].next(-1)
        if (line != NONE) {
            return lastRemainingRingOf(line.toInt())
        }

        line = board.lineSets[opponentThreeKind].next(-1)
        if (line != NONE) {
            val res = lastRemainingRingOf(line.toInt())
            if (level >= 20) return res
            if (level > randomThreshold(0, 20)) return res
        }

        if (level < 90) {
            if (level <= 20) {
                return if (level < 10) chooseRandom() else chooseByWeights(player)
            }

            var forkOrder = -1
            if (20 < level && level < 30) {
                if (level > randomThreshold(20, 10)) forkOrder = 0
            } else if (30 <= level && level <= 35) {
                forkOrder = 0
            } else if (35 < level && level < 50) {
                forkOrder = if (level > randomThreshold(35, 15)) 1 else 0
            }
            if (40 <= level && level < 60) {
                if (level > randomThreshold(40, 20)) {
                    forkOrder = 2
                } else if (50 <= level) {
                    forkOrder = 1
                }
            }
            if (60 <= level && level < 80) {
                forkOrder = if (level > randomThreshold(60, 20)) 3 else 2
            }
            if (70 <= level && level < 90) {
                if (level > randomThreshold(70, 20)) {
                    forkOrder = 4
                } else if (80 <= level) {
                    forkOrder = 3
                }
            }
            if (forkOrder == 0) {
                val res = simpleFork(player)
                if (res != NOT_FOUND) return res
            } else if (forkOrder > 0) {
                val chain = advancedFork(player, forkOrder)
                if (chain != null) return chain[0].toInt()
            }

            forkOrder = -1
            if (30 < level && level < 50) {
                if (level > randomThreshold(30, 20)) forkOrder = 0
            } else if (50 <= level && level < 80) {
                forkOrder = if (level > randomThreshold(50, 30)) 1 else 0
            }
            if (60 < level && level < 90) {
                if (level > randomThreshold(60, 30)) {
                    forkOrder = 2
                } else if (80 <= level) {
                    forkOrder = 1
                }
            }
            if (forkOrder >= 0) {
                val res = blockAdvancedFork(player, forkOrder, progress)
                if (res != NOT_FOUND) return res
            }
            return chooseByWeights(player)
        }
        return playLevelExpert(player, level, progress)
    }

    /**
     * The tail end of [playLevel] for levels 90-100: uses [bigAdvancedFork]
     * (memoized) instead of [advancedFork], reaching order 5-8 depending on
     * level, then falls back to [blockAdvancedFork] and finally
     * [chooseByWeights]. Ports `jouerNiveauSuperFort`.
     *
     * Difficulty behavior across the full 0..100 range (ported from the
     * legacy KDoc):
     * - The AI always completes a win in one move when it can.
     * - 0-10: blocks the opponent's simple threats with probability
     *   proportional to level, otherwise plays randomly.
     * - 10-20: same blocking behavior, plays the highest-weighted ring
     *   otherwise.
     * - 20-30: always blocks simple threats; starts using its own simple
     *   forks with growing probability.
     * - 30-35: always uses simple forks when available; starts blocking
     *   the opponent's simple forks with growing probability.
     * - 35-50: starts using order-1 advanced forks; blocking of simple
     *   forks keeps ramping up to certain.
     * - 40-60: starts using order-2 advanced forks.
     * - 50-80: starts blocking order-1 advanced forks.
     * - 60-80: starts using order-3 advanced forks; starts blocking
     *   order-2 advanced forks.
     * - 70-90: starts using order-4 advanced forks.
     * - 90+: switches to the memoized, deeper (order 5-8) search below.
     */
    private fun playLevelExpert(player: Int, level: Int, progress: AiProgress?): Int {
        val opponent = 3 - player
        var forkOrder = 4
        if (level > randomThreshold(89, 4)) forkOrder = 5
        if (level > randomThreshold(92, 4)) forkOrder = 6
        if (level > randomThreshold(96, 4)) forkOrder = 7
        if (level == 100) forkOrder = 8
        val forkRing = bigAdvancedFork(player, forkOrder, progress)
        if (forkRing != NOT_FOUND) return forkRing

        var blockOrder = 2
        if (level > randomThreshold(90, 5)) blockOrder = 3
        if (level > randomThreshold(90, 10)) blockOrder = 4
        val blockRing = blockAdvancedFork(player, blockOrder, progress)
        if (blockRing != NOT_FOUND) return blockRing

        return chooseByWeights(player)
    }

    private fun randomThreshold(base: Int, range: Int): Int =
        (base + random.nextDouble() * range).toInt()

    // ---- Challenge mode ----

    /**
     * The computer's move in Challenge mode (always player 2): completes a
     * win if possible, blocks the user's win if needed, otherwise searches
     * a memoized fork within the level's remaining ring budget, falls back
     * to blocking (only for a small remaining budget, matching the
     * legacy code), and finally a dedicated challenge weight table. Ports
     * `playChallenge`.
     */
    fun playChallenge(levelFork: Int, usedRings: Int): Int {
        var line = board.lineSets[7].next(-1)
        if (line != NONE) return lastRemainingRingOf(line.toInt())
        line = board.lineSets[3].next(-1)
        if (line != NONE) return lastRemainingRingOf(line.toInt())

        val remainingRings = levelFork + 2 - usedRings
        check(remainingRings >= 1) {
            "Problem remaining rings: $remainingRings (levelFork: $levelFork, usedRings: $usedRings)"
        }
        if (remainingRings == 1) return chooseByWeights(2)

        val ring = bigAdvancedFork(2, remainingRings - 2)
        if (ring != NOT_FOUND) return ring
        if (remainingRings - 2 <= 4) {
            val blockRing = blockAdvancedFork(1, remainingRings - 2)
            if (blockRing != NOT_FOUND) return blockRing
        }
        return chooseByWeights(WEIGHTS_CHALLENGE)
    }

    /**
     * A Challenge-mode hint, as played by [playHint]: the ring the user is
     * shown to play, and the computer's immediate reply.
     */
    data class HintMove(val ringForUser: Int, val ringForComputer: Int)

    /**
     * Plays out a hint pair on [board] (mutating it): the ring that
     * completes the user's advanced fork, then the computer's reply via
     * [playChallenge]. [numberHintRings] is how many hint pairs this attempt
     * already played. Ports `playStar` (the legacy app charged a star per hint).
     */
    fun playHint(levelFork: Int, numberHintRings: Int): HintMove {
        val ringForUser = bigAdvancedFork(1, levelFork - numberHintRings)
        check(ringForUser != NOT_FOUND) { "A result should have been found for the fork." }
        board.play(ringForUser, 1)
        val ringForComputer = playChallenge(levelFork, numberHintRings + 1)
        board.play(ringForComputer, 2)
        return HintMove(ringForUser, ringForComputer)
    }

    // ---- Small local helpers over LineOps/BoardTables, kept next to their one caller's shape ----

    private fun firstFreeRingOf(line: Int): Int = LineOps.firstFreeRing(board, line).toInt()
    private fun lastFreeRingOf(line: Int): Int = LineOps.lastFreeRing(board, line).toInt()
    private fun lastRemainingRingOf(line: Int): Int = LineOps.lastRemainingRing(board, line).toInt()

    private fun bitsGet(ring: Int): Boolean = (bits[0] ushr ring) and 1L != 0L
    private fun bitsSet(ring: Int) {
        bits[0] = bits[0] or (1L shl ring)
    }

    companion object {
        const val NOT_FOUND: Int = -1
        private const val NONE: Byte = -1
        private const val EMPTY: Byte = 0
        private const val REMOVED: Byte = -1
        private const val RING_MASK: Int = 0x3F
        private const val MEMO_DEPTH: Int = 6
        private const val MAX_SEARCH_DEPTH: Int = 12
        private const val MAX_CHAIN_LENGTH: Int = 24
        private const val MAX_TIED_CANDIDATES: Int = 20

        // Per-ring weights indexed by LineOps.lineKind (0..9); index 9
        // (dead/mixed lines) is always 0 and never used in practice.
        private val WEIGHTS_PLAYER1 = byteArrayOf(10, 7, -12, 40, 40, 6, 10, 10, 40, 0)
        private val WEIGHTS_PLAYER2 = byteArrayOf(10, 6, 10, 10, 40, 7, -12, 40, 40, 0)
        private val WEIGHTS_BLOCK_PLAYER1 = byteArrayOf(10, 7, -12, 40, 40, 6, 10, 10, 40, 0)
        private val WEIGHTS_BLOCK_PLAYER2 = byteArrayOf(10, 6, 10, 10, 40, 7, -12, 40, 40, 0)
        private val WEIGHTS_CHALLENGE = byteArrayOf(5, 3, 5, 40, 40, 5, 15, 40, 40, 0)
    }
}
