package dev.barna.calm

class SettingsCopyFormatter {
    fun notificationSurface(useTintedCards: Boolean): String {
        return if (useTintedCards) "Notification surface\nTinted cards" else "Notification surface\nChapter panel"
    }

    fun cardHaptics(enabled: Boolean): String {
        return if (enabled) "Card haptics\nOn" else "Card haptics\nOff"
    }

    fun appLibrary(splitByProfile: Boolean): String {
        return if (splitByProfile) "App library\nSplit personal and work" else "App library\nCombined apps"
    }

    fun workNotificationPlacement(beforeApps: Boolean): String {
        return if (beforeApps) "Work notification chapters\nLeft of apps" else "Work notification chapters\nWith other notifications"
    }

    fun advancedStackControls(shown: Boolean): String {
        return if (shown) "Advanced stack controls\nShown" else "Advanced stack controls\nHidden"
    }

    fun visibleCards(count: Int): String = "$count cards"

    fun cardFanRotation(progress: Int): String {
        return if (progress == 0) "Cards stay flat" else "$progress% tilt near tail"
    }

    fun cardArcWidth(progress: Int): String {
        return when {
            progress < 18 -> "Tight curve"
            progress > 78 -> "Wide ribbon"
            else -> "$progress% broadness"
        }
    }

    fun verticalSpacing(progress: Int): String = "$progress% spread"

    fun visualCurve(progress: Int): String = "$progress% depth"

    fun focusedCardGap(progress: Int): String = "$progress% separation"

    fun focusedCardScale(progress: Int): String = "$progress% focus size"

    fun magnetStrength(progress: Int): String = "$progress% snap"

    fun horizontalCurve(value: Int): String {
        return when {
            value < 0 -> "Curves from left ${kotlin.math.abs(value)}%"
            value > 0 -> "Curves from right $value%"
            else -> "Flat centre path"
        }
    }

    fun hapticStrength(strength: Int): String = "Very light / $strength of 5"

    fun cardVibrancy(progress: Int): String {
        return when {
            progress == 0 -> "Clear"
            progress == 100 -> "Maximum vibrancy"
            else -> "$progress% vibrancy"
        }
    }

    fun stackPeakPosition(position: Int): String {
        return when {
            position < 20 -> "Peak at top"
            position > 80 -> "Peak at bottom"
            position in 40..60 -> "Peak at centre"
            position < 40 -> "Peak above centre"
            else -> "Peak below centre"
        }
    }
}
