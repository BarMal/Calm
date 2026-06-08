package dev.barna.calm

class PageEntryAnimationPolicy {
    private var lastAnimatedKey: String? = null

    fun shouldAnimate(userSwipeInProgress: Boolean, currentKey: String, suppressedKey: String?): Boolean {
        if (suppressedKey == currentKey) return false
        if (userSwipeInProgress || lastAnimatedKey != currentKey) {
            lastAnimatedKey = currentKey
            return true
        }
        return false
    }
}
