package com.ivandsky.puzzlebox.games.queens.editor

/**
 * An editable Queens board (spec A2): [size] × [size] cells, each holding its region id in
 * `0 until size`, or [UNPAINTED]. [regions] is row-major. Partial boards are valid drafts;
 * only analysis asks for a full, size-region map.
 */
data class QueensDraft(val size: Int, val regions: List<Int>) {
    init {
        require(regions.size == size * size) { "expected ${size * size} cells, got ${regions.size}" }
        val bad = regions.firstOrNull { it != UNPAINTED && it !in 0 until size }
        require(bad == null) { "region id $bad outside 0 until $size" }
    }

    companion object {
        const val UNPAINTED: Int = -1
    }
}
