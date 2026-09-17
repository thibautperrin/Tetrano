package fr.thibautperrin.tetrano.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the legacy `PartieTest` (see docs/GLOSSARY.md). */
class GameTest {

    @Test
    fun testSignature() {
        val game = Game()
        var signature = game.signature()
        for (i in 0 until 13) {
            assertEquals(Byte.MIN_VALUE, signature[i])
        }

        game.play(0)
        signature = game.signature()
        assertEquals(-127, signature[0].toInt())
        for (i in 1 until 13) {
            assertEquals(-128, signature[i].toInt())
        }

        game.undo()
        game.play(42)
        signature = game.signature()
        // 42 = 8*5+2
        assertEquals(-119, signature[8].toInt())

        game.undo()
        game.play(19)
        // 19 = 3*5+4
        signature = game.signature()
        assertEquals((-128 + 81).toByte(), signature[3])

        game.play(63)
        game.play(33)
        game.play(63)
        game.play(33)
        game.play(12)
        game.play(11)
        game.play(10)
        game.play(47)
        game.play(48)
        game.play(26)
        game.play(29)
        game.play(2)
        val game2 = Game()
        game2.play(19)
        game2.play(63)
        game2.play(33)
        game2.play(63)
        game2.play(33)
        game2.play(12)
        game2.play(11)
        game2.play(10)
        game2.play(47)
        game2.play(48)
        game2.play(26)
        game2.play(29)
        game2.play(2)
        assertArrayEquals(game.signature(), game2.signature())
    }

    @Test
    fun testCanonicalSignature() {
        val game1 = Game()
        val game2 = Game()
        game1.play(0)
        game2.play(63)
        assertArrayEquals(game1.canonicalSignature(), game2.canonicalSignature())
        game1.play(3)
        game2.play(15)
        assertArrayEquals(game1.canonicalSignature(), game2.canonicalSignature())
        game1.play(2)
        game2.play(60)
        val tab1 = game1.canonicalSignature()
        val tab2 = game2.canonicalSignature()
        assertFalse(tab1.contentEquals(tab2))
    }

    @Test
    fun testConstructorFromSignature() {
        val game1 = Game()
        game1.play(10)
        game1.play(20)
        game1.play(13)
        game1.play(63)
        game1.play(3)
        game1.play(1)
        val signature = game1.signature()
        val game2 = Game(signature, false)
        assertArrayEquals(byteArrayOf(3, 1, 10, 20, 13, 63), game2.playedRings())

        val game3 = Game()
        game3.restart(true)
        game3.play(18)
        game3.play(11)
        game3.play(3)
        game3.play(53)
        game3.play(45)
        game3.play(12)
        val signature2 = game3.signature()
        val game4 = Game(signature2, true)
        assertArrayEquals(byteArrayOf(3, 11, 18, 12, 45, 53), game4.playedRings())
    }

    @Test
    fun testUndoAndRestart() {
        val game = Game()
        assertTrue(game.play(0))
        assertTrue(game.play(1))
        // undo() reverses the *last* move played (ring 1).
        assertEquals(1, game.undo())
        assertTrue(game.isRingFree(1))
        assertFalse(game.isRingFree(0))
    }
}
