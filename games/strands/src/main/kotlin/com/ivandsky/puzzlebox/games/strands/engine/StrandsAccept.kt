package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/**
 * Applies a verdict: a theme word or the spangram joins [StrandsState.found] (clearing the
 * active hint when it named that word), a non-theme word joins [StrandsState.nonTheme];
 * every other verdict leaves the state unchanged. A found word keeps the [path] the player traced
 * when it covers the word's own cells in another order.
 */
fun StrandsState.accept(verdict: StrandsVerdict, path: List<Coord>? = null): StrandsState = when (verdict) {
    is StrandsVerdict.ThemeWord -> withFound(verdict.word, path)
    is StrandsVerdict.Spangram -> withFound(verdict.word, path)
    is StrandsVerdict.NonTheme -> copy(nonTheme = nonTheme + verdict.word)
    else -> this
}

private fun StrandsState.withFound(word: StrandsWord, path: List<Coord>?): StrandsState {
    if (word.text in found) return this
    val kept = path?.takeIf { it != word.cells && it.toSet() == word.cells.toSet() }
    return copy(
        found = found + word.text,
        activeHint = activeHint?.takeIf { it.word != word.text },
        foundPaths = if (kept == null) foundPaths else foundPaths + (word.text to kept),
    )
}
