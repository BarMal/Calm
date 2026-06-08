package dev.barna.calm

class CardStackLayoutCache(private val maxSize: Int = 48) {
    private val topPaddings = LinkedHashMap<String, Int>()

    fun remember(stackKey: String, topPadding: Int) {
        topPaddings[stackKey] = topPadding
        prune()
    }

    fun rememberedTopPadding(stackKey: String): Int? = topPaddings[stackKey]

    private fun prune() {
        while (topPaddings.size > maxSize) {
            topPaddings.keys.firstOrNull()?.let(topPaddings::remove)
        }
    }
}
