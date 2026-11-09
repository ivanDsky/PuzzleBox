package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoPublishGates
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

private val FixRowMinHeight = 48.dp

/** Tango's editor UI: the play board over a [TangoEditorController]. */
object TangoEditorUi : GameEditorUi {

    override val gameId: GameId = TangoDefinition.id

    override fun sizeLabel(variant: VariantId): String {
        val n = TangoDraft.sizeOf(variant) ?: return variant.value
        return "$n×$n"
    }

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = TangoEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        TangoEditorBoard(controller as TangoEditorController, modifier, verdict)
    }

    /** The press-preview-lift line and the Tap zones and Forced cells toggles. */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        val tango = controller as TangoEditorController
        val zones by tango.tapZones.collectAsState()
        val forced by tango.forcedView.collectAsState()
        Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                TangoEditorCopy.TOOL_HINT,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().testTag("tango.editor.hint"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = zones,
                    onClick = tango::toggleTapZones,
                    label = { Text(TangoEditorCopy.TAP_ZONES, maxLines = 1) },
                    modifier = Modifier.testTag("tango.editor.tapZones"),
                )
                FilterChip(
                    selected = forced,
                    onClick = tango::toggleForcedView,
                    label = { Text(TangoEditorCopy.FORCED_CELLS, maxLines = 1) },
                    modifier = Modifier.testTag("tango.editor.forced.toggle"),
                )
            }
        }
    }

    /** One row per Incomplete reason and Tango block, each tagged `tango.editor.fix.<id>`. */
    @Composable
    override fun Issues(
        controller: GameEditorController,
        verdict: Verdict,
        blocks: List<PublishBlock>,
        onSelect: () -> Unit,
        modifier: Modifier,
    ) {
        val ids = buildList {
            if (verdict is Verdict.Incomplete) add(verdict.reason.id)
            blocks.filter { it.id in TangoPublishGates.blockIds }.forEach { add(it.id) }
        }
        val rows = ids.mapNotNull { id -> TangoEditorCopy.fix(id)?.let { id to it } }
        if (rows.isEmpty()) return
        Column(modifier.fillMaxWidth().testTag("tango.editor.fix")) {
            rows.forEachIndexed { index, (id, text) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = FixRowMinHeight)
                        .testTag("tango.editor.fix.$id"),
                )
            }
        }
    }
}
