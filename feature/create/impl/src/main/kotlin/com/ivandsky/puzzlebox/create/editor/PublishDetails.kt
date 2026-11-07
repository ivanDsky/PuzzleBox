package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.ValidatePublishDetails
import com.ivandsky.puzzlebox.game.editor.PublishBlock

/**
 * The inline details before publishing (08 handoff "details"): the [title] row, which opens
 * Rename ([onRename]); the author name, "by", from [details], with an `n/40` counter; and the
 * note behind "+ Add a note", with an `n/140` counter. Counters count trimmed characters; over a
 * cap the field shows its inline error. Signed out ([signedIn] false) the author placeholder
 * says Google fills it. Rows are disabled while not [enabled] (a publish runs).
 *
 * Motion: the note row **Expands** in with a **Fade** (EnterDuration on the emphasized
 * decelerate curve); under reduced motion it only fades.
 */
@Composable
fun PublishDetails(
    title: String,
    details: PublishSession.Details,
    signedIn: Boolean,
    onRename: (String) -> Unit,
    onAuthorName: (String) -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var noteOpen by rememberSaveable { mutableStateOf(details.note.isNotEmpty()) }
    var focusNote by remember { mutableStateOf(false) }
    val blocked = remember(details.blocks) { details.blocks.map(PublishBlock::id).toSet() }
    Column(modifier.testTag("create.details"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        TitleRow(title = title, enabled = enabled, onClick = { renaming = true })
        DetailField(
            value = details.authorName,
            onValueChange = onAuthorName,
            label = "by",
            placeholder = if (signedIn) "Anonymous" else "Filled from Google on sign-in",
            max = Draft.AUTHOR_MAX,
            tooLong = PublishBlock.AUTHOR_TOO_LONG in blocked,
            tag = "create.details.author",
            enabled = enabled,
            singleLine = true,
            capitalization = KeyboardCapitalization.Words,
        )
        if (!noteOpen) {
            AppTextButton(
                text = "+ Add a note",
                onClick = {
                    noteOpen = true
                    focusNote = true
                },
                enabled = enabled,
                modifier = Modifier.testTag("create.details.addNote"),
            )
        }
        val reduced = rememberReducedMotion()
        AnimatedVisibility(
            visible = noteOpen,
            enter = if (reduced) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration))
            } else {
                expandVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                    fadeIn(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
            },
            exit = ExitTransition.None,
        ) {
            DetailField(
                value = details.note,
                onValueChange = onNote,
                label = "Note",
                placeholder = "Optional, shown before they start",
                max = Draft.NOTE_MAX,
                tooLong = PublishBlock.NOTE_TOO_LONG in blocked,
                tag = "create.details.note",
                enabled = enabled,
                singleLine = false,
                capitalization = KeyboardCapitalization.Sentences,
                focus = focusNote,
            )
        }
    }
    if (renaming) {
        RenameSheet(
            title = title,
            onSave = { next ->
                renaming = false
                onRename(next)
            },
            onDismiss = { renaming = false },
        )
    }
}

/** The title as it will be shared, with its counter; a tap opens Rename. */
@Composable
private fun TitleRow(title: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag("create.details.title"),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Title", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = title.ifBlank { "Name your puzzle" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${title.trimmedLength()}/${Draft.TITLE_MAX}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(PuzzleIcons.Create, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One capped field. It keeps its own text, so typing never waits on the session; until the
 * author types in it, a new [value] (the display name filled in after sign-in) replaces it.
 */
@Composable
private fun DetailField(
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
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag(tag),
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
        supportingText = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    text = if (tooLong) "Up to $max characters" else "",
                    modifier = Modifier.weight(1f).then(if (tooLong) Modifier.testTag("$tag.error") else Modifier),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "${field.text.trimmedLength()}/$max",
                    modifier = Modifier.testTag("$tag.counter"),
                    color = if (tooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        },
    )
}

/** Characters as the caps count them ([ValidatePublishDetails]): code points, once trimmed. */
private fun String.trimmedLength(): Int = trim().let { it.codePointCount(0, it.length) }

private const val NOTE_LINES = 3

@PuzzleBoxPreviews
@Composable
private fun PublishDetailsPreview() {
    PuzzleBoxTheme {
        PublishDetails(
            title = "Castle",
            details = PublishSession.Details(authorName = "Ivan"),
            signedIn = true,
            onRename = {},
            onAuthorName = {},
            onNote = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Signed out: the author name waits for Google. */
@PuzzleBoxPreviews
@Composable
private fun PublishDetailsSignedOutPreview() {
    PreviewDetails(PublishSession.Details(), signedIn = false)
}

/** Long values: a 60-character title, an author name one over its cap (inline error) and a full note. */
@PuzzleBoxPreviews
@Composable
private fun PublishDetailsLongPreview() {
    val author = "Ivan the Puzzle Maker of the Northern Hills"
    val note = "Start in the corner where the violet region bends; every other region follows from it. Take your time and enjoy the climb!!"
    PreviewDetails(
        details = PublishSession.Details(author, note, ValidatePublishDetails()(author, note)),
        title = "The very long title of a castle puzzle with violet towers ok",
    )
}

/** Publishing: the rows are disabled. */
@PuzzleBoxPreviews
@Composable
private fun PublishDetailsBusyPreview() {
    PreviewDetails(PublishSession.Details(authorName = "Ivan", note = "Start in the corner"), enabled = false)
}

@Composable
private fun PreviewDetails(details: PublishSession.Details, signedIn: Boolean = true, enabled: Boolean = true, title: String = "Castle") {
    PuzzleBoxTheme {
        PublishDetails(title, details, signedIn, onRename = {}, onAuthorName = {}, onNote = {}, Modifier.fillMaxWidth(), enabled)
    }
}
