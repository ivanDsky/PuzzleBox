package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/** Tango's editor UI: the play board over a [TangoEditorController]. */
object TangoEditorUi : GameEditorUi {

    override val gameId: GameId = TangoDefinition.id

    override fun sizeLabel(variant: VariantId): String = "${TangoDraft.N}×${TangoDraft.N}"

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = TangoEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        TangoEditorBoard(controller as TangoEditorController, modifier, verdict)
    }
}
