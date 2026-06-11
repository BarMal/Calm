package dev.barna.calm

data class ClassicGridConfig(
    val columns: Int = DEFAULT_COLUMNS,
    val rows: Int = DEFAULT_ROWS,
) {
    fun boundedWidth(width: Int): Int = width.coerceIn(1, columns)
    fun boundedHeight(height: Int): Int = height.coerceIn(1, rows)
    fun boundedX(x: Int, width: Int): Int = x.coerceIn(0, (columns - width).coerceAtLeast(0))
    fun boundedY(y: Int, height: Int): Int = y.coerceIn(0, (rows - height).coerceAtLeast(0))

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 9
        const val MIN_ROWS = 3
        const val MAX_ROWS = 21
        const val DEFAULT_COLUMNS = ClassicGridItem.GRID_COLUMNS
        const val DEFAULT_ROWS = ClassicGridItem.DEFAULT_GRID_ROWS

        fun from(columns: Int, rows: Int): ClassicGridConfig {
            return ClassicGridConfig(
                columns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS),
                rows = rows.coerceIn(MIN_ROWS, MAX_ROWS),
            )
        }
    }
}
