package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/** Strands' editor UI: the play board over a [StrandsEditorController]. */
object StrandsEditorUi : GameEditorUi {

    override val gameId: GameId = StrandsDefinition.id

    override fun sizeLabel(variant: VariantId): String = "${StrandsDraft.ROWS}×${StrandsDraft.COLS}"

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = StrandsEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        StrandsEditorBoard(controller as StrandsEditorController, modifier)
    }
}
