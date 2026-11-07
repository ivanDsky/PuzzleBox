package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.games.zip.Wall

/**
 * An editable Zip board: [n] × [n] cells, row-major. [path] and [waypoints] hold cell indices.
 * Partial or empty paths are valid drafts; only analysis asks for a complete one.
 */
data class ZipDraft(
    val n: Int,
    /** Interior walls, `Wall(cell, RIGHT|DOWN)`. */
    val walls: Set<Wall>,
    /** The author's path in drawing order; may be partial or empty (share/import boards). */
    val path: List<Int>,
    /** `waypoints[i]` is label i+1. With a path: `[path.first]` + marks in path order + `[path.last]`. */
    val waypoints: List<Int>,
) {
    init {
        require(n > 0) { "board size $n" }
        val cells = n * n
        val bad = walls.map { it.cell.idx } + path + waypoints
        val off = bad.firstOrNull { it !in 0 until cells }
        require(off == null) { "cell $off outside 0 until $cells" }
    }
}
