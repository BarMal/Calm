package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterPagePrewarmPlannerTest {
    private val planner = ChapterPagePrewarmPlanner()

    @Test
    fun prewarmsNearestPagesFirst() {
        assertEquals(listOf(2, 4, 1, 5, 0, 6), planner.positions(pageCount = 7, initialPage = 3))
    }

    @Test
    fun doesNotPrewarmInitialPage() {
        assertEquals(listOf(1, 2), planner.positions(pageCount = 3, initialPage = 0))
    }

    @Test
    fun clampsInvalidInitialPage() {
        assertEquals(listOf(1, 0), planner.positions(pageCount = 3, initialPage = 8))
        assertEquals(listOf(1, 2), planner.positions(pageCount = 3, initialPage = -2))
    }

    @Test
    fun ignoresEmptyAndSinglePageSets() {
        assertEquals(emptyList<Int>(), planner.positions(pageCount = 0, initialPage = 0))
        assertEquals(emptyList<Int>(), planner.positions(pageCount = 1, initialPage = 0))
    }
}
