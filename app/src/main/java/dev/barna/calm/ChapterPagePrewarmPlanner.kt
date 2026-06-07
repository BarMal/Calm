package dev.barna.calm

class ChapterPagePrewarmPlanner {
    fun positions(pageCount: Int, initialPage: Int): List<Int> {
        if (pageCount <= 1) return emptyList()
        val boundedInitial = initialPage.coerceIn(0, pageCount - 1)
        return (0 until pageCount)
            .filter { it != boundedInitial }
            .sortedWith(compareBy<Int> { kotlin.math.abs(it - boundedInitial) }.thenBy { it })
    }
}
