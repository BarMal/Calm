package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class AppStackRenderPlannerTest {
    private val planner = AppStackRenderPlanner()

    @Test
    fun personalAppsPageRendersInitialBatchBeforeDeferringTheRest() {
        val personalApps = (1..40).map { index -> app("personal.$index", isWorkProfile = false) }

        val plan = planner.plan(personalApps, initialCardCount = 16)

        assertEquals(personalApps.take(16), plan.initialApps)
        assertEquals(personalApps.drop(16), plan.deferredApps)
    }

    @Test
    fun smallPersonalAppsPageRendersAllAppsImmediately() {
        val personalApps = (1..8).map { index -> app("personal.$index", isWorkProfile = false) }

        val plan = planner.plan(personalApps, initialCardCount = 16)

        assertEquals(personalApps, plan.initialApps)
        assertEquals(emptyList<AppEntry>(), plan.deferredApps)
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
}
