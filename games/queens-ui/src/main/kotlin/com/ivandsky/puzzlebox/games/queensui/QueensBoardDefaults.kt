package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.BoardTokens

/**
 * The named constants the direction doc §3/§5 turns into module vocabulary — never
 * inline literals in composables. Every duration used below is an existing
 * MotionTokens value; nothing new is introduced (§8 MUST NOT).
 */
object QueensBoardDefaults {

    // §3.2 X-MANUAL: two 3.5dp strokes (F-2 recalibration, fix round 3 — was 3dp/50%,
    // which read as a different family from AUTO on device), length 55% of the cell,
    // round caps, full-opacity on-ink.
    val xManualWeight: Dp = 3.5.dp
    const val xManualLengthFraction: Float = 0.55f

    // §3.4 Queen crown: 55% of the cell — the dominant glyph.
    const val queenHeightFraction: Float = 0.55f

    // §3.1 cell-edge layer (F-1, fix round 3): queens region fills are opaque and sit
    // ABOVE BoardGrid's under-strokes, so the board draws its own edges over the fills —
    // a 1dp hairline at low-alpha ink on every cell edge (outline included), and a
    // heavier full-contrast region-boundary stroke so region shapes read even when
    // adjacent fills sit close in lightness. The region-boundary weight IS the core
    // gridline token; the hint boundary is the heaviest edge.
    val cellGridLineWeight: Dp = 1.dp
    const val cellGridLineAlpha: Float = 0.25f
    val regionBorderWeight: Dp = BoardTokens.boardGridLineWeight
    val hintBorderWeight: Dp = 2.dp

    // §3.5 Violation stripes: 45°, 6dp pitch — F-5 recalibration (fix round 3) so
    // ERROR dominates: a 4dp error band on a 5dp black under-band (1dp under-margin
    // total; the old 3dp/2dp let the black band cover 5 of every 6dp and the cue read
    // as a murky dark rectangle over the fill instead of red stripes).
    val violationStripePitch: Dp = 6.dp
    val violationStripeWidth: Dp = 4.dp
    val violationScrimWidth: Dp = 1.dp

    // §3.3 mass exit (queen removal clears up to 3(N−1) marks): stagger 10ms/cell, cap 80ms.
    const val autoMarkStaggerStepMillis: Long = 10L
    const val autoMarkStaggerCapMillis: Long = 80L

    // §5.5 hint: boardSelection overlay at 25%, two pulse cycles of DurationExpressive.
    // Stage 2 (fix round 6): the repeat tap points at the forced cell — the overlay
    // emphasis triples and the cell renders the crown ghost at reduced alpha.
    const val hintOverlayAlpha: Float = 0.25f
    const val hintPulseCycles: Int = 2
    const val hintPointedAlphaBoost: Float = 3f
    const val hintGhostAlpha: Float = 0.45f
    // The ghost is a dashed crown outline, so it never reads as a placed (solid) queen.
    const val ghostOutlineAlpha: Float = 0.7f
    val ghostStrokeWeight: Dp = 2.dp
    val ghostDash: Dp = 5.dp

    // Guided lesson (spec A12): a queen off the answer carries a faint × in its corner
    // (Tango's guide badge size), so a lesson with every answer placed never looks stuck.
    const val strayBadgeFraction: Float = 0.4f

    // §6 upper bound: cell visual size caps at 64dp; larger panes pad instead. The cap
    // holds from 5 cells per side (every daily, A9); a smaller lesson board fills its
    // pane up to the shared PlayBoardMaxWidth, like Tango's and Zip's (queensBoardMaxSideFor).
    val cellMaxSize: Dp = 64.dp
    const val perCellCapFromCells: Int = 5

    // §6 lower bound reasoning: compact worst case (10x10 on 360dp) bottoms out here.
    val cellMinSize: Dp = 32.dp

    // §6 pane gutter per side; dense boards (n ≥ 10) tighten it so a 12×12 fits 360dp.
    val gutter: Dp = 16.dp
    val compactGutter: Dp = 8.dp
    const val compactGutterFromCells: Int = 10
}
