package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.replay.ReplayFrame

/** A game's read-only replay board. Present = the game can draw a replay. */
interface GameReplayBoard {
    /**
     * Plural noun of the pieces ReplayFacts.correct counts, for the lead line ("5 vs 4 queens").
     * The only owner of that word.
     */
    val pieces: String

    /** Read-only board for one frame. No input. No controller. Static: no per-move animation. */
    @Composable
    fun Board(frame: ReplayFrame, modifier: Modifier)
}
