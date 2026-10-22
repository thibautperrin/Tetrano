package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import fr.thibautperrin.tetrano.engine.wingraph.WinRules
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Phase 2 (and the end of each reduction pass): walks the whole strategy
 * from the empty board against every defender reply, filling any gap with
 * the extractor, keeps exactly the book nodes met (the graph), writes them
 * as a [WinBook], then checks that file alone with [verify].
 *
 * The 12 replies to the corner are walked in parallel; a node met in
 * several branches is kept once.
 */
class Assembler(private val run: Run, private val net: QubicNet) {
    @Volatile
    private var stopping = false
    private val dfpns = ArrayList<DfPn>()

    fun requestStop() {
        stopping = true
        synchronized(dfpns) { dfpns.forEach { it.abort = true } }
    }

    /** Writes the graph to [out]; returns its node count, or -1 (stopped or failed, see the log). */
    fun assemble(out: File): Int {
        val start = run.config.start
        run.log("assemblage : parcours complet depuis start=$start")
        val graph = PositionMap(16)
        if (start.fromEmpty) graph.put(0L, 0L, 0) // the first move
        val rootKids = Children().generate(start.rootA, start.rootD, Board.candidateMoves(start.rootA, start.rootD))
        val pool = Executors.newFixedThreadPool(run.config.threads) { r -> Thread(r).also { it.priority = Thread.MIN_PRIORITY } }
        // One extractor per pool thread (its df-pn table is large).
        val walkers = ThreadLocal.withInitial {
            val dfpn = DfPn(run.config.ttLog2).also { synchronized(dfpns) { dfpns += it } }
            val chooser = SmallGraphChooser(dfpn, NetEval(net), run.bookView, run.config.jobEvals, run.config.maxChooseEvals) { stopping }
            Walker(run.bookView, { x, y, m -> run.book.putIfAbsent(x, y, m) }, null, chooser).also { w ->
                w.onBookNode = { x, y, m -> graph.put(x, y, m) }
            }
        }
        try {
            val tasks = (0 until rootKids.count).map { i ->
                val a = rootKids.a[i]
                val d = rootKids.d[i]
                Callable {
                    val walker = walkers.get()
                    walker.reset()
                    val size = walker.walk(a, d)
                    if (size < 0 && !stopping) run.log("assemblage : échec sous ${hex(a)}/${hex(d)} : ${walker.failure}")
                    size >= 0
                }
            }
            val ok = pool.invokeAll(tasks).all { it.get() }
            if (!ok || stopping) return -1
        } finally {
            pool.shutdownNow()
        }
        val n = graph.size
        val a = LongArray(n)
        val d = LongArray(n)
        val m = ByteArray(n)
        var i = 0
        graph.forEach { x, y, v ->
            a[i] = x; d[i] = y; m[i] = v.toByte(); i++
        }
        val book = WinBook.of(a, d, m)
        val tmp = File(out.parentFile, out.name + ".tmp")
        tmp.outputStream().buffered().use { book.writeTo(it) }
        val check = verify(tmp, start)
        if (check.failure != null) {
            run.log("assemblage : le graphe écrit ne passe pas la vérification : ${check.failure}")
            run.fatal = IllegalStateException("graph verification failed: ${check.failure}")
            return -1
        }
        java.nio.file.Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        run.log("assemblage : ${check.nodes} nœuds, ${check.positions} positions vérifiées, parties d'au plus ${check.maxRings} anneaux")
        return check.nodes
    }

    class Verification(val nodes: Int, val positions: Long, val maxRings: Int, val failure: String?)

    companion object {
        /**
         * Checks a graph file alone (no extractor, nothing else from the run): the rules + this graph
         * must win from [start] against every defender reply.
         */
        fun verify(file: File, start: Start = Start.CORNER): Verification {
            val book = file.inputStream().use { WinBook.load(it) }
            val walker = Walker(book, { _, _, m -> m }, null, null)
            val used = PositionMap(16)
            walker.onBookNode = { a, d, m -> used.put(a, d, m) }
            val ok = start.walkAll(walker)
            return Verification(
                used.size, walker.positions, walker.maxRings,
                if (!ok) walker.failure ?: "lost" else if (used.size != book.size) "${book.size - used.size} unused entries" else null,
            )
        }
    }
}
