package fr.thibautperrin.tetrano

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Kept intentionally thin: Hilt wires up the
 * dependency graph, and one-off startup work (legacy data import, see
 * docs/PLAN.md P5) is added here once the data layer exists.
 */
@HiltAndroidApp
class TetranoApplication : Application()
