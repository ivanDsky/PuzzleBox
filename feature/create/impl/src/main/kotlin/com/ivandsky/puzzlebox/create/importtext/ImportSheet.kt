package com.ivandsky.puzzlebox.create.importtext

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
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
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ImportResult

private val BoxMinHeight = 160.dp
/** The Import button's height (PrimaryButton's minimum), so the sheet does not jump. */
private val CheckingMinHeight = 52.dp
private val SpinnerStroke = 3.dp
private const val TEXT_MIME = "text/*"

/**
 * The Import from text sheet (spec A23): a monospace box for the pasted notation
 * ([ImportSheetActions.onEdit]), an Import button that [ImportSheetActions.onSubmit]s the box
 * and stays off while the box is empty, and, after a failed import, one plain inline line
 * ([ImportErrorLine]). While [ImportSheetUi.checking] the button gives way to a spinner row,
 * the box turns read-only and a swipe cannot close the sheet (back and scrim still close it).
 * The box takes the height the sheet has left (keyboard included), at least [BoxMinHeight], and
 * scrolls inside, so a big paste never pushes the button or the error line off screen.
 * Draws nothing until [ImportSheetUi.visible]; [ImportSheetActions.onDismiss] closes it.
 *
 * Once per open, from this user-opened surface only, the clipboard's text goes to
 * [ImportSheetActions.onClipboard] (null when it holds none). When [ImportSheetUi.clipboard] is
 * set a banner above the box offers Paste ([ImportSheetActions.onPaste]) and a close button
 * ([ImportSheetActions.onDismissClipboard]). The sheet grows upward, so the banner sits above
 * the box: its arrival moves the title, never the box or the button. Hidden while a check runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    ui: ImportSheetUi,
    actions: ImportSheetActions,
    modifier: Modifier = Modifier,
) {
    if (!ui.visible) return
    val clipboard = LocalClipboard.current
    val manager = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // Saved, so an activity recreation does not read again; dropped with the sheet when it closes.
    var read by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!read) {
            read = true
            actions.onClipboard(clipboard.textOrNull(manager))
        }
    }
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        modifier = modifier.testTag("create.import.sheet"),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !ui.checking,
    ) {
        ImportSheetContent(ui, actions.onEdit, actions.onSubmit, actions.onPaste, actions.onDismissClipboard)
    }
}

/**
 * The clipboard's first item as text, or null when it is empty or holds something else. The
 * description is checked first: it carries no payload and shows no paste toast, so an image
 * is never read.
 */
private suspend fun Clipboard.textOrNull(manager: ClipboardManager): String? {
    if (manager.primaryClipDescription?.hasMimeType(TEXT_MIME) != true) return null
    val data = getClipEntry()?.clipData ?: return null
    if (data.itemCount == 0) return null
    return data.getItemAt(0).text?.toString()
}

@Composable
private fun ImportSheetContent(
    ui: ImportSheetUi,
    onEdit: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPaste: () -> Unit,
    onDismissClipboard: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = "Import from text",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ui.clipboard?.takeUnless { ui.checking }?.let { ClipboardBanner(it, onPaste, onDismissClipboard) }
        OutlinedTextField(
            value = ui.text,
            onValueChange = onEdit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = BoxMinHeight)
                .testTag("create.import.text"),
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

/** "Clipboard has {Game} puzzle text ({n} line|lines)". */
internal fun clipboardBannerText(clipboard: ImportSheetUi.ClipboardNotation): String {
    val noun = if (clipboard.lines == 1) "line" else "lines"
    return "Clipboard has ${clipboard.gameName} puzzle text (${clipboard.lines} $noun)"
}

/** "Clipboard has {Game} puzzle text ({n} lines)" with Paste and a close button. */
@Composable
private fun ClipboardBanner(clipboard: ImportSheetUi.ClipboardNotation, onPaste: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("create.import.clipboard"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clipboardBannerText(clipboard),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onPaste, modifier = Modifier.testTag("create.import.clipboard.paste")) {
                Text("Paste")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("create.import.clipboard.dismiss")) {
                Icon(PuzzleIcons.Close, contentDescription = "Dismiss")
            }
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
            onPaste = {},
            onDismissClipboard = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun ImportSheetClipboardPreview() {
    PuzzleBoxTheme {
        ImportSheetContent(
            ui = ImportSheetUi(
                visible = true,
                clipboard = ImportSheetUi.ClipboardNotation("Queens", lines = 6, text = "puzzlebox queens v1\n"),
            ),
            onEdit = {},
            onSubmit = {},
            onPaste = {},
            onDismissClipboard = {},
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
            onPaste = {},
            onDismissClipboard = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun ImportSheetPreview() {
    PuzzleBoxTheme {
        ImportSheet(ui = ImportSheetUi(visible = true), actions = ImportSheetActions())
    }
}
