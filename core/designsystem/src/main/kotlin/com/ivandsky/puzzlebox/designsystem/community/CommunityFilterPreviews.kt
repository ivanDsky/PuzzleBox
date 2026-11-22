package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.airbnb.android.showkase.annotation.ShowkaseComposable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Previews: the text dropdown set and unset, then the filter bar a surface lays out with the sort menu closed and open.
// Internal so Showkase's index can reach them.

private val SortOptions = listOf("Most played", "Newest")

@Composable
private fun PreviewColumn(content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
    }
}

@ShowkaseComposable(name = "TextDropdownFilter", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun TextDropdownFilterPreview() {
    PreviewColumn {
        TextDropdownFilter(label = "Any game", value = null, open = false, onClick = {})
        TextDropdownFilter(label = "Any game", value = "Mini Sudoku", open = false, onClick = {})
    }
}

@ShowkaseComposable(name = "TextDropdownFilter (open)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun TextDropdownFilterOpenPreview() {
    PreviewColumn {
        TextDropdownFilter(label = "Any difficulty", value = null, open = true, onClick = {})
        TextDropdownFilter(label = "Any difficulty", value = "Hard", open = true, onClick = {})
    }
}

@ShowkaseComposable(name = "TextDropdownFilter (quiet)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun TextDropdownFilterQuietPreview() {
    PreviewColumn {
        TextDropdownFilter(
            label = "Since",
            value = null,
            open = false,
            onClick = {},
            colors = TextDropdownFilterDefaults.quietColors(),
            textStyle = TextDropdownFilterDefaults.quietTextStyle(),
        )
        TextDropdownFilter(
            label = "Since",
            value = "Since Nov 14",
            open = false,
            onClick = {},
            colors = TextDropdownFilterDefaults.quietColors(),
            textStyle = TextDropdownFilterDefaults.quietTextStyle(),
        )
    }
}

@ShowkaseComposable(name = "TextDropdownFilter (disabled)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun TextDropdownFilterDisabledPreview() {
    PreviewColumn {
        TextDropdownFilter(label = "Any game", value = null, open = false, onClick = {}, enabled = false)
    }
}

@ShowkaseComposable(name = "SortMenuButton", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SortMenuButtonPreview() {
    PreviewColumn {
        SortMenuButton(
            options = SortOptions,
            selected = SortOptions.first(),
            optionLabel = { it },
            expanded = false,
            onExpandedChange = {},
            onSelect = {},
        )
    }
}

@ShowkaseComposable(name = "SortMenuButton (open)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SortMenuButtonOpenPreview() {
    PreviewColumn {
        SortMenuButton(
            options = SortOptions,
            selected = SortOptions.last(),
            optionLabel = { it },
            expanded = true,
            onExpandedChange = {},
            onSelect = {},
            modifier = Modifier.height(Spacing.xxl * 4),
        )
    }
}

@ShowkaseComposable(name = "Filter bar (text dropdowns and sort)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun CommunityFilterBarPreview() {
    PuzzleBoxTheme {
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            TextDropdownFilter(label = "Any game", value = "Zip", open = false, onClick = {})
            TextDropdownFilter(label = "Any difficulty", value = null, open = false, onClick = {})
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                SortMenuButton(
                    options = SortOptions,
                    selected = SortOptions.first(),
                    optionLabel = { it },
                    expanded = false,
                    onExpandedChange = {},
                    onSelect = {},
                )
            }
        }
    }
}
