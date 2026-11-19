package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Previews: the chip's states, then the row a surface lays out. Internal so Showkase's index can reach them.

@Composable
private fun PreviewColumn(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

@ShowkaseComposable(name = "DropdownFilterChip", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun DropdownFilterChipPreview() {
    PreviewColumn {
        DropdownFilterChip(label = "Source", picked = false, open = false, onClick = {})
        DropdownFilterChip(label = "From friends", picked = true, open = false, onClick = {})
    }
}

@ShowkaseComposable(name = "DropdownFilterChip (open)", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun DropdownFilterChipOpenPreview() {
    PreviewColumn {
        DropdownFilterChip(label = "Source", picked = false, open = true, onClick = {})
        DropdownFilterChip(label = "Source · 2", picked = true, open = true, onClick = {})
    }
}

@ShowkaseComposable(name = "DropdownFilterChip (disabled)", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun DropdownFilterChipDisabledPreview() {
    PreviewColumn {
        DropdownFilterChip(label = "Difficulty", picked = false, open = false, onClick = {}, enabled = false)
        DropdownFilterChip(label = "Hard", picked = true, open = false, onClick = {}, enabled = false)
    }
}

@ShowkaseComposable(name = "DropdownFilterChip (long label)", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun DropdownFilterChipLongPreview() {
    PreviewColumn {
        DropdownFilterChip(label = "Mini Sudoku and the other games", picked = true, open = false, onClick = {})
    }
}

@ShowkaseComposable(name = "FilterChipRow", group = "Chips")
@PuzzleBoxPreviews
@Composable
internal fun FilterChipRowPreview() {
    PuzzleBoxTheme {
        FilterChipRow(Modifier.padding(Spacing.lg)) {
            DropdownFilterChip(label = "Freshness", picked = false, open = false, onClick = {})
            DropdownFilterChip(label = "Source · 2", picked = true, open = false, onClick = {})
            DropdownFilterChip(label = "Games · 2", picked = true, open = false, onClick = {})
            AppTextButton(text = "Clear", onClick = {})
        }
    }
}
