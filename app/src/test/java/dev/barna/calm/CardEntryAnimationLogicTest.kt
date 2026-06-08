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
    fun entryTargetAlphaDefaultsTo1WhenCurrentAlphaIsZero() {
        // After exit animation, alpha=0; we should animate to 1f (visible), not 0→0 (invisible).
        assertEquals(1f, CardEntryAnimationLogic.entryTargetAlpha(0f))
    }

    @Test
    fun entryTargetAlphaPreservesPositiveAlpha() {
        assertEquals(0.7f, CardEntryAnimationLogic.entryTargetAlpha(0.7f), 0.001f)
    }

    @Test
    fun entryTargetAlphaPreservesFullAlpha() {
        assertEquals(1f, CardEntryAnimationLogic.entryTargetAlpha(1f))
    }

    // entryTargetTranslationY

    @Test
    fun exitAnimatedCardTranslationYIsRestoredByUndoingExitOffset() {
        // Exit animation shifts translationY by -exitOffset (card moves up 80dp).
        // Entry must undo this to land at the true styled position.
        val exitOffset = 80f
        val styledY = 0f
        val exitAnimatedY = styledY - exitOffset  // -80f
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            alpha = 0f,
            translationZ = 115f,
            currentTranslationY = exitAnimatedY,
            exitTranslateOffset = exitOffset,
        )
        assertEquals(styledY, result, 0.001f)
    }

    @Test
    fun freshCardTranslationYIsPreservedDirectly() {
        // Fresh card (never styled): alpha=0, translationZ=0; translationY is already correct.
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            alpha = 0f,
            translationZ = 0f,
            currentTranslationY = 0f,
            exitTranslateOffset = 80f,
        )
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun visibleStyledCardTranslationYIsPreservedDirectly() {
        // Visible card: translationY is the correct styled position, no adjustment needed.
        val result = CardEntryAnimationLogic.entryTargetTranslationY(
            alpha = 1f,
            translationZ = 115f,
            currentTranslationY = 0f,
            exitTranslateOffset = 80f,
        )
        assertEquals(0f, result, 0.001f)
    }
}
