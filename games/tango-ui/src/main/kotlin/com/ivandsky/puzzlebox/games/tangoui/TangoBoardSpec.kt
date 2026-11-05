package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Board anatomy fractions (design-direction §2/§4): every dimension is a fraction of
 * the cell pitch so n6→n8 and phone→tablet scale without new values (ZipBoardSpec
 * discipline). Retune through these names — never call-site literals.
 *
 * Measured floors: glyph cap ≥ 10.3 dp at the content worst case (n8 on a 360 dp
 * phone, pitch 41.0 dp → 0.28 × 41.0 = 11.5 dp ✓); mark height ≥ 10.3 dp same case
 * (mark fraction 0.30 × 41.0 = 12.3 dp).
 */
object TangoBoardSpec {

    /** Worst-case legibility floor for marks and glyph caps (senior-UX D5 gate). */
    val markHeightFloor: Dp = 10.3.dp

    /** Touch floor: n6 pitch on a 360 dp pane (§10.1). */
    val touchTargetFloor: Dp = 48.dp

    /** Sun/moon glyph box, fraction of cell pitch (D5 parity: n≥8 rescue step). */
    fun glyphSizeFraction(n: Int): Float = if (n >= 8) 0.66f else 0.60f

    /** Sun disc diameter, fraction of the glyph box. */
    const val sunDiscFraction: Float = 0.64f

    /** Sun ray length/width, fractions of the glyph box. */
    const val sunRayLengthFraction: Float = 0.18f
    const val sunRayWidthFraction: Float = 0.08f

    /** Disc→ray gap, fraction of the glyph box. */
    const val sunRayGapFraction: Float = 0.03f

    /** Moon crescent punch: offset / diameter, fractions of the glyph box (§2). */
    const val moonPunchOffsetFraction: Float = 0.28f
    const val moonPunchDiameterFraction: Float = 0.80f

    /** Given-disabled wash (locked-cell signal, user QA 2026-11-02): muted background
     *  on preplaced cells; violation tint draws above it when they co-occur. */
    val givenTintAlpha: Float = 0.5f

    /** `=`/`×` mark size, fraction of cell pitch (n≥8 rescue step).
     *  Worst case (§1): n8 mark = 0.30 × 41.0 = 12.3 dp ≥ 10.3 floor. */
    fun markSizeFraction(n: Int): Float = if (n >= 8) 0.30f else 0.34f

    /** Mark FONT size, fraction of pitch: the `=`/`×` ink is ~0.35 em, so the font
     *  must overshoot markSize to hit the ink floor. 0.36 after user QA round 4
     *  (0.44 still read oversized on the tablet). */
    fun markTextFraction(n: Int): Float = if (n >= 8) 0.32f else 0.36f

    /** Mark bar/stroke thickness, fraction of the mark box. */
    const val markStrokeFraction: Float = 0.14f

    /** `=` bar gap, fraction of the mark box. */
    const val markBarGapFraction: Float = 0.20f

    /** Violation dashed border dash/gap, fractions of pitch (§2). */
    const val violationDashFraction: Float = 0.06f
    const val violationDashGapFraction: Float = 0.06f

    /** Violation tint alpha over the offending cell (Zip rejection precedent). */
    const val violationTintAlpha: Float = 0.7f

    /** HintReveal tint alpha over the hint cell (§2/§7 — highlight without hiding the glyph). */
    const val hintTintAlpha: Float = 0.9f

    /** Guide ghost alpha (spec A12): the lesson's answer glyph, faint until the square
     *  matches (Queens' hint-ghost precedent, a touch lighter for a whole board of them). */
    const val ghostAlpha: Float = 0.4f

    /** Guide ghost beside a wrong glyph: a corner badge, fraction of the cell box. */
    const val ghostBadgeFraction: Float = 0.4f

    // ---- pitch-derived dp (call sites read these, never the raw fractions) ----

    /** Glyph box size at a given pitch and board size. */
    fun glyphSize(pitch: Dp, n: Int): Dp = pitch * glyphSizeFraction(n)

    /** Mark box size at a given pitch and board size. */
    fun markSize(pitch: Dp, n: Int): Dp = pitch * markSizeFraction(n)

    /** Violation dash length at a given pitch. */
    fun violationDashLength(pitch: Dp): Dp = pitch * violationDashFraction

    /** Violation dash gap at a given pitch. */
    fun violationDashGap(pitch: Dp): Dp = pitch * violationDashGapFraction
}
