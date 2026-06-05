package dev.barna.calm

import org.junit.Assert.assertEquals
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
    fun screenAnchorKeepsCardsAtTheSameAbsoluteHeightAcrossDifferentViewports() {
        val firstViewportTop = 120
        val secondViewportTop = 260
        val targetScreenTop = 420
        val firstTop = CardStackLayout.activeTopPadding(
            viewportHeight = 620,
            cardHeight = 180,
            minimumTopPadding = 6,
            viewportTopOnScreen = firstViewportTop,
            targetTopOnScreen = targetScreenTop,
        )
        val secondTop = CardStackLayout.activeTopPadding(
            viewportHeight = 520,
            cardHeight = 180,
            minimumTopPadding = 6,
            viewportTopOnScreen = secondViewportTop,
            targetTopOnScreen = targetScreenTop,
        )

        assertEquals(targetScreenTop, firstViewportTop + firstTop)
        assertEquals(targetScreenTop, secondViewportTop + secondTop)
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
