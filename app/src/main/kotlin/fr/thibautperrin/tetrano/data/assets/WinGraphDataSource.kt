package fr.thibautperrin.tetrano.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.engine.wingraph.WinBook
import javax.inject.Inject
import javax.inject.Singleton

/** The proven winning graphs of the Unbeatable levels (see tools/wingraph/README.md). */
interface WinGraphs {
    /** After a first ring on a strong place (2 519 positions). Loaded on first use, off the main thread. */
    val strong: WinBook

    /** After a first ring on a weak place (30 376 positions). Loaded on first use, off the main thread. */
    val weak: WinBook
}

/** Reads `res/raw/tetrano_win_strong.bin` and `res/raw/tetrano_win_weak.bin` once each. */
@Singleton
class WinGraphDataSource @Inject constructor(@ApplicationContext private val context: Context) : WinGraphs {
    override val strong: WinBook by lazy {
        context.resources.openRawResource(R.raw.tetrano_win_strong).use { WinBook.load(it) }
    }
    override val weak: WinBook by lazy {
        context.resources.openRawResource(R.raw.tetrano_win_weak).use { WinBook.load(it) }
    }
}
