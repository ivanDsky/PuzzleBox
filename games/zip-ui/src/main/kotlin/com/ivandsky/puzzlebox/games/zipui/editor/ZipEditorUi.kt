package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

private val FixRowMinHeight = 48.dp

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
    override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) {
        ZipEditorBoard(controller as ZipEditorController, modifier, verdict)
    }

    /** The covered-cell count, and the next step once the path covers the board. */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        ZipEditorHint(controller as ZipEditorController, modifier)
    }

    /** One row per Incomplete reason, tagged `zip.editor.fix.<id>`; Zip has no publish blocks. */
    @Composable
    override fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        onSelect: () -> Unit,
        modifier: Modifier,
    ) {
        val id = (verdict as? Verdict.Incomplete)?.reason?.id ?: return
        val text = ZipEditorCopy.fix(id) ?: return
        Column(modifier.fillMaxWidth().testTag("zip.editor.fix")) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FixRowMinHeight)
                    .testTag("zip.editor.fix.$id"),
            )
        }
    }
}
