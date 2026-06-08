package dev.barna.calm

import java.util.EnumMap

class AppSearchState(
    private val factory: AppLibraryPageModelFactory,
) {
    private val queries = EnumMap<AppLibraryScope, String>(AppLibraryScope::class.java)

    fun queryFor(scope: AppLibraryScope): String = queries[scope].orEmpty()

    fun buildModel(
        page: ChapterPage,
        appEntries: List<AppEntry>,
        loading: Boolean = false,
    ): AppLibraryPageModel {
        val scope = page.appScope ?: AppLibraryScope.ALL
        return factory.create(page, appEntries, queryFor(scope), loading)
    }

    fun updateQuery(scope: AppLibraryScope, query: String) {
        if (query.isBlank()) queries.remove(scope) else queries[scope] = query
    }

    fun clear() = queries.clear()
}
