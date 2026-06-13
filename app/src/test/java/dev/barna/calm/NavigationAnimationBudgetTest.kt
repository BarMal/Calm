package dev.barna.calm

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAnimationBudgetTest {
    @Test
    fun swipeNavigationAnimatesFewerCardsThanFullEntry() {
        assertTrue(NavigationAnimationBudget.SWIPE_ENTRY_ANIMATED_CARDS < NavigationAnimationBudget.DEFAULT_ENTRY_ANIMATED_CARDS)
        assertTrue(NavigationAnimationBudget.SWIPE_EXIT_ANIMATED_CARDS < NavigationAnimationBudget.DEFAULT_EXIT_ANIMATED_CARDS)
    }

    @Test
    fun swipeNavigationStillAnimatesRepresentativeCards() {
        assertTrue(NavigationAnimationBudget.SWIPE_ENTRY_ANIMATED_CARDS >= 3)
        assertTrue(NavigationAnimationBudget.SWIPE_EXIT_ANIMATED_CARDS >= 3)
    }
}
