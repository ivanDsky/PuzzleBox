package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.graphics.Color

/**
 * Module-local N-region fill vocabulary (direction doc §4 — the ONE approved new token,
 * module-local per the A3 posture: no other game consumes N distinct fills, so it stays
 * out of :core:designsystem until a second N-region game appears).
 *
 * Rules implemented here (all pinned by QueensRegionPaletteTest):
 * 1. Fixed hue set of 10 (red/orange/amber/green/teal/cyan/blue/indigo/violet/magenta),
 *    cycled by region id (`palette[region % 10]`) — deterministic across sessions, so
 *    spatial memory (region 3 is the same color tomorrow) holds.
 * 2. Fixed CIE L* bands per mode with parity alternation (rule 3): LIGHT fills sit at
 *    L* 84 (even ids) / 92 (odd ids), DARK fills at L* 18 / 26. Odd ids take the upper
 *    half of the band, even ids the lower, so ANY even/odd neighbor pair clears the
 *    ΔL* >= 8 adjacency floor by lightness alone — hue rotation stacks on top for the
 *    same-parity pairs. NOTE: the direction doc's nominal bands (light 86–92, dark
 *    18–26) cannot span 8 L* points inside themselves; the doc's own §9 hard floor
 *    (ΔL* >= 8) wins, so light's even step sits at 84 — the smallest reconciling move,
 *    flagged in the ticket report for the queens-09 walk.
 * 3. On-ink computation (`onInkFor`): evaluated once per board render at fill-resolution
 *    time against the ACTIVE scheme's candidates — the scheme `onSurface` ink vs the
 *    `scrim`/`surface` constant — picking the HIGHER-contrast candidate. Floors (§9):
 *    X glyphs and the crown >= 4.5:1 vs their own fill, everything >= 3:1. Because the
 *    candidates and fills are all token-driven, the floor is provable for any region id
 *    in either mode without trusting raw official colors.
 */
object QueensRegionPalette {

    // Light: pastel fills on the pale canvas; even ids L* 84, odd ids L* 92.
    private val light = listOf(
        Color(0xFFE3CDC6), // 0 red
        Color(0xFFF0E8DF), // 1 orange
        Color(0xFFD8D2B6), // 2 amber
        Color(0xFFDBEFDB), // 3 green
        Color(0xFFC1D6D2), // 4 teal
        Color(0xFFDAEDF1), // 5 cyan
        Color(0xFFC2D3EB), // 6 blue
        Color(0xFFEAE9F0), // 7 indigo
        Color(0xFFD8CEDF), // 8 violet
        Color(0xFFF1E7EF), // 9 magenta
    )

    // Dark: low-lightness fills on the near-black slate; even ids L* 18, odd ids L* 26.
    private val dark = listOf(
        Color(0xFF482115), // 0 red
        Color(0xFF493D2C), // 1 orange
        Color(0xFF312C17), // 2 amber
        Color(0xFF1F471F), // 3 green
        Color(0xFF13312C), // 4 teal
        Color(0xFF334145), // 5 cyan
        Color(0xFF142C4F), // 6 blue
        Color(0xFF413868), // 7 indigo
        Color(0xFF3F1A59), // 8 violet
        Color(0xFF662757), // 9 magenta
    )

    /** A/B-3 variant B: hue-only cycle (same hues, NO parity lightness alternation) —
     *  the loser candidate the §7 walk judges against the alternated default. */
    private val lightHueOnly = listOf(
        Color(0xFFEBD9D4), Color(0xFFEFE0CD), Color(0xFFE8E0C2), Color(0xFFDBEFDB),
        Color(0xFFC1D6D2), Color(0xFFDAEDF1), Color(0xFFC2D3EB), Color(0xFFD9D7EB),
        Color(0xFFD8CEDF), Color(0xFFEDE0EA),
    )

    /** Region fill for [region] id in [dark] mode, per the A/B-3 palette variant. */
    fun fill(region: Int, dark: Boolean, density: PaletteDensity = PaletteDensity.LIGHTNESS_ALTERNATED): Color {
        val palette = when {
            density == PaletteDensity.HUE_ONLY && !dark -> lightHueOnly
            else -> if (dark) this.dark else light
        }
        return palette[((region % palette.size) + palette.size) % palette.size]
    }

    /**
     * The computed on-ink for [fill] (§4 rule 4): the higher-contrast of the active
     * scheme's `onSurface` vs its `scrim`/`surface` constant. Call once per board
     * render (fill-resolution time), never per frame.
     */
    fun onInkFor(fill: Color, onSurface: Color, scrim: Color): Color =
        if (contrast(onSurface, fill) >= contrast(scrim, fill)) onSurface else scrim

    /** WCAG relative contrast ratio (BoardTokensTest precedent). */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminanceCompat() + 0.05f
        val lb = b.luminanceCompat() + 0.05f
        return if (la > lb) la / lb else lb / la
    }

    /** CIE L* lightness (the ΔL* floor's unit) — sRGB -> linear -> L*. */
    fun cielL(c: Color): Float {
        fun linear(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        val y = 0.2126f * linear(c.red) + 0.7152f * linear(c.green) + 0.0722f * linear(c.blue)
        return if (y > 0.008856f) 116f * Math.cbrt(y.toDouble()).toFloat() - 16f else 903.3f * y
    }

    // WCAG relative luminance without pulling androidx color-space math on the JVM tests.
    private fun Color.luminanceCompat(): Float {
        fun linear(channel: Float): Float =
            if (channel <= 0.04045f) channel / 12.92f else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
    }
}

/** §7 A/B-3 switch payload (debug switcher in the queens-09 walk). */
enum class PaletteDensity { LIGHTNESS_ALTERNATED, HUE_ONLY }
