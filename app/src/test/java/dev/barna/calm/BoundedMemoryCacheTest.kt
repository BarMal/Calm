package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedMemoryCacheTest {
    @Test
    fun evictsLeastRecentlyUsedEntryWhenCapacityIsExceeded() {
        val cache = BoundedMemoryCache<String, Int>(maxEntries = 2)

        cache["first"] = 1
        cache["second"] = 2
        assertEquals(1, cache["first"])

        cache["third"] = 3

        assertEquals(1, cache["first"])
        assertNull(cache["second"])
        assertEquals(3, cache["third"])
        assertEquals(2, cache.size())
    }

    @Test
    fun getOrPutStoresOnlyNonNullValues() {
        val cache = BoundedMemoryCache<String, Int>(maxEntries = 2)

        val missing = cache.getOrPut("missing") { null }
        val present = cache.getOrPut("present") { 7 }

        assertNull(missing)
        assertEquals(7, present)
        assertEquals(1, cache.size())
    }
}
