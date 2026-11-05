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

// Game accent slots (spec A1, identity §2.2): hand-picked light and dark rows per hue,
// unnamed so the design system never names a game — a game's brand picks a slot.
// Blue = the Zip row (light accent = light pathTrace, BoardTokens.kt), Orange = Tango
// (sunFill family), Violet = Queens, Teal = MiniSudoku, Pink = Strands. `onAccent` is
// white in light and the slot's onContainer in dark. Neutral is no hue: it derives from
// the ink (onSurface) and well (surfaceContainerHigh) roles so a brandless game reads
// as plain ink on every surface.

internal val LightAccentTokens: Map<AccentSlot, GameAccent> = mapOf(
    AccentSlot.Blue to GameAccent(Color(0xFF1899D6), Color(0xFFFFFFFF), Color(0xFFD6EEFA), Color(0xFF0A3A55)),
    AccentSlot.Orange to GameAccent(Color(0xFFE8740C), Color(0xFFFFFFFF), Color(0xFFFFE6CC), Color(0xFF5A2A00)),
    AccentSlot.Violet to GameAccent(Color(0xFF7B5CF0), Color(0xFFFFFFFF), Color(0xFFE7E1FF), Color(0xFF2C1A73)),
    AccentSlot.Teal to GameAccent(Color(0xFF0FA39A), Color(0xFFFFFFFF), Color(0xFFCFF3EF), Color(0xFF00403C)),
    AccentSlot.Pink to GameAccent(Color(0xFFE0407A), Color(0xFFFFFFFF), Color(0xFFFFD9E6), Color(0xFF5C0F2C)),
    AccentSlot.Neutral to GameAccent(
        accent = LightColorScheme.onSurface,
        onAccent = LightColorScheme.surface,
        container = LightColorScheme.surfaceContainerHigh,
        onContainer = LightColorScheme.onSurface,
    ),
)

internal val DarkAccentTokens: Map<AccentSlot, GameAccent> = mapOf(
    AccentSlot.Blue to GameAccent(Color(0xFF5CC2F2), Color(0xFFCDEBFB), Color(0xFF0E3A52), Color(0xFFCDEBFB)),
    AccentSlot.Orange to GameAccent(Color(0xFFFF9B45), Color(0xFFFFE1C4), Color(0xFF4D2A0A), Color(0xFFFFE1C4)),
    AccentSlot.Violet to GameAccent(Color(0xFFA994FF), Color(0xFFE5DEFF), Color(0xFF34296B), Color(0xFFE5DEFF)),
    AccentSlot.Teal to GameAccent(Color(0xFF4DD3C8), Color(0xFFC9F5F0), Color(0xFF0C3F3B), Color(0xFFC9F5F0)),
    AccentSlot.Pink to GameAccent(Color(0xFFFF7FA8), Color(0xFFFFD9E6), Color(0xFF57182F), Color(0xFFFFD9E6)),
    AccentSlot.Neutral to GameAccent(
        accent = DarkColorScheme.onSurface,
        onAccent = DarkColorScheme.surface,
        container = DarkColorScheme.surfaceContainerHigh,
        onContainer = DarkColorScheme.onSurface,
    ),
)

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
