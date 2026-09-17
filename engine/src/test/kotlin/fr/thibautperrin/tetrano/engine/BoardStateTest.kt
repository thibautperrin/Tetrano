package fr.thibautperrin.tetrano.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ports the legacy `AnoAligTest` (see docs/GLOSSARY.md). */
class BoardStateTest {

    private fun assertEmpty(board: BoardState) {
        assertEquals(0, board.playedCount)
        assertEquals(76, board.lineSets[0].length())
        for (i in 1 until 10) {
            assertEquals(0, board.lineSets[i].length())
            assertEquals(-1, board.lineSets[i].next(-1).toInt())
        }
        for (i in 0 until 64) {
            assertEquals(0, board.rings[i].toInt())
        }
        for (i in 0 until 76) {
            assertEquals(0, board.lines[i].toInt())
        }
    }

    @Test
    fun testGlobal() {
        val board = BoardState()
        assertEmpty(board)

        board.play(0, 1)

        assertEquals(1, board.rings[0].toInt())
        assertEquals(7, board.lineSets[1].length())
        assertEquals(69, board.lineSets[0].length())
        assertEquals(1, board.playedCount)
        assertEquals(1, board.lines[0].toInt())
        assertEquals(1, board.lines[1].toInt())
        assertEquals(1, board.lines[2].toInt())
        assertEquals(1, board.lines[48].toInt())
        assertEquals(1, board.lines[49].toInt())
        assertEquals(1, board.lines[50].toInt())
        assertEquals(1, board.lines[72].toInt())

        board.undo(0, 1)
        assertEmpty(board)

        board.play(0, 2)

        assertEquals(2, board.rings[0].toInt())
        assertEquals(7, board.lineSets[5].length())
        assertEquals(69, board.lineSets[0].length())
        assertEquals(1, board.playedCount)
        assertEquals(5, board.lines[0].toInt())
        assertEquals(5, board.lines[1].toInt())
        assertEquals(5, board.lines[2].toInt())
        assertEquals(5, board.lines[48].toInt())
        assertEquals(5, board.lines[49].toInt())
        assertEquals(5, board.lines[50].toInt())
        assertEquals(5, board.lines[72].toInt())

        board.undo(0, 2)
        assertEmpty(board)

        board.play(0, 1)
        board.play(48, 2)

        assertEquals(1, board.rings[0].toInt())
        assertEquals(2, board.rings[48].toInt())
        assertEquals(6, board.lineSets[5].length())
        assertEquals(6, board.lineSets[1].length())
        assertEquals(63, board.lineSets[0].length())
        assertEquals(1, board.lineSets[9].length())
        assertEquals(2, board.playedCount)
        assertEquals(1, board.lines[0].toInt())
        assertEquals(6, board.lines[1].toInt())
        assertEquals(1, board.lines[2].toInt())
        assertEquals(1, board.lines[48].toInt())
        assertEquals(1, board.lines[49].toInt())
        assertEquals(1, board.lines[50].toInt())
        assertEquals(1, board.lines[72].toInt())
        assertEquals(5, board.lines[9].toInt())
        assertEquals(5, board.lines[11].toInt())
        assertEquals(5, board.lines[51].toInt())
        assertEquals(5, board.lines[53].toInt())
        assertEquals(5, board.lines[67].toInt())
        assertEquals(5, board.lines[75].toInt())

        board.undo(48, 2)

        assertEquals(1, board.rings[0].toInt())
        assertEquals(7, board.lineSets[1].length())
        assertEquals(69, board.lineSets[0].length())
        assertEquals(1, board.playedCount)
        assertEquals(1, board.lines[0].toInt())
        assertEquals(1, board.lines[1].toInt())
        assertEquals(1, board.lines[2].toInt())
        assertEquals(1, board.lines[48].toInt())
        assertEquals(1, board.lines[49].toInt())
        assertEquals(1, board.lines[50].toInt())
        assertEquals(1, board.lines[72].toInt())

        board.undo(0, 1)
        assertEmpty(board)

        board.play(62, 1)
        board.play(38, 2)
        board.play(12, 1)
        board.play(3, 2)
        board.play(2, 1)
        board.play(0, 2)
        board.restart()

        assertEmpty(board)
    }

    @Test
    fun testConstructorFromSignature() {
        val board1 = BoardState()
        board1.play(1, 1)
        board1.play(45, 2)
        board1.play(8, 1)
        board1.play(63, 2)
        board1.play(42, 1)
        val signature = board1.signature(false)

        val board2 = BoardState(signature, false)
        board2.undo(42, 1)
        board2.undo(63, 2)
        board2.undo(8, 1)
        board2.undo(45, 2)
        board2.undo(1, 1)
        assertArrayEquals(
            byteArrayOf(-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128),
            board2.signature(false),
        )
        assertEmpty(board2)

        val board3 = BoardState(signature, true)
        board3.undo(42, 2)
        board3.undo(63, 1)
        board3.undo(8, 2)
        board3.undo(45, 1)
        board3.undo(1, 2)
        assertArrayEquals(
            byteArrayOf(-128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128, -128),
            board3.signature(false),
        )
        assertEmpty(board3)
    }
}
