package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Module-local N-region fill vocabulary (direction doc §4 — the ONE approved new token,
 * module-local per the A3 posture: no other game consumes N distinct fills, so it stays
 * out of :core:designsystem until a second N-region game appears).
 *
 * Rules implemented here (all pinned by QueensRegionPaletteTest):
 * 1. Fixed hue set of 10 (red/orange/amber/green/teal/cyan/blue/indigo/violet/magenta),
 *    cycled by region id (`palette[region % 10]`) — deterministic across sessions, so
 *    spatial memory (region 3 is the same color tomorrow) holds.
 * 2. Candy ramps (spec A13, page 06 variant D): saturated mid-tones generated in OKLCH
 *    with parity alternation — LIGHT even ids at L 0.80 / C 0.13, odd ids at L 0.88 /
 *    C 0.105; DARK even ids at L 0.36 / C 0.095, odd ids at L 0.44 / C 0.085 — each
 *    chroma clipped to the sRGB gamut. Odd ids always sit lighter than even ids, so any
 *    even/odd neighbour pair differs in lightness as well as hue; same-parity
 *    neighbours differ by hue alone.
 * 3. On-ink computation (`onInkFor`): evaluated once per board render at fill-resolution
 *    time against the ACTIVE scheme's candidates — the scheme `onSurface` ink vs the
 *    `scrim`/`surface` constant — picking the HIGHER-contrast candidate. Floors (§9):
 *    X glyphs and the crown >= 4.5:1 vs their own fill, everything >= 3:1. Because the
 *    candidates and fills are all token-driven, the floor is provable for any region id
 *    in either mode without trusting raw official colors.
 * 4. Dot texture (ticket 20, 03 handoff open question 2): with only 10 hues, ids 10/11
 *    (K/L on 11×11 and 12×12 boards) repeat A/B, so [textured] regions carry four dots
 *    per cell at [textureDotOffset]/(1 − offset) of the cell, ink at [textureDotAlpha],
 *    radius max([textureDotMinRadius], [textureDotRadiusFraction] of the cell). Static —
 *    drawn by `drawRegionTexture`, shared by play and the editor.
 */
object QueensRegionPalette {

    /** Dot ink opacity over a textured region's fill. */
    const val textureDotAlpha: Float = 0.35f

    /** Dot radius floor, so dots stay visible on the smallest cells. */
    val textureDotMinRadius: Dp = 1.5.dp

    /** Dot radius as a fraction of the cell side. */
    const val textureDotRadiusFraction: Float = 0.05f

    /** Dot centre inset from the cell's top/left edge, as a fraction of the cell side. */
    const val textureDotOffset: Float = 0.22f

    // Light: even ids OKLCH L 0.80 (CIE L* 75–79), odd ids L 0.88 (L* 85–87).
    private val light = listOf(
        Color(0xFFFFA196), // 0 red
        Color(0xFFFECCAA), // 1 orange
        Color(0xFFDEBA50), // 2 amber
        Color(0xFFACEAAD), // 3 green
        Color(0xFF37D8C9), // 4 teal
        Color(0xFF89E8FF), // 5 cyan
        Color(0xFF91C1FF), // 6 blue
        Color(0xFFCFD4FF), // 7 indigo
        Color(0xFFD1A9FF), // 8 violet
        Color(0xFFFFC3E3), // 9 magenta
    )

    // Dark: even ids OKLCH L 0.36 (CIE L* 25–27), odd ids L 0.44 (L* 34–36).
    private val dark = listOf(
        Color(0xFF662520), // 0 red
        Color(0xFF76441F), // 1 orange
        Color(0xFF4C3B01), // 2 amber
        Color(0xFF325F34), // 3 green
        Color(0xFF014841), // 4 teal
        Color(0xFF025D6D), // 5 cyan
        Color(0xFF153D6D), // 6 blue
        Color(0xFF4A4C80), // 7 indigo
        Color(0xFF492E64), // 8 violet
        Color(0xFF733E5D), // 9 magenta
    )

    /** Region fill for [region] id in [dark] mode. */
    fun fill(region: Int, dark: Boolean): Color {
        val palette = if (dark) this.dark else light
        return palette[((region % palette.size) + palette.size) % palette.size]
    }

    // §4's fixed hue set, by NAME (§5.5 hint captions: "the blue region's queen…").
    // Index-aligned with the fill lists above.
    private val hueNames = listOf(
        "red", "orange", "amber", "green", "teal", "cyan", "blue", "indigo", "violet", "magenta",
    )

    /** The spoken color of [region] (palette hue names, cycled by id like the fills). */
    fun hueName(region: Int): String = hueNames[((region % hueNames.size) + hueNames.size) % hueNames.size]

    /** Whether [region]'s cells carry the dot texture: ids past the hue cycle repeat a fill. */
    internal fun textured(region: Int): Boolean = region >= light.size

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
