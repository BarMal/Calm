package dev.barna.calm

class CardStackScrollMemory(
    private val maxRememberedStacks: Int,
) {
    private val rememberedScrollPositions = LinkedHashMap<String, Int>()
    private val pendingRestoreTargets = LinkedHashMap<String, Int>()

    fun remember(stackKey: String, scrollY: Int) {
        rememberedScrollPositions[stackKey] = scrollY.coerceAtLeast(0)
        pendingRestoreTargets.remove(stackKey)
        prune(rememberedScrollPositions)
    }

    fun initialRestore(stackKey: String, maxScroll: Int): CardStackScrollRestore {
        val desired = rememberedScrollPositions[stackKey]?.coerceAtLeast(0) ?: 0
        val boundedMaxScroll = maxScroll.coerceAtLeast(0)
        if (desired > boundedMaxScroll) {
            pendingRestoreTargets[stackKey] = desired
            prune(pendingRestoreTargets)
            return CardStackScrollRestore(scrollY = 0, pendingTarget = desired)
        } else {
            pendingRestoreTargets.remove(stackKey)
        }
        return CardStackScrollRestore(desired, pendingTarget = null)
    }

    fun restoreAfterBoundsExpanded(stackKey: String, maxScroll: Int): Int? {
        val desired = pendingRestoreTargets[stackKey] ?: return null
        val boundedMaxScroll = maxScroll.coerceAtLeast(0)
        val scrollY = desired.coerceIn(0, boundedMaxScroll)
        if (desired <= boundedMaxScroll) {
            pendingRestoreTargets.remove(stackKey)
        }
        return scrollY
    }

    fun pendingRestoreTarget(stackKey: String): Int? = pendingRestoreTargets[stackKey]

    fun snapshot(): CardStackScrollSnapshot {
        return CardStackScrollSnapshot(
            rememberedScrollPositions = LinkedHashMap(rememberedScrollPositions),
            pendingRestoreTargets = LinkedHashMap(pendingRestoreTargets),
        )
    }

    fun restore(snapshot: CardStackScrollSnapshot) {
        rememberedScrollPositions.clear()
        pendingRestoreTargets.clear()
        rememberedScrollPositions.putAll(snapshot.rememberedScrollPositions.mapValues { it.value.coerceAtLeast(0) })
        pendingRestoreTargets.putAll(snapshot.pendingRestoreTargets.mapValues { it.value.coerceAtLeast(0) })
        prune(rememberedScrollPositions)
        prune(pendingRestoreTargets)
    }

    private fun prune(map: LinkedHashMap<String, Int>) {
        while (map.size > maxRememberedStacks) {
            val first = map.keys.firstOrNull() ?: return
            map.remove(first)
        }
    }
}

data class CardStackScrollRestore(
    val scrollY: Int,
    val pendingTarget: Int?,
)

data class CardStackScrollSnapshot(
    val rememberedScrollPositions: LinkedHashMap<String, Int>,
    val pendingRestoreTargets: LinkedHashMap<String, Int>,
)
