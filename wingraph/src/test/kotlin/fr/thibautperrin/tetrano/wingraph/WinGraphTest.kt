package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import fr.thibautperrin.tetrano.engine.wingraph.UnbeatablePlayer
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class WinGraphTest {

    private fun randomPosition(rng: Random, rings: Int): Pair<Long, Long> {
        var a = 0L
        var d = 0L
        repeat(rings) { i ->
            val free = (a or d).inv()
            var bits = free
            var index = rng.nextInt(java.lang.Long.bitCount(free))
            while (index-- > 0) bits = bits and (bits - 1)
            if (i % 2 == 0) a = a or java.lang.Long.lowestOneBit(bits) else d = d or java.lang.Long.lowestOneBit(bits)
        }
        return a to d
    }

    @Test
    fun canonicalFormIsTheSameForEverySymmetricImage() {
        val rng = Random(7)
        val form = CanonicalForm()
        repeat(200) {
            val (a, d) = randomPosition(rng, rng.nextInt(1, 20))
            form.of(a, d)
            val ca = form.attacker
            val cd = form.defender
            val s = rng.nextInt(QubicGeometry.symmetryCount)
            form.of(QubicGeometry.transform(s, a), QubicGeometry.transform(s, d))
            assertEquals(ca, form.attacker)
            assertEquals(cd, form.defender)
        }
    }

    @Test
    fun toRealMapsCanonicalCellsBack() {
        val rng = Random(11)
        val form = CanonicalForm()
        repeat(200) {
            val (a, d) = randomPosition(rng, rng.nextInt(1, 20))
            form.of(a, d)
            val cell = rng.nextInt(64)
            val real = form.toReal(cell)
            assertEquals(1L shl cell, QubicGeometry.transform(form.symmetry, 1L shl real))
        }
    }

    @Test
    fun theCornerHasTwelveDistinctReplies() {
        val kids = Children().generate(1L, 0L, (1L).inv())
        assertArrayEquals(intArrayOf(1, 3, 5, 6, 7, 15, 21, 22, 23, 27, 31, 63), kids.move.copyOf(kids.count))
    }

    @Test
    fun winBookRoundTrips() {
        val book = WinBook.of(longArrayOf(5, 1, -1), longArrayOf(2, 9, 3), byteArrayOf(7, 8, 9))
        val bytes = ByteArrayOutputStream().also { book.writeTo(it) }.toByteArray()
        val loaded = WinBook.load(ByteArrayInputStream(bytes))
        assertEquals(3, loaded.size)
        assertEquals(8, loaded.move(1, 9))
        assertEquals(7, loaded.move(5, 2))
        assertEquals(9, loaded.move(-1, 3))
        assertEquals(-1, loaded.move(5, 3))
    }

    @Test
    fun positionMapGrowsSavesAndLoads() {
        val map = PositionMap(4)
        for (i in 0 until 1000) map.put(i.toLong() * 31, i.toLong(), i)
        assertEquals(1000, map.size)
        assertEquals(500, map.putIfAbsent(500L * 31, 500L, 1))
        val file = Files.createTempFile("pmap", ".bin").toFile()
        map.save(file)
        val loaded = PositionMap.load(file)
        assertEquals(1000, loaded.size)
        for (i in 0 until 1000) assertEquals(i, loaded.get(i.toLong() * 31, i.toLong()))
        file.delete()
    }

    @Test
    fun aRunProvesAssemblesReducesAndVerifies() {
        // Drawn at random (seeded) among positions after the corner and 7 more plies that df-pn proves
        // with 20 000 to 2 000 000 evaluations, then a proven move leaving at least 5 replies the rules
        // do not settle: the graph needs about a dozen nodes.
        val start = intArrayOf(0, 30, 28, 42, 54, 57, 43, 2, 60)
        val dir = Files.createTempDirectory("wingraph").toFile()
        try {
            File("../app/src/main/res/raw/tetrano_nn.bin").copyTo(File(dir, "net.bin"))
            val run = Run(
                dir,
                mapOf(
                    "start" to start.joinToString(","), "threads" to "3", "job_evals" to "1",
                    "tt_log2" to "18", "max_choose_evals" to "5000000", "reduce_evals" to "200000",
                ),
            )
            assertEquals(0, run.execute())
            assertEquals("done", run.phase)
            assertTrue("the top tree was not used", run.stats.expansions.get() > 0)
            val graph = File(dir, "graph-best.bin")
            val v = Assembler.verify(graph, Start(start))
            assertNull(v.failure)
            assertTrue(v.nodes >= 1)
            assertTrue(run.stateLong("graph_best_size") <= run.stateLong("graph_v1_size"))

            // The app's player follows the graph: play it against random defenders from the start.
            val player = UnbeatablePlayer(graph.inputStream().use { WinBook.load(it) })
            val rng = Random(3)
            repeat(200) {
                var a = 0L
                var d = 0L
                start.forEachIndexed { i, cell -> if (i % 2 == 0) a = a or (1L shl cell) else d = d or (1L shl cell) }
                var winner = 0
                while (winner == 0) {
                    // Defender: block when it must, else random.
                    val ta = Tactics.threats(a, d)
                    val free = (a or d).inv()
                    assertTrue("draw", free != 0L)
                    val choices = if (ta != 0L && rng.nextInt(4) != 0) ta else free
                    var bits = choices
                    var index = rng.nextInt(java.lang.Long.bitCount(choices))
                    while (index-- > 0) bits = bits and (bits - 1)
                    val dm = java.lang.Long.numberOfTrailingZeros(bits)
                    d = d or (1L shl dm)
                    assertTrue("defender won", !Tactics.wonWith(d, dm))
                    val m = player.chooseRing(a, d)
                    assertTrue("outside the graph", m >= 0)
                    assertEquals(0L, (a or d) and (1L shl m))
                    a = a or (1L shl m)
                    if (Tactics.wonWith(a, m)) winner = 1
                }
            }

            // A graph missing one of its nodes no longer verifies.
            val full = graph.inputStream().use { WinBook.load(it) }
            if (full.size > 0) {
                val bytes = ByteArrayOutputStream().also { full.writeTo(it) }.toByteArray()
                // Drop the last entry (17 bytes) and fix the count.
                val cut = bytes.copyOf(bytes.size - 17)
                java.nio.ByteBuffer.wrap(cut).putInt(8, full.size - 1)
                val broken = File(dir, "broken.bin").also { it.writeBytes(cut) }
                assertNotNull(Assembler.verify(broken, Start(start)).failure)
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
