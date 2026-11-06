package com.ivandsky.puzzlebox.games.queens.editor

/**
 * An editable Queens board (spec A2): [size] × [size] cells, each holding its region id in
 * `0 until size`, or [UNPAINTED]. [regions] is row-major. Partial boards are valid drafts;
 * only analysis asks for a full, size-region map.
 *
 * [crowns] are the author's answer crowns (cell indices; flow C, round 6). They are author-only:
 * never in the notation (A23), the puzzle, analysis or the layout key ([QueensEditor.layoutOf]).
 */
data class QueensDraft(
    val size: Int,
    val regions: List<Int>,
    val crowns: Set<Int> = emptySet(),
) {
    init {
        require(regions.size == size * size) { "expected ${size * size} cells, got ${regions.size}" }
        val bad = regions.firstOrNull { it != UNPAINTED && it !in 0 until size }
        require(bad == null) { "region id $bad outside 0 until $size" }
        val badCrown = crowns.firstOrNull { it !in regions.indices }
        require(badCrown == null) { "crown cell $badCrown outside 0 until ${regions.size}" }
    }

    companion object {
        const val UNPAINTED: Int = -1
    }
}
