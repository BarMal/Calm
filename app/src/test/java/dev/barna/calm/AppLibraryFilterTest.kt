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

    private fun app(
        packageName: String,
        label: String,
        profileLabel: String = "",
        isWorkProfile: Boolean = false,
    ): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = label,
            hueColor = 0xff123456.toInt(),
            profileLabel = profileLabel,
            isWorkProfile = isWorkProfile,
        )
    }
}
