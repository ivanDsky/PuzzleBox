package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/** One Sudoku game's editor UI: the play grid and keypad over a [SudokuEditorController]. */
class SudokuEditorUi(private val definition: SudokuDefinition) : GameEditorUi {

    override val gameId: GameId = definition.id

    override fun sizeLabel(variant: VariantId): String {
        val size = definition.variant.geometry.size
        return if (variant == definition.variant.id) "$size×$size" else variant.value
    }

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = SudokuEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        SudokuEditorBoard(controller as SudokuEditorController, definition.variant.geometry, modifier)
    }
}
