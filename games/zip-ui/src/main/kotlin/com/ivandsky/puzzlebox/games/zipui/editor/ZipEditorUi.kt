package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/** Zip's editor UI: the play board drawn over a [ZipEditorController], the author's path drawn by dragging. */
object ZipEditorUi : GameEditorUi {

    override val gameId: GameId = ZipDefinition.id

    override fun sizeLabel(variant: VariantId): String {
        val n = ZipVariants.byId(variant)?.size ?: return variant.value
        return "$n×$n"
    }

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = ZipEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        ZipEditorBoard(controller as ZipEditorController, modifier)
    }

    /** The covered-cell count, and the next step once the path covers the board. */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        ZipEditorHint(controller as ZipEditorController, modifier)
    }
}
