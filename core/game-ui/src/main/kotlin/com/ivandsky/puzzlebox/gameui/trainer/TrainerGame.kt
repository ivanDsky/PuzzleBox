package com.ivandsky.puzzlebox.gameui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope

/**
 * One game's Trainer adapter: the Compose half of its technique walker. The Train feature draws
 * any game's technique list and practice boards through this seam and never names a game. A game
 * has a trainer iff its UI module binds one; the [TrainerRegistry] holds the bound adapters.
 */
interface TrainerGame {
    /** The game this adapter trains. */
    val gameId: GameId

    /** The game's techniques with their plain copy, easiest first (the stepper's own order). */
    fun catalog(): List<TrainerTechnique>

    /** How this game finds drill puzzles, one strategy per source (the index, the generated set). */
    val drillSources: List<DrillSource>

    /**
     * A practice board for [technique] on [puzzle] (spec §2.2): the puzzle walked easiest-rung-first from its
     * givens up to the first point where [technique] is the easiest rung that applies, every move before it
     * already made. Null when [technique] is never the easiest rung on [puzzle] (the caller skips the puzzle).
     * [scope] is the board's lifetime. [onTrainedTurn] receives one verdict per turn where [technique] was the
     * easiest rung: Right once per step (a step's several squares count once), Wrong for an undone move,
     * ShowMe for Show me.
     */
    fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard?

    /**
     * The live board of [board], a board this adapter made: the game's normal play gestures (a line of
     * crosses, auto-cross on in Queens, every candidate note shown in Mini Sudoku), each move judged as it
     * settles. It draws [PracticeBoard.status]'s Spotlight itself, over its grid only (a board with a keypad
     * or other parts below the grid keeps the overlay off them); the caller mounts no overlay.
     */
    @Composable
    fun Board(board: PracticeBoard, modifier: Modifier)

    /**
     * What a game UI module binds so the [TrainerRegistry] can collect its adapter: [game] while the
     * game's Train flag is on, null while it is off. The module binds one of these per game, always
     * (a factory that reads the flag when asked), so a game whose flag is off adds nothing to the
     * registry and a flag flipped between launches is read afresh.
     */
    class Binding(val game: TrainerGame?)
}
