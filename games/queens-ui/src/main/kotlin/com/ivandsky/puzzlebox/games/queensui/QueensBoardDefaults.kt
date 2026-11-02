package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The named constants the direction doc §3/§5 turns into module vocabulary — never
 * inline literals in composables. Every duration used below is an existing
 * MotionTokens value; nothing new is introduced (§8 MUST NOT).
 */
object QueensBoardDefaults {

    // §3.2 X-MANUAL: two 3dp strokes (≈2× the 1.5dp gridline — content, not structure),
    // length 50% of the cell, round caps, full-opacity on-ink.
    val xManualWeight: Dp = 3.dp
    const val xManualLengthFraction: Float = 0.50f

    // §3.3 X-AUTO: the SAME shape, smaller and thinner — 32% length, 2dp weight.
    // Distinction by geometry, never opacity (contrast floor must hold identically).
    val xAutoWeight: Dp = 2.dp
    const val xAutoLengthFraction: Float = 0.32f

    // §3.4 Queen crown: 55% of the cell — the dominant glyph.
    const val queenHeightFraction: Float = 0.55f

    // §3.5 Violation stripes: 45°, 6dp pitch, 3dp error band over a 2dp black scrim band.
    val violationStripePitch: Dp = 6.dp
    val violationStripeWidth: Dp = 3.dp
    val violationScrimWidth: Dp = 2.dp

    // A/B-2 variant B: full-cell error tint at 30% over a luminance-gated fill list.
    const val violationTintAlpha: Float = 0.30f

    // §3.3 mass exit (queen removal clears up to 3(N−1) marks): stagger 10ms/cell, cap 80ms.
    const val autoMarkStaggerStepMillis: Long = 10L
    const val autoMarkStaggerCapMillis: Long = 80L

    // §5.6 win cascade: queens replay the pop 50ms apart in placement order.
    const val winCascadeStepMillis: Long = 50L

    // §5.5 hint: boardSelection overlay at 25%, two pulse cycles of DurationExpressive.
    const val hintOverlayAlpha: Float = 0.25f
    const val hintPulseCycles: Int = 2

    // §6 upper bound: cell visual size caps at 64dp; larger panes pad instead.
    val cellMaxSize: Dp = 64.dp

    // §6 lower bound reasoning: compact worst case (10x10 on 360dp) bottoms out here.
    val cellMinSize: Dp = 32.dp
}

/** §7 A/B-1 switch payload: geometry distinction vs size + subtle fill-dot. */
enum class XProvenanceCue { GEOMETRY, SIZE_DOT }

/** §7 A/B-2 switch payload: scrimmed red stripes vs gated full-cell tint. */
enum class ViolationCue { STRIPES_SCRIM, TINT }

/** §7 A/B-4 switch payload: win cascade in placement order vs scanline. */
enum class CascadeOrder { PLACEMENT, SCANLINE }

/** The four §7 toggles queens-08 hands to queens-09 (all default to variant A). */
data class QueensAbVariants(
    val xProvenanceCue: XProvenanceCue = XProvenanceCue.GEOMETRY,
    val violationCue: ViolationCue = ViolationCue.STRIPES_SCRIM,
    val paletteDensity: PaletteDensity = PaletteDensity.LIGHTNESS_ALTERNATED,
    val cascadeOrder: CascadeOrder = CascadeOrder.PLACEMENT,
)
