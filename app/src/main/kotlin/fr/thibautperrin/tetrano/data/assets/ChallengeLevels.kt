package fr.thibautperrin.tetrano.data.assets

/**
 * The read-only surface of [ChallengeLevelsDataSource] that
 * [fr.thibautperrin.tetrano.domain.service.ChallengeService] needs,
 * extracted so tests can supply a plain in-memory fake instead of the real
 * Android-`Context`-backed data source.
 */
interface ChallengeLevels {
    val levelCount: Int
    fun signature(level: Int): ByteArray
    fun forkOrder(level: Int): Int
    fun isInvertPlayer(level: Int): Boolean
}
