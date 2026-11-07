package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent

/**
 * How one cell look draws, speaks and moves (A40). Each base [CellState] has one spec
 * object; the live trace is [TraceOverlaySpec], drawn over whichever base spec applies.
 * A spec's motion is a pure timeline: [frame] at any elapsed time, reduced motion being
 * [StrandsCellFrame.Rest] from t0 (A33), so tests read it without a clock.
 */
internal interface StrandsCellSpec {

    /** The token's fill; null leaves the bare board showing through. */
    fun fill(colors: BoardColors): Color?

    fun letter(colors: BoardColors): Color

    /** The TalkBack state word (SC12); null when the look has nothing to say. */
    val stateWord: String?

    /** How long the motion runs after its trigger, its stagger included; 0 means none. */
    fun motionMs(cue: StrandsMotionCue): Int

    /** The look [elapsedMs] after the trigger; [StrandsCellFrame.Rest] once [motionMs] has passed. */
    fun frame(elapsedMs: Int, cue: StrandsMotionCue, reduceMotion: Boolean): StrandsCellFrame

    /**
     * What starts this look's motion on [cell]: a new trigger (by equality) replays it, null
     * holds it at rest. [found] is the word just found, if any.
     */
    fun trigger(cell: StrandsCell, found: FoundEvent?): StrandsMotionTrigger? = null

    /** Rings or dashes around the token of [tokenRadius], centred in the draw area. */
    fun DrawScope.decorate(colors: BoardColors, tokenRadius: Float, frame: StrandsCellFrame) = Unit
}

/**
 * One instant of a token's motion: its [scale], its ring's [ringScale], and [lit], how far
 * its fill has come in: a found letter's from the selection colour it had while traced into
 * its own, a letter joining the trace's from its own look into the selection colour.
 */
internal data class StrandsCellFrame(val scale: Float = 1f, val ringScale: Float = 1f, val lit: Float = 1f) {
    companion object {
        val Rest = StrandsCellFrame()
    }
}

/** A motion start: [key] identifies it (a new key replays), [cue] places the letter in it. */
internal data class StrandsMotionTrigger(val key: Any, val cue: StrandsMotionCue)

/** A letter's place in the motion that moves it: [order] of [count] letters. */
internal data class StrandsMotionCue(val order: Int = 0, val count: Int = 1)

/** The one registration point: a new [CellState] fails to compile here until its spec exists. */
internal object StrandsCellDrawing {
    fun specOf(state: CellState): StrandsCellSpec = when (state) {
        CellState.Idle -> IdleSpec
        CellState.FoundTheme -> FoundThemeSpec
        CellState.FoundSpangram -> FoundSpangramSpec
        CellState.HintOutlined -> HintOutlinedSpec
        is CellState.HintOrderRevealed -> HintOrderRevealedSpec
    }
}

/**
 * The value at [progress] (0..1) through keyframes [at] → [values], each leg eased with
 * [easing] (the motion pattern's). [at] starts at 0 and ends at 1, like CSS keyframe percentages.
 */
internal fun keyframes(progress: Float, at: FloatArray, values: FloatArray, easing: Easing): Float {
    val p = progress.coerceIn(0f, 1f)
    for (i in 1 until at.size) {
        if (p <= at[i]) {
            val leg = (p - at[i - 1]) / (at[i] - at[i - 1])
            val eased = easing.transform(leg)
            return values[i - 1] + (values[i] - values[i - 1]) * eased
        }
    }
    return values.last()
}
