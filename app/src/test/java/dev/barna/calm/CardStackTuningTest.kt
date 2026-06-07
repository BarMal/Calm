package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStackTuningTest {
    @Test
    fun horizontalPathProgressMakesActiveCardCenterOfArc() {
        val tuning = CardStackTuning(
            curve = 50,
            horizontalCurve = 100,
            arcWidth = 50,
            aboveFocusCards = 2,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = 5,
        )

        assertEquals(0f, tuning.horizontalPathProgress(0f), 0f)
        assertTrue(tuning.horizontalPathProgress(1f) < tuning.horizontalPathProgress(2f))
        assertTrue(tuning.horizontalPathProgress(2f) < tuning.horizontalPathProgress(4f))
        assertTrue(tuning.horizontalPathProgress(-0.5f) < tuning.horizontalPathProgress(-1f))
        assertTrue(tuning.horizontalPathProgress(-1f) < tuning.horizontalPathProgress(-2f))
        assertEquals(1f, tuning.horizontalPathProgress(-tuning.outgoingVisibleRange), 0f)
    }

    @Test
    fun rotationCanBeKeptIndependentFromHorizontalCurve() {
        val flat = CardStackTuning(
            curve = 50,
            horizontalCurve = 100,
            arcWidth = 50,
            aboveFocusCards = 2,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = 3,
        )
        val fanned = flat.copy(rotation = 100)

        assertEquals(0f, flat.rotationFactor, 0f)
        assertEquals(1f, fanned.rotationFactor, 0f)
        assertTrue(fanned.rotationProgress(2f) > fanned.rotationProgress(1f))
    }

    @Test
    fun aboveFocusCardsWidensOutgoingRange() {
        val compact = CardStackTuning(
            curve = 50,
            horizontalCurve = 100,
            arcWidth = 50,
            aboveFocusCards = 1,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = 3,
        )
        val expanded = compact.copy(aboveFocusCards = 4)

        assertTrue(expanded.outgoingVisibleRange > compact.outgoingVisibleRange)
        assertTrue(expanded.horizontalPathProgress(-1f) < compact.horizontalPathProgress(-1f))
    }

    @Test
    fun focusDistinctionControlsScaleGapAndMagnetTiming() {
        val subtle = CardStackTuning(
            curve = 50,
            horizontalCurve = 0,
            arcWidth = 50,
            aboveFocusCards = 2,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = 3,
            focusedCardGap = 10,
            focusedCardScale = 10,
            magnetStrength = 20,
        )
        val strong = subtle.copy(
            focusedCardGap = 70,
            focusedCardScale = 70,
            magnetStrength = 90,
        )

        assertTrue(strong.focusedCardGapFactor > subtle.focusedCardGapFactor)
        assertTrue(strong.focusedCardScaleFactor > subtle.focusedCardScaleFactor)
        assertTrue(strong.magnetStrengthFactor > subtle.magnetStrengthFactor)
        assertTrue(strong.magnetDelayMillis < subtle.magnetDelayMillis)
    }
}
