package com.ivandsky.puzzlebox.create.importtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ImportResult

private val BoxMinHeight = 160.dp
/** The Import button's height (PrimaryButton's minimum), so the sheet does not jump. */
private val CheckingMinHeight = 52.dp
private val SpinnerStroke = 3.dp

/**
 * The Import from text sheet (spec A23): a monospace box for the pasted notation ([onEdit]),
 * an Import button that [onSubmit]s the box and stays off while the box is empty, and, after a
 * failed import, one plain inline line ([ImportErrorLine]). While [ImportSheetUi.checking] the
 * button gives way to a spinner row, the box turns read-only and a swipe cannot close the
 * sheet (back and scrim still close it). Draws nothing until [ImportSheetUi.visible];
 * [onDismiss] closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    ui: ImportSheetUi,
    onEdit: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!ui.visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("create.import.sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !ui.checking,
    ) {
        ImportSheetContent(ui, onEdit, onSubmit)
    }
}

@Composable
private fun ImportSheetContent(ui: ImportSheetUi, onEdit: (String) -> Unit, onSubmit: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Import from text",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = ui.text,
            onValueChange = onEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = BoxMinHeight).testTag("create.import.text"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            readOnly = ui.checking,
            isError = ui.error != null,
        )
        ui.error?.let { ImportErrorLine(it) }
        if (ui.checking) {
            CheckingRow()
        } else {
            PrimaryButton(
                text = "Import",
                onClick = { onSubmit(ui.text) },
                modifier = Modifier.fillMaxWidth().testTag("create.import.submit"),
                enabled = ui.text.isNotEmpty(),
            )
        }
    }
}

/** Stands where the Import button was while the use case runs; read as one status. */
@Composable
private fun CheckingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CheckingMinHeight)
            .semantics(mergeDescendants = true) {}
            .testTag("create.import.checking"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Spacing.xl),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = SpinnerStroke,
        )
        Text(
            text = "Checking the text…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun ImportSheetContentPreview() {
    PuzzleBoxTheme {
        ImportSheetContent(
            ui = ImportSheetUi(
                visible = true,
                text = "puzzlebox queens v1\n00111\n",
                error = ImportResult.Malformed(line = 3, col = 1, message = "expected 5 rows"),
            ),
            onEdit = {},
            onSubmit = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun ImportSheetCheckingPreview() {
    PuzzleBoxTheme {
        ImportSheetContent(
            ui = ImportSheetUi(visible = true, text = "puzzlebox queens v1\n00111\n", checking = true),
            onEdit = {},
            onSubmit = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun ImportSheetPreview() {
    PuzzleBoxTheme {
        ImportSheet(ui = ImportSheetUi(visible = true), onEdit = {}, onSubmit = {}, onDismiss = {})
    }
}
