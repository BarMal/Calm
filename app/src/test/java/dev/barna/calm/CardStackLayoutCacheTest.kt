package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardStackLayoutCacheTest {
    @Test
    fun unknownKeyReturnsNull() {
        val cache = CardStackLayoutCache(maxSize = 4)

        assertNull(cache.rememberedTopPadding("nonexistent"))
    }

    @Test
    fun rememberedPaddingIsReturned() {
        val cache = CardStackLayoutCache(maxSize = 4)
        cache.remember("apps:personal", 210)

        assertEquals(210, cache.rememberedTopPadding("apps:personal"))
    }

    @Test
    fun rememberingNewValueOverwritesOld() {
        val cache = CardStackLayoutCache(maxSize = 4)
        cache.remember("apps:personal", 210)
        cache.remember("apps:personal", 180)

        assertEquals(180, cache.rememberedTopPadding("apps:personal"))
    }

    @Test
    fun lruEvictionRemovesOldestEntryWhenMaxSizeExceeded() {
        val cache = CardStackLayoutCache(maxSize = 2)
        cache.remember("one", 100)
        cache.remember("two", 200)
        cache.remember("three", 300)

        assertNull(cache.rememberedTopPadding("one"))
        assertEquals(200, cache.rememberedTopPadding("two"))
        assertEquals(300, cache.rememberedTopPadding("three"))
    }

    @Test
    fun multipleKeysCoexistUpToMaxSize() {
        val cache = CardStackLayoutCache(maxSize = 4)
        cache.remember("apps", 100)
        cache.remember("notifications", 150)
        cache.remember("recent", 200)
        cache.remember("media", 250)

        assertEquals(100, cache.rememberedTopPadding("apps"))
        assertEquals(150, cache.rememberedTopPadding("notifications"))
        assertEquals(200, cache.rememberedTopPadding("recent"))
        assertEquals(250, cache.rememberedTopPadding("media"))
    }

    @Test
    fun differentKeysAreIndependent() {
        val cache = CardStackLayoutCache(maxSize = 4)
        cache.remember("apps:personal", 210)
        cache.remember("apps:work", 180)

        assertEquals(210, cache.rememberedTopPadding("apps:personal"))
        assertEquals(180, cache.rememberedTopPadding("apps:work"))
    }
}
