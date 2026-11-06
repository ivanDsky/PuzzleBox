package com.ivandsky.puzzlebox.games.queens.editor

/**
 * Region connectivity on a Queens draft (spec A8): a region is one piece when its cells are
 * joined by 4-neighbour (orthogonal) steps; diagonal contact does not join. Unpainted cells
 * belong to no region.
 */
object QueensContiguity {
    /** region id → connected pieces (4-neighbour), only for regions with more than one piece. */
    fun splitRegions(draft: QueensDraft): Map<Int, List<Set<Int>>> {
        val size = draft.size
        val seen = BooleanArray(size * size)
        val pieces = sortedMapOf<Int, MutableList<Set<Int>>>()
        for (start in draft.regions.indices) {
            val region = draft.regions[start]
            if (region == QueensDraft.UNPAINTED || seen[start]) continue
            pieces.getOrPut(region) { mutableListOf() } += flood(draft, start, seen)
        }
        return pieces.filterValues { it.size > 1 }
    }

    /** The piece holding [start]: every same-region cell orthogonally reachable from it. */
    private fun flood(draft: QueensDraft, start: Int, seen: BooleanArray): Set<Int> {
        val size = draft.size
        val region = draft.regions[start]
        val piece = sortedSetOf<Int>()
        val stack = ArrayDeque<Int>().apply { addLast(start) }
        seen[start] = true
        while (stack.isNotEmpty()) {
            val cell = stack.removeLast()
            piece += cell
            val row = cell / size
            val col = cell % size
            fun visit(next: Int) {
                if (!seen[next] && draft.regions[next] == region) {
                    seen[next] = true
                    stack.addLast(next)
                }
            }
            if (row > 0) visit(cell - size)
            if (row < size - 1) visit(cell + size)
            if (col > 0) visit(cell - 1)
            if (col < size - 1) visit(cell + 1)
        }
        return piece
    }
}
