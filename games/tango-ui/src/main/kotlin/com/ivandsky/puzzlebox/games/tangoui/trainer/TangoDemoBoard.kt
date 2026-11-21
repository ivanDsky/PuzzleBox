package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeOpening
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeSquare
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPracticeStep
import com.ivandsky.puzzlebox.games.tangoui.TangoBoard
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * Frame [frame] of [demo], drawn on the daily's own board exactly as a practice board draws it, with no input: the
 * frame's grid on a controller of its own (one per demo and frame, so nothing is written while composing) and its
 * Spotlight as the overlay. [compute] is the dispatcher the play board's hint stepper is given; the demo never hints.
 */
@Composable
internal fun TangoDemoBoard(demo: TangoIntroDemo, frame: Int, compute: CoroutineDispatcher, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val shown = demo.frames[frame]
    val controller = remember(demo, frame) {
        TangoPlayController(
            env = PlayEnvironment(scope, onSessionChanged = {}, onWon = {}),
            hintMode = HintMode.Stepwise(compute),
        ).also { controller ->
            val placements = shown.grid.withIndex()
                .filter { (idx, value) -> value != null && !demo.puzzle.isGiven(idx) }
                .associate { (idx, value) -> idx to checkNotNull(value) }
            controller.revertTo(TangoSession(ref = null, puzzle = demo.puzzle, placements = placements))
        }
    }
    TangoBoard(
        controller = controller,
        paneCap = null,
        modifier = modifier,
        onTap = {},
        onPlacement = {},
        overlay = { SpotlightOverlay(shown.spotlight) },
    )
}

/** Runs a block on the calling thread; a preview never presses the hint button, so nothing is ever dispatched. */
private object PreviewDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

// Preview: the pair demo on a blank-ish 6×6, after the first square has landed, its two suns still lit.
@PuzzleBoxPreviews
@Composable
private fun TangoDemoBoardPreview() {
    val puzzle = TangoPuzzle(
        variant = VariantId("tango/n6"),
        n = 6,
        givens = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 8 to TangoCell.MOON, 20 to TangoCell.SUN),
        markers = emptyList(),
    )
    val pair = TangoStep.Deduction(TangoStepRung.PAIR, 2, TangoCell.MOON, setOf(0, 1), row = null, col = null, options = null)
    val opening = TangoPracticeOpening(
        board = List(puzzle.cellCount) { puzzle.givens[it] },
        step = TangoPracticeStep(TangoStepRung.PAIR, listOf(TangoPracticeSquare(2, TangoCell.MOON, pair))),
    )
    PuzzleBoxTheme {
        TangoDemoBoard(
            demo = TangoIntroDemo.of(puzzle, opening, variant = 0),
            frame = 2,
            compute = PreviewDispatcher,
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
        )
    }
}
