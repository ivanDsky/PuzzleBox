package com.ivandsky.puzzlebox.games.strands.editor

/**
 * The words a board accepts as non-theme finds, baked into a puzzle at publish so its players
 * earn hints without a dictionary. [StrandsDraft.BLANK] cells match nothing. The first call loads
 * [StrandsDictionary], so call it off the main thread.
 */
object StrandsValidWords {
    private const val ROWS = StrandsDraft.ROWS
    private const val COLS = StrandsDraft.COLS
    private const val CELLS = ROWS * COLS
    private const val MIN_LETTERS = 4

    /** Every dictionary word traceable on [letters] (prefix DFS, 8-neighbour, no reuse) ∪ [extra]. */
    fun of(letters: String, extra: Set<String>): Set<String> {
        require(letters.length == CELLS) { "a board has $CELLS letters, got ${letters.length}" }
        val found = HashSet<String>(extra)
        val used = BooleanArray(CELLS)
        val path = StringBuilder()
        for (cell in 0 until CELLS) walk(letters, cell, used, path, found)
        return found
    }

    /** Extends [path] by [cell], records it if it is a word, then tries every unused neighbour. */
    private fun walk(letters: String, cell: Int, used: BooleanArray, path: StringBuilder, found: MutableSet<String>) {
        path.append(letters[cell])
        if (StrandsDictionary.hasPrefix(path)) {
            if (path.length >= MIN_LETTERS && StrandsDictionary.contains(path)) found.add(path.toString())
            used[cell] = true
            val row = cell / COLS
            val col = cell % COLS
            for (r in maxOf(row - 1, 0)..minOf(row + 1, ROWS - 1)) {
                for (c in maxOf(col - 1, 0)..minOf(col + 1, COLS - 1)) {
                    val next = r * COLS + c
                    if (!used[next]) walk(letters, next, used, path, found)
                }
            }
            used[cell] = false
        }
        path.setLength(path.length - 1)
    }
}
