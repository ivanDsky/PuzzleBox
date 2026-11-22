package com.ivandsky.puzzlebox.gameui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
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
     * A practice board of [kind] for [technique] on [puzzle] (spec §2.2, [BoardKind]): the puzzle walked
     * easiest-rung-first from its givens up to the point [kind] opens at, every move before it already made.
     * Null when [technique] is never the easiest rung on [puzzle] (the caller skips the puzzle). [scope] is the
     * board's lifetime. [onTrainedTurn] receives one verdict per turn where [technique] was the easiest rung:
     * Right once per step (a step's several squares count once), Wrong for an undone move, ShowMe for Show me.
     */
    fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        kind: BoardKind,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard?

    /**
     * How many moves a [BoardKind.Focus] board of [technique] cut from [puzzle] trains before it ends, or null when this
     * game does not say (the default: the Train feature then keeps its usual order of puzzles). A board of one move is
     * "place what you just watched" and over, so the feature prefers puzzles that report two or more. May do CPU work:
     * call it off the main thread.
     */
    fun trainedMoves(puzzle: Puzzle, technique: TechniqueId): Int? = null

    /**
     * The live board of [board], a board this adapter made: the game's normal play gestures (a line of
     * crosses, auto-cross on in Queens, every candidate note shown in Mini Sudoku), each move judged as it
     * settles. It draws [PracticeBoard.status]'s Spotlight itself, over its grid only (a board with a keypad
     * or other parts below the grid keeps the overlay off them); the caller mounts no overlay.
     */
    @Composable
    fun Board(board: PracticeBoard, modifier: Modifier)

    /**
     * The variants of [technique] its intro writes down, in intro order (spec §2.7): one for a technique whose steps
     * act on one kind of unit, one per kind otherwise (Queens' Line lock: "Fill the line", "Fill the region").
     */
    fun variants(technique: TechniqueId): List<IntroVariant>

    /**
     * The demo of variant [variant] of [technique] on [puzzle]: the step that opens a [BoardKind.Focus] board of
     * [technique] on [puzzle], so frame 0 is exactly the position [practice] opens that board at. Null when [technique]
     * is never the easiest rung on [puzzle] or that opening step is of another variant (the caller tries the next
     * puzzle).
     */
    fun demo(puzzle: Puzzle, technique: TechniqueId, variant: Int): IntroDemo?

    /**
     * Frame [frame] of [demo], a demo this adapter made, drawn on the game's own board exactly as [Board] draws a
     * practice board of the same size, with no input: the evidence lit, the step's squares landed so far shown.
     */
    @Composable
    fun Demo(demo: IntroDemo, frame: Int, modifier: Modifier)

    /**
     * What a game UI module binds so the [TrainerRegistry] can collect its adapter: [game] while the
     * game's Train flag is on, null while it is off. The module binds one of these per game, always
     * (a factory that reads the flag when asked), so a game whose flag is off adds nothing to the
     * registry and a flag flipped between launches is read afresh.
     */
    class Binding(val game: TrainerGame?)
}
