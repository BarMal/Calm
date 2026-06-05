package dev.barna.calm

data class CardStackTuning(
    val curve: Int,
    val horizontalCurve: Int,
    val arcWidth: Int,
    val aboveFocusCards: Int,
    val rotation: Int,
    val verticalSpacing: Int,
    val visibleCards: Int,
) {
    val curveFactor: Float = curve / 50f
    val horizontalCurveFactor: Float = horizontalCurve / 100f
    val arcWidthFactor: Float = arcWidth / 100f
    val rotationFactor: Float = rotation / 100f
    val outgoingVisibleRange: Float = maxOf(0.65f, aboveFocusCards + 0.65f)

    fun horizontalPathProgress(visualDepth: Float): Float {
        val visibleRange = maxOf(1f, visibleCards - 1f) * CalmColor.lerp(0.65f, 1.55f, arcWidthFactor)
        val normalizedDepth = if (visualDepth < 0f) {
            CalmColor.clamp01(-visualDepth / outgoingVisibleRange)
        } else {
            CalmColor.clamp01(visualDepth / visibleRange)
        }
        return smootherCurve(normalizedDepth)
    }

    fun rotationProgress(visualDepth: Float): Float {
        val range = if (visualDepth < 0f) {
            outgoingVisibleRange
        } else {
            maxOf(1f, visibleCards - 1f)
        }
        val visibleDepth = CalmColor.clamp01(kotlin.math.abs(visualDepth) / range)
        return visibleDepth * CalmColor.lerp(0.65f, 1.1f, horizontalPathProgress(visualDepth))
    }

    fun rotationDirection(visualDepth: Float): Float {
        if (visualDepth == 0f) return 0f
        return if (visualDepth < 0f) -1f else 1f
    }

    private fun smootherCurve(amount: Float): Float {
        val t = CalmColor.clamp01(amount)
        return t * t * t * (t * ((6f * t) - 15f) + 10f)
    }
}
