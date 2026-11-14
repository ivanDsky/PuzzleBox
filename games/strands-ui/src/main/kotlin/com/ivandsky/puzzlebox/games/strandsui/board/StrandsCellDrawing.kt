package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.lerp
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strandsui.FoundEvent
import kotlin.math.ceil

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
     * What starts this look's motion on [cell]: a new trigger (by equality) restarts it, null
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

/** A motion start: [key] identifies it (a new key restarts it), [cue] places the letter in it. */
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

/*
 * The token's proportions, the single source for the painter ([drawTokenSquare]), the connector
 * clipper ([addToken], via [tokenGeometry]) and the layer's lip room ([lipRoomPx]): a face of
 * half-side `half * scale`, a lip [tokenDrop] lower, one rounded [tokenCorner] on both, keeping
 * the token's proportion as it grows or shrinks. Scalar, so the painter allocates nothing.
 */

/** The side of the token's face: [half] at [scale], doubled. */
internal fun tokenSide(half: Float, scale: Float = 1f): Float = half * scale * 2

/** How far the lip sits below a face of side [side]. */
internal fun tokenDrop(side: Float): Float = side * StrandsBoardSpec.TokenLipFraction

/** The corner radius of a face of side [side], and of its lip. */
internal fun tokenCorner(side: Float): Float = side * StrandsBoardSpec.TokenCornerFraction

/** One token's face and lip as shapes, for the clipper: built from the scalar functions above. */
internal class TokenGeometry(val face: Rect, val drop: Float, val cornerRadius: CornerRadius) {
    val lip: Rect get() = face.translate(0f, drop)
}

/** The geometry of the token of half-side [half] at [scale], its face centred on [center]. */
internal fun tokenGeometry(center: Offset, half: Float, scale: Float = 1f): TokenGeometry {
    val reach = half * scale
    val side = tokenSide(half, scale)
    val face = Rect(center.x - reach, center.y - reach, center.x + reach, center.y + reach)
    return TokenGeometry(face, tokenDrop(side), CornerRadius(tokenCorner(side)))
}

/**
 * The token's rounded square of half-side [half], centred in the draw area, or, with [lip], its
 * lip: the same square moved down by [tokenDrop].
 */
internal fun DrawScope.drawTokenSquare(
    color: Color,
    half: Float,
    lip: Boolean = false,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
) {
    val side = tokenSide(half)
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - half, center.y - half + if (lip) tokenDrop(side) else 0f),
        size = Size(side, side),
        cornerRadius = CornerRadius(tokenCorner(side)),
        alpha = alpha,
        style = style,
    )
}

/**
 * What [drawTokenSquare] paints for one token at [scale] about its face's centre [center]: the face
 * and, [StrandsBoardSpec.TokenLipFraction] lower, its lip. A connector cut out of its faded
 * tokens clips this exact shape, so the line meets the visible edge with no gap at a corner or
 * below the lip.
 */
internal fun Path.addToken(center: Offset, half: Float, scale: Float = 1f) {
    val token = tokenGeometry(center, half, scale)
    addRoundRect(RoundRect(token.face, token.cornerRadius))
    addRoundRect(RoundRect(token.lip, token.cornerRadius))
}

/**
 * The extra layer height, in px, above and below a token whose face is [facePx] square: its lip's
 * [tokenDrop] rounded up, so the layer reaches at least as far as the lip paints.
 */
internal fun lipRoomPx(facePx: Int): Int = ceil(tokenDrop(tokenSide(facePx / 2f))).toInt()

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
