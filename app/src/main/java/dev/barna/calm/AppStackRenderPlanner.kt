package dev.barna.calm

class AppStackRenderPlanner {
    fun plan(apps: List<AppEntry>, initialCardCount: Int): AppStackRenderPlan {
        val boundedInitialCount = initialCardCount.coerceAtLeast(1)
        return AppStackRenderPlan(
            initialApps = apps.take(boundedInitialCount),
            deferredApps = apps.drop(boundedInitialCount),
        )
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
