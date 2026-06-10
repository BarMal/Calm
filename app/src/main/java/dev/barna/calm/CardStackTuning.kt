package dev.barna.calm

data class CardStackTuning(
    val curve: Int,
    val horizontalCurve: Int,
    val arcWidth: Int,
    val aboveFocusCards: Int,
    val rotation: Int,
    val verticalSpacing: Int,
    val visibleCards: Int,
    val focusedCardGap: Int = 36,
    val focusedCardScale: Int = 32,
    val magnetStrength: Int = 70,
    val stackPeakPosition: Int = 50,
    val nonTopCardOpacity: Int = 100,
) {
    val nonTopCardOpacityFactor: Float = nonTopCardOpacity.coerceIn(0, 100) / 100f
    val curveFactor: Float = curve / 50f
    val stackPeakFraction: Float = stackPeakPosition.coerceIn(0, 100) / 100f
    val horizontalCurveFactor: Float = horizontalCurve / 100f
    val arcWidthFactor: Float = arcWidth / 100f
    val rotationFactor: Float = rotation / 100f
    val focusedCardGapFactor: Float = focusedCardGap.coerceIn(0, 100) / 100f
    val focusedCardScaleFactor: Float = 1f + (focusedCardScale.coerceIn(0, 100) / 100f) * MAX_SCALE_BOOST
    val magnetStrengthFactor: Float = magnetStrength.coerceIn(0, 100) / 100f
    val magnetDelayMillis: Long = CalmColor.lerp(130f, 40f, magnetStrengthFactor).toLong()
    val outgoingVisibleRange: Float = maxOf(OUTGOING_RANGE_BASE, aboveFocusCards + OUTGOING_RANGE_BASE)

    fun horizontalPathProgress(visualDepth: Float): Float {
        val visibleRange = maxOf(1f, visibleCards - 1f) * CalmColor.lerp(ARC_RANGE_MIN, ARC_RANGE_MAX, arcWidthFactor)
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
        return visibleDepth * CalmColor.lerp(ROTATION_RANGE_MIN, ROTATION_RANGE_MAX, horizontalPathProgress(visualDepth))
    }

    fun rotationDirection(visualDepth: Float): Float {
        if (visualDepth == 0f) return 0f
        return if (visualDepth < 0f) -1f else 1f
    }

    private fun smootherCurve(amount: Float): Float {
        val t = CalmColor.clamp01(amount)
        return t * t * t * (t * ((6f * t) - 15f) + 10f)
    }

    private companion object {
        const val MAX_SCALE_BOOST = 0.12f
        const val OUTGOING_RANGE_BASE = 0.65f
        const val ARC_RANGE_MIN = 0.65f
        const val ARC_RANGE_MAX = 1.55f
        const val ROTATION_RANGE_MIN = 0.65f
        const val ROTATION_RANGE_MAX = 1.1f
    }
}
