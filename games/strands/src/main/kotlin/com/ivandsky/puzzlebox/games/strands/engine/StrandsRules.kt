package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.strands.StrandsState

/**
 * Judges a trace in NYT's order (A3), by its spelling (A4): the spangram or an unfound theme
 * word counts wherever it was traced; any other word in
 * [com.ivandsky.puzzlebox.games.strands.StrandsPuzzle.validWords] is a non-theme word (A16).
 * Total over every [Move]: never throws.
 */
object StrandsRules : GameRules<StrandsState> {

    const val MIN_LETTERS = 4
    const val MAX_LETTERS = 24
    const val MAX_NON_THEME_WORDS = 100

    override fun validate(move: Move, state: StrandsState): MoveVerdict {
        if (move !is StrandsMove) return StrandsVerdict.Foreign
        val puzzle = state.puzzle
        if (!StrandsPath.isValid(move.path, puzzle.rows, puzzle.cols)) return StrandsVerdict.InvalidPath
        val word = move.path.map(puzzle::letterAt).joinToString("")
        // NYT checks the cap before theme words; matching them first means the cap never blocks one.
        return when {
            word.isEmpty() -> StrandsVerdict.Empty
            word.length < MIN_LETTERS -> StrandsVerdict.TooShort
            word.length > MAX_LETTERS -> StrandsVerdict.TooLong
            word in state.found || word in state.nonTheme -> StrandsVerdict.AlreadyFound
            word == puzzle.spangram.text -> StrandsVerdict.Spangram(puzzle.spangram)
            else -> puzzle.themeWords.firstOrNull { it.text == word }?.let(StrandsVerdict::ThemeWord)
                ?: when {
                    state.nonTheme.size >= MAX_NON_THEME_WORDS -> StrandsVerdict.WordLimitReached
                    word !in puzzle.validWords -> StrandsVerdict.NotInWordList
                    else -> StrandsVerdict.NonTheme(word)
                }
        }
    }

    override fun isComplete(state: StrandsState): Boolean = state.isComplete

    override fun hint(state: StrandsState): Hint = StrandsHints.hintFor(state)
}
