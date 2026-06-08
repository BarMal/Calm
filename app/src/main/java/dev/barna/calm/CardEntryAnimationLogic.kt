package dev.barna.calm

object CardEntryAnimationLogic {
    data class CardState(val isCard: Boolean, val alpha: Float, val translationZ: Float)

    fun isStackWaitingForFirstStyle(cards: List<CardState>): Boolean {
        val taggedCards = cards.filter { it.isCard }
        if (taggedCards.isEmpty()) return false
        return taggedCards.all { it.alpha == 0f && it.translationZ == 0f }
    }

    fun entryTargetAlpha(currentAlpha: Float): Float = if (currentAlpha > 0f) currentAlpha else 1f

    fun entryTargetTranslationY(
        alpha: Float,
        translationZ: Float,
        currentTranslationY: Float,
        exitTranslateOffset: Float,
    ): Float {
        // Exit-animated card: style() ran (translationZ≠0) then exit animation zeroed alpha
        // and shifted translationY upward by exitTranslateOffset. Undo the shift so the entry
        // animation targets the correct styled position rather than accumulating drift.
        return if (alpha == 0f && translationZ != 0f) {
            currentTranslationY + exitTranslateOffset
        } else {
            currentTranslationY
        }
    }
}
