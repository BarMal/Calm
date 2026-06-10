package dev.barna.calm

/** Items that can live on a custom home page grid. (Widgets are added in a later checkpoint.) */
enum class GridItemType { APP }

/** A single placed item on the home grid. [ref] is the app identity key for [GridItemType.APP]. */
data class HomeGridItem(
    val type: GridItemType,
    val ref: String,
    val column: Int,
    val row: Int,
)

/** A custom home page: a fixed-column grid of placed items. */
data class HomeGrid(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    val items: List<HomeGridItem> = emptyList(),
) {
    fun firstFreeCell(): Pair<Int, Int>? {
        val taken = items.map { it.column to it.row }.toHashSet()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((column to row) !in taken) return column to row
            }
        }
        return null
    }

    fun withItem(item: HomeGridItem): HomeGrid = copy(items = items + item)

    fun withoutItem(column: Int, row: Int): HomeGrid =
        copy(items = items.filterNot { it.column == column && it.row == row })

    fun encode(): String = items.joinToString(";") { "${it.type.name},${it.ref},${it.column},${it.row}" }

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6

        fun decode(serialized: String?, columns: Int): HomeGrid {
            val items = serialized
                ?.split(';')
                ?.mapNotNull { entry ->
                    val parts = entry.split(',')
                    if (parts.size != 4) return@mapNotNull null
                    val type = runCatching { GridItemType.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                    val column = parts[2].toIntOrNull() ?: return@mapNotNull null
                    val row = parts[3].toIntOrNull() ?: return@mapNotNull null
                    HomeGridItem(type, parts[1], column, row)
                }
                .orEmpty()
            return HomeGrid(columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS), rows = DEFAULT_ROWS, items = items)
        }
    }
}
