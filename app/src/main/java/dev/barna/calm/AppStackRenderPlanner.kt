package dev.barna.calm

class AppStackRenderPlanner {
    fun plan(apps: List<AppEntry>, tuning: CardStackTuning): AppStackRenderPlan {
        val boundedInitialCount = initialCardCount(tuning)
        return AppStackRenderPlan(
            initialApps = apps.take(boundedInitialCount),
            deferredApps = apps.drop(boundedInitialCount),
        )
    }

    fun initialCardCount(tuning: CardStackTuning): Int {
        return tuning.visibleCards.coerceAtLeast(1)
    }

    fun batches(apps: List<AppEntry>, batchSize: Int): List<List<AppEntry>> {
        val boundedBatchSize = batchSize.coerceAtLeast(1)
        return apps.chunked(boundedBatchSize)
    }
}

data class AppStackRenderPlan(
    val initialApps: List<AppEntry>,
    val deferredApps: List<AppEntry>,
)
