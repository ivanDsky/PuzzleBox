package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography

/**
 * Ticket 08 debug style prototypes (three typography, three palette), extended by
 * ticket 09 with four more playful palettes (two pinned light, two pinned dark).
 * Variants ADD around the shipped token set — the control
 * variant of each axis resolves to the exact existing token objects (identity, not a
 * copy), so the default rendering path stays byte-identical.
 *
 * The enums and their mappings are pure logic so the resolver is JVM-testable
 * (A17 style); persistence is a debug-only string key-value store (:core:data), with
 * names round-tripped through [TypographyVariant.fromStored] / [PaletteVariant.fromStored].
 */
enum class TypographyVariant(
    val label: String,
    val blurb: String,
) {
    /** Control: the shipped serif editorial voice. */
    EDITORIAL(
        label = "Editorial",
        blurb = "Serif display voice — today's control.",
    ),

    /** Quizlet-pole prototype: humanist sans, medium weights (Nunito Sans, OFL). */
    CLEAN(
        label = "Clean",
        blurb = "Humanist sans, medium weights (Nunito Sans).",
    ),

    /** Duolingo-pole prototype: rounded, chunky bold sans (Baloo 2, OFL). */
    PLAYFUL(
        label = "Playful",
        blurb = "Rounded chunky bold (Baloo 2).",
    ),
    ;

    /** Full M3 typography table for this voice. */
    fun toTypography(): Typography = when (this) {
        EDITORIAL -> AppTypography
        CLEAN -> CleanTypography
        PLAYFUL -> PlayfulTypography
    }

    companion object {
        val DEFAULT: TypographyVariant get() = EDITORIAL

        /** Null-safe read of a stored preference string; unknown values fall back to null. */
        fun fromStored(stored: String?): TypographyVariant? =
            stored?.let { name -> entries.firstOrNull { it.name == name } }
    }
}

enum class PaletteVariant(
    val label: String,
    val blurb: String,
) {
    /** Control: warm paper + persimmon; the only axis variant with a dark scheme. */
    EDITORIAL_DARK(
        label = "Editorial dark",
        blurb = "Warm paper + persimmon — today's control; follows dark mode.",
    ),

    /** Quizlet-pole prototype: near-white surfaces, one vivid cobalt accent. */
    CLEAN_LIGHT(
        label = "Clean light",
        blurb = "Near-white surfaces, one vivid cobalt accent.",
    ),

    /** Duolingo-pole prototype: saturated greens and blues on light surfaces. */
    PLAYFUL_SATURATED(
        label = "Playful saturated",
        blurb = "Saturated greens and blues on light surfaces.",
    ),

    /** Ticket 09: warm coral/amber/tangerine family on cream. */
    PLAYFUL_SUNSET(
        label = "Playful sunset",
        blurb = "Warm coral, amber and tangerine on cream.",
    ),

    /** Ticket 09: bubblegum/grape/berry family on near-white. */
    PLAYFUL_CANDY(
        label = "Playful candy",
        blurb = "Bubblegum, grape and berry on near-white.",
    ),

    /** Ticket 09: saturated Duolingo-family accents on a true dark navy. */
    PLAYFUL_NIGHT(
        label = "Playful night",
        blurb = "Green, sky and honey popping on true dark navy.",
    ),

    /** Ticket 09: second dark take — mint/violet/pink lead on violet-black. */
    PLAYFUL_MIDNIGHT(
        label = "Playful midnight",
        blurb = "Mint, lavender and pink popping on violet-black.",
    ),
    ;

    /**
     * Complete M3 color scheme for this palette. Every non-control palette is one
     * pinned look — its identity — so it ignores the theme-mode request: C2-C5 are
     * light looks (a dark request still renders light), C6/C7 are dark looks (a
     * light request still renders dark). Only the control palette follows [darkTheme].
     */
    fun toColorScheme(darkTheme: Boolean): ColorScheme = when (this) {
        EDITORIAL_DARK -> if (darkTheme) DarkColorScheme else LightColorScheme
        CLEAN_LIGHT -> CleanLightColorScheme
        PLAYFUL_SATURATED -> PlayfulLightColorScheme
        PLAYFUL_SUNSET -> PlayfulSunsetColorScheme
        PLAYFUL_CANDY -> PlayfulCandyColorScheme
        PLAYFUL_NIGHT -> PlayfulNightColorScheme
        PLAYFUL_MIDNIGHT -> PlayfulMidnightColorScheme
    }

    /**
     * Whether this palette resolves a dark look under the [darkTheme] request —
     * the system-bar appearance must follow the resolved scheme, not the request:
     * the control follows the request, pinned-light palettes never go dark,
     * pinned-dark palettes never go light.
     */
    fun resolvesDark(darkTheme: Boolean): Boolean = when (this) {
        EDITORIAL_DARK -> darkTheme
        PLAYFUL_NIGHT, PLAYFUL_MIDNIGHT -> true
        else -> false
    }

    companion object {
        val DEFAULT: PaletteVariant get() = EDITORIAL_DARK

        /** Null-safe read of a stored preference string; unknown values fall back to null. */
        fun fromStored(stored: String?): PaletteVariant? =
            stored?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
