package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict

private val StudioTopBarHeight = 64.dp
private val TagHorizontalPadding = 6.dp

/**
 * The Studio's compact bar (02 handoff "Top bar"): Back, the title block — a button that opens
 * Rename ([onRename]), the [title] on one line over a `STUDIO` tag, the [subtitle]
 * ("Queens 7×7") and the autosave indicator ("Saving…" while [saving], then "✓ Saved") — then
 * the [pill] slot and the overflow ⋮ with Rename, Duplicate as new draft ([onDuplicate]) and
 * Delete draft ([onDelete], in the error colour). A published puzzle is immutable and its
 * draft is the record its published page shows, so [readOnly] the title opens nothing and the
 * menu holds Duplicate and, while the code is live, Unpublish… ([onUnpublish], in the error
 * colour; null once unpublished).
 *
 * Motion: the title block's press is **Press feedback** at MotionTokens.PressScale; the menu's
 * origin-aware **Scale in + Fade in** from the ⋮ is DropdownMenu's own.
 */
@Composable
fun StudioTopBar(
    title: String,
    subtitle: String,
    saving: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    pill: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onUnpublish: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(StudioTopBarHeight)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("create.editor.back")) {
            Icon(PuzzleIcons.Back, contentDescription = "Back")
        }
        TitleBlock(title = title, subtitle = subtitle, saving = saving, onClick = onRename.takeUnless { readOnly }, modifier = Modifier.weight(1f))
        pill()
        OverflowMenu(
            onRename = onRename.takeUnless { readOnly },
            onDuplicate = onDuplicate,
            onDelete = onDelete.takeUnless { readOnly },
            onUnpublish = onUnpublish.takeIf { readOnly },
        )
    }
}

@Composable
private fun TitleBlock(title: String, subtitle: String, saving: Boolean, onClick: (() -> Unit)?, modifier: Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val press = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .pressScale(interactionSource, rememberReducedMotion())
            .clip(AppShapes.small)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .then(press)
            .testTag("create.studio.title")
            .padding(horizontal = Spacing.xs),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StudioTag()
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            SaveStatus(saving)
        }
    }
}

/** `· Saving…` while an edit waits to be stored, `· ✓ Saved` once it is (02 handoff "Autosave"). */
@Composable
private fun SaveStatus(saving: Boolean) {
    Text(
        text = if (saving) "· Saving…" else "· ✓ Saved",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.testTag(if (saving) "create.studio.saving" else "create.studio.saved"),
    )
}

/** The `STUDIO` tag on the editor's accent container (02 handoff tokens). */
@Composable
private fun StudioTag() {
    val accent = AccentSlot.Violet.colors()
    Box(
        modifier = Modifier
            .background(accent.container, AppShapes.extraSmall)
            .padding(horizontal = TagHorizontalPadding)
            .testTag("create.studio.tag"),
    ) {
        Text(text = "STUDIO", style = MaterialTheme.typography.labelSmall, color = accent.onContainer, maxLines = 1)
    }
}

@Composable
private fun OverflowMenu(onRename: (() -> Unit)?, onDuplicate: () -> Unit, onDelete: (() -> Unit)?, onUnpublish: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    val pick: (() -> Unit) -> () -> Unit = { action -> { open = false; action() } }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("create.studio.more")) {
            Icon(PuzzleIcons.More, contentDescription = "More options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = AppShapes.medium) {
            onRename?.let {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = pick(it),
                    modifier = Modifier.testTag("create.studio.rename"),
                )
            }
            DropdownMenuItem(
                text = { Text("Duplicate as new draft") },
                onClick = pick(onDuplicate),
                modifier = Modifier.testTag("create.studio.duplicate"),
            )
            onUnpublish?.let {
                DropdownMenuItem(
                    text = { Text("Unpublish…", color = MaterialTheme.colorScheme.error) },
                    onClick = pick(it),
                    modifier = Modifier.testTag("create.studio.unpublish"),
                )
            }
            onDelete?.let {
                DropdownMenuItem(
                    text = { Text("Delete draft", color = MaterialTheme.colorScheme.error) },
                    onClick = pick(it),
                    modifier = Modifier.testTag("create.studio.delete"),
                )
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun StudioTopBarPreview() {
    PuzzleBoxTheme {
        StudioTopBar(
            title = "Castle",
            subtitle = "Queens 7×7",
            saving = false,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            pill = { VerdictPill(verdict = Verdict.Incomplete(IncompleteReason("queens.unpainted", 49)), onClick = {}) },
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun StudioTopBarLongTitlePreview() {
    PuzzleBoxTheme {
        StudioTopBar(
            title = "Grandma Ruth's 80th — the big family crown puzzle, part 2",
            subtitle = "Queens 12×12",
            saving = true,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            pill = { VerdictPill(verdict = Verdict.Checking, onClick = {}) },
        )
    }
}
