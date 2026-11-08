package com.ivandsky.puzzlebox.games.strands.editor

/** Uncovered areas of a Strands draft too small for any word. */
object StrandsPockets {
    private const val MIN_WORD = 4

    /**
     * The uncovered 8-connected areas smaller than four cells, each as its cell indices; cells
     * no path holds count as uncovered, named or not.
     */
    fun of(draft: StrandsDraft): List<Set<Int>> {
        val open = (0 until StrandsDraft.ROWS * StrandsDraft.COLS).toMutableSet()
        draft.paths.forEach { open.removeAll(it.cells.toSet()) }
        val seen = HashSet<Int>()
        val pockets = ArrayList<Set<Int>>()
        for (start in open.sorted()) {
            if (start in seen) continue
            val area = flood(start, open)
            seen += area
            if (area.size < MIN_WORD) pockets += area
        }
        return pockets
    }

    private fun flood(start: Int, open: Set<Int>): Set<Int> {
        val area = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val row = cell / StrandsDraft.COLS
            val col = cell % StrandsDraft.COLS
            for (dr in -1..1) for (dc in -1..1) {
                val r = row + dr
                val c = col + dc
                if (r !in 0 until StrandsDraft.ROWS || c !in 0 until StrandsDraft.COLS) continue
                val next = r * StrandsDraft.COLS + c
                if (next in open && area.add(next)) queue.addLast(next)
            }
        }
        return area
    }
}
