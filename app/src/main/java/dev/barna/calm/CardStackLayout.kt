package dev.barna.calm

object CardStackLayout {
    fun activeTopPadding(
        viewportHeight: Int,
        cardHeight: Int,
        minimumTopPadding: Int,
        viewportTopOnScreen: Int = 0,
        targetTopOnScreen: Int? = null,
    ): Int {
        val centeredTop = (viewportHeight - cardHeight) / 2
        val desiredTop = targetTopOnScreen?.minus(viewportTopOnScreen) ?: centeredTop
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
