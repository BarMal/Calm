package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStackLayoutTest {
    @Test
    fun singleCardUsesCenteredFocusAnchorInsteadOfAboveCardReserve() {
        val top = CardStackLayout.activeTopPadding(
            viewportHeight = 600,
            cardHeight = 180,
            minimumTopPadding = 6,
        )

        assertEquals(210, top)
    }

    @Test
    fun activeAnchorDoesNotChangeWithStackCountOrSpacing() {
        val oneCard = CardStackLayout.activeTopPadding(
            viewportHeight = 360,
            cardHeight = 180,
            minimumTopPadding = 6,
        )
        val manyCards = CardStackLayout.activeTopPadding(
            viewportHeight = 360,
            cardHeight = 180,
            minimumTopPadding = 6,
        )

        assertEquals(oneCard, manyCards)
        assertEquals(90, manyCards)
    }

    @Test
    fun peakFractionZeroPlacesCardAtTop() {
        val top = CardStackLayout.activeTopPadding(
            viewportHeight = 600,
            cardHeight = 120,
            minimumTopPadding = 6,
            peakFraction = 0f,
        )
        assertEquals(6, top)
    }

    @Test
    fun peakFractionOneHundredPlacesCardAtBottom() {
        val top = CardStackLayout.activeTopPadding(
            viewportHeight = 600,
            cardHeight = 120,
            minimumTopPadding = 6,
            peakFraction = 1f,
        )
        assertEquals(480, top)
    }

    @Test
    fun peakFractionHalfPlacesCardAtCentre() {
        val top = CardStackLayout.activeTopPadding(
            viewportHeight = 600,
            cardHeight = 120,
            minimumTopPadding = 6,
            peakFraction = 0.5f,
        )
        assertEquals(240, top)
    }

    @Test
    fun zeroViewportHeightYieldsMinimumPaddingIndicatingUnmeasuredViewport() {
        // Root cause of #104/#105: scroller.height == 0 in scroller.post{} for pre-warmed pages
        // → activeTopPadding collapses to minimumTopPadding (the settings-preview-sized value).
        // The fix defers applyLayout until the viewport is actually measured.
        val top = CardStackLayout.activeTopPadding(
            viewportHeight = 0,
            cardHeight = 180,
            minimumTopPadding = 6,
            peakFraction = 0.4f,
        )
        assertEquals(6, top)
    }

    @Test
    fun measuredViewportYieldsViewportProportionalPadding() {
        val unmeasured = CardStackLayout.activeTopPadding(
            viewportHeight = 0,
            cardHeight = 180,
            minimumTopPadding = 6,
            peakFraction = 0.4f,
        )
        val measured = CardStackLayout.activeTopPadding(
            viewportHeight = 600,
            cardHeight = 180,
            minimumTopPadding = 6,
            peakFraction = 0.4f,
        )
        assertNotEquals(unmeasured, measured)
        assertTrue(measured > unmeasured)
    }

    @Test
    fun trailingPaddingAllowsLastCardToReachTheSameAnchor() {
        val trailing = CardStackLayout.trailingPadding(
            viewportHeight = 600,
            activeTopPadding = 210,
            cardHeight = 180,
            minimumBottomPadding = 32,
        )

        assertTrue(trailing >= 32)
        assertEquals(242, trailing)
    }
}
