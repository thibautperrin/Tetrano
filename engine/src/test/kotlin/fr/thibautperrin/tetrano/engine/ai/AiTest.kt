package fr.thibautperrin.tetrano.engine.ai

import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the legacy `IATest` (see docs/GLOSSARY.md). */
class AiTest {

    private fun completeNotFork(ai: Ai, orderP1: Int, orderP2: Int): Boolean {
        for (i in 0..orderP1) {
            if (ai.advancedFork(1, i) != null) return true
        }
        for (i in 0..orderP2) {
            if (ai.advancedFork(2, i) != null) return true
        }
        return false
    }

    private fun completeNotBigFork(ai: Ai, orderP1: Int, orderP2: Int): Boolean {
        for (i in 0..orderP1) {
            if (ai.bigAdvancedFork(1, i) != Ai.NOT_FOUND) return true
        }
        for (i in 0..orderP2) {
            if (ai.bigAdvancedFork(2, i) != Ai.NOT_FOUND) return true
        }
        return false
    }

    @Test
    fun testSimpleFork() {
        val board = BoardState()
        val ai = Ai(board)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(0, 1)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(25, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(3, 1)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(26, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(22, 1)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(8, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(42, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(40, 2)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(24, ai.simpleFork(2))
        board.play(24, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(2, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.undo(2, 2)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.undo(24, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(24, ai.simpleFork(2))
        board.play(27, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.undo(27, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(24, ai.simpleFork(2))
        board.play(56, 1)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(1, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.undo(1, 2)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.play(62, 2)
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.undo(62, 2)
        assertEquals(2, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
        board.restart()
        assertEquals(-1, ai.simpleFork(1))
        assertEquals(-1, ai.simpleFork(2))
    }

    @Test
    fun testAdvancedFork() {
        val board = BoardState()
        val ai = Ai(board)
        assertFalse(completeNotFork(ai, 5, 5))
        board.play(0, 1)
        assertFalse(completeNotFork(ai, 5, 5))
        board.play(63, 2)
        assertFalse(completeNotFork(ai, 5, 5))
        board.play(3, 1)
        assertFalse(completeNotFork(ai, 5, 5))
        board.play(62, 2)
        assertFalse(completeNotFork(ai, 5, 5))
        board.play(15, 1)
        assertFalse(completeNotFork(ai, 3, 5))
        assertNotNull(ai.advancedFork(1, 4))
        assertNotNull(ai.advancedFork(1, 5))
        board.play(59, 2)
        assertFalse(completeNotFork(ai, 3, 5))
        assertNotNull(ai.advancedFork(1, 4))
        assertNotNull(ai.advancedFork(1, 5))
        board.undo(59, 2)
        assertFalse(completeNotFork(ai, 3, 5))
        assertNotNull(ai.advancedFork(1, 4))
        assertNotNull(ai.advancedFork(1, 5))
        board.play(12, 2)
        assertFalse(completeNotFork(ai, 5, 5))
    }

    /** Just checking that no exception is thrown across a wide range of levels. */
    @Test
    fun testPlayLevelDoesNotThrow() {
        val board = BoardState()
        val ai = Ai(board)
        board.play(ai.playLevel(1, 7), 1)
        board.play(ai.playLevel(2, 89), 2)
        board.play(ai.playLevel(1, 0), 1)
        board.play(ai.playLevel(2, 80), 2)
        board.play(ai.playLevel(1, 40), 1)
        board.play(ai.playLevel(2, 50), 2)
        board.play(ai.playLevel(1, 70), 1)
        board.play(ai.playLevel(2, 69), 2)
        board.play(ai.playLevel(1, 21), 1)
        board.play(ai.playLevel(2, 20), 2)
    }

    @Test
    fun testBlockAdvancedFork() {
        val board = BoardState()
        val ai = Ai(board)
        board.play(0 + 4 * 0 + 16 * 0, 1)
        board.play(1 + 4 * 0 + 16 * 0, 2)
        board.play(0 + 4 * 3 + 16 * 0, 1)
        board.play(2 + 4 * 2 + 16 * 1, 2)
        board.play(0 + 4 * 1 + 16 * 1, 1)
        board.play(2 + 4 * 3 + 16 * 0, 2)
        board.play(0 + 4 * 1 + 16 * 2, 1)

        assertNotNull(ai.advancedFork(1, 0))
        assertEquals(0 + 4 * 1 + 0 * 16, ai.advancedFork(1, 0)!![0].toInt())

        assertEquals(0 + 4 * 1 + 0 * 16, ai.simpleFork(1))

        board.play(ai.blockAdvancedFork(2, 2), 2)
        assertNull(ai.advancedFork(1, 0))
    }

    @Test
    fun testPackMoves() {
        val ai = Ai(BoardState())
        var l = ai.packMoves(442)
        assertEquals(1152921504606847418L, l)
        l = ai.packMoves(l, 623)
        assertEquals(2305843009214332346L, l)
        l = ai.packMoves(l, 0)
        assertEquals(3458764514474256384L, l)
        l = ai.packMoves(l, 911)
        assertEquals(4611686997259905024L, l)
        l = ai.packMoves(l, 13)
        assertEquals(5765609847531779072L, l)
        l = ai.packMoves(l, 128)
        assertEquals(7943909313246934016L, l)
    }

    @Test
    fun testBigAdvancedFork() {
        val board = BoardState()
        val ai = Ai(board)
        assertFalse(completeNotBigFork(ai, 8, 8))
        board.play(0, 1)
        assertFalse(completeNotBigFork(ai, 8, 8))
        board.play(63, 2)
        assertFalse(completeNotBigFork(ai, 8, 8))
        board.play(3, 1)
        assertFalse(completeNotBigFork(ai, 8, 8))
        board.play(62, 2)
        assertFalse(completeNotBigFork(ai, 8, 8))
        board.play(15, 1)
        assertFalse(completeNotBigFork(ai, 3, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 4))
        assertTrue(-1 != ai.bigAdvancedFork(1, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 6))
        board.play(59, 2)
        assertFalse(completeNotBigFork(ai, 3, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 4))
        assertTrue(-1 != ai.bigAdvancedFork(1, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 6))
        board.undo(59, 2)
        assertFalse(completeNotBigFork(ai, 3, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 4))
        assertTrue(-1 != ai.bigAdvancedFork(1, 5))
        assertTrue(-1 != ai.bigAdvancedFork(1, 6))
        board.play(12, 2)
        assertFalse(completeNotBigFork(ai, 5, 5))
    }

    @Test
    fun testForksScenario1() {
        val board = BoardState()
        val ai = Ai(board)
        board.play(3 + 4 * 3 + 16 * 0, 1)
        board.play(1 + 4 * 2 + 16 * 2, 2)
        board.play(0 + 4 * 3 + 16 * 0, 1)
        board.play(1 + 4 * 1 + 16 * 1, 2)
        board.play(0 + 4 * 0 + 16 * 0, 1)
        board.play(2 + 4 * 1 + 16 * 1, 2)
        board.play(0 + 4 * 3 + 16 * 3, 1)
        board.play(1 + 4 * 1 + 16 * 2, 2)
        board.play(1 + 4 * 2 + 16 * 1, 1)
        board.play(2 + 4 * 1 + 16 * 2, 2)
        board.play(0 + 4 * 1 + 16 * 0, 1)
        board.play(0 + 4 * 2 + 16 * 0, 2)
        board.play(3 + 4 * 0 + 16 * 2, 1)
        val res1 = ai.advancedFork(2, 4)
        if (res1 == null) {
            assertEquals(-1, ai.bigAdvancedFork(2, 4))
        } else {
            assertEquals(-1, ai.bigAdvancedFork(2, res1.size / 2 - 1))
            assertTrue(-1 != ai.bigAdvancedFork(2, res1.size / 2))
        }
        board.play(0 + 4 * 1 + 16 * 1, 2)
        board.play(3 + 4 * 1 + 16 * 1, 1)
        board.play(1 + 4 * 0 + 16 * 0, 2)
        board.play(1 + 4 * 3 + 16 * 3, 1)
    }

    /** Just checking that no exception is thrown for this position. */
    @Test
    fun testForksScenario2() {
        val board = BoardState()
        val ai = Ai(board)
        board.play(3 + 4 * 3 + 16 * 0, 1)
        board.play(1 + 4 * 2 + 16 * 2, 2)

        board.play(0 + 4 * 3 + 16 * 0, 1)
        board.play(1 + 4 * 1 + 16 * 1, 2)

        board.play(3 + 4 * 0 + 16 * 0, 1)
        board.play(2 + 4 * 2 + 16 * 2, 2)

        board.play(2 + 4 * 1 + 16 * 1, 1)
        board.play(0 + 4 * 0 + 16 * 3, 2)

        board.play(1 + 4 * 1 + 16 * 2, 1)
        board.play(3 + 4 * 1 + 16 * 0, 2)

        board.play(3 + 4 * 0 + 16 * 3, 1)
        board.play(1 + 4 * 2 + 16 * 1, 2)

        board.play(2 + 4 * 2 + 16 * 1, 1)
        ai.bigAdvancedFork(2, 4)
        board.play(0 + 4 * 2 + 16 * 2, 2)
    }

    @Test
    fun testBigAdvancedFork2() {
        val game = Game()
        val ai = Ai(game.board)
        game.play(3)
        game.play(48)
        game.play(12)
        game.play(25)
        game.play(37)
        game.play(26)
        game.play(60)
        game.play(42)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 6) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 5))
        game.play(28)
        game.play(44)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 5) == -1)
        assertEquals(41, ai.bigAdvancedFork(1, 5))
        assertEquals(-1, ai.bigAdvancedFork(1, 4))
        game.play(41)
        game.play(22)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 4) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 3))
        game.play(45)
        game.play(33)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 3) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 2))
        game.play(30)
        game.play(15)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 2) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 1))
        game.play(29)
        game.play(31)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        assertFalse(ai.bigAdvancedFork(1, 1) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 0))
        game.play(63)
        game.play(46)
        assertEquals(61, ai.bigAdvancedFork(1, 0))
    }

    @Test
    fun testBigAdvancedFork3() {
        val game = Game()
        val ai = Ai(game.board)
        game.play(26)
        game.play(21)
        game.play(15)
        game.play(60)
        game.play(63)
        game.play(41)
        game.play(12)
        game.play(37)
        game.play(47)
        game.play(31)
        game.play(14)
        game.play(13)
        assertFalse(ai.bigAdvancedFork(1, 10) == -1)
        val res = ai.bigAdvancedFork(1, 5)
        assertFalse(res == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 4))
        game.play(38)
        game.play(50)
        assertFalse(ai.bigAdvancedFork(1, 4) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 3))
        game.play(46)
        game.play(29)
        assertFalse(ai.bigAdvancedFork(1, 3) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 2))
        game.play(42)
        game.play(34)
        assertFalse(ai.bigAdvancedFork(1, 2) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 1))
        game.play(10)
        game.play(58)
        assertFalse(ai.bigAdvancedFork(1, 1) == -1)
        assertEquals(-1, ai.bigAdvancedFork(1, 0))
        game.play(2)
        game.play(6)
        assertEquals(62, ai.bigAdvancedFork(1, 0))
    }
}
