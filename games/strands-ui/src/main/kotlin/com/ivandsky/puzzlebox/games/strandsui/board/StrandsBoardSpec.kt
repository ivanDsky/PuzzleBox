package com.ivandsky.puzzlebox.games.strandsui.board

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

    /*
     * Drag acceptance (A5, prototype 02 variant E), in cell pitches from the tail's centre
     * unless named otherwise. The direction is the finger's travel over TravelWindowCells,
     * so a diagonal drawn beside the centre line, wobbling, still reads as diagonal. A side
     * letter needs both a heading inside its narrow cone and the finger deep in its token,
     * so it never steals a corner-cutting diagonal.
     */
    const val TravelWindowCells: Float = 0.25f
    const val SideConeDegrees: Float = 20f
    const val SideJoinCells: Float = 0.40f
    const val DiagonalConeDegrees: Float = 25f

    /** Of the diagonal step (√2 pitches): the diagonal letter joins this far along it. */
    const val DiagonalJoinFraction: Float = 0.40f

    /** A joined letter grows the trace only once the finger comes this close to its centre. */
    const val ArriveCells: Float = 0.55f

    /** Within this of the previous letter's centre the drag backs up onto it. */
    const val BackJoinCells: Float = 0.30f

    /** Within this of the tail's centre nothing joins: a resting finger never picks a neighbour. */
    const val StillCells: Float = 0.30f

    /** A move is walked in steps no longer than this, so a fast swipe skips no letter. */
    const val SubStepCells: Float = 0.15f

    /** SC11: no cell, and so no touch-down target, is ever smaller than this. */
    val TouchFloor: Dp = 48.dp

    /** Cap on a large pane: a tablet's board grows to finger-sized cells, not a phone's. */
    val CellMaxSize: Dp = 96.dp
}
