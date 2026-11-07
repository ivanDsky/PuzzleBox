package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tangoui.TangoBoardRenderer

/** Widest the editor board grows, so a tablet pane does not stretch the cells. */
private val EditorBoardMaxWidth = 480.dp

/**
 * The editor's board: the play renderer over the [controller]'s draft, every cell a free
 * given (no cell is inert) so a tap cycles empty, sun, moon, empty ([TangoTapPolicy]).
 * Markers show as on the play board.
 */
@Composable
internal fun TangoEditorBoard(controller: TangoEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val board = (draft.state.draft as TangoDraft)
    val model = remember(board) { modelOf(board) }
    val renderer = remember(controller) { TangoBoardRenderer(onPlacement = { controller.place(it.idx, it.value) }) }
    renderer.Content(
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = EditorBoardMaxWidth)
            .testTag("tango.editor.board"),
    )
}

private fun modelOf(board: TangoDraft): TangoRendererModel = TangoRendererModel(
    n = TangoDraft.N,
    cells = List(TangoDraft.N * TangoDraft.N) { TangoRendererModel.CellRender(board.givens[it], isGiven = false) },
    markers = board.markers
        .sortedWith(compareBy({ it.cell }, { it.side.ordinal }))
        .map { TangoRendererModel.MarkerRender(it.cell, it.side, it.kind) },
    rejection = null,
    status = TangoRendererModel.Status.IDLE,
)

@PuzzleBoxPreviews
@Preview(name = "Tango editor board", widthDp = 360, heightDp = 420, showBackground = true)
@Composable
private fun TangoEditorBoardPreview() {
    val blank = EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())
    val givens = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON, 14 to TangoCell.SUN)
    val draft = EditorDraft(TangoEditor, blank.state.copy(draft = blank.state.draft.copy(givens = givens)))
    PuzzleBoxTheme { TangoEditorBoard(TangoEditorController(draft) {}) }
}
