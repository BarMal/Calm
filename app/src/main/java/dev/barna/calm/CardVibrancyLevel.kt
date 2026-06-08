package dev.barna.calm

object CardVibrancyLevel {
    fun applyTo(color: Int, vibrancy: Int): Int {
        val clampedVibrancy = vibrancy.coerceIn(0, 100)
        val baseAlpha = (color ushr 24) and 0xFF
        val scaledAlpha = (baseAlpha * 2 * (100 - clampedVibrancy) / 100).coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaledAlpha shl 24)
    }
}
