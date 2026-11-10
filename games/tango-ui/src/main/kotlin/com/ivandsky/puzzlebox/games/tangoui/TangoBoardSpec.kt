package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Board anatomy fractions (design-direction §2/§4): every dimension is a fraction of
 * the cell pitch so n6→n8 and phone→tablet scale without new values (ZipBoardSpec
 * discipline). Retune through these names — never call-site literals.
 *
 * Computed glyph floor at the content worst case, n10 on a 360 dp phone (328 dp board, pitch
 * 32.8 dp): glyph box 0.66 × 32.8 = 21.6 dp, its smallest solid ink, the sun disc,
 * 0.64 × 21.6 = 13.9 dp ≥ [glyphInkFloor]. Marks: see [markTextFraction].
 */
object TangoBoardSpec {

    /** Worst-case legibility floor for glyph ink (senior-UX D5 gate). */
    val glyphInkFloor: Dp = 10.3.dp

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

    /**
     * `=`/`×` mark font size, fraction of pitch. Sizes picked by eye (user QA round 4: 0.44 read
     * oversized on the tablet), not a dp floor: the ink is a small part of the em, so no font the
     * badge disc (0.26 pitch) can seat puts 10.3 dp of ink on screen. The 10×10 step keeps its ink
     * at ≥ 0.9 of the 8×8 ink. Computed font and measured ink (w × h, `=` then `×`) on a 328 dp
     * phone board, from `TangoMarkInkTest`:
     * - n4: 0.36 × 82.0 = 29.5 sp; 12.5 × 9.0 dp, 11.75 × 12.0 dp
     * - n6: 0.36 × 54.7 = 19.7 sp; 8.25 × 6.0 dp, 7.75 × 7.75 dp
     * - n8: 0.32 × 41.0 = 13.1 sp; 5.5 × 3.75 dp, 5.25 × 5.0 dp
     * - n10: 0.37 × 32.8 = 12.1 sp; 5.25 × 3.5 dp, 4.75 × 5.0 dp
     */
    fun markTextFraction(n: Int): Float = when {
        n >= 10 -> 0.37f // since M4-tango-25
        n >= 8 -> 0.32f
        else -> 0.36f
    }

    /** Violation dashed border dash/gap, fractions of pitch (§2). */
    const val violationDashFraction: Float = 0.06f
    const val violationDashGapFraction: Float = 0.06f

    /** Violation tint alpha over the offending cell (Zip rejection precedent). */
    const val violationTintAlpha: Float = 0.7f

    /** Guide ghost alpha (spec A12): the lesson's answer glyph, faint until the square
     *  matches (Queens' hint-ghost precedent, a touch lighter for a whole board of them). */
    const val ghostAlpha: Float = 0.4f

    /** Guide ghost beside a wrong glyph: a corner badge, fraction of the cell box. */
    const val ghostBadgeFraction: Float = 0.4f

    /** Editor edge hit band: half-width across a line, fraction of pitch. */
    const val editEdgeBandAcrossFraction: Float = 0.22f

    /** Editor edge hit inset: dead length at each end of a line, fraction of pitch. */
    const val editEdgeBandInsetFraction: Float = 0.22f

    /** Editor press wash over the square or edge under the finger. */
    const val editPressWashAlpha: Float = 0.18f

    /** Editor "2+ solutions" shade over the cells two answers disagree on. */
    const val editDifferAlpha: Float = 0.28f

    /** Editor clash ring weight, fraction of the cell. */
    const val editClashRingFraction: Float = 0.06f

    // ---- pitch-derived dp (call sites read these, never the raw fractions) ----

    /** Glyph box size at a given pitch and board size. */
    fun glyphSize(pitch: Dp, n: Int): Dp = pitch * glyphSizeFraction(n)

    /** Violation dash length at a given pitch. */
    fun violationDashLength(pitch: Dp): Dp = pitch * violationDashFraction

    /** Violation dash gap at a given pitch. */
    fun violationDashGap(pitch: Dp): Dp = pitch * violationDashGapFraction
}
