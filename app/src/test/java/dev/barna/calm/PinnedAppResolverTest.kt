package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedAppResolverTest {
    private val resolver = PinnedAppResolver()

    @Test
    fun resolvesEmptyPinnedKeysToEmptyList() {
        val app = app("pkg.one")

        assertEquals(emptyList<AppEntry>(), resolver.resolve(listOf(app), emptySet()))
    }

    @Test
    fun resolvesByIdentityKeyAndLegacyPackageName() {
        val first = app("pkg.one", identityKey = "profile/pkg.one")
        val second = app("pkg.two", identityKey = "profile/pkg.two")
        val third = app("pkg.three", identityKey = "profile/pkg.three")

        val result = resolver.resolve(
            listOf(first, second, third),
            setOf("profile/pkg.one", "pkg.two"),
        )

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun reportsPinnedStateByIdentityOrLegacyPackageName() {
        val app = app("pkg.one", identityKey = "profile/pkg.one")

        assertTrue(resolver.isPinned(app, setOf("profile/pkg.one")))
        assertTrue(resolver.isPinned(app, setOf("pkg.one")))
        assertFalse(resolver.isPinned(app, setOf("pkg.two")))
    }

    private fun app(packageName: String, identityKey: String = packageName): AppEntry {
        return AppEntry(
            packageName = packageName,
            label = packageName,
            hueColor = 0xff123456.toInt(),
            identityKey = identityKey,
        )
    }
}
