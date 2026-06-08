package dev.barna.calm

object CardEntryAnimationLogic {
    data class CardState(val isCard: Boolean, val alpha: Float, val translationZ: Float)

    fun isStackWaitingForFirstStyle(cards: List<CardState>): Boolean {
        val taggedCards = cards.filter { it.isCard }
        if (taggedCards.isEmpty()) return false
        return taggedCards.all { it.alpha == 0f && it.translationZ == 0f }
    }

    fun entryTargetAlpha(currentAlpha: Float): Float = if (currentAlpha > 0f) currentAlpha else 1f
}
