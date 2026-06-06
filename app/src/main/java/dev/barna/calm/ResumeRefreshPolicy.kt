package dev.barna.calm

class ResumeRefreshPolicy {
    fun shouldRefreshImmediately(hasCurrentScreen: Boolean, hasCurrentState: Boolean): Boolean {
        return !hasCurrentScreen || !hasCurrentState
    }
}
