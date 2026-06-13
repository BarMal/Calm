package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppLibraryPageModelTest {
    private val factory = AppLibraryPageModelFactory()

    @Test
    fun combinedAppsPageKeepsSubtitleAndFiltersApps() {
        val mail = app("com.example.mail", "Mail")
        val camera = app("com.example.camera", "Camera")
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = factory.create(page, listOf(mail, camera), "mail")

        assertEquals(CalmTheme.APP_LIBRARY_KEY, model.key)
        assertEquals("Apps", model.title)
        assertEquals(AppLibraryScope.ALL, model.scope)
        assertEquals("mail", model.query)
        assertEquals("Search, launch, and pin apps into the launcher spine.", model.subtitle)
        assertEquals(listOf(mail), model.apps)
        assertEquals("No apps match that search.", model.emptyMessage)
    }

    @Test
    fun personalAppsPageSuppressesSubtitle() {
        val personal = app("personal.pkg", "Personal")
        val work = app("work.pkg", "Work", isWorkProfile = true)
        val page = ChapterPage.personalApps(CalmTheme.PERSONAL_APP_LIBRARY_KEY, "I")

        val model = factory.create(page, listOf(personal, work), "")

        assertEquals(AppLibraryScope.PERSONAL, model.scope)
        assertNull(model.subtitle)
        assertEquals(listOf(personal), model.apps)
        assertEquals("No personal apps are available.", model.emptyMessage)
    }

    @Test
    fun workAppsPageSuppressesSubtitle() {
        val personal = app("personal.pkg", "Personal")
        val work = app("work.pkg", "Work", isWorkProfile = true)
        val page = ChapterPage.workApps(CalmTheme.WORK_APP_LIBRARY_KEY, "I")

        val model = factory.create(page, listOf(personal, work), "")

        assertEquals(AppLibraryScope.WORK, model.scope)
        assertNull(model.subtitle)
        assertEquals(listOf(work), model.apps)
        assertEquals("No work apps are available.", model.emptyMessage)
    }

    @Test
    fun loadingEmptyPageUsesLoadingMessageInsteadOfEmptyMessage() {
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = factory.create(page, emptyList(), "", loading = true)

        assertEquals(emptyList<AppEntry>(), model.apps)
        assertEquals("Loading apps...", model.emptyMessage)
        assertEquals(true, model.loading)
    }

    @Test
    fun categoryGroupingIsNullWhenContextAbsent() {
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)
        val model = factory.create(page, listOf(app("com.mail", "Mail")), "")
        assertNull(model.categoryGroups)
    }

    @Test
    fun categoryGroupingIsNullWhenGroupingDisabled() {
        val context = AppLibraryCategoryContext(
            categories = listOf(AppCategory("communications", "Communications")),
            assignments = mapOf("com.mail" to listOf("communications")),
            groupingEnabled = false,
        )
        val groupingFactory = AppLibraryPageModelFactory(categoryContext = { context })
        val mail = app("com.mail", "Mail", identityKey = "com.mail")
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = groupingFactory.create(page, listOf(mail), "")

        assertNull(model.categoryGroups)
    }

    @Test
    fun categoryGroupingPopulatesGroupsWhenEnabled() {
        val comms = AppCategory("communications", "Communications")
        val context = AppLibraryCategoryContext(
            categories = listOf(comms),
            assignments = mapOf("com.mail" to listOf("communications")),
            groupingEnabled = true,
        )
        val groupingFactory = AppLibraryPageModelFactory(categoryContext = { context })
        val mail = app("com.mail", "Mail", identityKey = "com.mail")
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = groupingFactory.create(page, listOf(mail), "")

        assertNotNull(model.categoryGroups)
        assertEquals(1, model.categoryGroups!!.size)
        assertEquals(comms, model.categoryGroups!![0].category)
        assertEquals(listOf(mail), model.categoryGroups!![0].apps)
    }

    @Test
    fun categoryGroupingIsNullWhenAssignmentsEmpty() {
        val context = AppLibraryCategoryContext(
            categories = listOf(AppCategory("communications", "Communications")),
            assignments = emptyMap(),
            groupingEnabled = true,
        )
        val groupingFactory = AppLibraryPageModelFactory(categoryContext = { context })
        val page = ChapterPage.appLibrary(CalmTheme.APP_LIBRARY_KEY)

        val model = groupingFactory.create(page, listOf(app("com.mail", "Mail")), "")

        assertNull(model.categoryGroups)
    }

    private fun app(
        packageName: String,
        label: String,
        isWorkProfile: Boolean = false,
        identityKey: String = packageName,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = 0xff123456.toInt(),
            isWorkProfile = isWorkProfile,
            identityKey = identityKey,
        )
    }
}
