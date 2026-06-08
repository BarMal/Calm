package dev.barna.calm

class AppLibraryFilter {
    fun filter(
        appEntries: List<AppEntry>,
        scope: AppLibraryScope,
        query: String,
    ): List<AppEntry> {
        val normalizedQuery = query.trim()
        return appEntries.filter { app ->
            when (scope) {
                AppLibraryScope.ALL -> true
                AppLibraryScope.PERSONAL -> !app.isWorkProfile
                AppLibraryScope.WORK -> app.isWorkProfile
            }
        }.filter { app ->
            normalizedQuery.isBlank() ||
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true) ||
                app.profileLabel.contains(normalizedQuery, ignoreCase = true)
        }
    }

    fun emptyMessage(scope: AppLibraryScope, query: String): String {
        if (query.isNotBlank()) return "No apps match that search."
        return when (scope) {
            AppLibraryScope.ALL -> "No apps are available."
            AppLibraryScope.PERSONAL -> "No personal apps are available."
            AppLibraryScope.WORK -> "No work apps are available."
        }
    }

    fun loadingMessage(scope: AppLibraryScope): String {
        return when (scope) {
            AppLibraryScope.ALL -> "Loading apps..."
            AppLibraryScope.PERSONAL -> "Loading personal apps..."
            AppLibraryScope.WORK -> "Loading work apps..."
        }
    }

    fun subtitle(scope: AppLibraryScope): String? {
        return when (scope) {
            AppLibraryScope.ALL -> "Search, launch, and pin apps into the launcher spine."
            AppLibraryScope.PERSONAL,
            AppLibraryScope.WORK -> null
        }
    }

    fun filterExcludingFolderMembers(
        appEntries: List<AppEntry>,
        folders: List<AppFolder>,
        scope: AppLibraryScope,
        query: String,
    ): List<AppEntry> {
        val folderPackages = folders.flatMapTo(HashSet()) { it.packageNames }
        return filter(appEntries, scope, query).filter { it.packageName !in folderPackages }
    }

    fun filterFolderContents(
        appEntries: List<AppEntry>,
        folder: AppFolder,
        query: String,
    ): List<AppEntry> {
        val normalizedQuery = query.trim()
        return appEntries.filter { app ->
            folder.contains(app.packageName)
        }.filter { app ->
            normalizedQuery.isBlank() ||
                app.label.contains(normalizedQuery, ignoreCase = true) ||
                app.packageName.contains(normalizedQuery, ignoreCase = true) ||
                app.profileLabel.contains(normalizedQuery, ignoreCase = true)
        }
    }
}
