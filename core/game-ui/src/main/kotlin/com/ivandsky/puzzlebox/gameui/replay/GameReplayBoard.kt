package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard

/** A game's read-only replay board. Present = the game can draw a replay. */
interface GameReplayBoard {
    /**
     * Plural noun of the pieces ReplayFacts.correct counts, for the lead line ("5 vs 4 queens").
     * The only owner of that word.
     */
    val pieces: String

    /** False when this game's replay can show no error, so the Show errors switch has nothing to do and is hidden. */
    val hasErrors: Boolean get() = true

    /** Read-only board for one frame. No input. No controller. Animates only while [ReplayBoardOptions.animate]. */
    @Composable
    fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier)

    /**
     * [puzzle]'s board before any move, for a page that shows a puzzle nobody has played yet. The game decodes
     * the payload, drops the solution it carries, and only then builds what it draws: the solution never reaches
     * a board or the screen reader. Null for a payload this game cannot read, and for a game that draws no such board.
     */
    fun givens(puzzle: Puzzle): GivensBoard? = null
}
