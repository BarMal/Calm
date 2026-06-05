package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTest {
    @Test
    fun hiddenAppsMatchProfileAwareIdentityKeys() {
        val personal = app("com.example.mail", AppIdentity.launcherKey("com.example.mail", "Main", 0))
        val work = app("com.example.mail", AppIdentity.launcherKey("com.example.mail", "Main", 10))

        assertTrue(AppVisibility.isHidden(work, setOf(work.identityKey)))
        assertFalse(AppVisibility.isHidden(personal, setOf(work.identityKey)))
    }

    @Test
    fun hiddenAppsStillMatchLegacyPackageKeys() {
        val app = app("com.example.mail", AppIdentity.launcherKey("com.example.mail", "Main", 10))

        assertTrue(AppVisibility.isHidden(app, setOf("com.example.mail")))
    }

    @Test
    fun visibleAppsFiltersOnlyHiddenEntries() {
        val visible = app("com.example.visible", AppIdentity.launcherKey("com.example.visible", "Main", 0))
        val hidden = app("com.example.hidden", AppIdentity.launcherKey("com.example.hidden", "Main", 0))

        assertEquals(listOf(visible), AppVisibility.visibleApps(listOf(visible, hidden), setOf(hidden.identityKey)))
    }

    private fun app(packageName: String, identityKey: String): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = packageName,
            hueColor = 0,
            identityKey = identityKey,
        )
    }
}
