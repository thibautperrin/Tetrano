package fr.thibautperrin.tetrano.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.thibautperrin.tetrano.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The 245 built-in Challenge mode levels, read once from
 * `res/raw/challenge.bin` — an irreplaceable asset carried over unchanged
 * from the legacy app (see docs/PLAN.md P0/P4, `seed_assets/README.md`).
 *
 * Ports the raw-file half of `MemoryVarsChallenge` (see docs/GLOSSARY.md):
 * each level is 14 bytes — a 13-byte [fr.thibautperrin.tetrano.engine.BoardState.signature]
 * plus one byte encoding both the fork order (`lastByte / 2 - 1`) and
 * whether the computer played first (`lastByte % 2 == 0`). The actual byte
 * parsing lives in [ChallengeLevelsCodec] so it's unit-testable without an
 * Android `Context`.
 */
@Singleton
class ChallengeLevelsDataSource @Inject constructor(@ApplicationContext context: Context) {

    private val data: ByteArray = context.resources.openRawResource(R.raw.challenge).use { it.readBytes() }

    init {
        check(data.size == ChallengeLevelsCodec.NUMBER_BYTES_CHALLENGE_FILE) {
            "challenge.bin has ${data.size} bytes, expected ${ChallengeLevelsCodec.NUMBER_BYTES_CHALLENGE_FILE}"
        }
    }

    val levelCount: Int get() = ChallengeLevelsCodec.NUMBER_CHALLENGE_LEVELS

    /** The board signature for [level] (1..[levelCount]). Ports `getSignature`. */
    fun signature(level: Int): ByteArray = ChallengeLevelsCodec.signature(data, level)

    /** How many forced pairs the built-in fork for [level] needs. Ports `getForkOrder`. */
    fun forkOrder(level: Int): Int = ChallengeLevelsCodec.forkOrder(data, level)

    /** True iff the computer played first on [level] (so the user isn't player 1 from ring 0). Ports `isInvertPlayer`. */
    fun isInvertPlayer(level: Int): Boolean = ChallengeLevelsCodec.isInvertPlayer(data, level)
}
