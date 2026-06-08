package dev.barna.calm

import dev.barna.calm.CardEntryAnimationLogic.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardEntryAnimationLogicTest {

    // isStackWaitingForFirstStyle

    @Test
    fun freshStackIsWaiting() {
        // appendCardsToStack sets alpha=0 and translationZ=0; style() has not run yet
        val cards = listOf(
            CardState(isCard = true, alpha = 0f, translationZ = 0f),
            CardState(isCard = true, alpha = 0f, translationZ = 0f),
        )
        assertTrue(CardEntryAnimationLogic.isStackWaitingForFirstStyle(cards))
    }

    @Test
    fun exitAnimatedStackIsNotWaiting() {
        // style() ran (translationZ set to non-zero), then exit animation zeroed alpha.
        // The old code returned true here (treating these as "never styled"), causing cards
        // to stay invisible after navigating back.
        val cards = listOf(
            CardState(isCard = true, alpha = 0f, translationZ = 115f),
            CardState(isCard = true, alpha = 0f, translationZ = 110f),
        )
        assertFalse(CardEntryAnimationLogic.isStackWaitingForFirstStyle(cards))
    }

    @Test
    fun styledStackWithVisibleCardsIsNotWaiting() {
        val cards = listOf(
            CardState(isCard = true, alpha = 1f, translationZ = 120f),
            CardState(isCard = true, alpha = 0.6f, translationZ = 115f),
        )
        assertFalse(CardEntryAnimationLogic.isStackWaitingForFirstStyle(cards))
    }

    @Test
    fun emptyStackIsNotWaiting() {
        assertFalse(CardEntryAnimationLogic.isStackWaitingForFirstStyle(emptyList()))
    }

    @Test
    fun nonCardViewsAreIgnored() {
        val cards = listOf(
            CardState(isCard = false, alpha = 0f, translationZ = 0f), // e.g. a divider
        )
        assertFalse(CardEntryAnimationLogic.isStackWaitingForFirstStyle(cards))
    }

    // entryTargetAlpha

    @Test
    fun exitAnimatedCardAlphaRestoredToOne() {
        // Exit animation shifts translationY negative; card was visible, must come back visible.
        assertEquals(1f, CardEntryAnimationLogic.entryTargetAlpha(alpha = 0f, translationY = -80f))
    }

    @Test
    fun styleInvisibleCardAlphaRemainsZero() {
        // style() set this card's alpha to 0 (out of visible range). translationY is the
        // positive focusedCardGap value style() assigned — not exit-shifted. Must stay hidden.
        assertEquals(0f, CardEntryAnimationLogic.entryTargetAlpha(alpha = 0f, translationY = 20f))
    }

    @Test
    fun freshCardAtOriginAlphaRemainsZero() {
        // Fresh card: style() hasn't run yet (translationZ=0) or style set alpha=0.
        // isStackWaitingForFirstStyle guards against this, but the function itself must be safe.
        assertEquals(0f, CardEntryAnimationLogic.entryTargetAlpha(alpha = 0f, translationY = 0f))
    }

    @Test
    fun entryTargetAlphaPreservesPositiveAlpha() {
        assertEquals(0.7f, CardEntryAnimationLogic.entryTargetAlpha(alpha = 0.7f, translationY = 0f), 0.001f)
    }

    @Test
    fun entryTargetAlphaPreservesFullAlpha() {
        assertEquals(1f, CardEntryAnimationLogic.entryTargetAlpha(alpha = 1f, translationY = 20f))
    }

    // entryTargetTranslationY

    @Test
    fun exitAnimatedCardTranslationYIsRestoredByUndoingExitOffset() {
        // Exit animation shifts translationY negative (card moves up 80dp).
        // Entry must undo this to land at the true styled position.
        val exitOffset = 80f
        val styledY = 0f
        val exitAnimatedY = styledY - exitOffset  // -80f
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = exitAnimatedY,
            exitTranslateOffset = exitOffset,
        )
        assertEquals(styledY, result, 0.001f)
    }

    @Test
    fun styleInvisibleCardTranslationYIsPreservedWithoutExitOffset() {
        // style() set this card's translationY to focusedCardGap (positive). It was never
        // exit-animated so the offset must NOT be added — doing so would push it even further
        // down, making it an outlier when the entry animation incorrectly renders it visible.
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = 20f,
            exitTranslateOffset = 80f,
        )
        assertEquals(20f, result, 0.001f)
    }

    @Test
    fun freshCardTranslationYIsPreservedDirectly() {
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = 0f,
            exitTranslateOffset = 80f,
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun visibleStyledCardTranslationYIsPreservedDirectly() {
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = 0f,
            exitTranslateOffset = 80f,
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun storedStyledPositionTakesPrecedenceForDeepCardWhereExitLandsPositive() {
        // Edge case: deep card with styledY(100) > exitOffset(80). Exit result = 20 (positive).
        // Without stored styledY: translationY(20) >= 0 → returned as-is → card drifts to 20.
        // With stored styledY=100: card always returns to its true styled position.
        assertEquals(100f, CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = 20f,
            exitTranslateOffset = 80f,
            styledTranslationY = 100f,
        ), 0.001f)
    }

    @Test
    fun storedStyledPositionAlsoWorksForNormalNegativeExitCase() {
        // When styledY is provided, it is always used regardless of translationY sign.
        assertEquals(30f, CardEntryAnimationLogic.entryTargetTranslationY(
            currentTranslationY = -50f,
            exitTranslateOffset = 80f,
            styledTranslationY = 30f,
        ), 0.001f)
    }

    // entryTargetAlpha with styledTranslationY

    @Test
    fun storedStyledPositionDetectsExitAnimatedCardWhenTranslationYIsPositive() {
        // Card styled at 100, exit resulted in 20 (positive). Alpha was zeroed by exit.
        // Without stored styledY: translationY(20)>=0 → 0f (card stays invisible — bug).
        // With stored styledY=100: position changed → card was animated → restore to 1f.
        assertEquals(1f, CardEntryAnimationLogic.entryTargetAlpha(
            alpha = 0f,
            translationY = 20f,
            styledTranslationY = 100f,
        ))
    }

    @Test
    fun storedStyledPositionKeepsInvisibleCardHiddenWhenNotExitAnimated() {
        // Deep invisible card: styledY = 100, never exit-animated, alpha = 0 by style().
        // translationY == styledY → card was never moved → must stay hidden.
        assertEquals(0f, CardEntryAnimationLogic.entryTargetAlpha(
            alpha = 0f,
            translationY = 100f,
            styledTranslationY = 100f,
        ))
    }
}
