package dev.barna.calm

internal object AccessibilityCopy {
    fun cardStackDescription(topCardText: String?): String {
        val topCard = topCardText
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
        return if (topCard.isBlank()) {
            "Card stack. Swipe vertically to browse cards."
        } else {
            "Card stack. Top card: $topCard. Swipe vertically to browse cards."
        }
    }

    fun pageOverviewCardDescription(title: String): String {
        return "$title page. Tap to open. Long press to move or manage."
    }

    fun notificationBadgeDescription(count: Int): String {
        val bounded = count.coerceAtLeast(0)
        return "$bounded notification${if (bounded == 1) "" else "s"}"
    }
}
