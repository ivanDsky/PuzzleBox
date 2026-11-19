package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

internal const val FILTER_CHIP_CHECK_TAG = "filter.chip.check"
internal const val FILTER_CHIP_ARROW_TAG = "filter.chip.arrow"

private const val DISABLED_ALPHA = 0.38f
private const val OPEN_ARROW_DEGREES = 180f
private val IconSize = 18.dp

// The stock outline width, kept once picked so the rim fades out under the fill instead of the chip resizing.
private val RimWidth = 1.dp

/**
 * One filter's chip. [picked] is "something is on" (filled, with the check); [open] is "its sheet is showing" (the arrow
 * is turned). Fill, border and text colour change over [MotionTokens.DurationShort]; reduced motion keeps that and drops
 * the arrow's turn and the press scale.
 */
@Composable
fun DropdownFilterChip(
    label: String,
    picked: Boolean,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val colorSpec = tween<Color>(MotionTokens.DurationShort)
    val fill by animateColorAsState(if (picked) scheme.secondaryContainer else Color.Transparent, colorSpec, label = "filter-chip-fill")
    val ink by animateColorAsState(if (picked) scheme.onSecondaryContainer else scheme.onSurfaceVariant, colorSpec, label = "filter-chip-ink")
    val rim by animateColorAsState(if (picked) Color.Transparent else scheme.outline, colorSpec, label = "filter-chip-rim")
    val turn by animateFloatAsState(
        targetValue = if (open && !reduced) OPEN_ARROW_DEGREES else 0f,
        animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
        label = "filter-chip-arrow",
    )

    FilterChip(
        selected = picked,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = modifier
            .pressScale(interactionSource, reduced)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics {
                role = Role.DropdownList
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        enabled = enabled,
        leadingIcon = if (picked) {
            { Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize).testTag(FILTER_CHIP_CHECK_TAG)) }
        } else {
            null
        },
        trailingIcon = {
            Icon(
                PuzzleIcons.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(IconSize).graphicsLayer { rotationZ = turn }.testTag(FILTER_CHIP_ARROW_TAG),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = fill,
            labelColor = ink,
            iconColor = ink,
            disabledContainerColor = fill,
            disabledLabelColor = ink,
            disabledLeadingIconColor = ink,
            disabledTrailingIconColor = ink,
            selectedContainerColor = fill,
            disabledSelectedContainerColor = fill,
            selectedLabelColor = ink,
            selectedLeadingIconColor = ink,
            selectedTrailingIconColor = ink,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = picked,
            borderColor = rim,
            selectedBorderColor = rim,
            disabledBorderColor = rim,
            disabledSelectedBorderColor = rim,
            selectedBorderWidth = RimWidth,
        ),
        interactionSource = interactionSource,
    )
}
