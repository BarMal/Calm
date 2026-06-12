package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ChapterPageTest {
    @Test
    fun overviewUsesProvidedKeyAndDefaultLabel() {
        val page = ChapterPage.overview("overview-key")

        assertEquals("overview-key", page.key)
        assertEquals("I", page.marker)
        assertEquals("Overview", page.title)
        assertNull(page.chapter)
        assertNull(page.appScope)
        assertNull(page.classicPage)
    }

    @Test
    fun notificationsUsesChapterIdentity() {
        val chapter = AppChapter("pkg.name", "Messages", emptyList(), true, 0xff123456.toInt())

        val page = ChapterPage.notifications(chapter, "II")

        assertEquals("pkg.name", page.key)
        assertEquals("II", page.marker)
        assertEquals("Messages", page.title)
        assertSame(chapter, page.chapter)
        assertNull(page.appScope)
        assertNull(page.classicPage)
    }

    @Test
    fun splitAppPagesCarryTheirOwnScopes() {
        val personal = ChapterPage.personalApps("personal-key", "I")
        val work = ChapterPage.workApps("work-key", "II")

        assertEquals("Personal apps", personal.title)
        assertEquals(AppLibraryScope.PERSONAL, personal.appScope)
        assertEquals("Work apps", work.title)
        assertEquals(AppLibraryScope.WORK, work.appScope)
    }

    @Test
    fun agendaUsesProvidedKeyAndMarker() {
        val page = ChapterPage.agenda("agenda-key", "III")

        assertEquals("agenda-key", page.key)
        assertEquals("III", page.marker)
        assertEquals("Agenda", page.title)
        assertNull(page.chapter)
        assertNull(page.appScope)
        assertNull(page.classicPage)
    }

    @Test
    fun alarmsUsesProvidedKeyAndMarker() {
        val page = ChapterPage.alarms("alarms-key", "IV")

        assertEquals("alarms-key", page.key)
        assertEquals("IV", page.marker)
        assertEquals("Alarms", page.title)
        assertNull(page.chapter)
        assertNull(page.appScope)
        assertNull(page.classicPage)
    }

    @Test
    fun classicPageUsesDefinitionKeyAndTitle() {
        val definition = ClassicLauncherPageDefinition(id = "classic-1", title = "Classic")

        val page = ChapterPage.classic(definition, "III")

        assertEquals("classic:classic-1", page.key)
        assertEquals("III", page.marker)
        assertEquals("Classic", page.title)
        assertNull(page.chapter)
        assertNull(page.appScope)
        assertSame(definition, page.classicPage)
    }

}
