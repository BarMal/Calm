package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageScrollAnimationTriggerTest {

    @Test
    fun swipeToNewPageTriggersEntryForNewPageInOnPageSelected() {
        // Bug: old code read pager.currentItem at SETTLING, which was still the old page.
        // Fix: fire the entry animation in onSwipePageChanged (called from onPageSelected).
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        assertEquals("B", trigger.onSwipePageChanged(newPageKey = "B", suppressedKey = null))
    }

    @Test
    fun settlingAfterSuccessfulSwipeDoesNotRetrigger() {
        // onSwipePageChanged already fired the animation; SETTLING must not double-trigger.
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        trigger.onSwipePageChanged("B", null)
        assertNull(trigger.onSettling("B", null))
    }

    @Test
    fun idleAfterSwipePageChangedDoesNotRetrigger() {
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        trigger.onSwipePageChanged("B", null)
        assertNull(trigger.onIdle("B", null))
    }

    @Test
    fun settlingAfterSwipeToSamePageTriggersEntryForCurrentPage() {
        // User swiped but returned to same page (no onPageSelected fired).
        // SETTLING is the only opportunity to re-animate the current page.
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        assertEquals("A", trigger.onSettling("A", null))
    }

    @Test
    fun settlingPastLastPageTriggersEntryForCurrentPage() {
        // Over-scrolling past the last page has the same shape as a same-page swipe.
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        assertEquals("Z", trigger.onSettling("Z", null))
    }

    @Test
    fun idleHandlesProgrammaticNavigation() {
        // No dragging happened; programmatic setCurrentItem triggers animation at IDLE.
        val trigger = PageScrollAnimationTrigger()
        assertEquals("B", trigger.onIdle("B", null))
    }

    @Test
    fun idleResetsStateForNextSwipe() {
        val trigger = PageScrollAnimationTrigger()
        trigger.onDragging()
        trigger.onSwipePageChanged("B", null)
        trigger.onIdle("B", null)

        // After IDLE, the trigger is clean; next programmatic nav must work.
        assertEquals("A", trigger.onIdle("A", null))
    }

    @Test
    fun suppressedKeyBlocksAnimation() {
        val trigger = PageScrollAnimationTrigger()
        assertNull(trigger.onSwipePageChanged("A", suppressedKey = "A"))
        assertNull(trigger.onSettling("A", suppressedKey = "A"))
        assertNull(trigger.onIdle("A", suppressedKey = "A"))
    }

    @Test
    fun fullSwipeSequenceAlwaysAnimatesDestination() {
        val trigger = PageScrollAnimationTrigger()
        // Initial load
        assertEquals("A", trigger.onIdle("A", null))

        // A→B swipe
        trigger.onDragging()
        assertEquals("B", trigger.onSwipePageChanged("B", null))
        assertNull(trigger.onSettling("B", null))
        assertNull(trigger.onIdle("B", null))

        // B→C swipe
        trigger.onDragging()
        assertEquals("C", trigger.onSwipePageChanged("C", null))
        assertNull(trigger.onIdle("C", null))

        // C→B swipe back
        trigger.onDragging()
        assertEquals("B", trigger.onSwipePageChanged("B", null))
        assertNull(trigger.onIdle("B", null))

        // B→A swipe back
        trigger.onDragging()
        assertEquals("A", trigger.onSwipePageChanged("A", null))
        assertNull(trigger.onIdle("A", null))
    }

    @Test
    fun onSwipePageChangedDoesNothingWithoutDragging() {
        // Defensive: if somehow called without a drag in progress, no animation.
        val trigger = PageScrollAnimationTrigger()
        assertNull(trigger.onSwipePageChanged("B", null))
    }

    @Test
    fun settlingDoesNothingWithoutDragging() {
        val trigger = PageScrollAnimationTrigger()
        assertNull(trigger.onSettling("A", null))
    }
}
