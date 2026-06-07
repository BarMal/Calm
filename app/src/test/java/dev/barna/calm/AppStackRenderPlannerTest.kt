package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStackRenderPlannerTest {
    private val planner = AppStackRenderPlanner()

    @Test
    fun personalAppsPageRendersOnlyVisibleCardsBeforeDeferringTheRest() {
        val personalApps = (1..40).map { index -> app("personal.$index", isWorkProfile = false) }

        val plan = planner.plan(personalApps, tuning = tuning(visibleCards = 4))

        assertEquals(personalApps.take(4), plan.initialApps)
        assertEquals(personalApps.drop(4), plan.deferredApps)
    }

    @Test
    fun smallPersonalAppsPageRendersAllAppsImmediately() {
        val personalApps = (1..4).map { index -> app("personal.$index", isWorkProfile = false) }

        val plan = planner.plan(personalApps, tuning = tuning(visibleCards = 5))

        assertEquals(personalApps, plan.initialApps)
        assertEquals(emptyList<AppEntry>(), plan.deferredApps)
    }

    @Test
    fun initialRenderCountComesFromVisibleCardSetting() {
        assertEquals(1, planner.initialCardCount(tuning(visibleCards = 1)))
        assertEquals(3, planner.initialCardCount(tuning(visibleCards = 3)))
        assertEquals(5, planner.initialCardCount(tuning(visibleCards = 5)))
    }

    @Test
    fun deferredAppsAreSplitIntoSmallBatches() {
        val personalApps = (1..33).map { index -> app("personal.$index", isWorkProfile = false) }

        val batches = planner.batches(personalApps, batchSize = 16)

        assertEquals(listOf(16, 16, 1), batches.map { it.size })
    }

    private fun app(packageName: String, isWorkProfile: Boolean): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = packageName,
            hueColor = 0,
            isWorkProfile = isWorkProfile,
        )
    }

    private fun tuning(visibleCards: Int): CardStackTuning {
        return CardStackTuning(
            curve = 50,
            horizontalCurve = 100,
            arcWidth = 50,
            aboveFocusCards = 2,
            rotation = 0,
            verticalSpacing = 50,
            visibleCards = visibleCards,
        )
    }
}
