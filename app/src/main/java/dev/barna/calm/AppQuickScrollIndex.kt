package dev.barna.calm

class AppQuickScrollIndex {
    fun create(apps: List<AppEntry>): AppQuickScrollModel {
        val activeLetters = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app ->
            activeLetters.putIfAbsent(letter(app.label), index)
        }
        val allLetters = ('A'..'Z').map { it.toString() } + listOf("#")
        val targets = allLetters.map { l ->
            AppQuickScrollTarget(
                label = l,
                cardIndex = activeLetters[l] ?: 0,
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
        val hit = model.targets[index]
        if (hit.active) return hit
        // Inactive: find nearest preceding active target (keeps scroll at last active letter),
        // falling forward only if there's no preceding active.
        for (i in index downTo 0) {
            if (model.targets[i].active) return model.targets[i]
        }
        for (i in index + 1..model.targets.lastIndex) {
            if (model.targets[i].active) return model.targets[i]
        }
        return null
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
