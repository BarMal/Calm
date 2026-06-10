package dev.barna.calm

/** The card surface treatment. NONE is a fully opaque solid card; GLASS is the full layered look. */
enum class CardEffect { NONE, FROSTED, GLASS }

/**
 * Configurable card appearance. The defaults reproduce the original hard-coded card look exactly,
 * so untouched installs render identically; only deliberate changes alter the card surface.
 */
data class CardAppearance(
    val effect: CardEffect = CardEffect.GLASS,
    val effectStrength: Int = 100,
    val tintStrength: Int = 100,
) {
    val solid: Boolean get() = effect == CardEffect.NONE

    /** Frost overlay multiplier (frosted and glass effects). */
    val frostFactor: Float get() = if (effect == CardEffect.NONE) 0f else effectStrength.coerceIn(0, 100) / 100f

    /** Gloss/refraction overlay multiplier (glass effect only). */
    val glossFactor: Float get() = if (effect == CardEffect.GLASS) effectStrength.coerceIn(0, 100) / 100f else 0f

    /** Hue/colour-interpolation multiplier. */
    val tintFactor: Float get() = tintStrength.coerceIn(0, 100) / 100f

    companion object {
        val DEFAULT = CardAppearance()
    }
}
