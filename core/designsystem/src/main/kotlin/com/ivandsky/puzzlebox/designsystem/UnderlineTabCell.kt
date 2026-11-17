package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

private const val COUNT_CAP = 99

/**
 * One cell of the strip: [tab]'s label, count and trailing slot centred in a [TabHeight] target.
 * [closeness] (1 on the cell's own position, 0 a whole tab away) blends the label from
 * `onSurfaceVariant` to `primaryText`; [selected] is the accessibility flag. The label shortens
 * with an ellipsis before the count or the trailing slot gives way (see [LabelThenTailPolicy]).
 */
@Composable
internal fun UnderlineTabCell(
    tab: UnderlineTab,
    closeness: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val labelColor = lerp(scheme.onSurfaceVariant, LocalPrimaryText.current, closeness)
    Box(
        modifier = modifier
            .heightIn(min = TabHeight)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                tab.description?.let { contentDescription = it }
                this.selected = selected
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .testTag(tab.tag),
        contentAlignment = Alignment.Center,
    ) {
        Layout(
            content = {
                Text(
                    tab.label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = tabCountText(tab.count)
                if (count != null || tab.trailing != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        count?.let { text ->
                            Text(
                                text,
                                modifier = Modifier.testTag("${tab.tag}.count"),
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge.tabular(),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        tab.trailing?.invoke()
                    }
                }
            },
            modifier = Modifier.padding(horizontal = Spacing.sm),
            measurePolicy = LabelThenTailPolicy,
        )
    }
}

private fun tabCountText(count: Int?): String? = when {
    count == null || count <= 0 -> null
    count > COUNT_CAP -> "$COUNT_CAP+"
    else -> count.toString()
}
