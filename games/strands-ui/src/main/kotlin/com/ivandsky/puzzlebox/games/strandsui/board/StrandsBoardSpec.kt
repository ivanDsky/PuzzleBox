package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Board anatomy (TangoBoardSpec / ZipBoardSpec discipline): every Strands board dimension
 * by name, never a call-site literal. NYT draws a 44 px token on a 54 px pitch (a 324 px
 * board, 6 columns), a 12 px connector and a 3 px dashed hint outline (research 03).
 */
internal object StrandsBoardSpec {

    /** Token diameter as a fraction of the cell pitch: NYT's 44 on 54. */
    const val TokenFraction: Float = 44f / 54f

    /** Letter size as a fraction of the pitch: NYT's 26 px on 54. */
    const val LetterFraction: Float = 26f / 54f

    /*
     * A filled token is a rounded square on a lip of its own hue (spec A14, prototype
     * 03-game-colors ?v=e: 24% of the pitch on an 80% token, a 3 px lip): corner radius
     * and lip depth as fractions of the token's side, so the depth scales with the board.
     */
    const val TokenCornerFraction: Float = 0.30f
    const val TokenLipFraction: Float = 0.08f

    val ConnectorWidth: Dp = 12.dp

    /** The spangram's non-colour cue (A32): a heavier line than a theme word's. */
    val SpangramConnectorWidth: Dp = 18.dp

    /** The hint's dashed ring, drawn just outside the token like NYT's CSS outline. */
    val HintRingWidth: Dp = 3.dp
    val HintDash: Dp = 6.dp
    val HintGap: Dp = 4.5.dp

    /** The spangram's second cue (A32): a ring inset inside the gold token. */
    val SpangramRingWidth: Dp = 2.dp
    val SpangramRingInset: Dp = 4.dp

    /**
     * The pitch the connector, ring and outline widths above are named for. At it and above
     * they keep them; a smaller pitch (the phone Studio's board beside its publish details)
     * thins them with the cell ([strandsStroke]), so no line or ring outgrows the token it marks.
     */
    val StrokePitch: Dp = 48.dp

    /*
     * Drag acceptance (A5, prototype 02 variant E), in cell pitches from the tail's centre
     * unless named otherwise. The direction is the finger's travel over TravelWindowCells,
     * so a diagonal drawn beside the centre line, wobbling, still reads as diagonal. A
     * neighbour joins when the finger is inside its square target (TargetHalf) and the
     * heading is toward it: a side letter needs a heading inside its cone, so it never steals
     * a corner-cutting diagonal, and the squares leave a no-join channel at each corner.
     * Units: ArriveCells and StillCells are circle radii; TargetHalf, BackHalf and HeartHalf are
     * half-sides of axis-aligned squares.
     */
    const val TravelWindowCells: Float = 0.25f
    const val SideConeDegrees: Float = 22.5f

    /** What the side cone leaves of the 45° between an axis and a diagonal: the cones tile the circle, so every heading decides one. */
    const val DiagonalConeDegrees: Float = 45f - SideConeDegrees

    /**
     * Half the side of a letter's square target, about the size of the token face. A side letter
     * joins 0.6 pitch from the tail, as its circle did; a diagonal joins at its square's near
     * corner, 0.6 pitch along each axis. The squares leave a channel of 1 - 2 × this between
     * them, where nothing joins.
     */
    const val TargetHalf: Float = 0.4f

    /** Half the side of the square about the letter before the tail the finger steps into to back up onto it; earlier letters are a wall and join nothing. Smaller than [TargetHalf]: a corner cut past the previous letter must not back up. */
    const val BackHalf: Float = 0.30f

    /** A joined letter grows the trace only once the finger comes this close to its centre. */
    const val ArriveCells: Float = 0.55f

    /**
     * A free letter beside the tail joins, heading or not, once the finger is inside the square of
     * this half side about its centre. At most half a pitch, so neighbouring heart squares never
     * overlap and the walk's first hit is the only hit (guarded by a test).
     */
    const val HeartHalf: Float = 0.25f

    /** Within this of the tail's centre nothing joins: a resting finger never picks a neighbour. */
    const val StillCells: Float = 0.30f

    /** A move is walked in steps no longer than this, so a fast swipe skips no letter. */
    const val SubStepCells: Float = 0.15f

    /**
     * SC11: no cell, and so no touch-down target, is smaller than this in play and in the tablet
     * Studio. The phone Studio lowers the floor so the whole grid fits its slot, since a scroll
     * there would lose the drag to the trace.
     */
    val TouchFloor: Dp = 48.dp

    /** Cap on a large pane: a tablet's board grows to finger-sized cells, not a phone's. */
    val CellMaxSize: Dp = 96.dp
}

/** [width] in px on a cell [pitchPx] wide: as named from [StrandsBoardSpec.StrokePitch] up, in proportion below it. */
internal fun Density.strandsStroke(width: Dp, pitchPx: Float): Float =
    width.toPx() * (pitchPx / StrandsBoardSpec.StrokePitch.toPx()).coerceAtMost(1f)

/** [strandsStroke] on the cell of a token of half-side [tokenRadius]. */
internal fun Density.tokenStroke(width: Dp, tokenRadius: Float): Float =
    strandsStroke(width, tokenRadius * 2 / StrandsBoardSpec.TokenFraction)
