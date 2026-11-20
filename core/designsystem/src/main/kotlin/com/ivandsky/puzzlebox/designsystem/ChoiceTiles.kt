package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

private val RingWidth = 2.dp
private val RingOutset = 6.dp

/**
 * The work-surface mode control: one equal-width quiet tile per [options] entry, [selected] drawn
 * chosen in [accent]'s container, a tap reporting its option through [onSelect]. The caller puts
 * the group's test tag on [modifier]; each tile carries `tag(option)`. A tile is as tall as its
 * tallest row-mate when a label wraps.
 *
 * [enabled] false blocks the whole group and mutes the chosen tile; [optionEnabled] false locks
 * one option (lock icon, dashed rim, no tap). [error] draws a 2dp ring [RingOutset] outside the
 * group, without moving any layout, so the group needs that much room around it; pair it with
 * a [ChoiceMessage].
 */
@Composable
fun <T> ChoiceTiles(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    tag: (T) -> String,
    accent: AccentSlot,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionEnabled: (T) -> Boolean = { true },
    error: Boolean = false,
) {
    val ringColor = MaterialTheme.colorScheme.error
    val corner = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .drawBehind {
                if (error) {
                    val outset = RingOutset.toPx()
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + 2 * outset, size.height + 2 * outset),
                        cornerRadius = CornerRadius(corner.topStart.toPx(size, this) + outset),
                        style = Stroke(RingWidth.toPx()),
                    )
                }
            }
            .selectableGroup()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            ChoiceTile(
                label = label(option),
                icon = icon(option),
                selected = option == selected,
                accent = accent,
                groupEnabled = enabled,
                optionEnabled = optionEnabled(option),
                onClick = { onSelect(option) },
                modifier = Modifier.weight(1f).fillMaxHeight().testTag(tag(option)),
            )
        }
    }
}
