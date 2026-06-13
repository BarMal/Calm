package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherFullscreenLayoutPolicyTest {
    @Test
    fun normalModeKeepsConfiguredSpineStyle() {
        val style = ChapterSpineStyle(ChapterSpineTitleMode.TITLE_ONLY, ChapterSpinePosition.TOP)
        val preferences = preferences(fullScreen = false, style = style)

        assertEquals(style, LauncherFullscreenLayoutPolicy.spineStyle(preferences))
    }

    @Test
    fun fullScreenModeUsesBottomIconSpine() {
        val preferences = preferences(
            fullScreen = true,
            style = ChapterSpineStyle(ChapterSpineTitleMode.SPLIT, ChapterSpinePosition.TOP),
        )

        assertEquals(
            ChapterSpineStyle(ChapterSpineTitleMode.ICONS_ONLY, ChapterSpinePosition.BOTTOM),
            LauncherFullscreenLayoutPolicy.spineStyle(preferences),
        )
    }

    private fun preferences(fullScreen: Boolean, style: ChapterSpineStyle): LauncherUiPreferences {
        return LauncherUiPreferences(
            useTintedNotificationCards = true,
            useCardIconBackgrounds = true,
            cardCornerRadiusDp = 18,
            cardIconBlur = 0,
            focusBlurRadius = 7,
            splitAppsByProfile = false,
            placeWorkNotificationChaptersBeforeApps = false,
            cardHapticsEnabled = true,
            cardHapticStrength = 2,
            cardStackTuning = CardStackTuning(
                curve = 50,
                horizontalCurve = 0,
                arcWidth = 50,
                aboveFocusCards = 2,
                rotation = 0,
                verticalSpacing = 50,
                visibleCards = 3,
            ),
            showAdvancedStackControls = false,
            cardVibrancy = 50,
            fullScreenModeEnabled = fullScreen,
            chapterSpineStyle = style,
        )
    }
}
