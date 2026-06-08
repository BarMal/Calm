package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFolderFilterTest {
    private val filter = AppLibraryFilter()

    private fun app(pkg: String, label: String = pkg, work: Boolean = false) = AppEntry(
        packageName = pkg,
        label = label,
        hueColor = 0,
        isWorkProfile = work,
    )

    private fun folder(id: String, vararg packages: String) =
        AppFolder(id = id, name = id, packageNames = setOf(*packages))

    // --- filterExcludingFolderMembers ---

    @Test
    fun noFoldersReturnsSameApps() {
        val apps = listOf(app("com.a"), app("com.b"))
        val result = filter.filterExcludingFolderMembers(apps, emptyList(), AppLibraryScope.ALL, "")
        assertEquals(apps, result)
    }

    @Test
    fun folderMembersExcludedFromMainList() {
        val apps = listOf(app("com.a"), app("com.b"), app("com.c"))
        val folders = listOf(folder("f1", "com.b"))
        val result = filter.filterExcludingFolderMembers(apps, folders, AppLibraryScope.ALL, "")
        assertEquals(listOf(app("com.a"), app("com.c")), result)
    }

    @Test
    fun appsInMultipleFoldersAllExcluded() {
        val apps = listOf(app("com.a"), app("com.b"), app("com.c"), app("com.d"))
        val folders = listOf(folder("f1", "com.a", "com.b"), folder("f2", "com.d"))
        val result = filter.filterExcludingFolderMembers(apps, folders, AppLibraryScope.ALL, "")
        assertEquals(listOf(app("com.c")), result)
    }

    @Test
    fun scopeFilterAppliedAlongsideFolderExclusion() {
        val apps = listOf(app("com.a"), app("com.b", work = true), app("com.c"))
        val folders = listOf(folder("f1", "com.c"))
        val result = filter.filterExcludingFolderMembers(apps, folders, AppLibraryScope.PERSONAL, "")
        assertEquals(listOf(app("com.a")), result)
    }

    @Test
    fun queryFilterAppliedAlongsideFolderExclusion() {
        val apps = listOf(app("com.a", "Alpha"), app("com.b", "Beta"), app("com.c", "Gamma"))
        val folders = listOf(folder("f1", "com.b"))
        val result = filter.filterExcludingFolderMembers(apps, folders, AppLibraryScope.ALL, "al")
        assertEquals(listOf(app("com.a", "Alpha")), result)
    }

    @Test
    fun emptyListReturnsEmptyList() {
        val result = filter.filterExcludingFolderMembers(emptyList(), emptyList(), AppLibraryScope.ALL, "")
        assertTrue(result.isEmpty())
    }

    // --- filterFolderContents ---

    @Test
    fun folderContentsReturnsOnlyFolderMembers() {
        val apps = listOf(app("com.a"), app("com.b"), app("com.c"))
        val folder = folder("f1", "com.a", "com.c")
        val result = filter.filterFolderContents(apps, folder, "")
        assertEquals(listOf(app("com.a"), app("com.c")), result)
    }

    @Test
    fun folderContentsQueryFiltersWithinFolder() {
        val apps = listOf(app("com.a", "Alpha"), app("com.b", "Beta"), app("com.c", "Almond"))
        val folder = folder("f1", "com.a", "com.c")
        val result = filter.filterFolderContents(apps, folder, "al")
        assertEquals(listOf(app("com.a", "Alpha"), app("com.c", "Almond")), result)
    }

    @Test
    fun folderContentsExcludesAppsNotInFolder() {
        val apps = listOf(app("com.a"), app("com.b"))
        val folder = folder("f1", "com.a")
        val result = filter.filterFolderContents(apps, folder, "")
        assertEquals(listOf(app("com.a")), result)
    }

    @Test
    fun folderContentsEmptyWhenNoMembersInstalled() {
        val apps = listOf(app("com.a"), app("com.b"))
        val folder = folder("f1", "com.x", "com.y")
        val result = filter.filterFolderContents(apps, folder, "")
        assertTrue(result.isEmpty())
    }

    @Test
    fun folderContentsPreservesInstalledAppOrder() {
        val apps = listOf(app("com.c"), app("com.a"), app("com.b"))
        val folder = folder("f1", "com.a", "com.b", "com.c")
        val result = filter.filterFolderContents(apps, folder, "")
        assertEquals(listOf(app("com.c"), app("com.a"), app("com.b")), result)
    }
}
