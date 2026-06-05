package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun launcherKeysSeparateSamePackageAcrossProfiles() {
        val personal = AppIdentity.launcherKey("com.example.mail", "Main", 0)
        val work = AppIdentity.launcherKey("com.example.mail", "Main", 10)

        assertNotEquals(personal, work)
    }

    @Test
    fun notificationKeysIgnoreLauncherClassButKeepProfile() {
        val first = AppIdentity("com.example.mail", "Main", 10).notificationSourceKey
        val second = AppIdentity("com.example.mail", "Alias", 10).notificationSourceKey

        assertEquals(first, second)
    }

    @Test
    fun legacyPackageKeysDecodeAsPackageOnlyIdentity() {
        val identity = AppIdentity.decode("com.example.mail")

        assertEquals("com.example.mail", identity.packageName)
        assertEquals(AppIdentity.LEGACY_USER_SERIAL, identity.userSerial)
        assertEquals("", identity.className)
    }
}
