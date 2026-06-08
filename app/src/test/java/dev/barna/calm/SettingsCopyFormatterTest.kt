package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCopyFormatterTest {
    private val formatter = SettingsCopyFormatter()

    @Test
    fun formatsToggleCards() {
        assertEquals("Notification surface\nTinted cards", formatter.notificationSurface(true))
        assertEquals("Notification surface\nChapter panel", formatter.notificationSurface(false))
        assertEquals("Card haptics\nOn", formatter.cardHaptics(true))
        assertEquals("Card haptics\nOff", formatter.cardHaptics(false))
        assertEquals("Advanced stack controls\nShown", formatter.advancedStackControls(true))
        assertEquals("Advanced stack controls\nHidden", formatter.advancedStackControls(false))
    }

    @Test
    fun formatsAppLibrarySettings() {
        assertEquals("App library\nSplit personal and work", formatter.appLibrary(true))
        assertEquals("App library\nCombined apps", formatter.appLibrary(false))
        assertEquals("Work notification chapters\nLeft of apps", formatter.workNotificationPlacement(true))
        assertEquals("Work notification chapters\nWith other notifications", formatter.workNotificationPlacement(false))
    }

    @Test
    fun formatsStackControlValues() {
        assertEquals("3 cards", formatter.visibleCards(3))
        assertEquals("Cards stay flat", formatter.cardFanRotation(0))
        assertEquals("24% tilt near tail", formatter.cardFanRotation(24))
        assertEquals("Tight curve", formatter.cardArcWidth(10))
        assertEquals("Wide ribbon", formatter.cardArcWidth(90))
        assertEquals("50% broadness", formatter.cardArcWidth(50))
        assertEquals("55% spread", formatter.verticalSpacing(55))
        assertEquals("42% depth", formatter.visualCurve(42))
        assertEquals("36% separation", formatter.focusedCardGap(36))
        assertEquals("32% focus size", formatter.focusedCardScale(32))
        assertEquals("70% snap", formatter.magnetStrength(70))
    }

    @Test
    fun formatsSignedAndHapticValues() {
        assertEquals("Curves from left 20%", formatter.horizontalCurve(-20))
        assertEquals("Curves from right 20%", formatter.horizontalCurve(20))
        assertEquals("Flat centre path", formatter.horizontalCurve(0))
        assertEquals("Very light / 3 of 5", formatter.hapticStrength(3))
    }

    @Test
    fun formatsCardVibrancy() {
        assertEquals("Clear", formatter.cardVibrancy(0))
        assertEquals("Maximum vibrancy", formatter.cardVibrancy(100))
        assertEquals("50% vibrancy", formatter.cardVibrancy(50))
        assertEquals("1% vibrancy", formatter.cardVibrancy(1))
        assertEquals("99% vibrancy", formatter.cardVibrancy(99))
    }
}
