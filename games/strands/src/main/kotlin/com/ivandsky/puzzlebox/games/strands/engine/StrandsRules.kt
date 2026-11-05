package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.strands.StrandsState

/**
 * Judges a trace by its spelling (A4): the spangram or an unfound theme word counts
 * wherever it was traced; a found word is [StrandsVerdict.AlreadyFound]; any other word in
 * [com.ivandsky.puzzlebox.games.strands.StrandsPuzzle.validWords] is a non-theme word (A16).
 * Total over every [Move]: never throws.
 */
object StrandsRules : GameRules<StrandsState> {

    override fun validate(move: Move, state: StrandsState): MoveVerdict {
        if (move !is StrandsMove) return StrandsVerdict.Foreign
        val puzzle = state.puzzle
        if (move.path.isEmpty()) return StrandsVerdict.Empty
        if (!StrandsPath.isValid(move.path, puzzle.rows, puzzle.cols)) return StrandsVerdict.InvalidPath
        val word = move.path.map(puzzle::letterAt).joinToString("")
        return when {
            word in state.found || word in state.nonTheme -> StrandsVerdict.AlreadyFound
            word == puzzle.spangram.text -> StrandsVerdict.Spangram(puzzle.spangram)
            else -> puzzle.themeWords.firstOrNull { it.text == word }?.let(StrandsVerdict::ThemeWord)
                ?: if (word in puzzle.validWords) StrandsVerdict.NonTheme(word) else StrandsVerdict.NotInWordList
        }
    }

    override fun isComplete(state: StrandsState): Boolean = state.isComplete

    override fun hint(state: StrandsState): Hint = StrandsHints.hintFor(state)
}
