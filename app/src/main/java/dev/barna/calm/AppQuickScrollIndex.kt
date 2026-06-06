package dev.barna.calm

class AppQuickScrollIndex {
    fun create(apps: List<AppEntry>): AppQuickScrollModel {
        val targets = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app ->
            targets.putIfAbsent(letter(app.label), index)
        }
        return AppQuickScrollModel(
            targets.map { (label, cardIndex) -> AppQuickScrollTarget(label, cardIndex) },
        )
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
)
