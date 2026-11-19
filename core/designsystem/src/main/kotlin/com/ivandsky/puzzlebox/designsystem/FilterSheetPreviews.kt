package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Previews: the single sheet with a game list, a level list and a long list at font scale 1.3, then the chip in its states and a page header.

private val Games = listOf("Queens" to GameGlyphs.Queens, "Zip" to GameGlyphs.Zip, "Tango" to GameGlyphs.Tango, "Mini Sudoku" to GameGlyphs.Sudoku, "Strands" to GameGlyphs.Strands)
private val Levels = listOf("Easy", "Medium", "Hard")

@Composable
private fun gameOptions() = Games.map { (name, glyph) ->
    FilterOption(name, name, icon = { tint -> Icon(glyph, contentDescription = null, tint = tint) })
}

@Composable
private fun levelOptions(): List<FilterOption<String>> {
    val marks = listOf(PuzzleIcons.LevelEasy, PuzzleIcons.LevelMedium, PuzzleIcons.LevelHard)
    return Levels.mapIndexed { i, name -> FilterOption(name, name, icon = { tint -> Icon(marks[i], contentDescription = null, tint = tint) }) }
}

@Composable
private fun longOptions() = List(12) { FilterOption("row $it", "A source whose name is long enough to wrap onto a second line ${it + 1}") }

@ShowkaseComposable(name = "SingleFilterSheet (games)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterSheetGamesPreview() {
    PuzzleBoxTheme {
        SingleFilterSheet("Game", "All games", gameOptions(), selected = "Zip", onSelect = {}, onDismiss = {})
    }
}

@ShowkaseComposable(name = "SingleFilterSheet (levels)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterSheetLevelsPreview() {
    PuzzleBoxTheme {
        SingleFilterSheet("Difficulty", "Any difficulty", levelOptions(), selected = null, onSelect = {}, onDismiss = {})
    }
}

@ShowkaseComposable(name = "SingleFilterSheet (long, font 1.3)", group = "Filters")
@Preview(fontScale = 1.3f, showBackground = true)
@Composable
internal fun SingleFilterSheetLongPreview() {
    PuzzleBoxTheme {
        SingleFilterSheet("Source", "All sources", longOptions(), selected = "row 3", onSelect = {}, onDismiss = {})
    }
}

@ShowkaseComposable(name = "SingleFilterChip", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterChipPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SingleFilterChip("Game", "All games", gameOptions(), selected = null, onSelect = {})
            SingleFilterChip("Game", "All games", gameOptions(), selected = "Zip", onSelect = {})
        }
    }
}

@ShowkaseComposable(name = "SingleFilterChip (inline)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterChipInlinePreview() {
    PuzzleBoxTheme {
        val inline = remember { FilterPresentation.Inline {} }
        SingleFilterChip("Difficulty", "Any difficulty", levelOptions(), selected = "Hard", onSelect = {}, presentation = inline, modifier = Modifier.padding(Spacing.lg))
    }
}

@ShowkaseComposable(name = "FilterPageHeader", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun FilterPageHeaderPreview() {
    PuzzleBoxTheme {
        FilterPageHeader(title = "Games", onBack = {})
    }
}
