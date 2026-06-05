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
    }

    @Test
    fun notificationsUsesChapterIdentity() {
        val chapter = AppChapter("pkg.name", "Messages", emptyList(), true, 0xff123456.toInt())

        val page = ChapterPage.notifications(chapter, "II")

        assertEquals("pkg.name", page.key)
        assertEquals("II", page.marker)
        assertEquals("Messages", page.title)
        assertSame(chapter, page.chapter)
    }

    @Test
    fun settingsUsesProvidedKeyAndMarker() {
        val page = ChapterPage.settings("settings-key", "V")

        assertEquals("settings-key", page.key)
        assertEquals("V", page.marker)
        assertEquals("Settings", page.title)
        assertNull(page.chapter)
    }
}
