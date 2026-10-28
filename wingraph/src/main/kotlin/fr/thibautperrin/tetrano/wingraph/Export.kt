package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import fr.thibautperrin.tetrano.engine.wingraph.WinRules
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Training positions whose value is proven: every position of the walk of a verified graph
 * (the strategy against every defender reply, as in [Assembler.verify]), written in the
 * self-play record format of `tools/nn/data.py` with the "exact" flag:
 * - attacker to move: a win for the side to move; at a book node the policy target is the
 *   graph's move (a proven winning move), elsewhere no policy target;
 * - defender to move: a loss for the side to move, no policy target.
 *
 * Positions with an immediate win, or a defender facing two threats, teach nothing and are
 * skipped. Book nodes are all kept; the other positions with probability [rate] (a hash of the
 * position, so the same positions on every run). No moves-left target (0).
 */
class ProvenExport(private val book: WinBook, private val rate: Double, private val out: java.io.OutputStream) {
    private val visited = PositionMap(16)
    private val defenderSeen = PositionMap(16)
    private val vcf = WinRules.newVcf()
    private val children = Array(65) { Children() }
    private val form = CanonicalForm()
    private val buffer = ByteBuffer.allocate(RECORD).order(ByteOrder.LITTLE_ENDIAN)
    private val threshold = (rate * (1L shl 32)).toLong()

    var written = 0L
        private set
    var bookNodes = 0L
        private set

    fun walkAll(start: Start) {
        if (start.fromEmpty) {
            walk(0L, 0L)
            return
        }
        val kids = Children().generate(start.rootA, start.rootD, Board.candidateMoves(start.rootA, start.rootD))
        for (i in 0 until kids.count) walk(kids.a[i], kids.d[i])
    }

    private fun walk(a: Long, d: Long) {
        if (!added(visited, a, d)) return
        val decision = WinRules.decide(a, d, book, vcf)
        check(decision != WinRules.NONE) { "the graph does not decide ${hex(a)}/${hex(d)}" }
        val m = WinRules.move(decision)
        val rule = WinRules.rule(decision)
        if (rule == WinRules.BOOK) {
            bookNodes++
            write(a, d, WIN, m)
        } else if (rule != WinRules.WIN && keep(a, d)) {
            write(a, d, WIN, -1)
        }
        val na = a or (1L shl m)
        if (Tactics.wonWith(na, m)) return
        val ta = Tactics.threats(na, d)
        if (java.lang.Long.bitCount(ta) >= 2) return
        form.of(na, d)
        if (added(defenderSeen, form.attacker, form.defender) && keep(form.attacker, form.defender)) {
            write(d, na, LOSS, -1)
        }
        val replies = if (ta != 0L) ta else (na or d).inv()
        val rings = java.lang.Long.bitCount(na or d) + 1
        val kids = children[rings - 1].generate(na, d, replies)
        for (i in 0 until kids.count) walk(kids.a[i], kids.d[i])
    }

    private fun added(map: PositionMap, a: Long, d: Long): Boolean {
        val before = map.size
        map.putIfAbsent(a, d, 0)
        return map.size > before
    }

    private fun keep(a: Long, d: Long): Boolean {
        var h = a * -0x61c8864680b583ebL xor java.lang.Long.rotateLeft(d * -0x3d4d51c2d82b14b1L, 29)
        h = h xor (h ushr 29)
        h *= -0x4b47d5b1a6d5e2b7L
        return (h ushr 32) < threshold
    }

    /** A record for the side to move [mine]; [move] >= 0: the policy target. */
    private fun write(mine: Long, theirs: Long, outcome: Int, move: Int) {
        buffer.clear()
        buffer.putLong(mine)
        buffer.putLong(theirs)
        buffer.put(outcome.toByte())
        buffer.put((EXACT or if (move >= 0) POLICY else 0).toByte())
        buffer.put(java.lang.Long.bitCount(mine or theirs).toByte())
        buffer.put(0)
        for (c in 0 until 64) buffer.putShort(if (c == move) 0xFFFF.toShort() else 0)
        buffer.putFloat(if (outcome == WIN) 1f else -1f)
        out.write(buffer.array())
        written++
    }

    companion object {
        const val RECORD = 152
        const val WIN = 0
        const val LOSS = 2
        const val POLICY = 1
        const val EXACT = 2
    }
}

/** `export graph=<file> [start=...] out=<file> [rate=0.1]`: see [ProvenExport]. */
internal fun exportCommand(options: Map<String, String>): Int {
    val file = File(options["graph"] ?: error("graph= missing"))
    val out = File(options["out"] ?: error("out= missing"))
    val rate = options["rate"]?.toDouble() ?: 0.1
    val book = file.inputStream().use { WinBook.load(it) }
    val started = System.currentTimeMillis()
    val export = out.outputStream().buffered(1 shl 20).use { stream ->
        ProvenExport(book, rate, stream).also { it.walkAll(Start.parse(options["start"])) }
    }
    println("${file.name} : ${export.written} positions écrites (${export.bookNodes} nœuds du graphe), " +
        "${System.currentTimeMillis() - started} ms")
    return 0
}
