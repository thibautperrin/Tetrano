package fr.thibautperrin.tetrano.wingraph

import fr.thibautperrin.tetrano.wingraph.TNode.Companion.INF
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * A node of the top tree: a canonical position, attacker to move (OR node)
 * or defender to move (AND node). [move] is the parent's move leading here,
 * in the parent's canonical frame.
 */
class TNode(val a: Long, val d: Long, val parent: TNode?, val move: Int) {
    var pn: Long = 1
    var dn: Long = 1
    var state: Int = LEAF
    var children: Array<TNode>? = null

    /** OR node: moves not yet children (best first) and their policy priors. */
    var pendingMoves: IntArray? = null
    var pendingPriors: FloatArray? = null
    var pendingIndex: Int = 0

    /** Selections through this node. */
    var visits: Int = 0

    /** Proved node: approximate number of book nodes below (itself included). */
    var size: Int = 0

    /** The network's win probability for the attacker at creation (status only). */
    var value: Float = Float.NaN

    val isOr: Boolean get() = Board.attackerToMove(a, d)
    val pendingCount: Int get() = (pendingMoves?.size ?: 0) - pendingIndex
    val solved: Boolean get() = state == PROVED || state == DISPROVED

    companion object {
        const val LEAF = 0
        const val BUSY = 1
        const val INNER = 2
        const val PROVED = 3
        const val DISPROVED = 4
        const val INF: Long = Long.MAX_VALUE / 4
    }
}

/**
 * The top tree: best-first proof-number search ("PN²" style) whose leaves are
 * handed to workers ([Prover]): a worker first tries to settle a leaf with a
 * bounded [DfPn]; failing that, the leaf is expanded with the network's
 * help (move ordering and widening at OR nodes, initial proof and disproof
 * numbers from its evaluation). Several workers run at once: [select] never
 * returns a leaf below a node whose whole subtree is busy.
 *
 * A tree, not a DAG: transpositions are only shared once solved (the
 * [solved] map, consulted when a node is created). Solved subtrees are
 * dropped to save memory; what they proved lives in the book.
 *
 * Not thread-safe: the [Prover] serializes access.
 */
class TopTree(val root: TNode) {
    var nodeCount: Int = 1
        private set

    // ---- initial numbers ----

    /** OR leaf from the attacker's win probability [p]. */
    fun initOr(node: TNode, p: Float) {
        val q = p.coerceIn(0.01f, 0.99f).toDouble()
        node.value = p
        node.pn = 1 + (PN_K * (1 - q) / q).roundToLong()
        node.dn = 1 + (DN_K * q / (1 - q)).roundToLong()
    }

    /** AND leaf from the attacker's policy prior [prior] of the move leading to it. */
    fun initAnd(node: TNode, prior: Float) {
        node.pn = andPn(prior)
        node.dn = 1 + (AND_DN * prior).roundToLong()
    }

    // ---- selection ----

    /** The most-proving leaf that no worker holds (marked [TNode.BUSY]), or null. */
    fun select(): TNode? = select(root)?.also { it.state = TNode.BUSY }

    private fun select(node: TNode): TNode? {
        when (node.state) {
            TNode.LEAF -> return node
            TNode.INNER -> {}
            else -> return null
        }
        node.visits++
        if (node.isOr) maybeWiden(node)
        val kids = node.children ?: return null
        val open = kids.filter { it.state == TNode.LEAF || it.state == TNode.INNER }
        val ordered = if (node.isOr) {
            open.sortedWith(compareBy<TNode> { it.pn }.thenByDescending { it.dn })
        } else {
            open.sortedWith(compareBy<TNode> { it.dn }.thenByDescending { it.pn })
        }
        for (child in ordered) {
            val leaf = select(child)
            if (leaf != null) return leaf
        }
        return null
    }

    /**
     * Pending moves are virtual children with the proof number of [initAnd]: the next one becomes
     * a real child as soon as it would be the most proving (or no child is left to search).
     */
    private fun maybeWiden(node: TNode) {
        if (node.pendingCount == 0) return
        val kids = node.children ?: emptyArray()
        val bestOpen = kids.filter { !it.solved }.minOfOrNull { it.pn }
        if (bestOpen != null && bestOpen <= pendingPn(node)) return
        val i = node.pendingIndex++
        val move = node.pendingMoves!![i]
        val prior = node.pendingPriors!![i]
        if (node.pendingCount == 0) {
            node.pendingMoves = null
            node.pendingPriors = null
            node.pendingIndex = 0
        }
        val child = newAndChild(node, move, prior)
        node.children = kids + child
        nodeCount++
        update(node)
    }

    /** Proof number of OR node [node]'s next pending move ([TNode.pendingCount] > 0). */
    private fun pendingPn(node: TNode): Long = andPn(node.pendingPriors!![node.pendingIndex])

    private fun andPn(prior: Float): Long = AND_PN_BASE + (AND_PN_LOG * -ln(max(prior.toDouble(), 1e-4))).roundToLong()

    /** The AND child of OR node [parent] for [move]; settled at once when trivial. */
    fun newAndChild(parent: TNode, move: Int, prior: Float): TNode {
        val form = formOf(parent.a or (1L shl move), parent.d)
        val child = TNode(form.attacker, form.defender, parent, move)
        initAnd(child, prior)
        val known = solvedState?.invoke(child.a, child.d) ?: TNode.LEAF
        when {
            known == TNode.PROVED -> prove(child, 0)
            known == TNode.DISPROVED -> disprove(child)
            fr.thibautperrin.tetrano.engine.nn.Tactics.threats(child.d, child.a) != 0L -> disprove(child)
            java.lang.Long.bitCount(fr.thibautperrin.tetrano.engine.nn.Tactics.threats(child.a, child.d)) >= 2 -> prove(child, 0)
            (child.a or child.d) == Board.FULL -> disprove(child)
        }
        return child
    }

    /** Looks up positions solved earlier (set by the [Prover]). */
    var solvedState: ((Long, Long) -> Int)? = null

    private val form = fr.thibautperrin.tetrano.engine.wingraph.CanonicalForm()
    private fun formOf(a: Long, d: Long) = form.of(a, d)

    // ---- results ----

    /** Makes [node] a leaf again (its job was abandoned). */
    fun release(node: TNode) {
        if (node.state == TNode.BUSY) node.state = TNode.LEAF
    }

    /** Expands [node] with [kids] (and, for an OR node, the moves kept for later). */
    fun expand(node: TNode, kids: List<TNode>, pendingMoves: IntArray?, pendingPriors: FloatArray?) {
        node.children = kids.toTypedArray()
        if (pendingMoves != null && pendingMoves.isNotEmpty()) {
            node.pendingMoves = pendingMoves
            node.pendingPriors = pendingPriors
            node.pendingIndex = 0
        }
        node.state = TNode.INNER
        nodeCount += kids.size
        update(node)
    }

    /** Called when a node is proved or disproved: (node, proved). */
    var onSolved: ((TNode, Boolean) -> Unit)? = null

    fun prove(node: TNode, size: Int) {
        node.state = TNode.PROVED
        node.size = size
        node.pn = 0
        node.dn = INF
        collapse(node)
        onSolved?.invoke(node, true)
    }

    fun disprove(node: TNode) {
        node.state = TNode.DISPROVED
        node.pn = INF
        node.dn = 0
        collapse(node)
        onSolved?.invoke(node, false)
    }

    /** Settles a busy or leaf node from a worker's result and updates its ancestors. */
    fun settle(node: TNode, proved: Boolean, size: Int) {
        if (proved) prove(node, size) else disprove(node)
        node.parent?.let { update(it) }
    }

    private fun collapse(node: TNode) {
        node.children?.let { nodeCount -= countBelow(it) }
        node.children = null
        node.pendingMoves = null
        node.pendingPriors = null
    }

    private fun countBelow(kids: Array<TNode>): Int {
        var n = kids.size
        for (k in kids) k.children?.let { n += countBelow(it) }
        return n
    }

    /** Recomputes [node]'s numbers from its children, then its ancestors' while they change. */
    fun update(node: TNode) {
        var current: TNode? = node
        while (current != null && current.state == TNode.INNER) {
            val oldPn = current.pn
            val oldDn = current.dn
            recompute(current)
            if (current.solved) {
                current = current.parent
                continue
            }
            if (current.pn == oldPn && current.dn == oldDn) break
            current = current.parent
        }
    }

    private fun recompute(node: TNode) {
        val kids = node.children ?: return
        if (node.isOr) {
            var best: TNode? = null
            var pn = if (node.pendingCount > 0) pendingPn(node) else INF
            var dn = node.pendingCount * PENDING_DN
            var allLost = true
            for (k in kids) {
                if (k.state == TNode.PROVED && (best == null || k.size < best.size)) best = k
                if (k.state != TNode.DISPROVED) allLost = false
                pn = min(pn, k.pn)
                dn = min(INF, dn + k.dn)
            }
            when {
                best != null -> {
                    onBookMove?.invoke(node, best.move)
                    prove(node, if (best.size == Int.MAX_VALUE) best.size else best.size + 1)
                }
                allLost && node.pendingCount == 0 -> disprove(node)
                else -> {
                    node.pn = pn
                    node.dn = max(dn, 1)
                }
            }
        } else {
            var pn = 0L
            var dn = INF
            var allWon = true
            var lost = false
            var size = 0L
            for (k in kids) {
                if (k.state == TNode.DISPROVED) lost = true
                if (k.state != TNode.PROVED) allWon = false else size += k.size
                pn = min(INF, pn + k.pn)
                dn = min(dn, k.dn)
            }
            when {
                lost -> disprove(node)
                allWon -> prove(node, min(size, Int.MAX_VALUE.toLong()).toInt())
                else -> {
                    node.pn = pn
                    node.dn = dn
                }
            }
        }
    }

    /** Called when an OR node is proved through a child: (node, its move); the [Prover] puts it in the book. */
    var onBookMove: ((TNode, Int) -> Unit)? = null

    // ---- persistence ----

    fun save(file: File) {
        writeAtomically(file) { out ->
            out.writeInt(MAGIC)
            write(out, root)
        }
    }

    private fun write(out: DataOutputStream, n: TNode) {
        out.writeLong(n.a)
        out.writeLong(n.d)
        out.writeByte(n.move)
        out.writeByte(if (n.state == TNode.BUSY) TNode.LEAF else n.state)
        out.writeLong(n.pn)
        out.writeLong(n.dn)
        out.writeInt(n.visits)
        out.writeInt(n.size)
        out.writeFloat(n.value)
        val pending = n.pendingCount
        out.writeInt(pending)
        for (i in 0 until pending) {
            out.writeByte(n.pendingMoves!![n.pendingIndex + i])
            out.writeFloat(n.pendingPriors!![n.pendingIndex + i])
        }
        val kids = n.children
        out.writeInt(kids?.size ?: -1)
        kids?.forEach { write(out, it) }
    }

    companion object {
        private const val MAGIC = 0x54545245 // "TTRE"

        /** Initial numbers: see [initOr] and [initAnd]. */
        const val PN_K = 8.0
        const val DN_K = 8.0
        const val AND_PN_BASE = 20L
        const val AND_PN_LOG = 20.0
        const val AND_DN = 30.0

        /** Disproof number counted for each move not yet a child of an OR node. */
        const val PENDING_DN = 1L

        fun load(file: File): TopTree {
            DataInputStream(file.inputStream().buffered(1 shl 16)).use { input ->
                require(input.readInt() == MAGIC) { "$file: not a top tree" }
                var count = 0
                fun read(parent: TNode?): TNode {
                    val n = TNode(input.readLong(), input.readLong(), parent, input.readByte().toInt())
                    count++
                    n.state = input.readByte().toInt()
                    n.pn = input.readLong()
                    n.dn = input.readLong()
                    n.visits = input.readInt()
                    n.size = input.readInt()
                    n.value = input.readFloat()
                    val pending = input.readInt()
                    if (pending > 0) {
                        n.pendingMoves = IntArray(pending)
                        n.pendingPriors = FloatArray(pending)
                        for (i in 0 until pending) {
                            n.pendingMoves!![i] = input.readByte().toInt()
                            n.pendingPriors!![i] = input.readFloat()
                        }
                    }
                    val kids = input.readInt()
                    if (kids >= 0) n.children = Array(kids) { read(n) }
                    return n
                }
                val root = read(null)
                return TopTree(root).also { it.nodeCount = count }
            }
        }
    }
}
