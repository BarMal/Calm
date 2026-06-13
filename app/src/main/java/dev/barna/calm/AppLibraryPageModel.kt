package dev.barna.calm

data class AppLibraryCategoryContext(
    val categories: List<AppCategory>,
    val assignments: Map<String, List<String>>,
    val groupingEnabled: Boolean,
)

class AppLibraryPageModelFactory(
    private val filter: AppLibraryFilter = AppLibraryFilter(),
    private val categoryContext: (() -> AppLibraryCategoryContext?)? = null,
) {
    fun create(
        page: ChapterPage,
        appEntries: List<AppEntry>,
        query: String,
        loading: Boolean = false,
    ): AppLibraryPageModel {
        val scope = page.appScope ?: AppLibraryScope.ALL
        val filteredApps = filter.filter(appEntries, scope, query)
        val context = categoryContext?.invoke()
        val categoryGroups = if (context != null && context.groupingEnabled && context.assignments.isNotEmpty()) {
            filter.groupByCategory(appEntries, context.categories, context.assignments, scope, query)
        } else {
            null
        }
        return AppLibraryPageModel(
            key = page.key,
            title = page.title,
            scope = scope,
            query = query,
            subtitle = filter.subtitle(scope),
            apps = filteredApps,
            categoryGroups = categoryGroups,
            emptyMessage = if (loading && filteredApps.isEmpty()) {
                filter.loadingMessage(scope)
            } else {
                filter.emptyMessage(scope, query)
            },
            loading = loading,
        )
    }
}

data class AppLibraryPageModel(
    val key: String,
    val title: String,
    val scope: AppLibraryScope,
    val query: String,
    val subtitle: String?,
    val apps: List<AppEntry>,
    val emptyMessage: String,
    val loading: Boolean,
    val categoryGroups: List<AppCategoryGroup>? = null,
)
