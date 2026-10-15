package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.lowestCell
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry.popCount
import fr.thibautperrin.tetrano.engine.nn.QubicNet
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.puzzle.Puzzle
import fr.thibautperrin.tetrano.engine.puzzle.WinSolver
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.random.Random

/**
 * `puzzles data=<dir>[,<dir>...] model=<net> out=<file> [count=3000 max_moves=5 max_lines=4
 * max_first=2 games=N threads=N seed=N report=<file.md>]`
 *
 * Builds the puzzle set of the app's Puzzles mode from recorded self-play games:
 * 1. every position of a game where the eventual winner, to move, wins by force within
 *    `max_moves` of its moves (exact [WinSolver], walking back from the end of the game);
 * 2. its full solution tree ([Puzzle.Step]: every correct move, and the most stubborn reply,
 *    ties broken by the network's prior for the defender) — puzzles with more than `max_lines`
 *    distinct solutions, or more than `max_first` correct first moves, are dropped, so that a
 *    puzzle has one solution or very few;
 * 3. a difficulty rating: the exact probability that "solvers" sampling moves from the
 *    network's prior at several temperatures (and uniformly) play a correct move at every step,
 *    turned into Elo-like ratings by a Rasch (logistic item-response) fit of solvers and
 *    puzzles, scaled so that the median puzzle is rated [MEDIAN_RATING];
 * 4. at most one puzzle per game, no two equivalent under the 192 board symmetries, and about
 *    `count` puzzles spread evenly over the ratings.
 */
internal fun generatePuzzles(options: Map<String, String>) {
    val net = File(options["model"] ?: error("model= required")).inputStream().use { QubicNet.load(it) }
    val dirs = (options["data"] ?: error("data= required")).split(',').map(::File)
    val out = File(options["out"] ?: error("out= required"))
    val count = options["count"]?.toInt() ?: 3000
    val maxMoves = options["max_moves"]?.toInt() ?: 5
    val maxLines = options["max_lines"]?.toInt() ?: 4
    val maxFirst = options["max_first"]?.toInt() ?: 2
    val maxGames = options["games"]?.toInt() ?: Int.MAX_VALUE
    val threads = options["threads"]?.toInt() ?: (Runtime.getRuntime().availableProcessors() - 2).coerceAtLeast(1)
    val rng = Random(options["seed"]?.toLong() ?: 1L)
    val started = System.nanoTime()

    val games = readGames(dirs).shuffled(rng).take(maxGames)
    println("${games.size} games")

    // ---- 1-2: candidates and their solution trees ----
    val candidates = ConcurrentLinkedQueue<Candidate>()
    val nextGame = AtomicInteger(0)
    val stats = Stats()
    val pool = Executors.newFixedThreadPool(threads)
    repeat(threads) {
        pool.execute {
            val builder = TreeBuilder(net, maxLines, maxFirst)
            while (true) {
                val g = nextGame.getAndIncrement()
                if (g >= games.size) break
                builder.candidates(g, games[g], maxMoves, stats).forEach(candidates::add)
                if (g % 2000 == 0) println("  game $g/${games.size}: ${candidates.size} candidates (${stats})")
            }
        }
    }
    pool.shutdown()
    pool.awaitTermination(7, TimeUnit.DAYS)
    println("${candidates.size} candidates ($stats) in ${(System.nanoTime() - started) / 1_000_000_000} s")

    // At most one per game, then one per symmetry class.
    val byGame = candidates.groupBy { it.game }.values.map { it.random(rng) }
    val unique = byGame.distinctBy { canonical(it.mine, it.theirs) }

    // ---- 3: ratings ----
    val ratings = raschRatings(unique.map { it.solveProbabilities })
    val rated = unique.mapIndexed { i, c -> c to ratings.puzzles[i] }

    // ---- 4: selection, spread over the ratings ----
    val selected = spread(rated, count, rng).sortedBy { it.second }
    val puzzles = selected.mapIndexed { index, (c, rating) ->
        Puzzle(index + 1, rating, c.mine, c.theirs, c.lastMove, c.movesToWin, c.solution)
    }
    out.outputStream().use { Puzzle.write(puzzles, it) }
    // Round trip check.
    check(out.inputStream().use { Puzzle.readAll(it) }.size == puzzles.size)
    println("wrote ${puzzles.size} puzzles to $out (${out.length()} bytes)")
    options["report"]?.let { File(it).writeText(report(puzzles, selected.map { it.first }, ratings, stats, games.size)) }
}

private const val MEDIAN_RATING = 1500
private const val MIN_RATING = 400
private const val MAX_RATING = 2800

/** Temperatures of the prior-sampling solvers; [Double.POSITIVE_INFINITY] = uniformly random moves. */
private val TEMPERATURES = doubleArrayOf(Double.POSITIVE_INFINITY, 4.0, 2.0, 1.0, 0.5, 0.25)

private class Candidate(
    val game: Int,
    val mine: Long,
    val theirs: Long,
    val lastMove: Int,
    val movesToWin: Int,
    val solution: Puzzle.Step,
    /** Per solver of [TEMPERATURES]: probability of solving the puzzle. */
    val solveProbabilities: DoubleArray,
)

private class Stats {
    val positions = AtomicInteger()
    val won = AtomicInteger()
    val tooManyLines = AtomicInteger()
    val outOfBudget = AtomicInteger()
    override fun toString() =
        "${positions.get()} positions tried, ${won.get()} forced wins, ${tooManyLines.get()} with too many solutions, " +
            "${outOfBudget.get()} too long to solve"
}

/** A recorded game: positions in order, from the point of view of the player to move. */
private class RecordedGame(val mine: LongArray, val theirs: LongArray, val moverWins: BooleanArray)

/** The games of every `gen_*.bin` file of [dirs] (consecutive records continuing each other). */
private fun readGames(dirs: List<File>): List<RecordedGame> {
    val games = ArrayList<RecordedGame>()
    for (dir in dirs) {
        val files = dir.listFiles { f -> f.name.matches(Regex("gen_\\d+\\.bin")) }?.sorted().orEmpty()
        for (file in files) {
            val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val n = buffer.capacity() / RECORD_SIZE
            var start = 0
            fun flush(end: Int) {
                if (end > start) {
                    games += RecordedGame(
                        LongArray(end - start) { buffer.getLong((start + it) * RECORD_SIZE) },
                        LongArray(end - start) { buffer.getLong((start + it) * RECORD_SIZE + 8) },
                        BooleanArray(end - start) { buffer.get((start + it) * RECORD_SIZE + 16).toInt() == 0 },
                    )
                }
                start = end
            }
            for (i in 1 until n) {
                val prevMine = buffer.getLong((i - 1) * RECORD_SIZE)
                val prevTheirs = buffer.getLong((i - 1) * RECORD_SIZE + 8)
                val mine = buffer.getLong(i * RECORD_SIZE)
                val theirs = buffer.getLong(i * RECORD_SIZE + 8)
                val added = theirs and prevMine.inv()
                val continues = mine == prevTheirs && theirs and prevMine == prevMine && popCount(added) == 1
                if (!continues) flush(i)
            }
            flush(n)
        }
    }
    return games
}

/** The smallest (mine, theirs) image of a position under the board symmetries. */
private fun canonical(mine: Long, theirs: Long): Pair<Long, Long> {
    var best: Pair<Long, Long>? = null
    for (s in 0 until QubicGeometry.symmetryCount) {
        val m = QubicGeometry.transform(s, mine)
        val t = QubicGeometry.transform(s, theirs)
        val b = best
        if (b == null || m < b.first || (m == b.first && t < b.second)) best = m to t
    }
    return best!!
}

/** Per thread: the exact solver, the network for priors, and the tree construction. */
private class TreeBuilder(net: QubicNet, val maxLines: Int, val maxFirst: Int) {
    private val solver = WinSolver(maxNodes = 1_000_000L)
    private val net = net
    private val workspace = net.newWorkspace()
    private val logits = FloatArray(64)
    private val wdl = FloatArray(3)

    private class TooManyLines : RuntimeException()

    /** Positions of [game] where the winner, to move, wins within [maxMoves], with their puzzles. */
    fun candidates(gameIndex: Int, game: RecordedGame, maxMoves: Int, stats: Stats): List<Candidate> {
        val result = ArrayList<Candidate>()
        solver.clear()
        // From the end back: the forced win gets longer until the loser's mistake. One more
        // move than the position after is all that is looked for (a longer win would mean the
        // loser blundered in between): proving that there is none is the expensive part.
        var limit = maxMoves
        for (i in game.mine.indices.reversed()) {
            if (!game.moverWins[i]) continue
            val p = game.mine[i]
            val o = game.theirs[i]
            stats.positions.incrementAndGet()
            val n = try {
                solver.minimalWin(p, o, limit)
            } catch (e: WinSolver.OutOfBudget) {
                stats.outOfBudget.incrementAndGet()
                break
            }
            if (n == 0) break
            limit = minOf(maxMoves, n + 1)
            stats.won.incrementAndGet()
            val tree = try {
                lines = 0
                step(p, o, n, first = true)
            } catch (e: TooManyLines) {
                stats.tooManyLines.incrementAndGet()
                continue
            } catch (e: WinSolver.OutOfBudget) {
                stats.outOfBudget.incrementAndGet()
                continue
            }
            result += Candidate(gameIndex, p, o, lastMoveOf(game, i), n, tree, TEMPERATURES.map { solveProbability(tree, p, o, it) }.toDoubleArray())
            if (memoryHeavy()) solver.clear()
        }
        return result
    }

    private fun memoryHeavy() = solver.nodes > 5_000_000

    private var lines = 0

    /** The solver ([p]) to move, winning within [n]: every correct move and what follows. */
    private fun step(p: Long, o: Long, n: Int, first: Boolean = false): Puzzle.Step {
        val moves = solver.winningMoves(p, o, n)
        check(moves != 0L) { "not a forced win" }
        if (first && popCount(moves) > maxFirst) throw TooManyLines()
        val branches = ArrayList<Puzzle.Branch>()
        var rest = moves
        while (rest != 0L) {
            val m = lowestCell(rest)
            rest = rest and (rest - 1)
            val p2 = p or (1L shl m)
            if (Tactics.wonWith(p2, m)) {
                if (++lines > maxLines) throw TooManyLines()
                branches += Puzzle.Branch(m, -1, null)
                continue
            }
            val reply = stubbornReply(p2, o, m, n - 1)
            branches += Puzzle.Branch(m, reply, step(p2, o or (1L shl reply), n - 1))
        }
        return Puzzle.Step(branches)
    }

    /**
     * The defender's reply after [p] played [m] that delays the win the most (the solver then
     * needs the most moves), ties broken by the network's prior for the defender.
     */
    private fun stubbornReply(p: Long, o: Long, m: Int, left: Int): Int {
        val replies = solver.replies(p, o, m)
        if (popCount(replies) == 1) return lowestCell(replies)
        net.evaluate(o, p, workspace, logits, wdl)
        var best = -1
        var bestLength = -1
        var bestLogit = Float.NEGATIVE_INFINITY
        var rest = replies
        while (rest != 0L) {
            val r = lowestCell(rest)
            rest = rest and (rest - 1)
            val length = solver.minimalWin(p, o or (1L shl r), left)
            check(length > 0) { "reply $r escapes" }
            if (length > bestLength || (length == bestLength && logits[r] > bestLogit)) {
                best = r
                bestLength = length
                bestLogit = logits[r]
            }
        }
        return best
    }

    /** Probability that a solver sampling from the prior at [temperature] plays a correct move at every step. */
    private fun solveProbability(step: Puzzle.Step, p: Long, o: Long, temperature: Double): Double {
        val legal = (p or o).inv()
        val probabilities = DoubleArray(64)
        if (temperature.isInfinite()) {
            val share = 1.0 / popCount(legal)
            for (c in 0 until 64) if (legal and (1L shl c) != 0L) probabilities[c] = share
        } else {
            net.evaluate(p, o, workspace, logits, wdl)
            var maxLogit = Double.NEGATIVE_INFINITY
            for (c in 0 until 64) if (legal and (1L shl c) != 0L) maxLogit = max(maxLogit, logits[c].toDouble())
            var total = 0.0
            for (c in 0 until 64) {
                if (legal and (1L shl c) != 0L) {
                    probabilities[c] = exp((logits[c] - maxLogit) / temperature)
                    total += probabilities[c]
                }
            }
            for (c in 0 until 64) probabilities[c] /= total
        }
        var success = 0.0
        for (b in step.branches) {
            val next = b.next
            success += probabilities[b.move] *
                (if (next == null) 1.0 else solveProbability(next, p or (1L shl b.move), o or (1L shl b.reply), temperature))
        }
        return success
    }
}

/** The opponent's ring that led to position [i] of [game] (-1 at the start of the record). */
private fun lastMoveOf(game: RecordedGame, i: Int): Int {
    if (i == 0) return -1
    // Position i-1 is the opponent's turn: its "mine" are the opponent's rings, to which
    // position i's "theirs" adds the move played.
    val move = game.theirs[i] and game.mine[i - 1].inv()
    return if (popCount(move) == 1) lowestCell(move) else -1
}

private class Ratings(val solvers: DoubleArray, val puzzles: IntArray)

/**
 * Rasch fit: P(solver s solves puzzle j) = 1 / (1 + 10^((b_j - θ_s) / 400)), maximizing the
 * likelihood of the exact solve probabilities (soft targets) by alternating Newton steps.
 * Scaled so that the median puzzle is [MEDIAN_RATING]; puzzles are clamped to
 * [MIN_RATING]..[MAX_RATING].
 */
private fun raschRatings(q: List<DoubleArray>): Ratings {
    val k = ln(10.0) / 400.0
    val solvers = TEMPERATURES.size
    val theta = DoubleArray(solvers) { 1000.0 + 200.0 * it }
    val b = DoubleArray(q.size) { j -> 1500.0 - 400.0 * (q[j].average() - 0.5) }
    fun sigma(x: Double) = 1.0 / (1.0 + exp(-x))
    repeat(300) {
        for (j in q.indices) {
            var g = 0.0
            var h = 0.0
            for (s in 0 until solvers) {
                val p = sigma(k * (theta[s] - b[j]))
                g += q[j][s] - p
                h += p * (1 - p)
            }
            b[j] = (b[j] - g / (k * max(h, 1e-6))).coerceIn(b[j] - 300, b[j] + 300).coerceIn(-3000.0, 6000.0)
        }
        for (s in 0 until solvers) {
            var g = 0.0
            var h = 0.0
            for (j in q.indices) {
                val p = sigma(k * (theta[s] - b[j]))
                g += p - q[j][s]
                h += p * (1 - p)
            }
            theta[s] = (theta[s] - g / (k * max(h, 1e-6))).coerceIn(theta[s] - 300, theta[s] + 300)
        }
        val shift = MEDIAN_RATING - b.sorted()[b.size / 2]
        for (j in b.indices) b[j] += shift
        for (s in theta.indices) theta[s] += shift
    }
    return Ratings(theta, IntArray(b.size) { Math.round(b[it]).toInt().coerceIn(MIN_RATING, MAX_RATING) })
}

/** About [count] puzzles, as evenly spread over 100-point rating bands as the pool allows. */
private fun <T> spread(rated: List<Pair<T, Int>>, count: Int, rng: Random): List<Pair<T, Int>> {
    val bands = rated.groupBy { it.second / 100 }.mapValues { it.value.shuffled(rng).toMutableList() }.toSortedMap()
    val chosen = ArrayList<Pair<T, Int>>()
    while (chosen.size < count && bands.values.any { it.isNotEmpty() }) {
        for (band in bands.values) {
            if (band.isNotEmpty() && chosen.size < count) chosen += band.removeAt(band.size - 1)
        }
    }
    return chosen
}

private fun report(puzzles: List<Puzzle>, candidates: List<Candidate>, ratings: Ratings, stats: Stats, games: Int): String {
    val sb = StringBuilder()
    sb.appendLine("# Puzzles")
    sb.appendLine()
    sb.appendLine("${puzzles.size} puzzles tirés de $games parties ($stats).")
    sb.appendLine()
    sb.appendLine("Solveurs (tirage selon la politique du réseau à la température T) et leur Elo ajusté :")
    sb.appendLine()
    sb.appendLine("| T | Elo |")
    sb.appendLine("|---|---|")
    TEMPERATURES.forEachIndexed { s, t ->
        sb.appendLine("| ${if (t.isInfinite()) "uniforme" else t} | ${ratings.solvers[s].toInt()} |")
    }
    sb.appendLine()
    sb.appendLine("| Coups pour gagner | Puzzles | Elo moyen | min | max | Solutions (moy.) | Puzzles à solution unique |")
    sb.appendLine("|---|---|---|---|---|---|---|")
    for ((n, group) in puzzles.groupBy { it.movesToWin }.toSortedMap()) {
        val r = group.map { it.rating }
        sb.appendLine(
            "| $n | ${group.size} | ${r.average().toInt()} | ${r.min()} | ${r.max()} | " +
                "${"%.2f".format(Locale.ROOT, group.map { it.solution.lineCount }.average())} | " +
                "${group.count { it.solution.lineCount == 1 }} |",
        )
    }
    sb.appendLine()
    sb.appendLine("| Elo | Puzzles |")
    sb.appendLine("|---|---|")
    for ((band, group) in puzzles.groupBy { it.rating / 200 * 200 }.toSortedMap()) sb.appendLine("| $band–${band + 199} | ${group.size} |")
    check(candidates.size == puzzles.size)
    return sb.toString()
}
