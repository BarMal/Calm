package dev.barna.calm

object CardStackLayout {
    fun activeTopPadding(
        viewportHeight: Int,
        cardHeight: Int,
        minimumTopPadding: Int,
        peakFraction: Float = 0.5f,
    ): Int {
        val desiredTop = (viewportHeight * peakFraction - cardHeight / 2f).toInt()
        val maximumTopPadding = maxOf(minimumTopPadding, viewportHeight - cardHeight)
        return desiredTop.coerceIn(minimumTopPadding, maximumTopPadding)
    }

    fun trailingPadding(
        viewportHeight: Int,
        activeTopPadding: Int,
        cardHeight: Int,
        minimumBottomPadding: Int,
    ): Int {
        return maxOf(minimumBottomPadding, viewportHeight - activeTopPadding - cardHeight + minimumBottomPadding)
    }
}
