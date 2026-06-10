package dev.barna.calm

/**
 * Visibility and placement of the global page chrome (the elements that sit around the pages).
 * Defaults reproduce the current layout: clock and spine shown, spine at the top.
 */
data class LauncherChrome(
    val showClock: Boolean = true,
    val showSpine: Boolean = true,
    val spineAtBottom: Boolean = false,
)
