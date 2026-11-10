package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardTokens

/**
 * The named constants the direction doc §3/§5 turns into module vocabulary — never
 * inline literals in composables. Every duration used below is an existing
 * MotionTokens value; nothing new is introduced (§8 MUST NOT).
 */
object QueensBoardDefaults {

    // Crosses (spec A13): manual and auto share one small × — 30% of the cell at 2.5dp,
    // round caps, full-opacity on-ink — so the queen stays the glyph the eye finds first.
    val crossWeight: Dp = 2.5.dp
    const val crossLengthFraction: Float = 0.30f

    // §3.4 Queen crown: 55% of the cell — the dominant glyph.
    const val queenHeightFraction: Float = 0.55f

    // §3.1 cell-edge layer (F-1, fix round 3): queens region fills are opaque and sit
    // ABOVE BoardGrid's under-strokes, so the board draws its own edges over the fills —
    // a 1dp hairline at low-alpha ink on every cell edge (outline included), and a
    // heavier full-contrast region-boundary stroke so region shapes read even when
    // adjacent fills sit close in lightness. The region-boundary weight IS the core
    // gridline token.
    val cellGridLineWeight: Dp = 1.dp
    const val cellGridLineAlpha: Float = 0.25f
    val regionBorderWeight: Dp = BoardTokens.boardGridLineWeight

    // §3.5 Violation stripes: 45°, 10dp pitch along the edge (≈7dp across the stripes),
    // a 3dp error band on a 4dp black under-band. The old 6dp pitch put 5dp of stroke
    // in every ≈4.2dp period, so the bands merged into a solid red block; this leaves a
    // ≈3dp gap of fill between stripes, so the cue reads as stripes.
    val violationStripePitch: Dp = 10.dp
    val violationStripeWidth: Dp = 3.dp
    val violationScrimWidth: Dp = 1.dp

    // Region Pulse and the solve's queen Pulse wash the fill toward white, the way a
    // highlight catches a tile; QueensMotion scales its peak per theme.
    val pulseWash: Color = Color.White

    // The guided lesson's answer ghost is a dashed crown outline, so it never reads as a
    // placed (solid) queen.
    const val ghostOutlineAlpha: Float = 0.7f
    val ghostStrokeWeight: Dp = 2.dp
    val ghostDash: Dp = 5.dp

    // Guided lesson (spec A12): a queen off the answer carries a faint × in its corner
    // (Tango's guide badge size), so a lesson with every answer placed never looks stuck.
    const val strayBadgeFraction: Float = 0.4f
    const val strayBadgeAlpha: Float = 0.45f
    val strayBadgeWeight: Dp = 1.75.dp
    const val strayBadgeLengthFraction: Float = 0.55f

    // §6 upper bound: cell visual size caps at 64dp; larger panes pad instead. The cap
    // holds from 5 cells per side (every daily, A9); a smaller lesson board fills its
    // pane up to the shared PlayBoardMaxWidth, like Tango's and Zip's (queensBoardMaxSideFor).
    val cellMaxSize: Dp = 64.dp
    const val perCellCapFromCells: Int = 5

    // §6 lower bound reasoning: compact worst case (10x10 on 360dp) bottoms out here.
    val cellMinSize: Dp = 32.dp

    // Hub thumbnail: the board sits this share of the square in from each edge, the
    // margin Zip's and Tango's thumbnails leave inside the hub's card frame.
    const val thumbnailInsetFraction: Float = 0.06f

    // §6 pane gutter per side; dense boards (n ≥ 10) tighten it so a 12×12 fits 360dp.
    val gutter: Dp = 16.dp
    val compactGutter: Dp = 8.dp
    const val compactGutterFromCells: Int = 10
}
