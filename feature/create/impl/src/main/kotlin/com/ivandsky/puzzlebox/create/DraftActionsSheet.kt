package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.ui.VerdictChip
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import kotlinx.coroutines.launch

private val ActionRowMinHeight = 56.dp

/**
 * A draft's actions (01 handoff "Draft actions sheet", opened by the row's ⋮ or a long press):
 * a header with the [row]'s glyph tile, title, verdict chip and meta line, then "Continue
 * editing" ([onContinue]), "Duplicate" ([onDuplicate]) and "Delete draft" ([onDelete], in the
 * error colour). Each action slides the sheet away first, then reports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftActionsSheet(
    row: CreateUiState.DraftRow,
    onContinue: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("create.actions.sheet"),
        sheetState = sheetState,
    ) {
        DraftActionsContent(
            row = row,
            onContinue = { hideThen(onContinue) },
            onDuplicate = { hideThen(onDuplicate) },
            onDelete = { hideThen(onDelete) },
        )
    }
}

@Composable
private fun DraftActionsContent(
    row: CreateUiState.DraftRow,
    onContinue: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Header(row)
        HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
        ActionRow("Continue editing", "create.actions.continue", MaterialTheme.colorScheme.onSurface, onContinue)
        ActionRow("Duplicate", "create.actions.duplicate", MaterialTheme.colorScheme.onSurface, onDuplicate)
        ActionRow("Delete draft", "create.actions.delete", MaterialTheme.colorScheme.error, onDelete)
    }
}

@Composable
private fun Header(row: CreateUiState.DraftRow) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameGlyph(row.brand, GameGlyphSize.Tile)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DraftMetaLine(row.meta, row.imported)
        }
        row.verdict?.let { VerdictChip(it) }
    }
}

@Composable
private fun ActionRow(label: String, tag: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ActionRowMinHeight)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

private val PreviewRow = CreateUiState.DraftRow(
    id = "a",
    gameId = PreviewGames.first().id,
    brand = PreviewGames.first().brand,
    title = "Sunday crowns",
    meta = "Queens · 8×8",
    edited = "edited 2 h ago",
    verdict = Verdict.Checking,
)

@PuzzleBoxPreviews
@Composable
private fun DraftActionsSheetPreview() {
    PuzzleBoxTheme {
        DraftActionsSheet(row = PreviewRow, onContinue = {}, onDuplicate = {}, onDelete = {}, onDismiss = {})
    }
}

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun DraftActionsContentPreview() {
    PuzzleBoxTheme {
        Surface {
            DraftActionsContent(
                row = PreviewRow.copy(title = "A very long draft title that runs past the end of the sheet header", verdict = null),
                onContinue = {},
                onDuplicate = {},
                onDelete = {},
            )
        }
    }
}
