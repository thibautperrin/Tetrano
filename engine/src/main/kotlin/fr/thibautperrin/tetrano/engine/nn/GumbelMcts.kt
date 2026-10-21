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
 * Moves equivalent under a board automorphism fixing the position (one of
 * the 192, see [QubicGeometry.symmetries]) are one move of the tree, its
 * class: prior summed over the class, searched once; results give every
 * cell of a class the same values, and the move played is a random cell of
 * the chosen class. The network is equivariant, so nothing is lost. A node's
 * symmetries are those of its parent that fix the move played (all of them
 * at the root), so finding them costs nothing below the first plies.
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
        /** [searchAdaptive]: simulations of its first round. */
        val adaptiveFirst: Int = 64,
        /**
         * [searchAdaptive] stops once the same move has been chosen [stableRounds] rounds in a
         * row, or only [decidedStableRounds] when the position is decided (|completed Q| of the
         * move at least [decidedValue]: clearly won or lost, the choice barely matters).
         * Calibrated with tools/nn/adaptive_rules.py on 1000 battle positions (big network):
         * against a 2048-simulation search, regret 0.0046 for 468 simulations on average,
         * where a fixed search needs about 950 (0.0109 at 512, 0.0037 at 1024).
         */
        val stableRounds: Int = 4,
        val decidedStableRounds: Int = 2,
        val decidedValue: Float = 0.8f,
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

        /** One cell per class of equivalent moves (its lowest). */
        internal var moves: IntArray = EMPTY_INT

        /** The cells of each class of [moves]; null when every class is a single cell. */
        internal var classes: LongArray? = null

        /** The automorphisms fixing this position known to the search (0, the identity, first). */
        internal var symmetries: IntArray = IDENTITY

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
        /** Per cell: simulations through that root move's class, over every search of the root. */
        val visits: IntArray = IntArray(64),
        /**
         * Per cell: index of its class of equivalent moves (cells of one class are the same move
         * up to a symmetry of the position, with the same values), -1 if not a legal move.
         */
        val moveClass: IntArray = IntArray(64) { -1 },
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
    ): Result = search(newRoot(mine, theirs), simulations, deadlineNanos, noise, onSimulation)

    /** A root for [search] that can be searched again: each search adds to its tree (analysis). */
    fun newRoot(mine: Long, theirs: Long): Node = Node(mine, theirs).also { it.symmetries = symmetriesOf(mine, theirs) }

    /**
     * A search whose budget follows the position's difficulty: [Config.adaptiveFirst]
     * simulations, then doubled (128, 256, ...) up to [maxSimulations] as long as the search
     * hesitates: until the same move (up to symmetry) has been chosen [Config.stableRounds]
     * rounds in a row ([Config.decidedStableRounds] in a decided position). The Gumbel noise is
     * drawn once, and each round adds to the same tree.
     */
    fun searchAdaptive(
        mine: Long,
        theirs: Long,
        maxSimulations: Int,
        deadlineNanos: Long = Long.MAX_VALUE,
        noise: Boolean = config.gumbelNoise,
        onSimulation: ((Int, Int) -> Boolean)? = null,
        /** Replaces the stopping rule (calibration): called after each round, true to stop. */
        stopRule: ((Result) -> Boolean)? = null,
    ): Result {
        val root = newRoot(mine, theirs)
        prepare(root)?.let { return it }
        val gumbel = FloatArray(root.moves.size) { if (noise) sampleGumbel() else 0f }
        var budget = minOf(config.adaptiveFirst, maxSimulations).coerceAtLeast(1)
        var done = 0
        var previous = -1
        var sameRounds = 0
        while (true) {
            val round = halve(root, gumbel, budget - done, done, maxSimulations, deadlineNanos, onSimulation)
            done += round.done
            sameRounds = if (round.chosen == previous) sameRounds + 1 else 1
            previous = round.chosen
            val stop = round.stopped || budget >= maxSimulations ||
                (if (stopRule != null) stopRule(finish(root, round.chosen, done)) else isSettled(root, round.chosen, sameRounds))
            if (stop) return finish(root, round.chosen, done)
            budget = minOf(maxSimulations, budget * 2)
        }
    }

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
        prepare(root)?.let { return it }
        val gumbel = FloatArray(root.moves.size) { if (noise) sampleGumbel() else 0f }
        val round = halve(root, gumbel, simulations, 0, simulations, deadlineNanos, onSimulation)
        return finish(root, round.chosen, round.done)
    }

    /** Expands [root] if needed; the result when there is nothing to search (proven, forced), else null. */
    private fun prepare(root: Node): Result? {
        if (!root.expanded && root.status == Status.UNKNOWN) {
            expand(root, config.vcfBudgetRoot)
            if (root.status == Status.UNKNOWN && root.moves.size == 1) {
                // A forced move (or all moves alike): one simulation is enough for its exact
                // outcome (e.g. the block only delays an opponent VCF) to reach the root.
                simulate(root, 0)
            }
        }
        if (root.status == Status.WIN) preferShortestWin(root)
        if (root.status != Status.UNKNOWN || root.moves.size <= 1) return immediateResult(root)
        return null
    }

    /** One round of sequential halving: [done] simulations, the move class [chosen], whether it [stopped] early. */
    private class Round(val done: Int, val chosen: Int, val stopped: Boolean)

    /**
     * Sequential halving at the root over the top [Config.maxConsidered] classes by
     * Gumbel + prior, with [simulations] simulations; [before] simulations were already run by
     * earlier rounds, [budget] is the total reported to [onSimulation].
     */
    private fun halve(
        root: Node,
        gumbel: FloatArray,
        simulations: Int,
        before: Int,
        budget: Int,
        deadlineNanos: Long,
        onSimulation: ((Int, Int) -> Boolean)?,
    ): Round {
        val k = root.moves.size
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
                    if (onSimulation != null && !onSimulation(before + done, budget)) stop = true
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
        val sig = FloatArray(k)
        sigmas(root, sig)
        val chosen = remaining.maxBy { gumbel[it] + root.logits[it] + sig[it] }
        return Round(done, chosen, stop && root.status == Status.UNKNOWN && done < simulations)
    }

    /** Whether [searchAdaptive] can stop, class [chosen] having been chosen [sameRounds] rounds in a row. */
    private fun isSettled(root: Node, chosen: Int, sameRounds: Int): Boolean {
        if (root.status != Status.UNKNOWN || sameRounds >= config.stableRounds) return true
        return sameRounds >= config.decidedStableRounds && kotlin.math.abs(completedQ(root, chosen)) >= config.decidedValue
    }

    /** The result of a searched root: the shortest win if proven won, else a cell of class [chosen]. */
    private fun finish(root: Node, chosen: Int, done: Int): Result {
        if (root.status == Status.WIN) {
            preferShortestWin(root)
            if (root.winningMove >= 0) return buildResult(root, root.winningMove, done)
        }
        return buildResult(root, member(root, chosen), done)
    }

    /** A random cell of [node]'s move class [index]. */
    private fun member(node: Node, index: Int): Int {
        val cells = node.classes?.get(index) ?: return node.moves[index]
        var pick = random.nextInt(popCount(cells))
        var bits = cells
        while (pick-- > 0) bits = bits and (bits - 1)
        return lowestCell(bits)
    }

    /** The cells of [node]'s move class [index]. */
    private fun cellsOf(node: Node, index: Int): Long = node.classes?.get(index) ?: (1L shl node.moves[index])

    /** The automorphisms fixing the position (identity first). */
    private fun symmetriesOf(mine: Long, theirs: Long): IntArray {
        if (mine == 0L && theirs == 0L) return ALL_SYMMETRIES
        var out: IntArray? = null
        var n = 0
        for (s in 1 until QubicGeometry.symmetryCount) {
            if (QubicGeometry.transform(s, mine) == mine && QubicGeometry.transform(s, theirs) == theirs) {
                if (out == null) out = IntArray(QubicGeometry.symmetryCount).also { it[0] = 0 }
                out[++n] = s
            }
        }
        return if (out == null) IDENTITY else out.copyOf(n + 1)
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
        val symmetries = node.symmetries
        val n: Int
        val moves: IntArray
        if (symmetries.size == 1) {
            n = popCount(legal)
            moves = IntArray(n)
            var bits = legal
            for (i in 0 until n) {
                moves[i] = lowestCell(bits)
                bits = bits and (bits - 1)
            }
            node.classes = null
        } else {
            // Classes of cells under the position's symmetries (they map legal moves to legal moves).
            val reps = ArrayList<Int>()
            val classes = ArrayList<Long>()
            var bits = legal
            while (bits != 0L) {
                val cell = lowestCell(bits)
                var orbit = 0L
                for (s in symmetries) orbit = orbit or (1L shl QubicGeometry.symmetries[s][cell])
                reps += cell
                classes += orbit
                bits = bits and orbit.inv()
            }
            n = reps.size
            moves = reps.toIntArray()
            node.classes = if (classes.any { popCount(it) > 1 }) classes.toLongArray() else null
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
        val classes = node.classes
        if (net == null) {
            val total = classes?.sumOf { popCount(it) } ?: n
            for (i in 0 until n) node.logits[i] = ln((classes?.let { popCount(it[i]) } ?: 1).toFloat() / total)
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
        if (classes == null) {
            var total = 0.0
            for (i in 0 until n) total += exp((netPolicy[node.moves[i]] - maxLogit).toDouble())
            val logTotal = ln(total).toFloat()
            for (i in 0 until n) node.logits[i] = netPolicy[node.moves[i]] - maxLogit - logTotal
        } else {
            // A class's prior is the sum over its cells.
            val mass = DoubleArray(n)
            var total = 0.0
            for (i in 0 until n) {
                forEachCell(classes[i]) { mass[i] += exp((netPolicy[it] - maxLogit).toDouble()) }
                total += mass[i]
            }
            for (i in 0 until n) node.logits[i] = ln(mass[i] / total).toFloat()
        }
        node.netValue = node.netWdl[0] - node.netWdl[2]
        node.netMovesLeft = workspace.movesLeft
    }

    private fun child(node: Node, index: Int): Node {
        node.children[index]?.let { return it }
        val cell = node.moves[index]
        val moverAfter = node.mine or (1L shl cell)
        val created = Node(mine = node.theirs, theirs = moverAfter)
        if (node.symmetries.size > 1) {
            created.symmetries = node.symmetries.filter { QubicGeometry.symmetries[it][cell] == cell }.toIntArray()
        }
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
            root.moves.isNotEmpty() -> member(root, root.logits.indices.maxBy { root.logits[it] })
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
            for (i in 0 until n) {
                val cells = cellsOf(root, i)
                val share = scores[i] / total / popCount(cells)
                forEachCell(cells) { policy[it] = share }
            }
        }
        val visits = IntArray(64)
        val moveClass = IntArray(64) { -1 }
        for (i in 0 until n) {
            val cells = cellsOf(root, i)
            val p = exp(root.logits[i]) / popCount(cells)
            val q = completedQ(root, i)
            forEachCell(cells) {
                prior[it] = p
                moveValues[it] = q
                visits[it] = root.visits[i]
                moveClass[it] = i
            }
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
        return Result(move, root.status, value, wdl, policy, prior, simulations, moveValues, visits, moveClass)
    }

    private inline fun forEachCell(cells: Long, action: (Int) -> Unit) {
        var bits = cells
        while (bits != 0L) {
            action(lowestCell(bits))
            bits = bits and (bits - 1)
        }
    }

    companion object {
        private val IDENTITY = intArrayOf(0)
        private val ALL_SYMMETRIES = IntArray(QubicGeometry.symmetryCount) { it }
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
