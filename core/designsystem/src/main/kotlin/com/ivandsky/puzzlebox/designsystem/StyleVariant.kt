package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography

/**
 * Ticket 08 debug style prototypes: three typography and three palette prototypes the
 * user picks between on-device. Variants ADD around the shipped token set — the control
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
    ;

    /**
     * Complete M3 color scheme for this palette. C2/C3 are light looks by design —
     * their identity is light surfaces — so they ignore the dark request; only the
     * control palette follows [darkTheme].
     */
    fun toColorScheme(darkTheme: Boolean): ColorScheme = when (this) {
        EDITORIAL_DARK -> if (darkTheme) DarkColorScheme else LightColorScheme
        CLEAN_LIGHT -> CleanLightColorScheme
        PLAYFUL_SATURATED -> PlayfulLightColorScheme
    }

    /** Whether this palette has a dark scheme the requested dark mode can apply to. */
    fun supportsDarkScheme(): Boolean = this == EDITORIAL_DARK

    companion object {
        val DEFAULT: PaletteVariant get() = EDITORIAL_DARK

        /** Null-safe read of a stored preference string; unknown values fall back to null. */
        fun fromStored(stored: String?): PaletteVariant? =
            stored?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
