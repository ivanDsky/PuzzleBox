package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/**
 * Strands' editor UI: the play board over a [StrandsEditorController]. The authoring tools sit
 * under the board on a phone and in the side pane on a tablet; the clue and the word list
 * credit wait for the publish details.
 */
object StrandsEditorUi : GameEditorUi {

    override val gameId: GameId = StrandsDefinition.id

    override fun sizeLabel(variant: VariantId): String = "${StrandsDraft.ROWS}×${StrandsDraft.COLS}"

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = StrandsEditorController(initial, onEdit)

    /** The grid and, on a phone, the word field under it; on a tablet the grid alone. */
    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) {
        StrandsEditorBoard(controller as StrandsEditorController, modifier, verdict, withTools = layout == PlayLayout.SinglePane)
    }

    /** The tablet's [StrandsAuthoringTools]: word field and path rows. */
    @Composable
    override fun PaneTools(controller: GameEditorController, modifier: Modifier) {
        StrandsAuthoringTools(controller as StrandsEditorController, modifier)
    }

    /** The theme clue and the word list credit. */
    @Composable
    override fun PublishFields(controller: GameEditorController, enabled: Boolean, modifier: Modifier) {
        StrandsPublishFields(controller as StrandsEditorController, enabled, modifier)
    }

    /** A missing or too-long clue reads as a field to fill, not a board to fix. */
    override val publishFieldCopy: Map<String, String> = mapOf(StrandsPublishGates.CLUE_LENGTH to StrandsEditorCopy.CLUE_READINESS)

    /**
     * "Trace a word", or the selected path's word and length with its spangram toggle and a
     * Remove action: in the tool strip, so both stay in reach on a phone however tall the board.
     */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        StrandsToolHint(controller as StrandsEditorController, modifier)
    }

    /** The reasons and blocks to fix, one row each; Select picks the word a row is about. */
    @Composable
    override fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        onSelect: () -> Unit,
        modifier: Modifier,
    ) {
        StrandsFixList(controller as StrandsEditorController, verdict, blocks, onSelect, modifier)
    }
}

@Composable
private fun StrandsToolHint(controller: StrandsEditorController, modifier: Modifier) {
    val draft by controller.draft.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val board = draft.state.draft as StrandsDraft
    val path = selected?.let { board.paths.getOrNull(it) }
    // The row reserves Remove's touch target with or without a selection, so selecting a path
    // never grows the tools and shrinks the phone board above them.
    Row(
        modifier.fillMaxWidth().heightIn(min = LocalMinimumInteractiveComponentSize.current),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (path == null) "Trace a word" else "${path.word ?: "Unnamed"} · ${path.cells.size}",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).testTag("strands.editor.hint"),
        )
        selected?.let { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StrandsSpangramToggle(
                    index = index,
                    isSpangram = board.spangram == index,
                    onToggle = { controller.toggleSpangram(index) },
                )
                TextButton(onClick = { controller.removePath(index) }, modifier = Modifier.testTag("strands.editor.remove")) {
                    Text("Remove")
                }
            }
        }
    }
}

/** One fix-list row: the copy and the path its Select picks, when it is about one. */
private data class StrandsFixRow(val id: String, val text: String, val path: Int?)

private fun strandsFixRows(draft: StrandsDraft, verdict: Verdict, blocks: List<PublishBlock>): List<StrandsFixRow> =
    buildList {
        (verdict as? Verdict.Incomplete)?.reason?.let { reason ->
            val unnamed = draft.paths.indexOfFirst { it.word.isNullOrBlank() }
            val path = if (reason.id == StrandsDraftAnalyzer.UNNAMED) unnamed.takeIf { it >= 0 } else null
            StrandsEditorCopy.fix(reason.id, reason.count, "")?.let { add(StrandsFixRow(reason.id, it, path)) }
        }
        // The clue is a publish field, not the board: the readiness line names it (publishFieldCopy).
        blocks.filter { it.id in StrandsPublishGates.blockIds && it.id != StrandsPublishGates.CLUE_LENGTH }.forEach { block ->
            val path = block.subject.takeIf { it in draft.paths.indices }
            val word = path?.let { draft.paths[it].word } ?: "Word ${block.subject + 1}"
            StrandsEditorCopy.fix(block.id, block.count, word)?.let { add(StrandsFixRow(block.id, it, path)) }
        }
    }

/** Rows tagged `strands.editor.fix.<id>`; a row about a path has `.select`, which picks that path. */
@Composable
private fun StrandsFixList(
    controller: StrandsEditorController,
    verdict: Verdict,
    blocks: List<PublishBlock>,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val editorDraft by controller.draft.collectAsState()
    val draft = editorDraft.state.draft as StrandsDraft
    val rows = remember(draft, verdict, blocks) { strandsFixRows(draft, verdict, blocks) }
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth().testTag("strands.editor.fix")) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().heightIn(min = FIX_ROW_MIN_HEIGHT).testTag("strands.editor.fix.${row.id}")
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                row.path?.let { path ->
                    TextButton(
                        onClick = {
                            controller.selectPath(path)
                            onSelect()
                        },
                        modifier = Modifier.testTag("strands.editor.fix.${row.id}.select"),
                    ) { Text("Select") }
                }
            }
        }
    }
}

private val FIX_ROW_MIN_HEIGHT = 48.dp
