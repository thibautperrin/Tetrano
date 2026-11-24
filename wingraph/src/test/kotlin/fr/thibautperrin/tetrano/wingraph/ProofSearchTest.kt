package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.ProofSearch
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The engine's generalized [ProofSearch], first player attacking, matches this module's [DfPn] exactly. */
class ProofSearchTest {
    @Test
    fun `same verdicts and evaluations as DfPn`() {
        val rng = Random(3)
        val form = CanonicalForm()
        val dfpn = DfPn(18)
        val search = ProofSearch(attackerFirst = true, 18)
        repeat(30) {
            var a = 1L
            var d = 0L
            var placed = 0
            val plies = 10 + rng.nextInt(10)
            while (placed < plies) {
                val free = (a or d).inv()
                var bits = free
                var k = rng.nextInt(java.lang.Long.bitCount(free))
                while (k-- > 0) bits = bits and (bits - 1)
                val cell = java.lang.Long.numberOfTrailingZeros(bits)
                if (Board.attackerToMove(a, d)) a = a or (1L shl cell) else d = d or (1L shl cell)
                placed++
            }
            form.of(a, d)
            val before1 = dfpn.evals
            val before2 = search.evals
            val r1 = dfpn.solve(form.attacker, form.defender, 20_000)
            val r2 = search.prove(form.attacker, form.defender, 20_000)
            assertEquals(r1, r2)
            assertEquals(dfpn.evals - before1, search.evals - before2)
        }
    }
}
