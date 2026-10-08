package fr.thibautperrin.tetrano.data.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.thibautperrin.tetrano.R
import fr.thibautperrin.tetrano.engine.nn.QubicNet
import javax.inject.Inject
import javax.inject.Singleton

/** The level-101 network (see tools/nn/README.md for how it is trained and exported). */
interface NeuralNets {
    /** Loaded on first use (off the main thread: callers are on the default dispatcher). */
    val net: QubicNet
}

/** Reads `res/raw/tetrano_nn.bin` once. */
@Singleton
class NeuralNetDataSource @Inject constructor(@ApplicationContext private val context: Context) : NeuralNets {
    override val net: QubicNet by lazy {
        context.resources.openRawResource(R.raw.tetrano_nn).use { QubicNet.load(it) }
    }
}
