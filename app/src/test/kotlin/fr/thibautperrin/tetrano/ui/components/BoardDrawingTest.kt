package fr.thibautperrin.tetrano.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks [BoardDrawing]'s position math against the legacy coefficients
 * (`StaticFields`) computed by hand, and a couple of structural
 * invariants — the parts of `Dessin` that don't need an actual `Canvas`
 * to exercise.
 */
class BoardDrawingTest {

    private val side = 1000f

    // pos0Coeff = thicknessBorderCoeff + distanceLinesCoeff / 2
    //           = 0.01262 + 0.236593 / 2 = 0.1309165
    // distanceRingsCentersCoeff = distanceLinesCoeff + thicknessSeparationLinesCoeff
    //           = 0.236593 + 0.009464 = 0.246057
    private val pos0 = 130.9165f
    private val distance = 246.057f

    @Test
    fun cellCenterMatchesLegacyCoefficients() {
        val origin = BoardDrawing.cellCenter(side, 0, 0)
        assertEquals(pos0, origin.x, 0.01f)
        assertEquals(pos0, origin.y, 0.01f)

        val opposite = BoardDrawing.cellCenter(side, 3, 3)
        assertEquals(pos0 + distance * 3, opposite.x, 0.01f)
        assertEquals(pos0 + distance * 3, opposite.y, 0.01f)
    }

    @Test
    fun trayRingCenterSitsPastTheGridEdgeOnTheCrossAxis() {
        val portrait = BoardDrawing.trayRingCenter(side, ringSize = 2, vertical = true)
        assertEquals(pos0 + distance * 2, portrait.x, 0.01f) // along the main axis
        assertEquals(pos0 + distance * 4, portrait.y, 0.01f) // past the grid, cross axis

        val landscape = BoardDrawing.trayRingCenter(side, ringSize = 2, vertical = false)
        assertEquals(pos0 + distance * 4, landscape.x, 0.01f)
        assertEquals(pos0 + distance * 2, landscape.y, 0.01f)
    }

    @Test
    fun trayRingCentersAreDistinctForEachSize() {
        val centers = (0 until 4).map { BoardDrawing.trayRingCenter(side, it, vertical = true) }
        assertEquals(4, centers.toSet().size)
    }
}
