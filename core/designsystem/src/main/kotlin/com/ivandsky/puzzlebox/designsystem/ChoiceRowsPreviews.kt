package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Self-contained previews: PuzzleBoxTheme follows the system theme, so the golden test drives light
// and dark through the configuration and reuses these as its captures.

private val Appearance = listOf("System", "Light", "Dark")
private val AppearanceHints = mapOf(
    "System" to "Follows your phone's setting",
    "Light" to "Always light",
    "Dark" to "Always dark",
)
private val Sources = listOf("Past puzzles", "New today")
private val SourceHints = mapOf(
    "Past puzzles" to "Ones you can still play from the archive",
    "New today" to "Shared by other people today",
)
private val LongSources = listOf("Puzzles from earlier days", "New puzzles from today")

/** A surface with the room the error ring needs around the group. */
@Composable
private fun ChoiceRowsPreviewFrame(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) { content() }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsChosenPreview() = ChoiceRowsPreviewFrame {
    ChoiceRows(
        options = Appearance,
        selected = "Light",
        onSelect = {},
        label = { it },
        description = { AppearanceHints.getValue(it) },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsIconsPreview() = ChoiceRowsPreviewFrame {
    val sparkle = PuzzleIcons.Sparkle
    val history = PuzzleIcons.History
    ChoiceRows(
        options = Sources,
        selected = "New today",
        onSelect = {},
        label = { it },
        description = { SourceHints.getValue(it) },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
        icon = { if (it == "New today") sparkle else history },
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsLockedOptionPreview() = ChoiceRowsPreviewFrame {
    ChoiceRows(
        options = Sources,
        selected = "Past puzzles",
        onSelect = {},
        label = { it },
        description = { SourceHints.getValue(it) },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
        optionEnabled = { it == "Past puzzles" },
        disabledReason = { "Nothing new yet today" },
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsGroupDisabledPreview() = ChoiceRowsPreviewFrame {
    ChoiceRows(
        options = Sources,
        selected = "Past puzzles",
        onSelect = {},
        label = { it },
        description = { "Picking a puzzle…" },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsErrorPreview() = ChoiceRowsPreviewFrame {
    ChoiceRows(
        options = Sources,
        selected = "New today",
        onSelect = {},
        label = { it },
        description = { SourceHints.getValue(it) },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
        error = true,
    )
    ChoiceMessage("Couldn't pick a puzzle.", isError = true, onRetry = {})
}

@PuzzleBoxPreviews
@Composable
internal fun ChoiceRowsLongestLabelsPreview() = ChoiceRowsPreviewFrame {
    val history = PuzzleIcons.History
    ChoiceRows(
        options = LongSources,
        selected = LongSources.first(),
        onSelect = {},
        label = { it },
        description = { "Match my phone's setting for every puzzle you open from here on" },
        tag = { it },
        modifier = Modifier.fillMaxWidth(),
        icon = { history },
    )
}
