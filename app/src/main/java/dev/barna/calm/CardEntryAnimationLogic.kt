package dev.barna.calm

object CardEntryAnimationLogic {
    data class CardState(val isCard: Boolean, val alpha: Float, val translationZ: Float)

    fun isStackWaitingForFirstStyle(cards: List<CardState>): Boolean {
        val taggedCards = cards.filter { it.isCard }
        if (taggedCards.isEmpty()) return false
        return taggedCards.all { it.alpha == 0f && it.translationZ == 0f }
    }

    fun entryTargetAlpha(alpha: Float, translationY: Float): Float {
        if (alpha > 0f) return alpha
        // Negative translationY means the exit animation shifted the card upward — it was
        // visible before and should animate back to visible. style() never produces negative
        // translationY (focusedCardGap is always ≥ 0), so this uniquely identifies exit-animated.
        // Non-negative translationY means style() set this card to invisible (out of visible
        // range) or the card is fresh — either way, it must stay hidden.
        return if (translationY < 0f) 1f else 0f
    }

    fun entryTargetTranslationY(currentTranslationY: Float, exitTranslateOffset: Float): Float {
        // style() never produces negative translationY, so a negative value unambiguously
        // identifies an exit-animated card whose translationY was shifted upward by the exit
        // animation. Undo that shift to recover the true styled position.
        return if (currentTranslationY < 0f) currentTranslationY + exitTranslateOffset
        else currentTranslationY
    }
}
