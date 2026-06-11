package dev.barna.calm

/** Items that can live on a custom home page grid. */
enum class GridItemType { APP, WIDGET }

/**
 * A single placed item on the home grid. [ref] is the app identity key ([GridItemType.APP]) or the
 * widget id as a string ([GridItemType.WIDGET]). Items occupy [columnSpan] × [rowSpan] cells.
 */
data class HomeGridItem(
    val type: GridItemType,
    val ref: String,
    val column: Int,
    val row: Int,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
)

/** A custom home page: a fixed-column grid of placed items. */
data class HomeGrid(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
    val items: List<HomeGridItem> = emptyList(),
) {
    private fun occupiedCells(): Set<Pair<Int, Int>> {
        val cells = HashSet<Pair<Int, Int>>()
        items.forEach { item ->
            for (c in item.column until item.column + item.columnSpan) {
                for (r in item.row until item.row + item.rowSpan) {
                    cells.add(c to r)
                }
            }
        }
        return cells
    }

    fun firstFreeCell(): Pair<Int, Int>? {
        val taken = occupiedCells()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((column to row) !in taken) return column to row
            }
        }
        return null
    }

    /** The first fully-empty row, where a full-width widget block can be appended. */
    fun nextFreeRow(): Int = items.maxOfOrNull { it.row + it.rowSpan } ?: 0

    fun withItem(item: HomeGridItem): HomeGrid = copy(items = items + item)

    fun withoutItem(column: Int, row: Int): HomeGrid =
        copy(items = items.filterNot { it.column == column && it.row == row })

    fun encode(): String = items.joinToString(";") {
        "${it.type.name},${it.ref},${it.column},${it.row},${it.columnSpan},${it.rowSpan}"
    }

    /** Whether [item] (identified by value) can occupy the block anchored at [column],[row]. */
    fun canPlace(item: HomeGridItem, column: Int, row: Int): Boolean {
        if (column < 0 || row < 0 || column + item.columnSpan > columns) return false
        val occupiedByOthers = HashSet<Pair<Int, Int>>()
        items.filter { it != item }.forEach { other ->
            for (c in other.column until other.column + other.columnSpan) {
                for (r in other.row until other.row + other.rowSpan) {
                    occupiedByOthers.add(c to r)
                }
            }
        }
        for (c in column until column + item.columnSpan) {
            for (r in row until row + item.rowSpan) {
                if ((c to r) in occupiedByOthers) return false
            }
        }
        return true
    }

    fun moving(item: HomeGridItem, column: Int, row: Int): HomeGrid =
        copy(items = items.map { if (it == item) it.copy(column = column, row = row) else it })

    fun without(item: HomeGridItem): HomeGrid = copy(items = items.filterNot { it == item })

    companion object {
        const val DEFAULT_COLUMNS = 4
        const val DEFAULT_ROWS = 6
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6

        fun decode(serialized: String?, columns: Int): HomeGrid {
            val cols = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            val items = serialized
                ?.split(';')
                ?.mapNotNull { entry ->
                    val parts = entry.split(',')
                    if (parts.size < 4) return@mapNotNull null
                    val type = runCatching { GridItemType.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                    val column = parts[2].toIntOrNull() ?: return@mapNotNull null
                    val row = parts[3].toIntOrNull() ?: return@mapNotNull null
                    HomeGridItem(
                        type = type,
                        ref = parts[1],
                        column = column,
                        row = row,
                        columnSpan = parts.getOrNull(4)?.toIntOrNull() ?: 1,
                        rowSpan = parts.getOrNull(5)?.toIntOrNull() ?: 1,
                    )
                }
                .orEmpty()
            return HomeGrid(columns = cols, rows = DEFAULT_ROWS, items = items)
        }
    }
}
