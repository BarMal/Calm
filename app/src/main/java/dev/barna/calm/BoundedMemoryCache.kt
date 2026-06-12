package dev.barna.calm

import java.util.LinkedHashMap

internal class BoundedMemoryCache<K, V>(
    private val maxEntries: Int,
) {
    private val values = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    @Synchronized
    operator fun get(key: K): V? = values[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        values[key] = value
    }

    @Synchronized
    fun getOrPut(key: K, defaultValue: () -> V?): V? {
        values[key]?.let { return it }
        val value = defaultValue() ?: return null
        values[key] = value
        return value
    }

    @Synchronized
    fun clear() {
        values.clear()
    }

    @Synchronized
    fun size(): Int = values.size
}
