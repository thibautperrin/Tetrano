package fr.thibautperrin.tetrano.engine

import fr.thibautperrin.tetrano.engine.ai.Ai
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random
import thibautperrin.tetrano.Intelligence2.AnoAlig as LegacyBoardState
import thibautperrin.tetrano.Intelligence2.IA as LegacyAi

/**
 * Compares this Kotlin engine against the original, unmodified 2015 Java
 * engine (compiled from `v4.1-legacy`, see `engine/src/test/java`) on many
 * randomly generated positions. This is the decisive fidelity check called
 * for in docs/PLAN.md P3.2: every function compared here is fully
 * deterministic in both implementations (no `Math.random()`/`Random`
 * involved), so any divergence is a real bug, not sampling noise.
 *
 * `choixAnneauPoints`/`choixAnneauRandom`/`jouerNiveau` (ex `chooseByWeights`
 * /`chooseRandom`/`playLevel`) are intentionally *not* compared here: they
 * draw random numbers, so the two implementations can legitimately pick
 * different (equally valid) rings — see docs/PLAN.md §6.2.
 */
class EngineDifferentialTest {

    private class Position(val moves: IntArray)

    /**
     * Random legal positions: each plays a random number of random free
     * rings (alternating players 1, 2, 1, 2, ...), stopping early if a
     * player completes a line of 4 (search functions are never asked to
     * search from an already-decided board in practice).
     */
    private fun randomPositions(count: Int, maxMoves: Int, seed: Long): List<Position> {
        val rng = Random(seed)
        return List(count) {
            val target = rng.nextInt(maxMoves + 1)
            val board = BoardState()
            val moves = ArrayList<Int>(target)
            var player = 1
            for (i in 0 until target) {
                val free = ArrayList<Int>(BoardTables.RING_COUNT - board.playedCount)
                for (ring in 0 until BoardTables.RING_COUNT) {
                    if (board.rings[ring] == 0.toByte()) free.add(ring)
                }
                if (free.isEmpty()) break
                val ring = free[rng.nextInt(free.size)]
                board.play(ring, player)
                moves.add(ring)
                if (board.lineSets[4].length() > 0 || board.lineSets[8].length() > 0) break
                player = 3 - player
            }
            Position(moves.toIntArray())
        }
    }

    private fun buildNew(moves: IntArray): BoardState {
        val board = BoardState()
        var player = 1
        for (ring in moves) {
            board.play(ring, player)
            player = 3 - player
        }
        return board
    }

    private fun buildLegacy(moves: IntArray): LegacyBoardState {
        val board = LegacyBoardState()
        var player = 1
        for (ring in moves) {
            board.jouer(ring.toByte(), player.toByte())
            player = 3 - player
        }
        return board
    }

    @Test
    fun simpleForkMatchesLegacy() {
        for (position in randomPositions(count = 50_000, maxMoves = 45, seed = 1L)) {
            val board = buildNew(position.moves)
            val ai = Ai(board)
            val legacyAi = LegacyAi(buildLegacy(position.moves))
            for (player in 1..2) {
                assertEquals(
                    "moves=${position.moves.toList()} player=$player",
                    legacyAi.isFourchetteSimple(player.toByte()).toInt(),
                    ai.simpleFork(player),
                )
            }
        }
    }

    @Test
    fun advancedForkMatchesLegacy() {
        for (position in randomPositions(count = 5_000, maxMoves = 45, seed = 2L)) {
            val board = buildNew(position.moves)
            val ai = Ai(board)
            val legacyAi = LegacyAi(buildLegacy(position.moves))
            for (player in 1..2) {
                for (order in 0..4) {
                    val context = "moves=${position.moves.toList()} player=$player order=$order"
                    val legacyResult = legacyAi.fourchetteAvancee(player.toByte(), order)
                    val newResult = ai.advancedFork(player, order)
                    if (legacyResult == null) {
                        assertNull(context, newResult)
                    } else {
                        assertNotNull(context, newResult)
                        assertArrayEquals(context, legacyResult, newResult)
                    }
                }
            }
        }
    }

    @Test
    fun bigAdvancedForkMatchesLegacy() {
        for (position in randomPositions(count = 4_000, maxMoves = 45, seed = 3L)) {
            val board = buildNew(position.moves)
            val ai = Ai(board)
            val legacyAi = LegacyAi(buildLegacy(position.moves))
            for (player in 1..2) {
                for (order in 0..5) {
                    assertEquals(
                        "moves=${position.moves.toList()} player=$player order=$order",
                        legacyAi.bigFourchetteAvancee(player.toByte(), order.toByte(), null).toInt(),
                        ai.bigAdvancedFork(player, order),
                    )
                }
            }
        }
    }

    @Test
    fun blockAdvancedForkMatchesLegacy() {
        for (position in randomPositions(count = 3_000, maxMoves = 45, seed = 4L)) {
            val board = buildNew(position.moves)
            val ai = Ai(board)
            val legacyAi = LegacyAi(buildLegacy(position.moves))
            for (player in 1..2) {
                for (order in 0..3) {
                    assertEquals(
                        "moves=${position.moves.toList()} player=$player order=$order",
                        legacyAi.bloqueFourchetteAvanceeSimplifiee(player.toByte(), order, null).toInt(),
                        ai.blockAdvancedFork(player, order),
                    )
                }
            }
        }
    }

    @Test
    fun signatureMatchesLegacy() {
        for (position in randomPositions(count = 30_000, maxMoves = 63, seed = 5L)) {
            val board = buildNew(position.moves)
            val legacy = buildLegacy(position.moves)
            for (invert in booleanArrayOf(false, true)) {
                val context = "moves=${position.moves.toList()} invert=$invert"
                assertArrayEquals(context, legacy.getSignature(invert), board.signature(invert))
                assertArrayEquals(context, legacy.getLowestSignature(invert), board.canonicalSignature(invert))
            }
        }
    }

    @Test
    fun lineSetTraversalMatchesLegacy() {
        for (position in randomPositions(count = 20_000, maxMoves = 45, seed = 6L)) {
            val board = buildNew(position.moves)
            val legacy = buildLegacy(position.moves)
            for (kind in 0 until 10) {
                var newLine = board.lineSets[kind].next(-1)
                var legacyLine = legacy.ensembles[kind].next(-1)
                val context = "moves=${position.moves.toList()} kind=$kind"
                while (legacyLine.toInt() != -1) {
                    assertEquals(context, legacyLine, newLine)
                    newLine = board.lineSets[kind].next(newLine)
                    legacyLine = legacy.ensembles[kind].next(legacyLine)
                }
                assertEquals(context, -1, newLine.toInt())
            }
        }
    }

    @Test
    fun packMovesMatchesLegacy() {
        val ai = Ai(BoardState())
        val rng = Random(7L)
        repeat(500) { chain ->
            var legacyValue = 0L
            var newValue = 0L
            // Mirrors isAdvancedFork's own usage: chains of up to 6 encoded
            // moves (MEMO_DEPTH), each starting fresh via the 1-argument form.
            for (i in 0 until 6) {
                val indice = rng.nextInt(913)
                if (i == 0) {
                    legacyValue = LegacyAi.getDejaJoue(indice)
                    newValue = ai.packMoves(indice)
                } else {
                    legacyValue = LegacyAi.getDejaJoue(legacyValue, indice)
                    newValue = ai.packMoves(newValue, indice)
                }
                assertEquals("chain=$chain i=$i indice=$indice", legacyValue, newValue)
            }
        }
    }
}
