package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

private val TagHorizontalPadding = 6.dp
private val MinLabelWidth = 48.dp

/**
 * The subtitle line, left to right: STUDIO tag, game label, separator, save state. The save state
 * is always placed whole. The tag shows only while the whole label fits beside it; otherwise it
 * drops first, so the label is the last thing to give. Then the label ellipsizes down to
 * [MinLabelWidth] and drops with its separator, so nothing is left dangling. With nothing before
 * it, the save state reads without its separator.
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
                modifier = Modifier.testTag("create.studio.label"),
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("create.studio.separator"),
            )
            SaveState(saving)
        },
    ) { measurables, constraints ->
        val gap = Spacing.xs.roundToPx()
        val minLabel = MinLabelWidth.roundToPx()
        val maxWidth = constraints.maxWidth
        val tag = measurables[0].measure(Constraints(maxWidth = maxWidth))
        val dot = measurables[2].measure(Constraints(maxWidth = maxWidth))
        val word = measurables[3].measure(Constraints(maxWidth = maxWidth))
        val tail = dot.width + gap + word.width
        val roomWithTag = maxWidth - tag.width - 2 * gap - tail
        val roomWithoutTag = maxWidth - gap - tail
        val wholeLabel = measurables[1].maxIntrinsicWidth(Int.MAX_VALUE)
        val tagFits = roomWithTag >= wholeLabel
        val labelRoom = if (tagFits) roomWithTag else roomWithoutTag
        val labelShown = subtitle.isNotEmpty() && labelRoom >= minOf(minLabel, wholeLabel)
        val tagShown = if (subtitle.isEmpty()) tag.width + gap + word.width <= maxWidth else tagFits
        val label = if (labelShown) measurables[1].measure(Constraints(maxWidth = labelRoom)) else null
        val tagP = if (tagShown) tag else null
        val dotP = if (labelShown) dot else null
        val height = listOfNotNull(word, tagP, label, dotP).maxOf { it.height }
        layout(maxWidth, height) {
            var x = 0
            tagP?.let {
                it.placeRelative(x, centred(it, height))
                x += it.width + gap
            }
            label?.let {
                it.placeRelative(x, centred(it, height))
                x += it.width + gap
            }
            dotP?.let {
                it.placeRelative(x, centred(it, height))
                x += it.width + gap
            }
            word.placeRelative(x, centred(word, height))
        }
    }
}

private fun centred(placeable: Placeable, height: Int): Int = (height - placeable.height) / 2

/** `✓ Saved` once the edit is stored, `Saving…` while it waits. The separator is its own node. */
@Composable
private fun SaveState(saving: Boolean) {
    Text(
        text = if (saving) "Saving…" else "✓ Saved",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.testTag(if (saving) "create.studio.saving" else "create.studio.saved"),
    )
}

/** The `STUDIO` tag on the editor's accent container. */
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
