package dev.barna.calm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherPreferencesFactoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("plain_migration_test", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("secure_migration_test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun migrationCopiesPlaintextValuesAndClearsLegacyStore() {
        val plaintext = context.getSharedPreferences("plain_migration_test", Context.MODE_PRIVATE)
        val secure = context.getSharedPreferences("secure_migration_test", Context.MODE_PRIVATE)
        plaintext.edit()
            .putBoolean("dock_enabled", true)
            .putInt("card_corner_radius", 24)
            .putString("pinned_page", "apps")
            .putStringSet("hidden_app_keys", setOf("user:com.example.hidden"))
            .commit()

        val migrated = LauncherPreferencesFactory.migratePlaintextPreferences(plaintext, secure)

        assertTrue(migrated)
        assertTrue(plaintext.all.isEmpty())
        assertEquals(true, secure.getBoolean("dock_enabled", false))
        assertEquals(24, secure.getInt("card_corner_radius", 0))
        assertEquals("apps", secure.getString("pinned_page", null))
        assertEquals(setOf("user:com.example.hidden"), secure.getStringSet("hidden_app_keys", emptySet()))
    }

    @Test
    fun migrationDoesNotOverwriteSecureValues() {
        val plaintext = context.getSharedPreferences("plain_migration_test", Context.MODE_PRIVATE)
        val secure = context.getSharedPreferences("secure_migration_test", Context.MODE_PRIVATE)
        plaintext.edit()
            .putString("pinned_page", "old")
            .putString("last_page_key", "overview")
            .commit()
        secure.edit()
            .putString("pinned_page", "secure")
            .commit()

        LauncherPreferencesFactory.migratePlaintextPreferences(plaintext, secure)

        assertEquals("secure", secure.getString("pinned_page", null))
        assertEquals("overview", secure.getString("last_page_key", null))
        assertTrue(plaintext.all.isEmpty())
    }
}
