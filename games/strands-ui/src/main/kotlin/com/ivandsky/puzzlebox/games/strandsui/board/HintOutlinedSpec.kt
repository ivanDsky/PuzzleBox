package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

/**
 * A hinted word's letters before their order is shown (A11): a dashed ring just outside
 * the token, and one pass of NYT's `pulseHintOutline` on the ring. Every outlined letter
 * pulses at once, whatever its place in the word, so the motion leaks no order.
 */
internal object HintOutlinedSpec : StrandsCellSpec {
    private val PulseAt = floatArrayOf(0f, StrandsMotion.HintDipAt, StrandsMotion.HintSwellAt, 1f)
    private val PulseScale = floatArrayOf(1f, StrandsMotion.PressScale, StrandsMotion.HintSwellScale, 1f)

    override fun fill(colors: BoardColors): Color? = null

    override fun letter(colors: BoardColors): Color = colors.strandsIdleLetter

    override val stateWord: String = StrandsCopy.STATE_HINTED

    /** Pulses once as the hint lands, the same cue for every letter. */
    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger =
        StrandsMotionTrigger(key = cell.state, cue = StrandsMotionCue())

    override fun motionMs(cue: StrandsMotionCue): Int = CelebrationMotion.HintPulseMs

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        pulse(elapsedMs, reduceMotion)

    /** One ring pulse [localMs] after its own start. */
    fun pulse(localMs: Int, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || localMs <= 0 || localMs >= CelebrationMotion.HintPulseMs) return StrandsCellFrame.Rest
        return StrandsCellFrame(ringScale = keyframes(localMs.toFloat() / CelebrationMotion.HintPulseMs, PulseAt, PulseScale))
    }

    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) {
        val width = StrandsBoardSpec.HintRingWidth.toPx()
        val dash = floatArrayOf(StrandsBoardSpec.HintDash.toPx(), StrandsBoardSpec.HintGap.toPx())
        drawCircle(
            color = colors.strandsHintRing,
            radius = (tokenRadius + width / 2) * frame.ringScale,
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(dash)),
        )
    }
}
