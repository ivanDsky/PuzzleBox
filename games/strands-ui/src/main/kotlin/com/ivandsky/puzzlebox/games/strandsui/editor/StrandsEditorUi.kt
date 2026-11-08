package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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

    /** "Trace a word", or the selected path's word and length with a Remove action. */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        StrandsToolHint(controller as StrandsEditorController, modifier)
    }
}

@Composable
private fun StrandsToolHint(controller: StrandsEditorController, modifier: Modifier) {
    val draft by controller.draft.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val path = selected?.let { (draft.state.draft as StrandsDraft).paths.getOrNull(it) }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (path == null) "Trace a word" else "${path.word ?: "Unnamed"} · ${path.cells.size}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.testTag("strands.editor.hint"),
        )
        if (selected != null) {
            TextButton(onClick = { selected?.let(controller::removePath) }, modifier = Modifier.testTag("strands.editor.remove")) {
                Text("Remove")
            }
        }
    }
}
