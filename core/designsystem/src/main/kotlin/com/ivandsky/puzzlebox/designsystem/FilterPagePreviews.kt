package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.airbnb.android.showkase.annotation.ShowkaseComposable

// Previews: the single-select page over a game list and a level list, and the swap host showing its content, then a page.

private enum class PreviewPage { Game, Level }

private val PageGames = listOf("Queens" to GameGlyphs.Queens, "Zip" to GameGlyphs.Zip, "Tango" to GameGlyphs.Tango)

@Composable
private fun pageGames() = PageGames.map { (name, glyph) ->
    FilterOption(name, name, icon = { tint -> Icon(glyph, contentDescription = null, tint = tint) })
}

@Composable
private fun pageLevels(): List<FilterOption<String>> {
    val marks = listOf(PuzzleIcons.LevelEasy, PuzzleIcons.LevelMedium, PuzzleIcons.LevelHard)
    return listOf("Easy", "Medium", "Hard").mapIndexed { i, name -> FilterOption(name, name, icon = { tint -> Icon(marks[i], contentDescription = null, tint = tint) }) }
}

@Composable
private fun PreviewSwap(open: PreviewPage?) {
    var page by remember { mutableStateOf(open) }
    FilterPageSwap(
        page = page,
        onBack = { page = null },
        pageContent = { group ->
            when (group) {
                PreviewPage.Game -> SingleFilterPage("Game", "All games", pageGames(), selected = "Zip", onSelect = {}, onBack = { page = null })
                PreviewPage.Level -> SingleFilterPage("Difficulty", "Any difficulty", pageLevels(), selected = null, onSelect = {}, onBack = { page = null })
            }
        },
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Random puzzle")
            SingleFilterChip("Game", "All games", pageGames(), selected = "Zip", onSelect = {}, presentation = FilterPresentation.Inline { page = PreviewPage.Game })
            SingleFilterChip("Difficulty", "Any difficulty", pageLevels(), selected = null, onSelect = {}, presentation = FilterPresentation.Inline { page = PreviewPage.Level })
        }
    }
}

@ShowkaseComposable(name = "SingleFilterPage (games)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterPageGamesPreview() {
    PuzzleBoxTheme {
        SingleFilterPage("Game", "All games", pageGames(), selected = "Zip", onSelect = {}, onBack = {})
    }
}

@ShowkaseComposable(name = "SingleFilterPage (levels)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun SingleFilterPageLevelsPreview() {
    PuzzleBoxTheme {
        SingleFilterPage("Difficulty", "Any difficulty", pageLevels(), selected = null, onSelect = {}, onBack = {})
    }
}

@ShowkaseComposable(name = "FilterPageSwap (content)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun FilterPageSwapContentPreview() {
    PuzzleBoxTheme {
        PreviewSwap(open = null)
    }
}

@ShowkaseComposable(name = "FilterPageSwap (page)", group = "Filters")
@PuzzleBoxPreviews
@Composable
internal fun FilterPageSwapPagePreview() {
    PuzzleBoxTheme {
        PreviewSwap(open = PreviewPage.Game)
    }
}
