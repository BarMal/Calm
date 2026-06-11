package dev.barna.calm

import org.json.JSONArray
import org.json.JSONObject

data class ClassicLauncherPageDefinition(
    val id: String,
    val title: String,
    val enabled: Boolean = true,
    val items: List<ClassicGridItem> = emptyList(),
) {
    val key: String
        get() = "$KEY_PREFIX$id"

    fun encode(): JSONObject {
        return JSONObject()
            .put(FIELD_ID, id)
            .put(FIELD_TITLE, title)
            .put(FIELD_ENABLED, enabled)
            .put(FIELD_ITEMS, encodeItems(items))
    }

    fun containsApp(identityKey: String): Boolean {
        return items.any { item -> item.type == ClassicGridItemType.APP && item.target == identityKey }
    }

    fun containsWidget(appWidgetId: Int): Boolean {
        return items.any { item -> item.type == ClassicGridItemType.WIDGET && item.target == appWidgetId.toString() }
    }

    fun withApp(identityKey: String): ClassicLauncherPageDefinition? {
        if (containsApp(identityKey)) return this
        val position = nextFreeArea(width = 1, height = 1) ?: return null
        return copy(items = items + ClassicGridItem.app(identityKey, position.first, position.second))
    }

    fun withWidget(appWidgetId: Int, width: Int = ClassicGridItem.GRID_COLUMNS, height: Int = 2): ClassicLauncherPageDefinition? {
        if (containsWidget(appWidgetId)) return this
        val boundedWidth = width.coerceIn(1, ClassicGridItem.GRID_COLUMNS)
        val boundedHeight = height.coerceIn(1, ClassicGridItem.DEFAULT_GRID_ROWS)
        val position = nextFreeArea(boundedWidth, boundedHeight) ?: return null
        return copy(items = items + ClassicGridItem.widget(appWidgetId, position.first, position.second, boundedWidth, boundedHeight))
    }

    fun withoutItem(itemId: String): ClassicLauncherPageDefinition {
        return copy(items = items.filterNot { item -> item.id == itemId })
    }

    private fun nextFreeArea(width: Int, height: Int): Pair<Int, Int>? {
        val occupied = items.flatMap { item ->
            (item.x until item.x + item.width).flatMap { x ->
                (item.y until item.y + item.height).map { y -> x to y }
            }
        }.toSet()
        for (y in 0..ClassicGridItem.DEFAULT_GRID_ROWS - height) {
            for (x in 0..ClassicGridItem.GRID_COLUMNS - width) {
                val clear = (x until x + width).all { candidateX ->
                    (y until y + height).all { candidateY -> (candidateX to candidateY) !in occupied }
                }
                if (clear) return x to y
            }
        }
        return null
    }

    companion object {
        const val KEY_PREFIX = "classic:"

        private const val FIELD_ID = "id"
        private const val FIELD_TITLE = "title"
        private const val FIELD_ENABLED = "enabled"
        private const val FIELD_ITEMS = "items"

        fun default(index: Int = 1): ClassicLauncherPageDefinition {
            return ClassicLauncherPageDefinition(id = "classic-$index", title = if (index == 1) "Classic" else "Classic $index")
        }

        fun decode(json: JSONObject): ClassicLauncherPageDefinition? {
            val id = json.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: return null
            val title = json.optString(FIELD_TITLE).takeIf { it.isNotBlank() } ?: "Classic"
            return ClassicLauncherPageDefinition(
                id = id,
                title = title,
                enabled = json.optBoolean(FIELD_ENABLED, true),
                items = decodeItems(json.optJSONArray(FIELD_ITEMS)),
            )
        }

        private fun encodeItems(items: List<ClassicGridItem>): JSONArray {
            val array = JSONArray()
            items.forEach { item -> array.put(item.encode()) }
            return array
        }

        private fun decodeItems(array: JSONArray?): List<ClassicGridItem> {
            if (array == null) return emptyList()
            return buildList {
                for (index in 0 until array.length()) {
                    val item = ClassicGridItem.decode(array.optJSONObject(index) ?: continue) ?: continue
                    add(item)
                }
            }.distinctBy { it.id }
        }

        fun encodeList(pages: List<ClassicLauncherPageDefinition>): String {
            val array = JSONArray()
            pages.forEach { page -> array.put(page.encode()) }
            return array.toString()
        }

        fun decodeList(raw: String?): List<ClassicLauncherPageDefinition> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        val page = decode(array.optJSONObject(index) ?: continue) ?: continue
                        add(page)
                    }
                }.distinctBy { it.id }
            }.getOrDefault(emptyList())
        }
    }
}
