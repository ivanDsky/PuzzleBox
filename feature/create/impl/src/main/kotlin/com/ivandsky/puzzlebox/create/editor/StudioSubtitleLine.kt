package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

private val TagHorizontalPadding = 6.dp

/**
 * The subtitle line, left to right: the STUDIO tag, the subtitle, the save state. The save state
 * is measured first and keeps its width. The tag is dropped whole when it cannot sit beside the
 * save state with a gap on each side, so the subtitle ellipsizes instead of the tag being squeezed.
 */
@Composable
internal fun SubtitleLine(subtitle: String, saving: Boolean, modifier: Modifier) {
    Layout(
        modifier = modifier,
        content = {
            StudioTag()
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SaveStatus(saving)
        },
    ) { measurables, constraints ->
        val gap = Spacing.xs.roundToPx()
        val maxWidth = constraints.maxWidth
        val status = measurables[2].measure(Constraints(maxWidth = maxWidth))
        val tag = measurables[0].measure(Constraints(maxWidth = maxWidth))
        val showTag = tag.width + status.width + 2 * gap <= maxWidth
        val leading = if (showTag) tag.width + gap else 0
        val subtitleWidth = (maxWidth - leading - status.width - gap).coerceAtLeast(0)
        val subtitleLine = measurables[1].measure(Constraints(maxWidth = subtitleWidth))
        val height = maxOf(status.height, subtitleLine.height, if (showTag) tag.height else 0)
        layout(maxWidth, height) {
            if (showTag) tag.placeRelative(0, (height - tag.height) / 2)
            subtitleLine.placeRelative(leading, (height - subtitleLine.height) / 2)
            status.placeRelative(leading + subtitleLine.width + gap, (height - status.height) / 2)
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
