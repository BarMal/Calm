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

    @Test
    fun snapshotRestoresRememberedAndPendingPositions() {
        val original = CardStackScrollMemory(maxRememberedStacks = 4)
        original.remember("apps:personal", 900)
        original.initialRestore("apps:personal", maxScroll = 320)
        original.remember("overview", 240)

        val restored = CardStackScrollMemory(maxRememberedStacks = 4)
        restored.restore(original.snapshot())

        assertEquals(CardStackScrollRestore(scrollY = 0, pendingTarget = 900), restored.initialRestore("apps:personal", maxScroll = 320))
        assertEquals(900, restored.pendingRestoreTarget("apps:personal"))
        assertEquals(CardStackScrollRestore(scrollY = 240, pendingTarget = null), restored.initialRestore("overview", maxScroll = 500))
    }

    @Test
    fun restoreSnapshotPrunesToCapacity() {
        val restored = CardStackScrollMemory(maxRememberedStacks = 2)

        restored.restore(
            CardStackScrollSnapshot(
                rememberedScrollPositions = linkedMapOf("one" to 100, "two" to 200, "three" to 300),
                pendingRestoreTargets = linkedMapOf("one" to 1000, "two" to 2000, "three" to 3000),
            ),
        )

        assertNull(restored.pendingRestoreTarget("one"))
        assertEquals(2000, restored.pendingRestoreTarget("two"))
        assertEquals(3000, restored.pendingRestoreTarget("three"))
        assertEquals(CardStackScrollRestore(scrollY = 0, pendingTarget = null), restored.initialRestore("one", maxScroll = 500))
        assertEquals(CardStackScrollRestore(scrollY = 200, pendingTarget = null), restored.initialRestore("two", maxScroll = 500))
        assertEquals(CardStackScrollRestore(scrollY = 300, pendingTarget = null), restored.initialRestore("three", maxScroll = 500))
    }
}
