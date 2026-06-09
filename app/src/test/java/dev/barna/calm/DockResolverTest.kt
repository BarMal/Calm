package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockResolverTest {
    private val resolver = DockResolver()

    private fun app(
        pkg: String,
        label: String = pkg,
        identityKey: String = pkg,
    ) = AppEntry(packageName = pkg, label = label, hueColor = 0, identityKey = identityKey)

    @Test
    fun emptyKeysReturnsEmpty() {
        val apps = listOf(app("com.a"))
        assertTrue(resolver.resolve(apps, emptyList()).isEmpty())
    }

    @Test
    fun emptyAppsReturnsEmpty() {
        assertTrue(resolver.resolve(emptyList(), listOf("com.a")).isEmpty())
    }

    @Test
    fun resolvesAppByPackageName() {
        val a = app("com.a")
        val result = resolver.resolve(listOf(a), listOf("com.a"))
        assertEquals(listOf(a), result)
    }

    @Test
    fun resolvesAppByIdentityKey() {
        val a = app("com.a", identityKey = "profile:com.a")
        val result = resolver.resolve(listOf(a), listOf("profile:com.a"))
        assertEquals(listOf(a), result)
    }

    @Test
    fun preservesDockOrder() {
        val a = app("com.a")
        val b = app("com.b")
        val c = app("com.c")
        val apps = listOf(c, a, b)
        val result = resolver.resolve(apps, listOf("com.a", "com.b", "com.c"))
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun uninstalledKeyIsSkipped() {
        val a = app("com.a")
        val result = resolver.resolve(listOf(a), listOf("com.a", "com.missing"))
        assertEquals(listOf(a), result)
    }

    @Test
    fun multipleAppsResolvedCorrectly() {
        val apps = listOf(app("com.a"), app("com.b"), app("com.c"))
        val result = resolver.resolve(apps, listOf("com.c", "com.a"))
        assertEquals(listOf(app("com.c"), app("com.a")), result)
    }
}
