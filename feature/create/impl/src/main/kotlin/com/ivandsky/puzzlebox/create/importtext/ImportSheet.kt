package com.ivandsky.puzzlebox.create.importtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ImportResult

private val BoxMinHeight = 160.dp

/**
 * The Import from text sheet (spec A23): a monospace box for the pasted notation ([onEdit]),
 * an Import button that [onSubmit]s the box and stays off while the box is empty or a check
 * runs, and, after a failed import, one plain inline line. Draws nothing until
 * [ImportSheetUi.visible]; [onDismiss] closes it.
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
            isError = ui.error != null,
        )
        ui.error?.let { error ->
            Text(
                text = errorLine(error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("create.import.error"),
            )
        }
        PrimaryButton(
            text = "Import",
            onClick = { onSubmit(ui.text) },
            modifier = Modifier.fillMaxWidth().testTag("create.import.submit"),
            enabled = ui.text.isNotEmpty() && !ui.checking,
            loading = ui.checking,
        )
    }
}

/** "Line {line}: {message}" for a failure that names a line; one plain sentence for the rest. */
private fun errorLine(error: ImportResult): String = when (error) {
    is ImportResult.Malformed -> "Line ${error.line}: ${error.message}"
    is ImportResult.TooLarge -> "Line ${error.line}: The text is too large to import."
    is ImportResult.UnknownGame -> "Unknown game “${error.gameId}”."
    is ImportResult.UnsupportedVersion -> "Version ${error.found} is newer than this app reads (v${error.maxKnown})."
    is ImportResult.Imported -> ""
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
private fun ImportSheetPreview() {
    PuzzleBoxTheme {
        ImportSheet(ui = ImportSheetUi(visible = true), onEdit = {}, onSubmit = {}, onDismiss = {})
    }
}
