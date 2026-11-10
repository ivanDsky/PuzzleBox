package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.lerp
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

    /** The darker same-hue edge under a filled token; null exactly when [fill] is. */
    fun lip(colors: BoardColors): Color?

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

    /** Rings or dashes around the token of half-side [tokenRadius], centred in the draw area. */
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
 * The token's rounded square of half-side [half], centred in the draw area and moved down
 * by [drop] (a lip under its face), its corner keeping the token's proportion as it grows
 * or shrinks.
 */
internal fun DrawScope.drawTokenSquare(
    color: Color,
    half: Float,
    drop: Float = 0f,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - half, center.y - half + drop),
        size = Size(half * 2, half * 2),
        cornerRadius = CornerRadius(half * 2 * StrandsBoardSpec.TokenCornerFraction),
        alpha = alpha,
        style = style,
    )
}

/**
 * What one faded token paints at [scale] about its face's centre [center]: its face, and its lip
 * [StrandsBoardSpec.TokenLipFraction] lower only inside the face's own box, because a translucent
 * layer clips its content to its bounds (the lip hangs below them). A connector cut out of its
 * tokens clips this exact shape, so the line meets the visible edge with no gap at a corner or
 * below the lip. Not what an opaque token paints: that keeps its whole lip, and cuts nothing.
 */
internal fun Path.addToken(center: Offset, half: Float, scale: Float = 1f) {
    val reach = half * scale
    val drop = half * 2 * StrandsBoardSpec.TokenLipFraction * scale
    val corner = CornerRadius(reach * 2 * StrandsBoardSpec.TokenCornerFraction)
    val box = Rect(center.x - reach, center.y - reach, center.x + reach, center.y + reach)
    addRoundRect(RoundRect(box, corner))
    val lip = Path().apply { addRoundRect(RoundRect(box.translate(0f, drop), corner)) }
    val inBox = Path().apply { addRect(box) }
    addPath(Path.combine(PathOperation.Intersect, lip, inBox))
}

/**
 * One role of the token (its fill or its lip): the live trace's [selection] overlays the
 * letter's own [base], filling in over it as the letter joins ([join]); a found letter
 * turns from the trace colour to its own as it lights ([frame]).
 */
internal fun tokenColor(
    base: Color?,
    selection: Color,
    traced: Boolean,
    frame: StrandsCellFrame,
    join: StrandsCellFrame,
): Color? {
    if (traced) {
        if (join.lit >= 1f) return selection
        return if (base == null) selection.copy(alpha = selection.alpha * join.lit) else lerp(base, selection, join.lit)
    }
    if (base == null) return null
    return if (frame.lit >= 1f) base else lerp(selection, base, frame.lit)
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
