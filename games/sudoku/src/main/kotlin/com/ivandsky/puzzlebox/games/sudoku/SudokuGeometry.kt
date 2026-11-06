package com.ivandsky.puzzlebox.games.sudoku

/**
 * Board shape as data: an N x N grid split into boxes of [boxRows] x [boxCols] cells.
 * Cell index is `row * size + col`. Units and peers are computed once here so the solver,
 * hints and commands read precomputed arrays instead of allocating per lookup.
 *
 * The arrays returned by [cellsOf], [peersOf] and [boxCells] are shared, not copies:
 * callers must never write to them.
 */
data class SudokuGeometry(val size: Int, val boxRows: Int, val boxCols: Int) {

    init {
        require(boxRows > 0 && boxCols > 0 && boxRows * boxCols == size) {
            "boxes of $boxRows x $boxCols do not tile a $size x $size board"
        }
        // Notes are stored as a 16-bit mask, one bit per digit.
        require(size <= MAX_SIZE) { "size $size exceeds $MAX_SIZE" }
    }

    val cellCount: Int = size * size

    /** Rows 0..N-1, then columns, then boxes. */
    val units: List<SudokuUnit> =
        UnitKind.entries.flatMap { kind -> (0 until size).map { SudokuUnit(kind, it) } }

    private val unitCells: Array<IntArray> = Array(units.size) { cellsFor(units[it]) }

    private val boxes: List<IntArray> = (0 until size).map { unitCells[UnitKind.BOX.ordinal * size + it] }

    private val peers: Array<IntArray> = Array(cellCount) { peersFor(it) }

    fun rowOf(idx: Int): Int = idx / size

    fun colOf(idx: Int): Int = idx % size

    fun boxOf(idx: Int): Int = (rowOf(idx) / boxRows) * (size / boxCols) + colOf(idx) / boxCols

    /** The unit's cells, ascending. */
    fun cellsOf(unit: SudokuUnit): IntArray {
        require(unit.index in 0 until size) { "unit index ${unit.index} outside 0 until $size" }
        return unitCells[unit.kind.ordinal * size + unit.index]
    }

    /** Every other cell sharing a row, column or box with [idx]; distinct and ascending. */
    fun peersOf(idx: Int): IntArray = peers[idx]

    /** The boxes as regions, in box order; the feed's regions are validated against these. */
    fun boxCells(): List<IntArray> = boxes

    private fun cellsFor(unit: SudokuUnit): IntArray = when (unit.kind) {
        UnitKind.ROW -> IntArray(size) { unit.index * size + it }
        UnitKind.COLUMN -> IntArray(size) { it * size + unit.index }
        UnitKind.BOX -> {
            val top = (unit.index / (size / boxCols)) * boxRows
            val left = (unit.index % (size / boxCols)) * boxCols
            IntArray(size) { (top + it / boxCols) * size + left + it % boxCols }
        }
    }

    private fun peersFor(idx: Int): IntArray {
        val seen = BooleanArray(cellCount)
        val row = unitCells[UnitKind.ROW.ordinal * size + rowOf(idx)]
        val col = unitCells[UnitKind.COLUMN.ordinal * size + colOf(idx)]
        val box = unitCells[UnitKind.BOX.ordinal * size + boxOf(idx)]
        for (unit in arrayOf(row, col, box)) for (cell in unit) seen[cell] = true
        seen[idx] = false
        return (0 until cellCount).filter { seen[it] }.toIntArray()
    }

    private companion object {
        const val MAX_SIZE = 16
    }
}
