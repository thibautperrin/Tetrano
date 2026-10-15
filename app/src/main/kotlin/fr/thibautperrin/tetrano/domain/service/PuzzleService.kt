package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.PuzzleCatalog
import fr.thibautperrin.tetrano.data.prefs.PuzzleProgress
import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.engine.nn.QubicGeometry
import fr.thibautperrin.tetrano.engine.puzzle.Puzzle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The Puzzles mode: an Elo-like rating for the player, and puzzles (each
 * with its own rating) chosen so that the player solves about
 * [TARGET_SUCCESS] of them — a puzzle rated [TARGET_OFFSET] below the
 * player, among the closest ones not attempted yet.
 *
 * A puzzle counts once, on its first attempt: solved or not, the rating
 * changes by the Elo rule ([K_NEW] while the player has few attempts, then
 * [K]); retries are free. The puzzle on display (not attempted yet) is
 * persisted, so leaving the screen does not skip it. Puzzles are shown
 * under a random board symmetry.
 */
@Singleton
class PuzzleService @Inject constructor(
    private val catalog: PuzzleCatalog,
    private val preferences: TetranoPreferencesDataSource,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /** A puzzle to play, as displayed (symmetry applied), and the player's rating. */
    class Round(val puzzle: Puzzle, val progress: PuzzleProgress)

    /** The player's rating before and after a puzzle. */
    class RatingChange(val before: Int, val after: Int, val progress: PuzzleProgress)

    /** The puzzle on display, unless it was already attempted: then a new one. */
    suspend fun current(): Round = withContext(dispatcher) {
        val progress = preferences.puzzleProgress.first()
        val puzzle = catalog.puzzles.firstOrNull { it.id == progress.currentId && it.id !in progress.seen }
        if (puzzle != null) Round(puzzle.transformed(progress.currentSymmetry), progress) else pickNext(progress)
    }

    /** Leaves the puzzle on display (attempted or not) for a new one. */
    suspend fun next(): Round = withContext(dispatcher) { pickNext(preferences.puzzleProgress.first()) }

    /** Rates the first attempt at [puzzle]; later calls for the same puzzle change nothing. */
    suspend fun record(puzzle: Puzzle, solved: Boolean): RatingChange {
        val progress = preferences.puzzleProgress.first()
        val before = progress.rating
        if (puzzle.id in progress.seen) return RatingChange(before.roundToInt(), before.roundToInt(), progress)
        val expected = 1.0 / (1.0 + 10.0.pow((puzzle.rating - before) / 400.0))
        val k = if (progress.attempts < PROVISIONAL_ATTEMPTS) K_NEW else K
        val after = (before + k * ((if (solved) 1.0 else 0.0) - expected)).toFloat().coerceAtLeast(MIN_RATING)
        val updated = progress.copy(
            rating = after,
            attempts = progress.attempts + 1,
            solved = progress.solved + if (solved) 1 else 0,
            seen = progress.seen + puzzle.id,
        )
        preferences.setPuzzleProgress(updated)
        return RatingChange(before.roundToInt(), after.roundToInt(), updated)
    }

    private suspend fun pickNext(progress: PuzzleProgress): Round {
        val all = catalog.puzzles
        val target = progress.rating - TARGET_OFFSET
        val fresh = all.filter { it.id !in progress.seen && it.id != progress.currentId }
        val pool = fresh.ifEmpty { all.filter { it.id != progress.currentId }.ifEmpty { all } }
        val puzzle = pool.sortedBy { abs(it.rating - target) }.take(CLOSEST).random(random)
        val symmetry = random.nextInt(QubicGeometry.symmetryCount)
        val updated = progress.copy(currentId = puzzle.id, currentSymmetry = symmetry)
        preferences.setPuzzleProgress(updated)
        return Round(puzzle.transformed(symmetry), updated)
    }

    private val random = Random.Default

    companion object {
        const val TARGET_SUCCESS = 0.75

        /** A player solves a puzzle rated this much lower [TARGET_SUCCESS] of the time (≈ 191). */
        val TARGET_OFFSET: Float = (400 * log10(TARGET_SUCCESS / (1 - TARGET_SUCCESS))).toFloat()

        /** The next puzzle is drawn among this many puzzles rated closest to the target. */
        const val CLOSEST = 12
        const val K_NEW = 40.0
        const val K = 20.0
        const val PROVISIONAL_ATTEMPTS = 20
        const val MIN_RATING = 100f
    }
}
