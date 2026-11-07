package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

/**
 * The hint's second stage (A7): the same dashed ring, now joined by the hint connector,
 * and the ring pulse run in spelling order, letter n at n × [StrandsMotion.HintRevealStaggerMs]
 * (NYT's `animationDelay: 300 × index`). The cue's order is the letter's index in the word.
 */
internal object HintOrderRevealedSpec : StrandsCellSpec {

    override fun fill(colors: BoardColors): Color? = HintOutlinedSpec.fill(colors)

    override fun lip(colors: BoardColors): Color? = HintOutlinedSpec.lip(colors)

    override fun letter(colors: BoardColors): Color = HintOutlinedSpec.letter(colors)

    override val stateWord: String = HintOutlinedSpec.stateWord

    /** Pulses once in spelling order as the order is revealed; the order is the state's index. */
    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? {
        val state = cell.state as? CellState.HintOrderRevealed ?: return null
        return StrandsMotionTrigger(key = state, cue = StrandsMotionCue(order = state.index))
    }

    override fun motionMs(cue: StrandsMotionCue): Int = delayMs(cue) + StrandsMotion.HintPulse.enterDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        HintOutlinedSpec.pulse(elapsedMs - delayMs(cue), reduceMotion)

    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) =
        with(HintOutlinedSpec) { decorate(colors, tokenRadius, frame) }

    private fun delayMs(cue: StrandsMotionCue): Int = cue.order * StrandsMotion.HintRevealStaggerMs
}
