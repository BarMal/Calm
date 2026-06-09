package dev.barna.calm

interface DockRepository {
    val maxSize: Int
    fun getKeys(): List<String>
    fun add(identityKey: String): Boolean
    fun remove(identityKey: String)
    fun move(identityKey: String, toIndex: Int)
    fun contains(identityKey: String): Boolean
}

class InMemoryDockRepository(override val maxSize: Int = 6) : DockRepository {
    private val keys = ArrayList<String>()

    override fun getKeys(): List<String> = keys.toList()

    override fun add(identityKey: String): Boolean {
        if (keys.size >= maxSize || identityKey in keys) return false
        keys.add(identityKey)
        return true
    }

    override fun remove(identityKey: String) {
        keys.remove(identityKey)
    }

    override fun move(identityKey: String, toIndex: Int) {
        val current = keys.indexOf(identityKey)
        if (current == -1) return
        keys.removeAt(current)
        keys.add(toIndex.coerceIn(0, keys.size), identityKey)
    }

    override fun contains(identityKey: String): Boolean = identityKey in keys
}
