package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLibraryFilterTest {
    private val filter = AppLibraryFilter()

    @Test
    fun filtersPersonalAndWorkScopes() {
        val personal = app("personal.pkg", "Personal app")
        val work = app("work.pkg", "Work app", isWorkProfile = true)

        assertEquals(listOf(personal, work), filter.filter(listOf(personal, work), AppLibraryScope.ALL, ""))
        assertEquals(listOf(personal), filter.filter(listOf(personal, work), AppLibraryScope.PERSONAL, ""))
        assertEquals(listOf(work), filter.filter(listOf(personal, work), AppLibraryScope.WORK, ""))
    }

    @Test
    fun searchesLabelPackageAndProfileLabel() {
        val mail = app("com.example.mail", "Mail", profileLabel = "Personal")
        val chat = app("com.example.chat", "Messages", profileLabel = "Work")

        assertEquals(listOf(mail), filter.filter(listOf(mail, chat), AppLibraryScope.ALL, "mail"))
        assertEquals(listOf(chat), filter.filter(listOf(mail, chat), AppLibraryScope.ALL, "example.chat"))
        assertEquals(listOf(chat), filter.filter(listOf(mail, chat), AppLibraryScope.ALL, "work"))
    }

    @Test
    fun personalAndWorkSubtitlesAreSuppressed() {
        assertEquals("Search, launch, and pin apps into the launcher spine.", filter.subtitle(AppLibraryScope.ALL))
        assertNull(filter.subtitle(AppLibraryScope.PERSONAL))
        assertNull(filter.subtitle(AppLibraryScope.WORK))
    }

    @Test
    fun emptyMessagesMatchScopeAndSearchState() {
        assertEquals("No apps match that search.", filter.emptyMessage(AppLibraryScope.ALL, "camera"))
        assertEquals("No apps are available.", filter.emptyMessage(AppLibraryScope.ALL, ""))
        assertEquals("No personal apps are available.", filter.emptyMessage(AppLibraryScope.PERSONAL, ""))
        assertEquals("No work apps are available.", filter.emptyMessage(AppLibraryScope.WORK, ""))
    }

    @Test
    fun loadingMessagesMatchScope() {
        assertEquals("Loading apps...", filter.loadingMessage(AppLibraryScope.ALL))
        assertEquals("Loading personal apps...", filter.loadingMessage(AppLibraryScope.PERSONAL))
        assertEquals("Loading work apps...", filter.loadingMessage(AppLibraryScope.WORK))
    }

    @Test
    fun groupByCategoryGroupsAppsIntoEnabledCategories() {
        val mail = app("com.mail", "Mail", identityKey = "mail")
        val chat = app("com.chat", "Chat", identityKey = "chat")
        val camera = app("com.camera", "Camera", identityKey = "camera")
        val comms = AppCategory("communications", "Communications")
        val media = AppCategory("media", "Media")
        val assignments = mapOf("mail" to listOf("communications"), "chat" to listOf("communications"), "camera" to listOf("media"))

        val groups = filter.groupByCategory(listOf(mail, chat, camera), listOf(comms, media), assignments, AppLibraryScope.ALL, "")

        assertEquals(2, groups.size)
        assertEquals(comms, groups[0].category)
        assertEquals(listOf(mail, chat), groups[0].apps)
        assertEquals(media, groups[1].category)
        assertEquals(listOf(camera), groups[1].apps)
    }

    @Test
    fun groupByCategorySkipsEmptyCategories() {
        val mail = app("com.mail", "Mail", identityKey = "mail")
        val comms = AppCategory("communications", "Communications")
        val media = AppCategory("media", "Media")
        val assignments = mapOf("mail" to listOf("communications"))

        val groups = filter.groupByCategory(listOf(mail), listOf(comms, media), assignments, AppLibraryScope.ALL, "")

        assertEquals(1, groups.size)
        assertEquals(comms, groups[0].category)
    }

    @Test
    fun groupByCategorySkipsDisabledCategories() {
        val mail = app("com.mail", "Mail", identityKey = "mail")
        val comms = AppCategory("communications", "Communications", enabled = false)
        val assignments = mapOf("mail" to listOf("communications"))

        val groups = filter.groupByCategory(listOf(mail), listOf(comms), assignments, AppLibraryScope.ALL, "")

        assertTrue(groups.isEmpty())
    }

    @Test
    fun groupByCategoryAppliesSearchQuery() {
        val mail = app("com.mail", "Mail", identityKey = "mail")
        val chat = app("com.chat", "Chat", identityKey = "chat")
        val comms = AppCategory("communications", "Communications")
        val assignments = mapOf("mail" to listOf("communications"), "chat" to listOf("communications"))

        val groups = filter.groupByCategory(listOf(mail, chat), listOf(comms), assignments, AppLibraryScope.ALL, "mail")

        assertEquals(1, groups.size)
        assertEquals(listOf(mail), groups[0].apps)
    }

    private fun app(
        packageName: String,
        label: String,
        profileLabel: String = "",
        isWorkProfile: Boolean = false,
        identityKey: String = packageName,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = 0xff123456.toInt(),
            profileLabel = profileLabel,
            isWorkProfile = isWorkProfile,
            identityKey = identityKey,
        )
    }
}
