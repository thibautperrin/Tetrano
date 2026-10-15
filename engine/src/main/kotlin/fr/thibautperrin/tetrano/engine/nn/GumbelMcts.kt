package fr.thibautperrin.tetrano.engine.nn

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lowestCell
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.popCount
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/**
 * Monte Carlo tree search guided by [QubicNet], following Gumbel AlphaZero
 * (Danihelka et al., "Policy improvement by planning with Gumbel", 2022):
 * Gumbel-top-k sampling plus sequential halving at the root, deterministic
 * selection by the improved policy below it. Works with few simulations.
 *
 * Every node first runs exact tactics, before any network call: immediate
 * win, opponent double threat (lost), forced block (single legal move), VCF
 * win, full board (draw). Exact results propagate up the tree
 * (MCTS-solver): a move to a lost position wins; a node whose moves all
 * lead to won positions for the opponent is lost.
 *
 * Values are from the point of view of the player to move at the node, in
 * [-1, 1] (win probability minus loss probability). Not thread-safe: one
 * instance per thread (the [net] itself may be shared).
 *
 * @param net null for a network-free search (uniform priors, value 0),
 *   used to bootstrap training.
 */
class GumbelMcts(
    private val net: QubicNet?,
    val config: Config = Config(),
    private val random: Random = Random.Default,
) {
    data class Config(
        /** Number of root moves sampled before sequential halving. */
        val maxConsidered: Int = 16,
        val cVisit: Float = 50f,
        val cScale: Float = 0.1f,
        /** VCF node budgets (root, other nodes). */
        val vcfBudgetRoot: Int = 50_000,
        val vcfBudgetNode: Int = 1_000,
        /** Gumbel noise at the root: on for self-play, off for deterministic play. */
        val gumbelNoise: Boolean = true,
        /**
         * Weight of the network's moves-left estimate in clearly decided positions
         * (|value| > [movesLeftThreshold]): the winner prefers short wins, the loser long
         * resistance. 0 disables it (pure values, as the review needs).
         */
        val movesLeftWeight: Float = 0.05f,
        val movesLeftThreshold: Float = 0.8f,
    )

    /** Exact status of a node, for the player to move there. */
    enum class Status { UNKNOWN, WIN, LOSS, DRAW }

    class Node internal constructor(val mine: Long, val theirs: Long) {
        var status: Status = Status.UNKNOWN
            internal set

        /** When [status] is [Status.WIN] by tactics or solver: a winning cell. */
        var winningMove: Int = -1
            internal set
        internal var expanded = false
        internal var moves: IntArray = EMPTY_INT

        /** Log prior probabilities of [moves] (log-softmax over legal moves). */
        internal var logits: FloatArray = EMPTY_FLOAT
        internal var children: Array<Node?> = EMPTY_NODES
        internal var visits: IntArray = EMPTY_INT

        /** Sum of the values of each move, from this node's point of view. */
        internal var valueSum: FloatArray = EMPTY_FLOAT
        internal var totalVisits = 0

        /** Simulations run through this node so far (all searches of a reused root included). */
        val simulations: Int get() = totalVisits

        /** Network value for the player to move (0 when exact or without network). */
        var netValue: Float = 0f
            internal set

        /** Network estimate of the plies left until the end of the game (0 without that head). */
        var netMovesLeft: Float = 0f
            internal set
        val netWdl: FloatArray = FloatArray(3)
    }

    /** Outcome of [search]. Cells are ring numbers 0..63. */
    class Result(
        val move: Int,
        /** Exact status of the root, if the search proved it. */
        val status: Status,
        /** Search value of the root for the player to move, in [-1, 1]. */
        val value: Float,
        /** The network's win/draw/loss estimate at the root (exact one-hot if proven). */
        val netWdl: FloatArray,
        /** Improved policy over the 64 cells (0 for occupied cells) — the training target. */
        val policy: FloatArray,
        /** Network prior over the 64 cells. */
        val prior: FloatArray,
        val simulations: Int,
        /** Per legal move: completed Q from the root player's point of view (NaN for occupied cells). */
        val moveValues: FloatArray,
        /** Per cell: simulations through that root move, over every search of the root. */
        val visits: IntArray = IntArray(64),
    )

    private val workspace = net?.newWorkspace()
    private val netPolicy = FloatArray(64)
    private val vcf = Vcf(18)

    /** Network evaluations performed so far. */
    var evaluations: Long = 0L
        private set

    /**
     * Searches the position where the player to move owns [mine] and the
     * opponent [theirs], with at most [simulations] simulations and until
     * [deadlineNanos] (`System.nanoTime()` scale). [onSimulation] is called
     * after each simulation with (done, budget) and may return false to stop.
     * [noise] overrides [Config.gumbelNoise] for this search.
     */
    fun search(
        mine: Long,
        theirs: Long,
        simulations: Int,
        deadlineNanos: Long = Long.MAX_VALUE,
        noise: Boolean = config.gumbelNoise,
        onSimulation: ((Int, Int) -> Boolean)? = null,
    ): Result = search(Node(mine, theirs), simulations, deadlineNanos, noise, onSimulation)

    /** A root for [search] that can be searched again: each search adds to its tree (analysis). */
    fun newRoot(mine: Long, theirs: Long): Node = Node(mine, theirs)

    /**
     * Like the other [search], on [root]: a root already searched keeps its tree, so searching
     * it again with [simulations] more refines the previous result. Not with Gumbel noise.
     */
    fun search(
        root: Node,
        simulations: Int,
        deadlineNanos: Long = Long.MAX_VALUE,
        noise: Boolean = config.gumbelNoise,
        onSimulation: ((Int, Int) -> Boolean)? = null,
    ): Result {
        if (!root.expanded && root.status == Status.UNKNOWN) {
            expand(root, config.vcfBudgetRoot)
            if (root.status == Status.UNKNOWN && root.moves.size == 1) {
                // A forced move: one simulation is enough for its exact outcome (e.g. the
                // block only delays an opponent VCF) to reach the root through the solver.
                simulate(root, 0)
            }
        }
        if (root.status == Status.WIN) preferShortestWin(root)
        if (root.status != Status.UNKNOWN || root.moves.size <= 1) {
            return immediateResult(root)
        }

        val k = root.moves.size
        val gumbel = FloatArray(k) { if (noise) sampleGumbel() else 0f }
        val m = minOf(config.maxConsidered, k)
        var remaining = (0 until k).sortedByDescending { gumbel[it] + root.logits[it] }.take(m).toIntArray()
        val phases = max(1, ceil(ln(m.toDouble()) / ln(2.0)).toInt())
        var done = 0
        var stop = false
        for (phase in 0 until phases) {
            val perAction = max(1, (simulations - done) / ((phases - phase) * remaining.size))
            loop@ for (a in remaining) {
                for (r in 0 until perAction) {
                    if (done >= simulations || System.nanoTime() > deadlineNanos) {
                        stop = true
                        break@loop
                    }
                    simulate(root, a)
                    done++
                    if (onSimulation != null && !onSimulation(done, simulations)) stop = true
                    if (root.status != Status.UNKNOWN) stop = true
                    if (stop) break@loop
                }
            }
            if (stop || remaining.size == 1) break
            val sig = FloatArray(k)
            sigmas(root, sig)
            val scored = remaining.sortedByDescending { gumbel[it] + root.logits[it] + sig[it] }
            remaining = scored.take((remaining.size + 1) / 2).toIntArray()
        }

        if (root.status == Status.WIN) preferShortestWin(root)
        val sig = FloatArray(k)
        sigmas(root, sig)
        val chosen = when (root.status) {
            Status.WIN -> root.moves.indexOf(root.winningMove).takeIf { it >= 0 }
                ?: remaining.maxBy { gumbel[it] + root.logits[it] + sig[it] }
            else -> remaining.maxBy { gumbel[it] + root.logits[it] + sig[it] }
        }
        return buildResult(root, root.moves[chosen], done)
    }

    // ---- tree operations ----

    /** Runs tactics and the network on an unexpanded node; returns its value. */
    private fun expand(node: Node, vcfBudget: Int): Float {
        val mine = node.mine
        val theirs = node.theirs
        val win = Tactics.threats(mine, theirs)
        if (win != 0L) return prove(node, Status.WIN, lowestCell(win))
        val empty = (mine or theirs).inv()
        if (empty == 0L) return prove(node, Status.DRAW, -1)
        val block = Tactics.threats(theirs, mine)
        if (popCount(block) >= 2) {
            // Lost, but keep the moves so a player can still pick the least bad one.
            setMoves(node, block)
            evaluateNet(node)
            node.status = Status.LOSS
            return -1f
        }
        val vcfMove = vcf.solve(mine, theirs, vcfBudget)
        if (vcfMove >= 0) return prove(node, Status.WIN, vcfMove)
        setMoves(node, if (block != 0L) block else empty)
        evaluateNet(node)
        return searchValue(node)
    }

    /**
     * A won root keeps the first winning move found, which may lead to a needlessly long win:
     * when the win is a VCF, play the first move of a shortest one instead.
     */
    private fun preferShortestWin(root: Node) {
        val move = vcf.shortest(root.mine, root.theirs, config.vcfBudgetRoot)
        if (move >= 0) root.winningMove = move
    }

    /** The value backed up for a freshly evaluated node: [Node.netValue] nudged by moves left. */
    private fun searchValue(node: Node): Float {
        val v = node.netValue
        val w = config.movesLeftWeight
        if (w == 0f || kotlin.math.abs(v) <= config.movesLeftThreshold) return v
        val left = node.netMovesLeft / QubicNet.MOVES_LEFT_SCALE
        return (if (v > 0f) v - w * left else v + w * left).coerceIn(-1f, 1f)
    }

    private fun prove(node: Node, status: Status, move: Int): Float {
        node.status = status
        node.winningMove = move
        return exactValue(status)
    }

    private fun setMoves(node: Node, legal: Long) {
        val n = popCount(legal)
        val moves = IntArray(n)
        var bits = legal
        for (i in 0 until n) {
            moves[i] = lowestCell(bits)
            bits = bits and (bits - 1)
        }
        node.moves = moves
        node.children = arrayOfNulls(n)
        node.visits = IntArray(n)
        node.valueSum = FloatArray(n)
        node.logits = FloatArray(n)
        node.expanded = true
    }

    private fun evaluateNet(node: Node) {
        val n = node.moves.size
        if (net == null) {
            val uniform = -ln(n.toFloat())
            node.logits.fill(uniform)
            node.netValue = 0f
            node.netWdl[0] = 0f
            node.netWdl[1] = 1f
            node.netWdl[2] = 0f
            return
        }
        evaluations++
        net.evaluate(node.mine, node.theirs, workspace!!, netPolicy, node.netWdl)
        var maxLogit = Float.NEGATIVE_INFINITY
        for (i in 0 until n) maxLogit = max(maxLogit, netPolicy[node.moves[i]])
        var total = 0.0
        for (i in 0 until n) total += exp((netPolicy[node.moves[i]] - maxLogit).toDouble())
        val logTotal = ln(total).toFloat()
        for (i in 0 until n) node.logits[i] = netPolicy[node.moves[i]] - maxLogit - logTotal
        node.netValue = node.netWdl[0] - node.netWdl[2]
        node.netMovesLeft = workspace.movesLeft
    }

    private fun child(node: Node, index: Int): Node {
        node.children[index]?.let { return it }
        val cell = node.moves[index]
        val moverAfter = node.mine or (1L shl cell)
        val created = Node(mine = node.theirs, theirs = moverAfter)
        if (Tactics.wonWith(moverAfter, cell)) created.status = Status.LOSS
        node.children[index] = created
        return created
    }

    private val pathNodes = arrayOfNulls<Node>(70)
    private val pathMoves = IntArray(70)

    /** One simulation starting with root move [rootIndex]. */
    private fun simulate(root: Node, rootIndex: Int) {
        var depth = 0
        var node = root
        var index = rootIndex
        var value: Float
        while (true) {
            pathNodes[depth] = node
            pathMoves[depth] = index
            depth++
            val next = child(node, index)
            if (next.status != Status.UNKNOWN) {
                value = exactValue(next.status)
                break
            }
            if (!next.expanded) {
                value = expand(next, config.vcfBudgetNode)
                break
            }
            node = next
            index = selectInner(node)
        }
        // value is from the point of view of the player to move at the leaf.
        for (d in depth - 1 downTo 0) {
            val n = pathNodes[d]!!
            val i = pathMoves[d]
            value = -value
            n.visits[i]++
            n.valueSum[i] += value
            n.totalVisits++
            updateStatus(n, i)
        }
    }

    /** MCTS-solver: refresh [node]'s exact status after its child [index] changed. */
    private fun updateStatus(node: Node, index: Int) {
        if (node.status != Status.UNKNOWN) return
        val childStatus = node.children[index]?.status ?: return
        if (childStatus == Status.LOSS) {
            node.status = Status.WIN
            node.winningMove = node.moves[index]
            return
        }
        if (childStatus == Status.UNKNOWN) return
        var anyDraw = false
        for (c in node.children) {
            val s = c?.status ?: return
            when (s) {
                Status.UNKNOWN -> return
                Status.DRAW -> anyDraw = true
                else -> {}
            }
        }
        node.status = if (anyDraw) Status.DRAW else Status.LOSS
    }

    /** Deterministic inner selection: argmax of improved policy minus visit share. */
    private fun selectInner(node: Node): Int {
        val n = node.moves.size
        if (n == 1) return 0
        var maxScore = Float.NEGATIVE_INFINITY
        val scores = scratchScores(n)
        sigmas(node, scores)
        for (i in 0 until n) {
            scores[i] += node.logits[i]
            maxScore = max(maxScore, scores[i])
        }
        var total = 0f
        for (i in 0 until n) {
            scores[i] = exp(scores[i] - maxScore)
            total += scores[i]
        }
        val denominator = 1f + node.totalVisits
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            val s = scores[i] / total - node.visits[i] / denominator
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        return best
    }

    private var scores = FloatArray(64)
    private fun scratchScores(n: Int): FloatArray {
        if (scores.size < n) scores = FloatArray(n)
        return scores
    }

    /** Q of move [index] from [node]'s point of view; exact when the child is solved. */
    private fun completedQ(node: Node, index: Int, vMix: Float = mixedValue(node)): Float {
        val c = node.children[index]
        if (c != null && c.status != Status.UNKNOWN) return -exactValue(c.status)
        val visits = node.visits[index]
        return if (visits > 0) node.valueSum[index] / visits else vMix
    }

    /** Value estimate mixing the network value with the visited moves' Q (Gumbel paper, eq. 33). */
    private fun mixedValue(node: Node): Float {
        val total = node.totalVisits
        if (total == 0) return node.netValue
        var priorSum = 0f
        var weighted = 0f
        for (i in node.moves.indices) {
            if (node.visits[i] > 0) {
                val p = exp(node.logits[i])
                priorSum += p
                weighted += p * node.valueSum[i] / node.visits[i]
            }
        }
        if (priorSum <= 0f) return node.netValue
        return (node.netValue + total * weighted / priorSum) / (1f + total)
    }

    /**
     * sigma(completed Q) of the Gumbel paper for every move of [node], into [out]: Q rescaled to
     * [0, 1] by the min and max over the node's non-solved moves (as in DeepMind's mctx), solved
     * moves clamped to 0 (loss) or 1 (win), times `(cVisit + max visits) * cScale`.
     */
    private fun sigmas(node: Node, out: FloatArray) {
        val n = node.moves.size
        val vMix = mixedValue(node)
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        var maxVisits = 0
        for (i in 0 until n) {
            val q = completedQ(node, i, vMix)
            out[i] = q
            val c = node.children[i]
            if (c == null || c.status == Status.UNKNOWN) {
                lo = minOf(lo, q)
                hi = max(hi, q)
            }
            maxVisits = max(maxVisits, node.visits[i])
        }
        val scale = (config.cVisit + maxVisits) * config.cScale
        val range = hi - lo
        for (i in 0 until n) {
            val normalized = if (range > 1e-6f) ((out[i] - lo) / range).coerceIn(0f, 1f) else {
                // all non-solved moves look alike: keep solved ones apart
                ((out[i] + 1f) * 0.5f).coerceIn(0f, 1f)
            }
            out[i] = scale * normalized
        }
    }

    private fun sampleGumbel(): Float {
        val u = random.nextDouble(1e-12, 1.0)
        return (-ln(-ln(u))).toFloat()
    }

    // ---- results ----

    private fun immediateResult(root: Node): Result {
        val move = when {
            root.status == Status.WIN -> root.winningMove
            root.moves.isNotEmpty() -> root.moves[root.logits.indices.maxBy { root.logits[it] }]
            else -> -1
        }
        return buildResult(root, move, 0)
    }

    private fun buildResult(root: Node, move: Int, simulations: Int): Result {
        val policy = FloatArray(64)
        val prior = FloatArray(64)
        val moveValues = FloatArray(64) { Float.NaN }
        val n = root.moves.size
        if (root.status == Status.WIN && root.winningMove >= 0) {
            policy[root.winningMove] = 1f
        } else if (n > 0) {
            val scores = FloatArray(n)
            sigmas(root, scores)
            var maxScore = Float.NEGATIVE_INFINITY
            for (i in 0 until n) {
                scores[i] += root.logits[i]
                maxScore = max(maxScore, scores[i])
            }
            var total = 0f
            for (i in 0 until n) {
                scores[i] = exp(scores[i] - maxScore)
                total += scores[i]
            }
            for (i in 0 until n) policy[root.moves[i]] = scores[i] / total
        }
        val visits = IntArray(64)
        for (i in 0 until n) {
            prior[root.moves[i]] = exp(root.logits[i])
            moveValues[root.moves[i]] = completedQ(root, i)
            visits[root.moves[i]] = root.visits[i]
        }
        val value = when (root.status) {
            Status.UNKNOWN -> mixedValue(root)
            else -> exactValue(root.status)
        }
        val wdl = when (root.status) {
            Status.WIN -> floatArrayOf(1f, 0f, 0f)
            Status.LOSS -> floatArrayOf(0f, 0f, 1f)
            Status.DRAW -> floatArrayOf(0f, 1f, 0f)
            Status.UNKNOWN -> root.netWdl.copyOf()
        }
        return Result(move, root.status, value, wdl, policy, prior, simulations, moveValues, visits)
    }

    companion object {
        private val EMPTY_INT = IntArray(0)
        private val EMPTY_FLOAT = FloatArray(0)
        private val EMPTY_NODES = arrayOfNulls<Node>(0)

        fun exactValue(status: Status): Float = when (status) {
            Status.WIN -> 1f
            Status.LOSS -> -1f
            else -> 0f
        }
    }
}
