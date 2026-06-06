package dev.barna.calm

class AppLibraryPageModelFactory(
    private val filter: AppLibraryFilter = AppLibraryFilter(),
) {
    fun create(
        page: ChapterPage,
        appEntries: List<AppEntry>,
        query: String,
    ): AppLibraryPageModel {
        val scope = page.appScope ?: AppLibraryScope.ALL
        val filteredApps = filter.filter(appEntries, scope, query)
        return AppLibraryPageModel(
            key = page.key,
            title = page.title,
            scope = scope,
            query = query,
            subtitle = filter.subtitle(scope),
            apps = filteredApps,
            emptyMessage = filter.emptyMessage(scope, query),
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
)
