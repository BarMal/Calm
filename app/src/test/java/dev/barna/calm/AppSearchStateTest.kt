package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSearchStateTest {
    private val state = AppSearchState(AppLibraryPageModelFactory())

    @Test
    fun buildModelUsesProvidedAppEntriesNotAlternativeState() {
        val provided = listOf(app("provided.pkg", "Provided"))
        val stale = listOf(app("stale.pkg", "Stale"))
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = state.buildModel(page, provided)

        assertEquals(provided, model.apps)
        assertEquals(false, stale == model.apps)
    }

    @Test
    fun buildModelRespectsActiveQuery() {
        val foo = app("com.foo", "Foo")
        val bar = app("com.bar", "Bar")
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        state.updateQuery(AppLibraryScope.ALL, "foo")
        val model = state.buildModel(page, listOf(foo, bar))

        assertEquals(listOf(foo), model.apps)
    }

    @Test
    fun queriesAreIsolatedByScope() {
        val personal = app("personal.pkg", "Foo")
        val work = app("work.pkg", "Bar", isWorkProfile = true)
        val personalPage = ChapterPage.personalApps(CalmTheme.PERSONAL_APP_LIBRARY_KEY, "I")
        val workPage = ChapterPage.workApps(CalmTheme.WORK_APP_LIBRARY_KEY, "I")

        state.updateQuery(AppLibraryScope.PERSONAL, "foo")
        val personalModel = state.buildModel(personalPage, listOf(personal, work))
        val workModel = state.buildModel(workPage, listOf(personal, work))

        assertEquals(listOf(personal), personalModel.apps)
        assertEquals(listOf(work), workModel.apps)
    }

    @Test
    fun clearResetsAllScopes() {
        val foo = app("com.foo", "Foo")
        val bar = app("com.bar", "Bar")
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        state.updateQuery(AppLibraryScope.ALL, "foo")
        state.clear()
        val model = state.buildModel(page, listOf(foo, bar))

        assertEquals(listOf(foo, bar), model.apps)
    }

    private fun app(packageName: String, label: String, isWorkProfile: Boolean = false): AppEntry {
        return AppEntry(packageName = packageName, label = label, hueColor = 0xff123456.toInt(), isWorkProfile = isWorkProfile)
    }
}
