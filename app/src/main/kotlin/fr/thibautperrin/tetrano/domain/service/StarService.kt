package fr.thibautperrin.tetrano.domain.service

import fr.thibautperrin.tetrano.data.prefs.TetranoPreferencesDataSource
import fr.thibautperrin.tetrano.domain.model.GameMode
import fr.thibautperrin.tetrano.domain.model.StarAward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Awards Challenge-mode hint stars for reaching game-count milestones, and
 * tracks how many are available to spend. Ports `GameLog` (see
 * docs/GLOSSARY.md, docs/PLAN.md P6).
 *
 * Callers (a ViewModel, in P8) call [recordGameFinished]/
 * [recordChallengeAttempted] once per finished game/attempt — typically
 * where the legacy `GestionJeu.sortir()`/`logGame()` used to — and show
 * whatever [StarAward]s come back.
 */
@Singleton
class StarService @Inject constructor(
    private val preferences: TetranoPreferencesDataSource,
) {
    val numberStars: Flow<Int> = preferences.numberStars

    /**
     * Records that a [mode] game just finished (win, loss or draw all
     * count the same, matching the legacy behavior); [won] only matters
     * for [GameMode.VS_COMPUTER]. Returns every star this triggered, if
     * any — usually none.
     */
    suspend fun recordGameFinished(mode: GameMode, won: Boolean = false): List<StarAward> {
        val awards = mutableListOf<StarAward>()
        when (mode) {
            GameMode.TWO_PLAYERS -> {
                val count = increment(preferences.gamesPlayedTwoPlayers, preferences::setGamesPlayedTwoPlayers)
                awardIfMilestone(count) { StarAward.GamesPlayed(mode, count) }?.let { awards += it }
                // Only the very first time a mode is ever played can newly
                // complete "every mode tried" — see checkAllModesTried's doc.
                if (count == 1) checkAllModesTried()?.let { awards += it }
            }
            GameMode.VS_COMPUTER -> {
                val count = increment(preferences.gamesPlayedAgainstComputer, preferences::setGamesPlayedAgainstComputer)
                awardIfMilestone(count) { StarAward.GamesPlayed(mode, count) }?.let { awards += it }
                if (count == 1) checkAllModesTried()?.let { awards += it }
                if (won) {
                    val wonCount = increment(preferences.gamesWonAgainstComputer, preferences::setGamesWonAgainstComputer)
                    awardIfMilestone(wonCount) { StarAward.GamesWonAgainstComputer(wonCount) }?.let { awards += it }
                }
            }
            GameMode.BLUETOOTH, GameMode.ONLINE -> Unit // Can't happen — see docs/PLAN.md §10.
        }
        return awards
    }

    /**
     * Records a Challenge-mode attempt (win or loss both count). Ports the
     * "tried Challenge" contribution to `GameLog.testAllModeTested` — only
     * ever meaningful the first time, exactly like [recordGameFinished]'s
     * `count == 1` guard for the other modes.
     */
    suspend fun recordChallengeAttempted(): StarAward.AllModesTried? {
        if (preferences.hasTriedChallenge.first()) return null
        preferences.setHasTriedChallenge(true)
        return checkAllModesTried()
    }

    /** Spends one star; false if none are available. */
    suspend fun useStar(): Boolean {
        val current = numberStars.first()
        if (current <= 0) return false
        preferences.setNumberStars(current - 1)
        return true
    }

    private suspend fun increment(flow: Flow<Int>, set: suspend (Int) -> Unit): Int {
        val next = flow.first() + 1
        set(next)
        return next
    }

    private suspend fun awardIfMilestone(count: Int, award: () -> StarAward): StarAward? {
        if (!MILESTONES.binarySearchContains(count)) return null
        addStars(1)
        return award()
    }

    /**
     * Two players, vs computer and Challenge have each now been tried at
     * least once. Adjusted from the legacy "all 4 modes" (which also
     * required Bluetooth and online) per docs/PLAN.md P6/§10 — otherwise
     * this star would be unearnable for anyone who never played Bluetooth
     * before those modes were removed.
     */
    private suspend fun checkAllModesTried(): StarAward.AllModesTried? {
        val twoPlayers = preferences.gamesPlayedTwoPlayers.first() > 0
        val vsComputer = preferences.gamesPlayedAgainstComputer.first() > 0
        val challenge = preferences.hasTriedChallenge.first()
        if (!(twoPlayers && vsComputer && challenge)) return null
        addStars(1)
        return StarAward.AllModesTried
    }

    private suspend fun addStars(count: Int) {
        preferences.setNumberStars(numberStars.first() + count)
    }

    private fun IntArray.binarySearchContains(value: Int): Boolean = binarySearch(value) >= 0

    private companion object {
        // GameLog.tab, unchanged.
        val MILESTONES = intArrayOf(5, 10, 20, 50, 100, 200, 500, 1000)
    }
}
