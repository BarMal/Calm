package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardStackScrollMemoryTest {
    @Test
    fun partialStackRestoreKeepsDeepTargetPending() {
        val memory = CardStackScrollMemory(maxRememberedStacks = 4)
        memory.remember("apps:personal", 900)

        val restore = memory.initialRestore("apps:personal", maxScroll = 320)

        assertEquals(CardStackScrollRestore(scrollY = 0, pendingTarget = 900), restore)
        assertEquals(900, memory.pendingRestoreTarget("apps:personal"))
    }

    @Test
    fun expandedBoundsContinueRestoringTowardPendingTarget() {
        val memory = CardStackScrollMemory(maxRememberedStacks = 4)
        memory.remember("apps:personal", 900)
        memory.initialRestore("apps:personal", maxScroll = 320)

        assertEquals(640, memory.restoreAfterBoundsExpanded("apps:personal", maxScroll = 640))
        assertEquals(900, memory.pendingRestoreTarget("apps:personal"))

        assertEquals(900, memory.restoreAfterBoundsExpanded("apps:personal", maxScroll = 1200))
        assertNull(memory.pendingRestoreTarget("apps:personal"))
    }

    @Test
    fun userScrollCancelsPendingRestore() {
        val memory = CardStackScrollMemory(maxRememberedStacks = 4)
        memory.remember("apps:personal", 900)
        memory.initialRestore("apps:personal", maxScroll = 320)

        memory.remember("apps:personal", 360)

        assertNull(memory.pendingRestoreTarget("apps:personal"))
        assertNull(memory.restoreAfterBoundsExpanded("apps:personal", maxScroll = 1200))
        assertEquals(CardStackScrollRestore(scrollY = 360, pendingTarget = null), memory.initialRestore("apps:personal", maxScroll = 1200))
    }

    @Test
    fun oldStacksArePrunedFromPendingRestores() {
        val memory = CardStackScrollMemory(maxRememberedStacks = 2)
        memory.remember("one", 100)
        memory.remember("two", 200)
        memory.remember("three", 300)

        assertEquals(CardStackScrollRestore(scrollY = 0, pendingTarget = null), memory.initialRestore("one", maxScroll = 500))
        assertEquals(CardStackScrollRestore(scrollY = 200, pendingTarget = null), memory.initialRestore("two", maxScroll = 500))
        assertEquals(CardStackScrollRestore(scrollY = 300, pendingTarget = null), memory.initialRestore("three", maxScroll = 500))
    }
}
