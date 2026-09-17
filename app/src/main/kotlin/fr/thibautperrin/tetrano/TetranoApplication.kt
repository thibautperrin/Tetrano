package fr.thibautperrin.tetrano

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.thibautperrin.tetrano.data.legacy.LegacyDataImporter
import fr.thibautperrin.tetrano.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Kept intentionally thin: Hilt wires up the
 * dependency graph, and the only startup work is kicking off the one-time
 * legacy data import (see docs/PLAN.md P5) — [LegacyDataImporter] itself
 * is a no-op after the first successful run.
 */
@HiltAndroidApp
class TetranoApplication : Application() {

    @Inject
    lateinit var legacyDataImporter: LegacyDataImporter

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            legacyDataImporter.importIfNeeded()
        }
    }
}
