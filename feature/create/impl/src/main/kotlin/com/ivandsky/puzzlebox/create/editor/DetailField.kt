package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * One capped publish-details field, with its `n/[max]` counter and, while [tooLong], its inline
 * error. It keeps its own text, so typing never waits on the session; until the author types in
 * it, a new [value] (the display name filled in after sign-in) replaces it.
 */
@Composable
internal fun DetailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    max: Int,
    tooLong: Boolean,
    tag: String,
    enabled: Boolean,
    singleLine: Boolean,
    capitalization: KeyboardCapitalization,
    modifier: Modifier = Modifier,
    focus: Boolean = false,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var edited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!edited && value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    val focusRequester = remember { FocusRequester() }
    if (focus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            val changed = next.text != field.text
            field = next
            if (changed) {
                edited = true
                onValueChange(next.text)
            }
        },
        modifier = modifier.focusRequester(focusRequester).testTag(tag),
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else NOTE_LINES,
        isError = tooLong,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        supportingText = { CapSupport(field.text.trimmedLength(), max, tooLong, tag) },
    )
}

/** A capped field's supporting line: while [tooLong] its "Up to [max]" error, and its `[length]/[max]` counter. */
@Composable
internal fun CapSupport(length: Int, max: Int, tooLong: Boolean, tag: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = if (tooLong) "Up to $max characters" else "",
            modifier = Modifier.weight(1f).then(if (tooLong) Modifier.testTag("$tag.error") else Modifier),
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "$length/$max",
            modifier = Modifier.testTag("$tag.counter"),
            color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/** Characters as the caps count them ([com.ivandsky.puzzlebox.domain.ValidatePublishDetails]): code points, once trimmed. */
internal fun String.trimmedLength(): Int = trim().let { it.codePointCount(0, it.length) }

private const val NOTE_LINES = 3
