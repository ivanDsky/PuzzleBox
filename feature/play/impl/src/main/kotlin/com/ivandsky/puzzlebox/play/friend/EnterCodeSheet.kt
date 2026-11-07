package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.launch

/**
 * Open a friend's puzzle by its code: one field and Open, enabled once the field parses as a
 * [ShareCode] (case, dashes and spaces don't matter). [onOpen] gets the parsed code after the
 * sheet hides; the IME's Go submits too.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnterCodeSheet(onOpen: (ShareCode) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("friend.code.sheet"),
        sheetState = sheetState,
    ) {
        EnterCodeContent(
            onOpen = { code -> scope.launch { sheetState.hide() }.invokeOnCompletion { onOpen(code) } },
            autoFocus = true,
        )
    }
}

@Composable
private fun EnterCodeContent(onOpen: (ShareCode) -> Unit, autoFocus: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val code = ShareCode.parse(text)
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Open a friend's puzzle", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Type the 6-character code they sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("friend.code.field"),
            singleLine = true,
            placeholder = { Text("K7Q-2MX") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { code?.let(onOpen) }),
        )
        PrimaryButton(
            text = "Open puzzle",
            onClick = { code?.let(onOpen) },
            enabled = code != null,
            modifier = Modifier.fillMaxWidth().testTag("friend.code.open"),
        )
    }
}

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentPreview() {
    PuzzleBoxTheme {
        EnterCodeContent(onOpen = {}, autoFocus = false)
    }
}
