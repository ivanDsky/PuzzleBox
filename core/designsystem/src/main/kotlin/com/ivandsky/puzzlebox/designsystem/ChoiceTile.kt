package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TileMinHeight = 52.dp
private val TilePadding = 14.dp
private val IconSize = 20.dp
private val OutlineWidth = 2.dp
private val DashLength = 6.dp
private val DashGap = 4.dp

// The caller's [ChoiceMessage] carries the reason; the tile can only say it is locked.
private const val LOCKED = "Not available yet"

private enum class Rim { None, Solid, Dashed }

private class TileLook(val fill: Color, val ink: Color, val weight: FontWeight, val rim: Rim)

/**
 * One tile's face. A locked option (transparent, dashed rim, quiet ink) wins over a muted chosen
 * tile (whole group disabled: the chosen one keeps a solid rim so it still reads as current).
 */
private fun tileLook(
    selected: Boolean,
    groupEnabled: Boolean,
    optionEnabled: Boolean,
    accent: GameAccent,
    scheme: ColorScheme,
): TileLook = when {
    !optionEnabled -> TileLook(Color.Transparent, scheme.onSurfaceVariant, FontWeight.Medium, Rim.Dashed)
    !groupEnabled && selected -> TileLook(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, FontWeight.Bold, Rim.Solid)
    !groupEnabled -> TileLook(scheme.surfaceContainerHigh, scheme.onSurfaceVariant, FontWeight.SemiBold, Rim.None)
    selected -> TileLook(accent.container, accent.onContainer, FontWeight.Bold, Rim.None)
    else -> TileLook(scheme.surfaceContainerHigh, scheme.onSurface, FontWeight.SemiBold, Rim.None)
}

/**
 * A quiet tile of [ChoiceTiles]: [icon] 20dp left of [label], at least [TileMinHeight] tall and
 * growing with a wrapped label (never an ellipsis). Fill and ink crossfade on selection, instantly
 * under reduced motion; a press scales it like every pressable surface.
 */
@Composable
internal fun ChoiceTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    accent: AccentSlot,
    groupEnabled: Boolean,
    optionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = tileLook(selected, groupEnabled, optionEnabled, accent.colors(), MaterialTheme.colorScheme)
    val reduced = rememberReducedMotion()
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard)
    val fill by animateColorAsState(look.fill, colorSpec, label = "choice-tile-fill")
    val ink by animateColorAsState(look.ink, colorSpec, label = "choice-tile-ink")
    val rimColor = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction, reduced)
            .heightIn(min = TileMinHeight)
            .background(fill, shape)
            .drawBehind { drawRim(look.rim, rimColor, shape.topStart.toPx(size, this)) }
            .semantics { if (!optionEnabled) stateDescription = LOCKED }
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                enabled = groupEnabled && optionEnabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = TilePadding, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (optionEnabled) icon else PuzzleIcons.Lock, contentDescription = null, modifier = Modifier.size(IconSize), tint = ink)
        Text(
            label,
            modifier = Modifier.weight(1f, fill = false),
            color = ink,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = look.weight),
        )
    }
}

private fun DrawScope.drawRim(rim: Rim, color: Color, corner: Float) {
    if (rim == Rim.None) return
    val width = OutlineWidth.toPx()
    val half = width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(half, half),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius(corner - half),
        style = Stroke(
            width = width,
            pathEffect = if (rim == Rim.Dashed) PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGap.toPx())) else null,
        ),
    )
}
