package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.launch

/**
 * Rename (02 handoff "Dialogs › Rename"): a field holding the current [title], all selected, a
 * `{len}/60` counter, a dice button that fills the field with a fresh [onRoll] codename, Cancel ([onDismiss]) and Save ([onSave] with the trimmed title), which
 * is enabled only for 1–[Draft.TITLE_MAX] characters (spec A25); over the cap the counter
 * turns the error colour.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameSheet(
    title: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onRoll: suspend () -> String,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("create.rename.sheet"),
        sheetState = sheetState,
    ) {
        RenameContent(
            title = title,
            onSave = { next -> hideThen { onSave(next) } },
            onCancel = { hideThen(onDismiss) },
            onRoll = onRoll,
            autoFocus = true,
        )
    }
}

@Composable
private fun RenameContent(title: String, onSave: (String) -> Unit, onCancel: () -> Unit, onRoll: suspend () -> String, autoFocus: Boolean) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(title, selection = TextRange(0, title.length)))
    }
    val length = field.text.trim().length
    val valid = DraftMeta.isValidTitle(field.text)
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Rename draft", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Players see this title on the shared puzzle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("create.rename.field"),
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { scope.launch { field = onRoll().let { TextFieldValue(it, selection = TextRange(it.length)) } } },
                    modifier = Modifier.testTag("create.rename.dice"),
                ) {
                    Icon(PuzzleIcons.Dice, contentDescription = "Suggest a name")
                }
            },
            isError = length > Draft.TITLE_MAX,
            supportingText = {
                Text(
                    text = "$length/${Draft.TITLE_MAX}",
                    modifier = Modifier.fillMaxWidth().testTag("create.rename.counter"),
                    color = if (length > Draft.TITLE_MAX) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
            SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.testTag("create.rename.cancel"))
            PrimaryButton(
                text = "Save",
                onClick = { onSave(field.text.trim()) },
                enabled = valid,
                modifier = Modifier.testTag("create.rename.save"),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun RenameSheetPreview() {
    PuzzleBoxTheme {
        RenameSheet(title = "Castle", onSave = {}, onDismiss = {}, onRoll = { "Amber Falcon" })
    }
}

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun RenameContentPreview() {
    PuzzleBoxTheme {
        RenameContent(title = "Castle", onSave = {}, onCancel = {}, onRoll = { "Amber Falcon" }, autoFocus = false)
    }
}

/** Over the cap: the counter in the error colour and Save disabled. */
@PuzzleBoxPreviews
@Composable
private fun RenameContentTooLongPreview() {
    PuzzleBoxTheme {
        RenameContent(title = "x".repeat(Draft.TITLE_MAX + 1), onSave = {}, onCancel = {}, onRoll = { "Amber Falcon" }, autoFocus = false)
    }
}
