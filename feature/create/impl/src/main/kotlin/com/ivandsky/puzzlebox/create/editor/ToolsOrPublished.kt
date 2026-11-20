package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * The tools while the draft is editable; once published, the "Published · read-only" chip with
 * Duplicate ([onDuplicate]), the one way on to an editable board; "Unpublished · read-only"
 * once [unpublished]. The two cross-fade.
 */
@Composable
internal fun ToolsOrPublished(readOnly: Boolean, unpublished: Boolean, tools: @Composable (Modifier) -> Unit, onDuplicate: () -> Unit, modifier: Modifier) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    AnimatedContent(
        targetState = readOnly,
        modifier = modifier,
        transitionSpec = { ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = null) },
        label = "toolsOrPublished",
    ) { published ->
        if (!published) {
            tools(Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .testTag("create.published.readOnly"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val tint = MaterialTheme.colorScheme.onSurfaceVariant
                if (!unpublished) Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ChipMarkSize), tint = tint)
                Text(if (unpublished) "Unpublished · read-only" else "Published · read-only", style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AppTextButton(text = "Duplicate", onClick = onDuplicate, modifier = Modifier.testTag("create.published.duplicate"))
        }
    }
}

private val ChipMarkSize = 16.dp
