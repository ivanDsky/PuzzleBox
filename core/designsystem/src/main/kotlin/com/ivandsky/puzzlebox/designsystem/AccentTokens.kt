package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Game accent slots (spec A1, A14, identity §2.2): the Splash set, unnamed so the design
// system never names a game — a game's brand picks a slot. Blue = Zip, Orange = Tango,
// Violet = Queens, Teal = MiniSudoku, Pink = Strands. Each hue sits at sRGB gamut-edge
// chroma at its peak lightness (prototype 03-game-colors ?v=d); `onAccent` is the hue's
// own deep shade because white clears 4.5:1 on none of them; `lip` is the chunky-depth
// bottom edge, same hue at OKLCH L −0.13 light / −0.20 dark. Light `onContainer` is the
// brightest same-hue step holding 4.5:1 on the tint. Neutral is no hue: it derives from
// the ink (onSurface) and well (surfaceContainerHigh) roles so a brandless game reads
// as plain ink on every surface.

internal val LightAccentTokens: Map<AccentSlot, GameAccent> = mapOf(
    AccentSlot.Blue to GameAccent(Color(0xFF00B4FE), Color(0xFF002A3F), Color(0xFFCAEAFF), Color(0xFF006C9B), Color(0xFF008AC4)),
    AccentSlot.Orange to GameAccent(Color(0xFFFF7F02), Color(0xFF401A00), Color(0xFFFFDDC9), Color(0xFFA04D00), Color(0xFFC56000)),
    AccentSlot.Violet to GameAccent(Color(0xFF9582FF), Color(0xFF27145C), Color(0xFFE2E1FF), Color(0xFF722FFF), Color(0xFF7059D2)),
    AccentSlot.Teal to GameAccent(Color(0xFF00C8BD), Color(0xFF002E2B), Color(0xFFB7F2EC), Color(0xFF00736D), Color(0xFF009B92)),
    AccentSlot.Pink to GameAccent(Color(0xFFFF478A), Color(0xFF4D0021), Color(0xFFFFD9E2), Color(0xFFC4005F), Color(0xFFCF0064)),
    AccentSlot.Neutral to GameAccent(
        accent = LightColorScheme.onSurface,
        onAccent = LightColorScheme.surface,
        container = LightColorScheme.surfaceContainerHigh,
        onContainer = LightColorScheme.onSurface,
        lip = Color(0xFF14171D),
    ),
)

internal val DarkAccentTokens: Map<AccentSlot, GameAccent> = mapOf(
    AccentSlot.Blue to GameAccent(Color(0xFF21B7FF), Color(0xFF002A40), Color(0xFF003752), Color(0xFF21B7FF), Color(0xFF0077AA)),
    AccentSlot.Orange to GameAccent(Color(0xFFFF8524), Color(0xFF401A00), Color(0xFF4E270B), Color(0xFFFF8524), Color(0xFFAB5300)),
    AccentSlot.Violet to GameAccent(Color(0xFFA69BFF), Color(0xFF27145C), Color(0xFF312C54), Color(0xFFA69BFF), Color(0xFF6C5EBC)),
    AccentSlot.Teal to GameAccent(Color(0xFF00E8DC), Color(0xFF002E2B), Color(0xFF003C38), Color(0xFF00E8DC), Color(0xFF00A198)),
    AccentSlot.Pink to GameAccent(Color(0xFFFF77A0), Color(0xFF4D0021), Color(0xFF4F222F), Color(0xFFFF77A0), Color(0xFFB93665)),
    AccentSlot.Neutral to GameAccent(
        accent = DarkColorScheme.onSurface,
        onAccent = DarkColorScheme.surface,
        container = DarkColorScheme.surfaceContainerHigh,
        onContainer = DarkColorScheme.onSurface,
        lip = Color(0xFF9C9EA2),
    ),
)

/** The accent palette for the light or dark scheme; [PuzzleBoxTheme] provides it. */
internal fun accentPalette(darkTheme: Boolean): Map<AccentSlot, GameAccent> =
    if (darkTheme) DarkAccentTokens else LightAccentTokens

/**
 * This slot's colours in the RESOLVED theme ([LocalAccentPalette], provided by
 * [PuzzleBoxTheme]), so a forced appearance opposite the system still flips them.
 */
@Composable
@ReadOnlyComposable
fun AccentSlot.colors(): GameAccent = LocalAccentPalette.current.getValue(this)

// Accent page for the catalog: one row per slot — the fill with its on-colour, then the
// container with its on-colour — in light and dark.
@ShowkaseComposable(name = "AccentSlots", group = "Tokens")
@PuzzleBoxPreviews
@Composable
internal fun AccentSlotsPreview() {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AccentSlot.entries.forEach { slot ->
                val accent = slot.colors()
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AccentSwatch(slot.name, accent.accent, accent.onAccent)
                    AccentSwatch("container", accent.container, accent.onContainer)
                    AccentSwatch("lip", accent.lip, accent.onAccent)
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(label: String, fill: Color, on: Color) {
    Box(
        modifier = Modifier.size(width = 112.dp, height = 40.dp).background(fill, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = on)
    }
}
