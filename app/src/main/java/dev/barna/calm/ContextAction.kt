package dev.barna.calm

enum class ContextActionCloseBehavior {
    RETURN_TO_STACK,
    REMOVE_CARD,
}

class ContextAction(
    @JvmField val label: String,
    @JvmField val action: Runnable,
    @JvmField val closeBehavior: ContextActionCloseBehavior = ContextActionCloseBehavior.RETURN_TO_STACK,
)
