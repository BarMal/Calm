package dev.barna.calm

interface PinnedChapterRepository {
    fun getPinnedPackages(): Set<String>
    fun pin(packageName: String)
    fun unpin(packageName: String)
    fun isPinned(packageName: String): Boolean
}

class InMemoryPinnedChapterRepository : PinnedChapterRepository {
    private val packages = LinkedHashSet<String>()

    override fun getPinnedPackages(): Set<String> = packages.toSet()

    override fun pin(packageName: String) {
        packages.add(packageName)
    }

    override fun unpin(packageName: String) {
        packages.remove(packageName)
    }

    override fun isPinned(packageName: String): Boolean = packageName in packages
}
