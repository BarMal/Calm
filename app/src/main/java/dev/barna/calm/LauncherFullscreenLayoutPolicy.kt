package dev.barna.calm

object LauncherFullscreenLayoutPolicy {
    fun spineStyle(preferences: LauncherUiPreferences): ChapterSpineStyle {
        if (!preferences.fullScreenModeEnabled) return preferences.chapterSpineStyle
        return ChapterSpineStyle(
            titleMode = ChapterSpineTitleMode.ICONS_ONLY,
            position = ChapterSpinePosition.BOTTOM,
        )
    }
}
