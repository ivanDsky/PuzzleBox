package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
private val DividerThickness = 1.dp

/**
 * The settings-style choice control: one full-width radio row per [options] entry, [selected]
 * drawn chosen in the primary container, a tap reporting its option through [onSelect]. The caller
 * puts the group's test tag on [modifier]; each row carries `tag(option)`.
 *
 * [enabled] false blocks the whole group and mutes the chosen row; [optionEnabled] false locks one
 * option (lock icon, quiet ink, no tap), its row showing [disabledReason] in place of its
 * description when there is one. [error] draws a 2dp ring [RingOutset] outside the group, without
 * moving any layout, so the group needs that much room around it; pair it with a [ChoiceMessage].
 */
@Composable
fun <T> ChoiceRows(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    description: (T) -> String,
    tag: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
    enabled: Boolean = true,
    optionEnabled: (T) -> Boolean = { true },
    disabledReason: (T) -> String? = { null },
    error: Boolean = false,
) {
    val ringColor = MaterialTheme.colorScheme.error
    val shape = MaterialTheme.shapes.small
    Column(
        modifier = modifier
            .drawBehind {
                if (error) {
                    val outset = RingOutset.toPx()
                    drawRoundRect(
                        color = ringColor,
                        topLeft = Offset(-outset, -outset),
                        size = Size(size.width + 2 * outset, size.height + 2 * outset),
                        cornerRadius = CornerRadius(shape.topStart.toPx(size, this) + outset),
                        style = Stroke(RingWidth.toPx()),
                    )
                }
            }
            .clip(shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) HorizontalDivider(thickness = DividerThickness, color = MaterialTheme.colorScheme.outlineVariant)
            val locked = !optionEnabled(option)
            ChoiceRow(
                title = label(option),
                description = (if (locked) disabledReason(option) else null) ?: description(option),
                icon = icon?.invoke(option),
                selected = option == selected,
                groupEnabled = enabled,
                optionEnabled = !locked,
                onClick = { onSelect(option) },
                modifier = Modifier.fillMaxWidth().testTag(tag(option)),
            )
        }
    }
}
