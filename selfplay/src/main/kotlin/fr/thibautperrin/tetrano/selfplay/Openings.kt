package fr.thibautperrin.tetrano.selfplay

import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.nn.Tactics
import fr.thibautperrin.tetrano.engine.nn.Vcf
import kotlin.random.Random

/** Where a self-play game starts: the side to move owns [mine], the opponent [theirs], after [ply] plies. */
internal class Start(val mine: Long, val theirs: Long, val ply: Int)

/**
 * The openings of self-play with `openings=schedule`: cycles of [CYCLE] games, each holding exactly
 * (per 10 000 games)
 * - 200 games from the empty board;
 * - 8 after one bad first move (a cell on only 4 lines, under a random symmetry);
 * - 35 after 2 moves, one per position up to symmetry, and 757 after 3 moves, one per position up
 *   to symmetry, each shown under a random symmetry;
 * - 2000 each after 4, 5 and 6 random moves, 1000 each after 7 and 8, 500 each after 9 and 10;
 *   from 6 moves on, the side to move is not facing a forced move (no line of its own to complete,
 *   none of the opponent's to block); from 7 on, the game is not over and the side to move has no
 *   forced win (VCF) either; 80 % of each of these groups are "disputed": the network (one forward
 *   pass) gives the first player 20-80 % of winning.
 *
 * Each group is spread evenly over the cycle (game k of a group of n sits near (k + u) / n of it,
 * u random per group), so that short and long openings alternate instead of coming in series; the
 * order changes with every cycle. Game i of the run (`offset=` + index in the call) always gets the
 * same slot, whatever the chunking.
 */
internal class OpeningSchedule(private val seed: Long) {

    enum class Kind { EMPTY, BAD_FIRST, TWO, THREE, RANDOM }

    /** One game of the cycle: [classIndex] for [Kind.TWO]/[Kind.THREE], [plies] and [disputed] for [Kind.RANDOM]. */
    class Slot(val kind: Kind, val plies: Int, val classIndex: Int = -1, val disputed: Boolean = false)

    private var cachedCycle = -1L
    private var cached: Array<Slot> = emptyArray()

    @Synchronized
    fun slot(gameIndex: Long): Slot {
        val cycle = gameIndex / CYCLE
        if (cycle != cachedCycle) {
            cached = buildCycle(cycle)
            cachedCycle = cycle
        }
        return cached[(gameIndex % CYCLE).toInt()]
    }

    private fun buildCycle(cycle: Long): Array<Slot> {
        val rng = Random(seed * 1_000_003 + cycle)
        val groups = ArrayList<List<Slot>>()
        groups += List(EMPTY_GAMES) { Slot(Kind.EMPTY, 0) }
        groups += List(BAD_FIRST_GAMES) { Slot(Kind.BAD_FIRST, 1) }
        groups += TWO_MOVES.indices.shuffled(rng).map { Slot(Kind.TWO, 2, it) }
        groups += THREE_MOVES.indices.shuffled(rng).map { Slot(Kind.THREE, 3, it) }
        for ((plies, count) in RANDOM_GAMES) {
            val disputed = count * DISPUTED_SHARE_PERCENT / 100
            groups += List(count) { it < disputed }.shuffled(rng).map { Slot(Kind.RANDOM, plies, disputed = it) }
        }
        val keyed = ArrayList<Pair<Double, Slot>>(CYCLE)
        for (group in groups) {
            val u = rng.nextDouble()
            group.forEachIndexed { k, slot -> keyed += ((k + u) / group.size + rng.nextDouble() * 1e-9) to slot }
        }
        check(keyed.size == CYCLE) { "the schedule holds ${keyed.size} games, not $CYCLE" }
        return keyed.sortedBy { it.first }.map { it.second }.toTypedArray()
    }

    companion object {
        const val CYCLE = 10_000
        const val EMPTY_GAMES = 200
        const val BAD_FIRST_GAMES = 8
        const val DISPUTED_SHARE_PERCENT = 80

        /** Plies of random moves -> games per cycle. */
        val RANDOM_GAMES = listOf(4 to 2000, 5 to 2000, 6 to 2000, 7 to 1000, 8 to 1000, 9 to 500, 10 to 500)

        /** Only from this many plies is a forced move at the start avoided. */
        const val NO_FORCED_MOVE_FROM = 6

        /** Only from this many plies are finished games and forced wins avoided. */
        const val NO_FORCED_WIN_FROM = 7

        /** The 48 cells on only 4 lines (the other 16 are on 7). */
        val BAD_FIRST_CELLS: IntArray = (0 until 64).filter { QubicGeometry.cellDegree(it) == 4 }.toIntArray()

        /** One representative (first player's cell, second player's cell) per position after 2 moves. */
        val TWO_MOVES: List<Pair<Long, Long>> = classes(firstStones = 1, secondStones = 1)

        /** One representative (first player's 2 cells, second player's cell) per position after 3 moves. */
        val THREE_MOVES: List<Pair<Long, Long>> = classes(firstStones = 2, secondStones = 1)

        /** Positions up to the board symmetries, as (first player's bits, second player's bits). */
        private fun classes(firstStones: Int, secondStones: Int): List<Pair<Long, Long>> {
            val seen = HashSet<Pair<Long, Long>>()
            fun subsets(k: Int, from: Int, excluded: Long, acc: Long, out: MutableList<Long>) {
                if (k == 0) {
                    out += acc
                    return
                }
                for (c in from until 64) {
                    if (excluded and (1L shl c) == 0L) subsets(k - 1, c + 1, excluded, acc or (1L shl c), out)
                }
            }
            val firsts = ArrayList<Long>()
            subsets(firstStones, 0, 0L, 0L, firsts)
            for (first in firsts) {
                val seconds = ArrayList<Long>()
                subsets(secondStones, 0, first, 0L, seconds)
                for (second in seconds) seen += canonical(first, second)
            }
            return seen.sortedWith(compareBy<Pair<Long, Long>>({ it.first }, { it.second }))
        }

        private fun canonical(first: Long, second: Long): Pair<Long, Long> {
            var best: Pair<Long, Long>? = null
            for (s in 0 until QubicGeometry.symmetryCount) {
                val f = QubicGeometry.transform(s, first)
                val t = QubicGeometry.transform(s, second)
                val b = best
                if (b == null || f < b.first || (f == b.first && t < b.second)) best = f to t
            }
            return best!!
        }
    }
}

/**
 * Builds the start of each scheduled game (one per thread: its VCF solver and network judge).
 * [disputed] tells whether a position (side to move: [Start.mine]) is disputed, given whether the
 * first player is to move.
 */
internal class OpeningMaker(
    private val schedule: OpeningSchedule,
    private val disputed: ((mine: Long, theirs: Long, firstToMove: Boolean) -> Boolean)?,
    private val rng: Random,
) {
    private val vcf = Vcf(16)

    fun start(gameIndex: Long): Start {
        val slot = schedule.slot(gameIndex)
        return when (slot.kind) {
            OpeningSchedule.Kind.EMPTY -> Start(0L, 0L, 0)
            OpeningSchedule.Kind.BAD_FIRST -> {
                val cell = OpeningSchedule.BAD_FIRST_CELLS.random(rng)
                Start(0L, 1L shl cell, 1)
            }
            OpeningSchedule.Kind.TWO, OpeningSchedule.Kind.THREE -> {
                val classes = if (slot.kind == OpeningSchedule.Kind.TWO) OpeningSchedule.TWO_MOVES else OpeningSchedule.THREE_MOVES
                val (first, second) = classes[slot.classIndex]
                val s = rng.nextInt(QubicGeometry.symmetryCount)
                val f = QubicGeometry.transform(s, first)
                val t = QubicGeometry.transform(s, second)
                // Even plies: the first player is to move.
                if (slot.plies % 2 == 0) Start(f, t, slot.plies) else Start(t, f, slot.plies)
            }
            OpeningSchedule.Kind.RANDOM -> random(slot.plies, slot.disputed)
        }
    }

    /** [plies] uniformly random moves, drawn again until the position meets the slot's conditions. */
    private fun random(plies: Int, disputed: Boolean): Start {
        while (true) {
            var mine = 0L
            var theirs = 0L
            var over = false
            for (ply in 0 until plies) {
                val cell = randomFreeCell(mine or theirs, rng)
                val after = mine or (1L shl cell)
                if (Tactics.wonWith(after, cell)) {
                    over = true
                    break
                }
                mine = theirs
                theirs = after
            }
            if (over) continue
            if (plies >= OpeningSchedule.NO_FORCED_MOVE_FROM &&
                (Tactics.threats(mine, theirs) != 0L || Tactics.threats(theirs, mine) != 0L)
            ) {
                continue
            }
            if (plies >= OpeningSchedule.NO_FORCED_WIN_FROM && vcf.solve(mine, theirs, VCF_BUDGET) != Vcf.NONE) continue
            if (disputed && !checkNotNull(this.disputed) { "disputed openings need a model" }(mine, theirs, plies % 2 == 0)) {
                continue
            }
            return Start(mine, theirs, plies)
        }
    }

    private companion object {
        const val VCF_BUDGET = 20_000
    }
}

/**
 * "Game under way" starts for self-play (`human_share=`): positions like those of games between
 * people, which the analysis must judge (plan § 5.3), not recorded themselves:
 * - half: a prefix of 6 to 24 plies of the legacy AI against itself (levels 10 to 85 per side);
 * - half: 4 to 20 plies drawn from the network's policy (one forward pass per move), then one
 *   weak move: drawn from the policy flattened (temperature 2) or, one time in three, uniformly.
 * Drawn again if the game is over or the side to move can complete a line at once; half of them
 * also if not disputed (see [disputed]), so that decided positions do not take over.
 */
internal class HumanOpenings(
    private val net: fr.thibautperrin.tetrano.engine.nn.QubicNet,
    private val rng: Random,
    /** Half of the starts must be disputed for it (given whether the first player is to move), when given. */
    private val disputed: ((mine: Long, theirs: Long, firstToMove: Boolean) -> Boolean)? = null,
) {
    private val ws = net.newWorkspace()
    private val logits = FloatArray(64)
    private val wdl = FloatArray(3)
    private val prior = FloatArray(64)
    private var wantDisputed = false

    fun start(): Start {
        while (true) {
            val legacy = rng.nextBoolean()
            val plies = if (legacy) 6 + rng.nextInt(19) else 4 + rng.nextInt(17)
            val levels = intArrayOf(10 + rng.nextInt(76), 10 + rng.nextInt(76))
            var mine = 0L
            var theirs = 0L
            var over = false
            for (ply in 0..plies) {
                val weak = !legacy && ply == plies
                if (legacy && ply == plies) break
                val cell = when {
                    legacy -> legacyMove(mine, theirs, ply, levels[ply % 2], rng)
                    weak -> {
                        policy(mine, theirs)
                        if (rng.nextInt(3) == 0) randomFreeCell(mine or theirs, rng) else sampleFromPrior(prior, 2.0, rng)
                    }
                    else -> {
                        policy(mine, theirs)
                        sampleFromPrior(prior, 1.0, rng)
                    }
                }
                val (m, t, o) = play(cell, mine, theirs)
                mine = m
                theirs = t
                if (o) {
                    over = true
                    break
                }
            }
            if (over || (mine or theirs) == -1L || Tactics.threats(mine, theirs) != 0L) continue
            val ply = QubicGeometry.popCount(mine or theirs)
            if (wantDisputed && disputed != null && !disputed.invoke(mine, theirs, ply % 2 == 0)) continue
            wantDisputed = !wantDisputed
            return Start(mine, theirs, ply)
        }
    }

    /** (side to move after, opponent, game over) once [cell] is played by the side to move [mine]. */
    private fun play(cell: Int, mine: Long, theirs: Long): Triple<Long, Long, Boolean> {
        val after = mine or (1L shl cell)
        return Triple(theirs, after, Tactics.wonWith(after, cell))
    }

    private fun policy(mine: Long, theirs: Long) {
        net.evaluate(mine, theirs, ws, logits, wdl)
        val free = (mine or theirs).inv()
        var max = Float.NEGATIVE_INFINITY
        for (c in 0 until 64) if ((free ushr c) and 1L != 0L) max = maxOf(max, logits[c])
        var total = 0f
        for (c in 0 until 64) {
            prior[c] = if ((free ushr c) and 1L != 0L) kotlin.math.exp(logits[c] - max) else 0f
            total += prior[c]
        }
        for (c in 0 until 64) prior[c] /= total
    }

    companion object {
        /** Whether game [index] of the run starts this way (a fixed share, the same on every resume). */
        fun chosen(index: Long, share: Double, seed: Long): Boolean {
            var h = (index + 1) * -0x61c8864680b583ebL xor seed * -0x3d4d51c2d82b14b1L
            h = h xor (h ushr 31)
            h *= -0x4b47d5b1a6d5e2b7L
            h = h xor (h ushr 29)
            return (h ushr 11).toDouble() / (1L shl 53).toDouble() < share
        }
    }
}

/** `openings`: the classes found and how a cycle of the schedule is spread. */
internal fun printOpeningSchedule(options: Map<String, String>) {
    val schedule = OpeningSchedule(options["schedule_seed"]?.toLong() ?: 1L)
    val cycle = options["cycle"]?.toLong() ?: 0L
    println("bad first cells: ${OpeningSchedule.BAD_FIRST_CELLS.size}, 2-move classes: ${OpeningSchedule.TWO_MOVES.size}, " +
        "3-move classes: ${OpeningSchedule.THREE_MOVES.size}")
    val slots = (0 until OpeningSchedule.CYCLE).map { schedule.slot(cycle * OpeningSchedule.CYCLE + it) }
    val counts = slots.groupBy { "${it.kind} ${it.plies}" + if (it.disputed) " disputed" else "" }.mapValues { it.value.size }
    for ((k, n) in counts.toSortedMap()) println("$k: $n")
    println("distinct 3-move classes: ${slots.filter { it.kind == OpeningSchedule.Kind.THREE }.map { it.classIndex }.toSet().size}")
    println("first 60 plies: " + slots.take(60).joinToString(" ") { it.plies.toString() })
    // Longest run of games with the same number of plies.
    var longest = 0
    var run = 0
    for (i in slots.indices) {
        run = if (i > 0 && slots[i].plies == slots[i - 1].plies) run + 1 else 1
        longest = maxOf(longest, run)
    }
    println("longest run with the same number of plies: $longest")
    val emptyGaps = slots.indices.filter { slots[it].kind == OpeningSchedule.Kind.EMPTY }.zipWithNext { a, b -> b - a }
    println("gaps between empty-board games: min ${emptyGaps.min()} max ${emptyGaps.max()}")
}
