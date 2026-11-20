package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

// Self-contained previews: PuzzleBoxTheme follows the system theme, so the golden test drives light
// and dark through the configuration and reuses these as its captures.

private val PathAndNumbers = listOf("Path", "Numbers & walls")
private val LongSolutions = listOf("Solution A (original)", "Solution B (6 cells differ)")

private fun previewIcon(label: String): ImageVector = if (label.startsWith("Path")) GameGlyphs.Zip else PuzzleIcons.Notes

/** A surface with the room the error ring needs around the group. */
@Composable
private fun ChoicePreviewFrame(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceTilesChosenPreview() = ChoicePreviewFrame {
    ChoiceTiles(
        options = listOf("Path", "Numbers", "Walls"),
        selected = "Numbers",
        onSelect = {},
        label = { it },
        icon = ::previewIcon,
        tag = { it },
        accent = AccentSlot.Violet,
        modifier = Modifier.fillMaxWidth(),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceTilesLockedOptionPreview() = ChoicePreviewFrame {
    ChoiceTiles(
        options = PathAndNumbers,
        selected = "Path",
        onSelect = {},
        label = { it },
        icon = ::previewIcon,
        tag = { it },
        accent = AccentSlot.Violet,
        modifier = Modifier.fillMaxWidth(),
        optionEnabled = { it == "Path" },
    )
    ChoiceMessage("Cover every square with the path first.", isError = false)
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceTilesGroupDisabledPreview() = ChoicePreviewFrame {
    ChoiceTiles(
        options = LongSolutions.map { it.substringBefore(" (") },
        selected = "Solution A",
        onSelect = {},
        label = { it },
        icon = ::previewIcon,
        tag = { it },
        accent = AccentSlot.Violet,
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
    )
    ChoiceMessage("Checking your puzzle. Solutions come back in a moment.", isError = false)
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceTilesErrorPreview() = ChoicePreviewFrame {
    ChoiceTiles(
        options = listOf("Queen", "Paint"),
        selected = "Paint",
        onSelect = {},
        label = { it },
        icon = ::previewIcon,
        tag = { it },
        accent = AccentSlot.Violet,
        modifier = Modifier.fillMaxWidth(),
        error = true,
    )
    ChoiceMessage("Couldn't paint that region.", isError = true, onRetry = {})
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceTilesLongestLabelsPreview() = ChoicePreviewFrame {
    ChoiceTiles(
        options = LongSolutions,
        selected = LongSolutions.last(),
        onSelect = {},
        label = { it },
        icon = ::previewIcon,
        tag = { it },
        accent = AccentSlot.Violet,
        modifier = Modifier.fillMaxWidth(),
    )
}
