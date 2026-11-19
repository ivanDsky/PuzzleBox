package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val MinRowHeight = 52.dp
private val RowPadding = 6.dp
private val IconSize = 24.dp
private val CheckSize = 20.dp

internal const val FILTER_ROW_CHECK_TAG = "filter.row.check"

/**
 * One row of a filter list: the optional 24dp [icon] (given the row's tint), a gap and the wrapping [label]. A [selected] row fills
 * `secondaryContainer` over [MotionTokens.DurationShort]. The hairline above it is left out when it is the [first] row. [modifier]
 * lands on the selectable node, which is a radio, or a checkbox when [multi]: then a 20dp check slot ends the row, always reserved, and
 * the check fades in over [MotionTokens.DurationShort] when selected, so the label never moves.
 */
@Composable
internal fun FilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (tint: Color) -> Unit)? = null,
    first: Boolean = false,
    multi: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = tween<Color>(MotionTokens.DurationShort)
    val fill by animateColorAsState(if (selected) scheme.secondaryContainer else Color.Transparent, spec, label = "filter-row-fill")
    val ink by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurface, spec, label = "filter-row-ink")
    val tint by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, spec, label = "filter-row-tint")
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(MotionTokens.DurationShort), label = "filter-row-check")

    Column(Modifier.fillMaxWidth()) {
        if (!first) HorizontalDivider(thickness = 1.dp, color = scheme.outlineVariant)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(fill)
                .selectable(selected = selected, role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
                .heightIn(min = MinRowHeight)
                .padding(horizontal = Spacing.lg, vertical = RowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(Modifier.size(IconSize)) { icon(tint) }
                Spacer(Modifier.width(Spacing.lg))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = if (multi) Modifier.weight(1f) else Modifier)
            if (multi) {
                Icon(
                    PuzzleIcons.Check,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.padding(start = Spacing.sm).size(CheckSize).graphicsLayer { alpha = checkAlpha }.testTag(FILTER_ROW_CHECK_TAG),
                )
            }
        }
    }
}
