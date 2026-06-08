package dev.barna.calm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageEntryAnimationPolicyTest {

    @Test
    fun animatesOnFirstVisitWithoutSwipe() {
        val policy = PageEntryAnimationPolicy()
        assertTrue(policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = null))
    }

    @Test
    fun doesNotAnimateSamePageAgainWithoutSwipe() {
        val policy = PageEntryAnimationPolicy()
        policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = null)
        assertFalse(policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = null))
    }

    @Test
    fun animatesDestinationPageAfterSwipe() {
        // Bug: the old code blocked animation when userSwipeInProgress=true
        val policy = PageEntryAnimationPolicy()
        assertTrue(policy.shouldAnimate(userSwipeInProgress = true, currentKey = "A", suppressedKey = null))
    }

    @Test
    fun animatesWhenSwipingBackToPreviouslyExitAnimatedPage() {
        // This is the core regression: navigate A→B→A, cards on A disappear
        // because the entry animation was never fired on return to A.
        val policy = PageEntryAnimationPolicy()
        policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = null) // initial load
        policy.shouldAnimate(userSwipeInProgress = true, currentKey = "B", suppressedKey = null)  // swipe to B
        assertTrue(
            "Entry animation must fire when swiping back to a page whose cards were exit-animated",
            policy.shouldAnimate(userSwipeInProgress = true, currentKey = "A", suppressedKey = null), // swipe back
        )
    }

    @Test
    fun doesNotAnimateWhenSuppressed() {
        val policy = PageEntryAnimationPolicy()
        assertFalse(policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = "A"))
    }

    @Test
    fun animatesAfterSuppressionLifts() {
        val policy = PageEntryAnimationPolicy()
        policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = "A")
        assertTrue(policy.shouldAnimate(userSwipeInProgress = false, currentKey = "A", suppressedKey = null))
    }

    @Test
    fun multiPageSwipeSequenceAlwaysAnimates() {
        val policy = PageEntryAnimationPolicy()
        policy.shouldAnimate(false, "A", null)         // initial
        assertTrue(policy.shouldAnimate(true, "B", null))  // A→B
        assertTrue(policy.shouldAnimate(true, "C", null))  // B→C
        assertTrue(policy.shouldAnimate(true, "B", null))  // C→B
        assertTrue(policy.shouldAnimate(true, "A", null))  // B→A
    }
}
