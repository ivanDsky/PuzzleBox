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
     * A drag joins a cell only inside this circle, radius as a fraction of the pitch, so a
     * diagonal stroke through a corner never picks up the side neighbour (A12). A straight
     * diagonal passes 0.71 × pitch from the side neighbours' centres, so 0.40 leaves 0.3 pitch
     * of wobble while the circle spans 80% of the cell, the size of the drawn token.
     */
    const val HitCircleFraction: Float = 0.40f

    /** SC11: no cell, and so no touch-down target, is ever smaller than this. */
    val TouchFloor: Dp = 48.dp

    /** Cap on a large pane: a tablet's board grows to finger-sized cells, not a phone's. */
    val CellMaxSize: Dp = 96.dp
}
