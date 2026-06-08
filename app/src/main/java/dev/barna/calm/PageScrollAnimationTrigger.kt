package dev.barna.calm

class PageScrollAnimationTrigger(
    private val policy: PageEntryAnimationPolicy = PageEntryAnimationPolicy(),
) {
    var isSwipeInProgress: Boolean = false
        private set
    private var triggeredForCurrentSwipe = false

    fun onDragging() {
        isSwipeInProgress = true
    }

    // Call from onPageSelected when a swipe commits to a different page.
    // Returns the key to animate entry for, or null if no animation is needed.
    fun onSwipePageChanged(newPageKey: String, suppressedKey: String?): String? {
        if (!isSwipeInProgress) return null
        if (!policy.shouldAnimate(true, newPageKey, suppressedKey)) return null
        triggeredForCurrentSwipe = true
        return newPageKey
    }

    // Call at SCROLL_STATE_SETTLING. Handles swipe-back-to-same-page and overscroll,
    // where onPageSelected was never called so onSwipePageChanged wasn't triggered.
    fun onSettling(currentPageKey: String, suppressedKey: String?): String? {
        if (!isSwipeInProgress || triggeredForCurrentSwipe) return null
        if (!policy.shouldAnimate(true, currentPageKey, suppressedKey)) return null
        triggeredForCurrentSwipe = true
        return currentPageKey
    }

    // Call at SCROLL_STATE_IDLE. Handles programmatic navigation and resets swipe state.
    fun onIdle(currentPageKey: String, suppressedKey: String?): String? {
        val result = if (!triggeredForCurrentSwipe) {
            if (policy.shouldAnimate(isSwipeInProgress, currentPageKey, suppressedKey)) currentPageKey
            else null
        } else null
        isSwipeInProgress = false
        triggeredForCurrentSwipe = false
        return result
    }
}
