package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/** Queens' editor UI: the paint-a-cell board over a [QueensEditorController]. */
object QueensEditorUi : GameEditorUi {

    override val gameId: GameId = QueensDefinition.id

    override fun sizeLabel(variant: VariantId): String {
        val n = QueensVariants.byId(variant)?.size ?: return variant.value
        return "$n×$n"
    }

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = QueensEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        QueensEditorBoard(controller as QueensEditorController, modifier)
    }
}
