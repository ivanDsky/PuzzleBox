package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ivandsky.puzzlebox.designsystem.BoardColors
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
    private val Pulse = StrandsMotion.HintPulse
    private val PulseAt = floatArrayOf(0f, StrandsMotion.HintDipAt, StrandsMotion.HintSwellAt, 1f)
    private val PulseScale = floatArrayOf(1f, StrandsMotion.PressScale, StrandsMotion.HintSwellScale, 1f)

    override fun fill(colors: BoardColors): Color? = null

    override fun lip(colors: BoardColors): Color? = null

    override fun letter(colors: BoardColors): Color = colors.strandsIdleLetter

    override val stateWord: String = StrandsCopy.STATE_HINTED

    /** Pulses once as the hint lands, the same cue for every letter. */
    override fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger =
        StrandsMotionTrigger(key = cell.state, cue = StrandsMotionCue())

    override fun motionMs(cue: StrandsMotionCue): Int = Pulse.enterDurationMillis

    override fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame =
        pulse(elapsedMs, reduceMotion)

    /** One ring pulse [localMs] after its own start. */
    fun pulse(localMs: Int, reduceMotion: Boolean): StrandsCellFrame {
        if (reduceMotion || localMs <= 0 || localMs >= Pulse.enterDurationMillis) return StrandsCellFrame.Rest
        return StrandsCellFrame(ringScale = keyframes(localMs.toFloat() / Pulse.enterDurationMillis, PulseAt, PulseScale, Pulse.enterEasing))
    }

    override fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) {
        val width = tokenStroke(StrandsBoardSpec.HintRingWidth, tokenRadius)
        val dash = floatArrayOf(tokenStroke(StrandsBoardSpec.HintDash, tokenRadius), tokenStroke(StrandsBoardSpec.HintGap, tokenRadius))
        val half = (tokenRadius + width / 2) * frame.ringScale
        drawRoundRect(
            color = colors.strandsHintRing,
            topLeft = Offset(center.x - half, center.y - half),
            size = Size(half * 2, half * 2),
            cornerRadius = CornerRadius(half * 2 * StrandsBoardSpec.TokenCornerFraction),
            style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(dash)),
        )
    }
}
