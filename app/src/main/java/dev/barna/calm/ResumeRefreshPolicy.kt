package dev.barna.calm

class ResumeRefreshPolicy {
    fun shouldRefreshImmediately(
        hasCurrentScreen: Boolean,
        hasCurrentState: Boolean,
        launcherSettingsChanged: Boolean,
    ): Boolean {
        return launcherSettingsChanged || !hasCurrentScreen || !hasCurrentState
    }
}
