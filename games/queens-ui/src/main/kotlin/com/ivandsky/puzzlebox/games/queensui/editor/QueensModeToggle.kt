package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.ChoiceTiles
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

private val QueensEditorMode.label: String
    get() = when (this) {
        QueensEditorMode.Crown -> "Queen"
        QueensEditorMode.Paint -> "Paint"
    }

/** The [controller]'s Crown | Paint switch, shown in the tool strip's hint slot. */
@Composable
internal fun QueensModeToggle(controller: QueensEditorController, modifier: Modifier = Modifier) {
    val mode by controller.mode.collectAsState()
    QueensModeToggle(mode, controller::setMode, modifier)
}

/**
 * Two choices, **Queen** and **Paint** (tags `queens.editor.mode.crown` / `.paint`), as quiet
 * tiles in the Studio's violet selection style. The [mode] choice is selected and a tap on
 * either calls [onSelect].
 */
@Composable
internal fun QueensModeToggle(
    mode: QueensEditorMode,
    onSelect: (QueensEditorMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceTiles(
        options = QueensEditorMode.entries,
        selected = mode,
        onSelect = onSelect,
        label = { it.label },
        icon = QueensModeGlyphs::icon,
        tag = { "queens.editor.mode.${it.name.lowercase()}" },
        accent = AccentSlot.Violet,
        modifier = modifier.testTag("queens.editor.mode"),
    )
}

@PuzzleBoxPreviews
@Preview(name = "Crown", widthDp = 360, showBackground = true, group = "Queens mode toggle")
@Composable
private fun QueensModeToggleCrownPreview() {
    PuzzleBoxTheme { QueensModeToggle(QueensEditorMode.Crown, onSelect = {}) }
}

@PuzzleBoxPreviews
@Preview(name = "Paint", widthDp = 360, showBackground = true, group = "Queens mode toggle")
@Composable
private fun QueensModeTogglePaintPreview() {
    PuzzleBoxTheme { QueensModeToggle(QueensEditorMode.Paint, onSelect = {}) }
}

// The longest labels the tiles may carry; they wrap on a narrow strip instead of being cut.
@PuzzleBoxPreviews
@Preview(name = "Long labels", widthDp = 240, showBackground = true, group = "Queens mode toggle")
@Composable
private fun QueensModeToggleLongLabelsPreview() {
    PuzzleBoxTheme {
        ChoiceTiles(
            options = QueensEditorMode.entries,
            selected = QueensEditorMode.Crown,
            onSelect = {},
            label = { if (it == QueensEditorMode.Crown) "Place queens" else "Paint regions" },
            icon = QueensModeGlyphs::icon,
            tag = { "queens.editor.mode.${it.name.lowercase()}" },
            accent = AccentSlot.Violet,
            modifier = Modifier.testTag("queens.editor.mode"),
        )
    }
}
