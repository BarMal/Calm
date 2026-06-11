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

    fun containsStaticItem(staticItem: ClassicStaticItem): Boolean {
        return items.any { item -> item.type == ClassicGridItemType.STATIC && item.target == staticItem.name }
    }

    fun withApp(identityKey: String): ClassicLauncherPageDefinition? {
        return withApp(identityKey, ClassicGridConfig())
    }

    fun withApp(identityKey: String, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        if (containsApp(identityKey)) return this
        val position = nextFreeArea(width = 1, height = 1, gridConfig = gridConfig) ?: return null
        return copy(items = items + ClassicGridItem.app(identityKey, position.first, position.second))
    }

    fun withWidget(appWidgetId: Int, width: Int = ClassicGridItem.GRID_COLUMNS, height: Int = 2): ClassicLauncherPageDefinition? {
        return withWidget(appWidgetId, width, height, ClassicGridConfig())
    }

    fun withWidget(appWidgetId: Int, width: Int = gridConfigDefaultColumns(), height: Int = 2, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        if (containsWidget(appWidgetId)) return this
        val boundedWidth = gridConfig.boundedWidth(width)
        val boundedHeight = gridConfig.boundedHeight(height)
        val position = nextFreeArea(boundedWidth, boundedHeight, gridConfig) ?: return null
        return copy(items = items + ClassicGridItem.widget(appWidgetId, position.first, position.second, boundedWidth, boundedHeight))
    }

    fun withStaticItem(staticItem: ClassicStaticItem, width: Int = ClassicGridItem.GRID_COLUMNS, height: Int = 1): ClassicLauncherPageDefinition? {
        return withStaticItem(staticItem, width, height, ClassicGridConfig())
    }

    fun withStaticItem(staticItem: ClassicStaticItem, width: Int = gridConfigDefaultColumns(), height: Int = 1, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        if (containsStaticItem(staticItem)) return this
        val boundedWidth = gridConfig.boundedWidth(width)
        val boundedHeight = gridConfig.boundedHeight(height)
        val position = nextFreeArea(boundedWidth, boundedHeight, gridConfig) ?: return null
        return copy(items = items + ClassicGridItem.static(staticItem, position.first, position.second, boundedWidth, boundedHeight))
    }

    fun withItemAtNextFreeArea(item: ClassicGridItem): ClassicLauncherPageDefinition? {
        return withItemAtNextFreeArea(item, ClassicGridConfig())
    }

    fun withItemAtNextFreeArea(item: ClassicGridItem, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        if (items.any { existing -> existing.id == item.id }) return null
        val boundedWidth = gridConfig.boundedWidth(item.width)
        val boundedHeight = gridConfig.boundedHeight(item.height)
        val position = nextFreeArea(boundedWidth, boundedHeight, gridConfig) ?: return null
        return copy(items = items + item.copy(x = position.first, y = position.second, width = boundedWidth, height = boundedHeight))
    }

    fun withResizedItem(itemId: String, width: Int, height: Int): ClassicLauncherPageDefinition? {
        return withResizedItem(itemId, width, height, ClassicGridConfig())
    }

    fun withResizedItem(itemId: String, width: Int, height: Int, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        val item = items.firstOrNull { candidate -> candidate.id == itemId } ?: return null
        val boundedWidth = gridConfig.boundedWidth(width)
        val boundedHeight = gridConfig.boundedHeight(height)
        val otherItems = items.filterNot { candidate -> candidate.id == itemId }
        val position = if (areaIsClear(otherItems, item.x, item.y, boundedWidth, boundedHeight, gridConfig)) {
            item.x to item.y
        } else {
            firstFreeArea(otherItems, boundedWidth, boundedHeight, gridConfig) ?: return null
        }
        val resizedItem = item.copy(x = position.first, y = position.second, width = boundedWidth, height = boundedHeight)
        return copy(items = items.map { candidate -> if (candidate.id == itemId) resizedItem else candidate })
    }

    fun availablePositionsForItem(itemId: String): List<Pair<Int, Int>> {
        return availablePositionsForItem(itemId, ClassicGridConfig())
    }

    fun availablePositionsForItem(itemId: String, gridConfig: ClassicGridConfig): List<Pair<Int, Int>> {
        val item = items.firstOrNull { candidate -> candidate.id == itemId } ?: return emptyList()
        val otherItems = items.filterNot { candidate -> candidate.id == itemId }
        return positionsFor(item.width, item.height, gridConfig)
            .filter { position -> areaIsClear(otherItems, position.first, position.second, item.width, item.height, gridConfig) }
    }

    fun withMovedItem(itemId: String, x: Int, y: Int): ClassicLauncherPageDefinition? {
        return withMovedItem(itemId, x, y, ClassicGridConfig())
    }

    fun withMovedItem(itemId: String, x: Int, y: Int, gridConfig: ClassicGridConfig): ClassicLauncherPageDefinition? {
        val item = items.firstOrNull { candidate -> candidate.id == itemId } ?: return null
        val otherItems = items.filterNot { candidate -> candidate.id == itemId }
        if (!areaIsClear(otherItems, x, y, item.width, item.height, gridConfig)) return null
        val movedItem = item.copy(x = x, y = y)
        return copy(items = items.map { candidate -> if (candidate.id == itemId) movedItem else candidate })
    }

    fun canPlaceItem(itemId: String, x: Int, y: Int, width: Int, height: Int, gridConfig: ClassicGridConfig): Boolean {
        val otherItems = items.filterNot { candidate -> candidate.id == itemId }
        return areaIsClear(otherItems, x, y, width, height, gridConfig)
    }

    fun withoutItem(itemId: String): ClassicLauncherPageDefinition {
        return copy(items = items.filterNot { item -> item.id == itemId })
    }

    private fun nextFreeArea(width: Int, height: Int, gridConfig: ClassicGridConfig): Pair<Int, Int>? {
        return firstFreeArea(items, width, height, gridConfig)
    }

    private fun firstFreeArea(placedItems: List<ClassicGridItem>, width: Int, height: Int, gridConfig: ClassicGridConfig): Pair<Int, Int>? {
        for ((x, y) in positionsFor(width, height, gridConfig)) {
            if (areaIsClear(placedItems, x, y, width, height, gridConfig)) return x to y
        }
        return null
    }

    private fun positionsFor(width: Int, height: Int, gridConfig: ClassicGridConfig): List<Pair<Int, Int>> {
        if (width < 1 || height < 1) return emptyList()
        if (width > gridConfig.columns || height > gridConfig.rows) return emptyList()
        return (0..gridConfig.rows - height).flatMap { y ->
            (0..gridConfig.columns - width).map { x -> x to y }
        }
    }

    private fun areaIsClear(
        placedItems: List<ClassicGridItem>,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        gridConfig: ClassicGridConfig,
    ): Boolean {
        if (x < 0 || y < 0) return false
        if (x + width > gridConfig.columns) return false
        if (y + height > gridConfig.rows) return false
        val occupied = placedItems.flatMap { item ->
            (item.x until item.x + item.width).flatMap { x ->
                (item.y until item.y + item.height).map { y -> x to y }
            }
        }.toSet()
        return (x until x + width).all { candidateX ->
            (y until y + height).all { candidateY ->
                (candidateX to candidateY) !in occupied
            }
        }
    }

    companion object {
        const val KEY_PREFIX = "classic:"

        private const val FIELD_ID = "id"
        private const val FIELD_TITLE = "title"
        private const val FIELD_ENABLED = "enabled"
        private const val FIELD_ITEMS = "items"

        private fun gridConfigDefaultColumns(): Int = ClassicGridConfig.DEFAULT_COLUMNS

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
