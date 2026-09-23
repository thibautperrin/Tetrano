package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.assets.ChallengeLevels
import fr.thibautperrin.tetrano.data.db.ChallengeProgressDao
import fr.thibautperrin.tetrano.data.db.ChallengeProgressEntity
import fr.thibautperrin.tetrano.di.DefaultDispatcher
import fr.thibautperrin.tetrano.domain.model.ChallengeHintResult
import fr.thibautperrin.tetrano.domain.model.ChallengeMoveResult
import fr.thibautperrin.tetrano.domain.model.ChallengeState
import fr.thibautperrin.tetrano.engine.BoardState
import fr.thibautperrin.tetrano.engine.BoardTables
import fr.thibautperrin.tetrano.engine.LineOps
import fr.thibautperrin.tetrano.engine.ai.Ai
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Challenge mode: a sequence of 245 built-in positions, each played with a
 * strict ring budget. Ports `Challenge`/`MemoryVarsChallenge`/
 * `ChallengeActivity`'s orchestration (see docs/GLOSSARY.md,
 * docs/PLAN.md P6) — the two legacy classes are merged here since their
 * split (a raw-file cache versus a single in-progress attempt) doesn't
 * carry its weight once both are backed by the same Room row.
 *
 * A single instance tracks the *current* attempt; [startCurrentLevel]
 * (re)loads it from persisted progress, so it's safe to keep this
 * `@Singleton` across screen visits (unlike [GameSessionService], which is
 * per-screen). Once an attempt ends, its final position stays readable
 * through [snapshot]/[blinkingRings] (for the end-of-level animation) while
 * the *next* attempt is already persisted (`nextLevelChallenge`); further
 * moves, hints or give-ups are refused until [startCurrentLevel] runs again.
 *
 * The computer's searches run on [aiDispatcher] (the legacy ran them on the
 * UI thread). Callers must not call into this class concurrently.
 */
@Singleton
class ChallengeService @Inject constructor(
    private val levels: ChallengeLevels,
    private val progressDao: ChallengeProgressDao,
    private val starService: StarService,
    @param:DefaultDispatcher private val aiDispatcher: CoroutineDispatcher,
) {
    private lateinit var board: BoardState
    private lateinit var ai: Ai
    private var level: Int = 1
    private var symmetry: Int = 0
    private var forkOrder: Int = 0
    private var isOver: Boolean = false

    /** Rings played "normally" (not via a star hint) this attempt, in order. Ports `Challenge.ringsPlayed`. */
    private var ringsPlayedNormally = mutableListOf<Int>()

    /** Rings played via star hints this attempt (pairs: user, computer), in order. Ports `playedByStarsChallenge`. */
    private var ringsPlayedByStars = mutableListOf<Int>()

    val isAllLevelsPassed: Boolean get() = level > levels.levelCount

    /** (Re)loads the current attempt from persisted progress. False iff every level has been passed. */
    suspend fun startCurrentLevel(): Boolean {
        val progress = progressDao.get()
        level = progress?.level ?: 1
        if (isAllLevelsPassed) return false

        symmetry = progress?.symmetry ?: Random.nextInt(BoardState.SYMMETRY_COUNT)
        forkOrder = levels.forkOrder(level)
        board = BoardState(levels.signature(level), symmetry.toByte(), levels.isInvertPlayer(level))
        ai = Ai(board)
        isOver = false

        ringsPlayedByStars = mutableListOf()
        progress?.playedByStars?.let { replay(it, onto = ringsPlayedByStars) }
        ringsPlayedNormally = mutableListOf()
        progress?.normallyPlayed?.let { replay(it, onto = ringsPlayedNormally) }
        return true
    }

    fun snapshot(): ChallengeState = ChallengeState(
        level = level,
        rings = board.rings.copyOf(),
        usedRings = usedRings(),
        authorizedRings = authorizedRings(),
        canAbandon = !isOver && numberUserRings() > 0 && usedRings() != authorizedRings(),
        isOver = isOver,
    )

    /** The winning line (the user's, else the computer's), or nothing if no line is complete. Ports `getBlinkingRings`. */
    fun blinkingRings(): ByteArray = when {
        board.lineSets[4].length() > 0 -> BoardTables.copyLineRings(board.lineSets[4].next(-1).toInt())
        board.lineSets[8].length() > 0 -> BoardTables.copyLineRings(board.lineSets[8].next(-1).toInt())
        else -> ByteArray(0)
    }

    /**
     * Plays [ring] for the user, then — unless that already finished the
     * level — the computer's reply. Ports `userTriedPlaying` +
     * `postRingPlayedByUser`.
     */
    suspend fun playUserRing(ring: Int): ChallengeMoveResult {
        if (isOver || ring !in 0 until BoardTables.RING_COUNT || board.rings[ring] != EMPTY || isFinished()) {
            return ChallengeMoveResult.InvalidMove
        }
        board.play(ring, USER)
        ringsPlayedNormally.add(ring)
        if (isFinished()) {
            return if (isWonByUser()) {
                ChallengeMoveResult.LevelPassed(level, allModesTriedAward = finishAttempt(won = true))
            } else {
                ChallengeMoveResult.LevelFailed(level, allModesTriedAward = finishAttempt(won = false))
            }
        }

        val computerRing = withContext(aiDispatcher) { ai.playChallenge(forkOrder, usedRings()) }
        board.play(computerRing, COMPUTER)
        ringsPlayedNormally.add(computerRing)
        if (isFinished()) {
            // Legacy: any end right after the computer's reply is SITUATION_COMPUTER_WINS.
            return ChallengeMoveResult.LevelFailed(level, computerRing, allModesTriedAward = finishAttempt(won = false))
        }

        persistProgress()
        return ChallengeMoveResult.ComputerReplied(computerRing)
    }

    /**
     * Spends a star for a hint. Undoes this attempt's normal moves first
     * (keeping star-played ones) if any were played, then either plays the
     * user's immediate winning move (if one is already available) or asks
     * the AI for one more forced pair towards a fork. Ports `useAStar`.
     */
    suspend fun useStarHint(): ChallengeHintResult {
        if (isOver) return ChallengeHintResult.LevelOver
        if (!starService.useStar()) return ChallengeHintResult.NoStarsAvailable

        var boardBeforeHint: ByteArray? = null
        if (numberUserRings() > 0) {
            var player = 2 - (ringsPlayedNormally.size % 2)
            for (i in ringsPlayedNormally.indices.reversed()) {
                board.undo(ringsPlayedNormally[i], player)
                player = 3 - player
            }
            ringsPlayedNormally = mutableListOf()
            boardBeforeHint = board.rings.copyOf()
        }

        return if (board.lineSets[3].length() > 0) {
            val line = board.lineSets[3].next(-1).toInt()
            val ringForUser = LineOps.lastRemainingRing(board, line).toInt()
            board.play(ringForUser, USER)
            ChallengeHintResult.LevelWon(ringForUser, level, allModesTriedAward = finishAttempt(won = true))
        } else {
            val move = withContext(aiDispatcher) { ai.playStar(forkOrder, ringsPlayedByStars.size / 2) }
            ringsPlayedByStars.add(move.ringForUser)
            ringsPlayedByStars.add(move.ringForComputer)
            persistProgress()
            ChallengeHintResult.Hint(move.ringForUser, move.ringForComputer, boardBeforeHint)
        }
    }

    /** Gives up on the current level (counts as a loss); null if the attempt is already over. Ports `buttonAbandon`'s click listener. */
    suspend fun abandon(): ChallengeMoveResult.LevelFailed? {
        if (isOver) return null
        return ChallengeMoveResult.LevelFailed(level, allModesTriedAward = finishAttempt(won = false))
    }

    /**
     * Ends the attempt and persists the next one — ports
     * `MemoryVarsChallenge.nextLevelChallenge(passed)`: a pass moves to the
     * next level under a new symmetry and forgets the star rings; a failure
     * keeps the level, symmetry and star rings. The in-memory position is
     * left as is. Returns whether this also earned the "every main mode
     * tried" star.
     */
    private suspend fun finishAttempt(won: Boolean): Boolean {
        isOver = true
        val award = starService.recordChallengeAttempted() != null
        progressDao.upsert(
            ChallengeProgressEntity(
                level = if (won) level + 1 else level,
                symmetry = if (won) Random.nextInt(BoardState.SYMMETRY_COUNT) else symmetry,
                normallyPlayed = null,
                playedByStars = if (won) null else ringsPlayedByStars.toByteArrayOrNull(),
                nextTimeAdvert = false,
            ),
        )
        return award
    }

    private suspend fun persistProgress() {
        progressDao.upsert(
            ChallengeProgressEntity(
                level = level,
                symmetry = symmetry,
                normallyPlayed = ringsPlayedNormally.toByteArrayOrNull(),
                playedByStars = ringsPlayedByStars.toByteArrayOrNull(),
                nextTimeAdvert = false,
            ),
        )
    }

    private fun replay(rings: ByteArray, onto: MutableList<Int>) {
        var player = USER
        for (ring in rings) {
            board.play(ring.toInt(), player)
            onto.add(ring.toInt())
            player = 3 - player
        }
    }

    private fun numberUserRings(): Int = ringsPlayedNormally.size / 2 + ringsPlayedNormally.size % 2
    private fun usedRings(): Int = numberUserRings() + ringsPlayedByStars.size / 2
    private fun authorizedRings(): Int = forkOrder + 2

    private fun isFinished(): Boolean =
        board.playedCount == BoardTables.RING_COUNT ||
            board.lineSets[4].length() > 0 ||
            board.lineSets[8].length() > 0 ||
            usedRings() == authorizedRings()

    private fun isWonByUser(): Boolean = board.lineSets[4].length() > 0

    private fun List<Int>.toByteArrayOrNull(): ByteArray? =
        if (isEmpty()) null else ByteArray(size) { this[it].toByte() }

    private companion object {
        const val EMPTY: Byte = 0
        const val USER = 1
        const val COMPUTER = 2
    }
}
