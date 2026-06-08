package dev.barna.calm

object CardEntryAnimationLogic {
    data class CardState(val isCard: Boolean, val alpha: Float, val translationZ: Float)

    fun isStackWaitingForFirstStyle(cards: List<CardState>): Boolean {
        val taggedCards = cards.filter { it.isCard }
        if (taggedCards.isEmpty()) return false
        return taggedCards.all { it.alpha == 0f && it.translationZ == 0f }
    }

    fun entryTargetAlpha(alpha: Float, translationY: Float, styledTranslationY: Float? = null): Float {
        if (alpha > 0f) return alpha
        if (styledTranslationY != null) {
            // If the card is not at its styled position, it was moved by an animation (exit or
            // interrupted entry). It was visible before and must animate back to visible.
            // If it IS at its styled position, style() left it invisible (deep in stack) — keep hidden.
            return if (translationY != styledTranslationY) 1f else 0f
        }
        // Fallback: negative translationY uniquely identifies exit-animated cards when
        // styledTranslationY is not stored (styledY < exitOffset holds for default settings).
        return if (translationY < 0f) 1f else 0f
    }

    fun entryTargetTranslationY(
        currentTranslationY: Float,
        exitTranslateOffset: Float,
        styledTranslationY: Float? = null,
    ): Float {
        // When the styled position is known, use it directly — this handles deep cards where
        // styledY >= exitOffset, causing exit to land at a positive (not-negative) translationY
        // which the sign-based fallback below would incorrectly treat as "not exit-animated".
        if (styledTranslationY != null) return styledTranslationY
        // Fallback: style() never produces negative translationY, so negative unambiguously
        // identifies an exit-animated card. Undo the exit shift to recover the styled position.
        return if (currentTranslationY < 0f) currentTranslationY + exitTranslateOffset
        else currentTranslationY
    }
}
