package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CardStackStateKeyTest {
    @Test
    fun appLibraryKeyIsStableForPageScopeAndQuery() {
        assertEquals(
            "app-library:apps:PERSONAL:mail",
            CardStackStateKey.appLibrary("apps", AppLibraryScope.PERSONAL, "mail"),
        )
    }

    @Test
    fun appLibraryKeySeparatesSearchQueries() {
        assertNotEquals(
            CardStackStateKey.appLibrary("apps", AppLibraryScope.ALL, ""),
            CardStackStateKey.appLibrary("apps", AppLibraryScope.ALL, "mail"),
        )
    }

    @Test
    fun appEntryKeyUsesStableAppIdentities() {
        val apps = listOf(
            app("pkg.mail", "work:mail"),
            app("pkg.chat", "personal:chat"),
        )

        assertEquals("pinned:work:mail|personal:chat", CardStackStateKey.appEntries("pinned", apps))
    }

    @Test
    fun notificationKeyUsesChapterIdentity() {
        assertEquals("notifications:chat:user:10", CardStackStateKey.notifications(chapter("chat:user:10")))
    }

    private fun app(packageName: String, identityKey: String): AppEntry {
        return AppEntry(packageName = packageName, label = packageName, hueColor = 0, identityKey = identityKey)
    }

    private fun chapter(identityKey: String): AppChapter {
        return AppChapter(
            packageName = "chat",
            label = "Chat",
            notifications = emptyList(),
            launchable = true,
            hueColor = 0,
            identityKey = identityKey,
        )
    }
}
