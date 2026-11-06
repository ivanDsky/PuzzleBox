package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.StrandsState

/**
 * Applies a verdict: a theme word or the spangram joins [StrandsState.found] (clearing the
 * active hint when it named that word), a non-theme word joins [StrandsState.nonTheme];
 * every other verdict leaves the state unchanged.
 */
fun StrandsState.accept(verdict: StrandsVerdict): StrandsState = when (verdict) {
    is StrandsVerdict.ThemeWord -> withFound(verdict.word.text)
    is StrandsVerdict.Spangram -> withFound(verdict.word.text)
    is StrandsVerdict.NonTheme -> copy(nonTheme = nonTheme + verdict.word)
    else -> this
}

private fun StrandsState.withFound(word: String): StrandsState {
    if (word in found) return this
    return copy(found = found + word, activeHint = activeHint?.takeIf { it.word != word })
}
