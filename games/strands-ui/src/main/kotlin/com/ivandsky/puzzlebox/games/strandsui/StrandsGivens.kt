package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard

/**
 * [puzzle] as a state nobody has played: its letters. The words, where they run, the spangram, the clue and the
 * word list are left behind here, before a state exists: nothing that draws or speaks the state can give them
 * away. Null for another game's puzzle and for a payload that does not decode.
 */
internal fun strandsGivensState(puzzle: Puzzle): StrandsState? {
    if (puzzle.gameId != StrandsDefinition.id) return null
    val decoded = StrandsCodec.decode(puzzle.payload).getOrNull() ?: return null
    val letters = StrandsPuzzle(
        rows = decoded.rows,
        cols = decoded.cols,
        letters = decoded.letters,
        clue = "",
        spangram = StrandsWord("", emptyList()),
        themeWords = emptyList(),
        validWords = emptySet(),
        constructors = "",
    )
    return StrandsState(letters)
}

/**
 * The letter grid alone, at rest and read-only. Not the replay board: that one puts the word slot above the
 * grid, and a board nobody has played has no word to show.
 */
internal fun strandsGivensBoard(state: StrandsState): GivensBoard {
    val model = StrandsRendererModel.of(state)
    return GivensBoard(state.puzzle.rows, state.puzzle.cols) { modifier ->
        StrandsBoardContent(
            model = model,
            found = null,
            reduceMotion = true,
            trace = { Trace(emptyList()) },
            onTrace = {},
            floor = 0.dp,
            readOnly = true,
            modifier = modifier,
        )
    }
}
