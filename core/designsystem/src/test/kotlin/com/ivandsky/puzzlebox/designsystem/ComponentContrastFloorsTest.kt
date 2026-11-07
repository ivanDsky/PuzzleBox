package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

// Spec A8 floors over the colour roles the shared buttons and the timer bar render
// from, in light and dark: text >= 4.5:1, non-text borders and fills >= 3:1 (WCAG
// relative luminance). Disabled states are exempt but must stay distinct from enabled.
class ComponentContrastFloorsTest {

    private class Mode(
        val name: String,
        val scheme: ColorScheme,
        val primaryText: Color,
        val timerFill: Color,
        val accents: Map<AccentSlot, GameAccent>,
        val neutralLip: Color,
    )

    private val modes = listOf(
        Mode("light", LightColorScheme, LightPrimaryText, LightTimerFill, LightAccentTokens, LightNeutralLip),
        Mode("dark", DarkColorScheme, DarkPrimaryText, DarkTimerFill, DarkAccentTokens, DarkNeutralLip),
    )

    // Every surface a shared button sits on: the pane, cards, the dialog surface.
    private fun buttonSurfaces(scheme: ColorScheme) = listOf(
        scheme.background,
        scheme.surface,
        scheme.surfaceContainerLowest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
    )

    @Test
    fun `text button labels meet 4_5 to 1 on every button surface`() {
        for (mode in modes) {
            for (surface in buttonSurfaces(mode.scheme)) {
                val ratio = contrast(mode.primaryText, surface)
                assertTrue("${mode.name} label on $surface = $ratio", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun `tonal secondary button label meets 4_5 to 1 on its well fill`() {
        for (mode in modes) {
            val ratio = contrast(mode.scheme.onSurface, mode.scheme.surfaceContainerHigh)
            assertTrue("${mode.name} secondary label = $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `primary button label meets 4_5 to 1 on its fill`() {
        for (mode in modes) {
            val ratio = contrast(mode.scheme.onPrimary, mode.scheme.primary)
            assertTrue("${mode.name} primary label = $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `game button label meets 4_5 to 1 on every fill, neutral and each accent`() {
        for (mode in modes) {
            val fills = listOf<Pair<String, GameAccent?>>("neutral" to null) + mode.accents.map { (slot, accent) -> slot.name to accent }
            for ((name, accent) in fills) {
                val colors = gameButtonColors(accent, mode.scheme, mode.neutralLip)
                val ratio = contrast(colors.content, colors.fill)
                assertTrue("${mode.name} $name game button label = $ratio", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun `timer fill meets 3 to 1 against its track`() {
        for (mode in modes) {
            val ratio = contrast(mode.timerFill, mode.scheme.surfaceVariant)
            assertTrue("${mode.name} timer fill = $ratio", ratio >= 3f)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
