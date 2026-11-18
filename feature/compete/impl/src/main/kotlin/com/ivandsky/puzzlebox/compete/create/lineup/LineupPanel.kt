package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelGame
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val FLASH_MS = 600L
private val ADD_MIN_HEIGHT = 56.dp
private val ADD_SHAPE = RoundedCornerShape(12.dp)
private val ROW_GAP = Spacing.sm
private val DASH = floatArrayOf(16f, 12f)

/**
 * The Customize panel's puzzles: "Puzzles, in order · N of 10", one row per puzzle, then the dashed Add row, which at
 * the limit says so instead. Every change goes through [onEdit] as the edit to apply to the [editor]; the add sheet shows
 * while the editor has a target.
 */
@Composable
internal fun LineupPanel(
    editor: LineupEditor,
    games: List<DuelGame>,
    includeFriends: Boolean,
    onEdit: ((LineupEditor) -> LineupEditor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val registry = koinInject<GamePlayRegistry>()
    val draft = editor.draft
    val edit by rememberUpdatedState(onEdit)
    val heights = remember { IntArray(Match.MAX_LEGS) }
    val gap = with(LocalDensity.current) { ROW_GAP.toPx() }
    var drag by remember { mutableStateOf<ActiveDrag?>(null) }
    val landed = drag?.let { it.dropped && (draft != it.base || it.reorder.target == it.reorder.lifted) } == true
    LaunchedEffect(editor.flashed, draft) {
        if (editor.flashed != null) {
            delay(FLASH_MS)
            edit { it.flashDone() }
        }
    }
    Column(modifier.fillMaxWidth().testTag("duel.lineup"), verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        Text(
            text = "Puzzles, in order · ${draft.size} of ${Match.MAX_LEGS}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("duel.lineup.header"),
        )
        draft.entries.forEachIndexed { index, entry ->
            LineupSlot(
                index = index,
                drag = drag,
                landed = landed,
                onSize = { heights[index] = it },
                onSettled = { drag = null },
            ) { placed, lifted ->
                LineupRow(
                    position = index + 1,
                    model = entry.rowModel(games.firstOrNull { it.id == entry.gameId }?.name ?: entry.gameId.value, includeFriends),
                    brand = registry.brandOf(entry.gameId),
                    canRemove = draft.canRemove,
                    flashed = editor.flashed == index,
                    refused = editor.marked == index,
                    lifted = lifted,
                    drag = RowDrag(
                        onStart = { if (drag == null) drag = ActiveDrag(LineupReorder.lift(List(draft.size) { heights[it] + gap }, index), draft) },
                        onDelta = { dy -> drag = drag?.takeIf { !it.dropped }?.let { it.copy(reorder = it.reorder.dragged(dy)) } ?: drag },
                        onEnd = {
                            drag = drag?.takeIf { !it.dropped }?.also { held ->
                                val (from, to) = held.reorder.lifted to held.reorder.target
                                if (from != to) edit { it.moved(from, to) }
                            }?.copy(dropped = true) ?: drag
                        },
                        onCancel = { drag = null },
                    ),
                    onClick = { edit { it.openChange(index) } },
                    onRemove = { edit { it.remove(index) } },
                    modifier = placed,
                )
            }
        }
        if (draft.canAdd) {
            AddRow(position = draft.size + 1, onClick = { edit { it.openAdd() } })
        } else {
            Text(
                text = "${Match.MAX_LEGS} puzzles is the most a duel holds. Remove one to add another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm).testTag("duel.lineup.max"),
            )
        }
    }
    editor.target?.let { row ->
        AddPuzzleSheet(
            position = row + 1,
            changing = editor.changing,
            games = games,
            onPickGame = { game -> edit { it.pick(AnyGame(game)) } },
            onDismiss = { edit { it.dismiss() } },
        )
    }
}

@Composable
private fun AddRow(position: Int, onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ADD_MIN_HEIGHT)
            .drawBehind {
                drawRoundRect(line, cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(DASH)))
            }
            .testTag("duel.lineup.add"),
        shape = ADD_SHAPE,
        color = Color.Transparent,
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text("Add puzzle $position", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("A game, or an exact puzzle by name or code", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
