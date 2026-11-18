package com.ivandsky.puzzlebox.games.strands

/**
 * The rules state. [found] holds theme words and the spangram in find order; [nonTheme]
 * holds valid non-theme words in insertion order. Strands keeps [hintsUsed] here because its
 * hint economy is earned minus spent (see GameRules); only `spend` grows it.
 *
 * [foundPaths] holds the trace the player drew for a found word only when it differs from the
 * word's own cells (same cells, another order); every other found word draws [StrandsWord.cells].
 */
data class StrandsState(
    val puzzle: StrandsPuzzle,
    val found: List<String> = emptyList(),
    val nonTheme: Set<String> = emptySet(),
    val hintsUsed: Int = 0,
    val activeHint: ActiveHint? = null,
    val foundPaths: Map<String, List<Coord>> = emptyMap(),
) {
    /** Every theme word plus the spangram found (A5). */
    val isComplete: Boolean
        get() = puzzle.spangram.text in found && puzzle.themeWords.all { it.text in found }

    /** The cells [word] is drawn along: the player's own trace when it kept one, else the puzzle's. */
    fun pathOf(word: StrandsWord): List<Coord> = foundPaths[word.text] ?: word.cells
}
