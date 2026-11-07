package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
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
 * says Google fills it. Rows are disabled while not [enabled] (a publish runs). [paired] sets the
 * title and the author side by side, for a short slot; an [action] ends the "+ Add a note" row.
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
    paired: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    var noteOpen by rememberSaveable { mutableStateOf(details.note.isNotEmpty()) }
    var focusNote by remember { mutableStateOf(false) }
    val blocked = remember(details.blocks) { details.blocks.map(PublishBlock::id).toSet() }
    Column(modifier.testTag("create.details"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Paired(paired) { half ->
            TitleRow(title, enabled, counted = !paired, half) { renaming = true }
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
                modifier = half,
            )
        }
        if (!noteOpen || action != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.weight(1f))
                action?.invoke()
            }
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
                modifier = Modifier.fillMaxWidth(),
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

/** The details folded into one line, "Castle · by Ivan", while the board is edited; a tap ([onClick]) opens them again. */
@Composable
internal fun PublishDetailsSummary(title: String, authorName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick).testTag("create.details.summary"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${title.ifBlank { "Name your puzzle" }} · by ${authorName.trim().ifBlank { "Anonymous" }}",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(PuzzleIcons.Create, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(SummaryIconSize))
    }
}

private val SummaryIconSize = 16.dp

/** [content]'s two rows side by side, each half the width, while [paired]; else stacked full width. */
@Composable
private fun Paired(paired: Boolean, content: @Composable (Modifier) -> Unit) {
    if (paired) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { content(Modifier.weight(1f)) } else content(Modifier.fillMaxWidth())
}

/** The title as it will be shared, with its counter while [counted]; a tap opens Rename. */
@Composable
private fun TitleRow(title: String, enabled: Boolean, counted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.testTag("create.details.title"),
    ) {
        Row(
            Modifier.padding(horizontal = if (counted) Spacing.lg else Spacing.md, vertical = Spacing.md),
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
            if (counted) {
                Text(
                    text = "${title.trimmedLength()}/${Draft.TITLE_MAX}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(PuzzleIcons.Create, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

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
