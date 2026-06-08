package dev.barna.calm

class AppQuickScrollIndex {
    fun create(apps: List<AppEntry>): AppQuickScrollModel {
        val activeLetters = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app ->
            activeLetters.putIfAbsent(letter(app.label), index)
        }
        val allLetters = ('A'..'Z').map { it.toString() } + listOf("#")
        val activePositions = allLetters.mapIndexedNotNull { i, l ->
            if (activeLetters.containsKey(l)) i to activeLetters[l]!! else null
        }
        val targets = allLetters.mapIndexed { i, l ->
            val cardIndex = activeLetters[l] ?: interpolateCardIndex(i, activePositions)
            AppQuickScrollTarget(
                label = l,
                cardIndex = cardIndex,
                active = activeLetters.containsKey(l),
            )
        }
        return AppQuickScrollModel(targets)
    }

    fun targetAt(
        model: AppQuickScrollModel,
        railHeight: Int,
        y: Float,
    ): AppQuickScrollTarget? {
        if (model.targets.isEmpty() || railHeight <= 0) return null
        val index = ((y.coerceIn(0f, (railHeight - 1).toFloat()) / railHeight) * model.targets.size)
            .toInt()
            .coerceIn(0, model.targets.lastIndex)
        return model.targets[index]
    }

    private fun interpolateCardIndex(position: Int, activePositions: List<Pair<Int, Int>>): Int {
        val prev = activePositions.lastOrNull { (pos, _) -> pos < position }
        val next = activePositions.firstOrNull { (pos, _) -> pos > position }
        return when {
            prev != null && next != null -> {
                val fraction = (position - prev.first).toFloat() / (next.first - prev.first)
                (prev.second + fraction * (next.second - prev.second)).toInt()
            }
            prev != null -> prev.second
            next != null -> next.second
            else -> 0
        }
    }

    private fun letter(label: String): String {
        val first = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
        return if (first in 'A'..'Z') first.toString() else "#"
    }
}

data class AppQuickScrollModel(
    val targets: List<AppQuickScrollTarget>,
)

data class AppQuickScrollTarget(
    val label: String,
    val cardIndex: Int,
    val active: Boolean = true,
)
