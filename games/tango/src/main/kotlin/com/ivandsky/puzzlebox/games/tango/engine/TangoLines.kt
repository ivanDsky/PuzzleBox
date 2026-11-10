package com.ivandsky.puzzlebox.games.tango.engine

/** The line-index scheme every Tango rule shares: lines `0 until n` are rows, `n until 2n` are columns. */
internal object TangoLines {

    /** Row-major index of position [j] in [line]. */
    fun cellAt(n: Int, line: Int, j: Int): Int = if (line < n) line * n + j else j * n + (line - n)

    /** The cells of [line], in order. */
    fun cells(n: Int, line: Int): IntArray = IntArray(n) { j -> cellAt(n, line, j) }

    /** The cells of all `2n` lines of an n×n board, rows first; `result[line][j] == cellAt(n, line, j)`. */
    fun all(n: Int): Array<IntArray> = Array(2 * n) { line -> cells(n, line) }
}
