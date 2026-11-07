package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one selection style of a surface tinted by this slot: the chosen segment takes the
 * slot's container and its ink, with the segmented button's own check mark, so every
 * either/or choice on that surface reads alike.
 */
@Composable
fun AccentSlot.segmentedButtonColors(): SegmentedButtonColors {
    val accent = colors()
    return SegmentedButtonDefaults.colors(
        activeContainerColor = accent.container,
        activeContentColor = accent.onContainer,
        activeBorderColor = accent.accent,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun AccentSegmentedButtonPreview() {
    PuzzleBoxTheme {
        SingleChoiceSegmentedButtonRow(Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
            listOf("Chosen", "Other").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == 0,
                    onClick = {},
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                    colors = AccentSlot.Violet.segmentedButtonColors(),
                ) { Text(label) }
            }
        }
    }
}
