package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RowMinHeight = 56.dp
private val RowHorizontalPadding = 14.dp
private val SlotSize = 24.dp
private val RingDiameter = 22.dp
private val RingStroke = 2.dp
private val DotDiameter = 10.dp

// The caller's description carries the reason for a locked option; this is the fallback when it is empty.
private const val LOCKED = "Not available yet"

private class RowLook(val fill: Color, val ink: Color, val detail: Color, val ring: Color)

/**
 * One row's colours. A locked option stays quiet and unfilled even when it was the chosen one; a
 * muted chosen row (whole group disabled) keeps its ring and dot in `outline` so it still reads
 * as current.
 */
private fun rowLook(selected: Boolean, groupEnabled: Boolean, optionEnabled: Boolean, scheme: ColorScheme): RowLook = when {
    !optionEnabled -> RowLook(Color.Transparent, scheme.onSurfaceVariant, scheme.onSurfaceVariant, scheme.outline)
    !groupEnabled && selected -> RowLook(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, scheme.onSurfaceVariant, scheme.outline)
    !groupEnabled -> RowLook(Color.Transparent, scheme.onSurfaceVariant, scheme.onSurfaceVariant, scheme.outline)
    selected -> RowLook(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.onPrimaryContainer, Color.Unspecified)
    else -> RowLook(Color.Transparent, scheme.onSurface, scheme.onSurfaceVariant, scheme.outline)
}

/**
 * A row of [ChoiceRows]: optional 24dp [icon], a title with its [description] under it, and a
 * 24dp trailing slot (radio ring, or a lock for a locked option). At least [RowMinHeight] tall and
 * growing with wrapped text (never an ellipsis). Fill and ink crossfade on selection, instantly
 * under reduced motion; a press scales it like every pressable surface.
 */
@Composable
internal fun ChoiceRow(
    title: String,
    description: String,
    icon: ImageVector?,
    selected: Boolean,
    groupEnabled: Boolean,
    optionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val look = rowLook(selected, groupEnabled, optionEnabled, scheme)
    val reduced = rememberReducedMotion()
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
    val fill by animateColorAsState(look.fill, colorSpec, label = "choice-row-fill")
    val ink by animateColorAsState(look.ink, colorSpec, label = "choice-row-ink")
    val detail by animateColorAsState(look.detail, colorSpec, label = "choice-row-detail")
    val ringTarget = look.ring.takeOrElse { LocalPrimaryText.current }
    val ring by animateColorAsState(ringTarget, colorSpec, label = "choice-row-ring")
    val dot by animateColorAsState(if (selected) ringTarget else Color.Transparent, colorSpec, label = "choice-row-dot")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction, reduced)
            .heightIn(min = RowMinHeight)
            .background(fill)
            .semantics { if (!optionEnabled) stateDescription = description.takeIf { it.isNotEmpty() } ?: LOCKED }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = groupEnabled && optionEnabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = RowHorizontalPadding, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(SlotSize), tint = ink)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = ink,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
            )
            Text(
                description,
                color = detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            )
        }
        Box(modifier = Modifier.size(SlotSize), contentAlignment = Alignment.Center) {
            if (optionEnabled) {
                Box(
                    Modifier
                        .size(RingDiameter)
                        .drawBehind {
                            drawCircle(ring, style = Stroke(RingStroke.toPx()), radius = (RingDiameter - RingStroke).toPx() / 2)
                            drawCircle(dot, radius = DotDiameter.toPx() / 2, center = Offset(size.width / 2, size.height / 2))
                        },
                )
            } else {
                Icon(PuzzleIcons.Lock, contentDescription = null, modifier = Modifier.size(RingDiameter), tint = ink)
            }
        }
    }
}
