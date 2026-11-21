package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.share.ShareStyle
import com.ivandsky.puzzlebox.insights.share.sharePalette

/**
 * The share page's chips on one layout, 48dp each: a radio chip per style and, when the player has a name to hide, the
 * "Name" toggle. The picked chip is outlined and filled, the others only outlined. [enabled] is false while the deck is
 * arriving, so a pick cannot land on a poster still face-down.
 */
@Composable
internal fun ShareStyleChips(
    style: ShareStyle,
    onStyle: (ShareStyle) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    nameShown: Boolean? = null,
    onNameShown: (Boolean) -> Unit = {},
    stamped: Int = Int.MAX_VALUE,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ShareStyle.entries.forEach { entry ->
            Chip(
                label = entry.name,
                on = entry == style,
                tag = "recap.share.style.${entry.name}",
                modifier = Modifier
                    .stampedIn(entry.ordinal < stamped)
                    .selectable(entry == style, enabled, Role.RadioButton) { onStyle(entry) },
            ) { color -> StyleMark(entry, color) }
        }
        if (nameShown != null) {
            Chip(
                label = "Name",
                on = nameShown,
                tag = "recap.share.name",
                modifier = Modifier
                    .stampedIn(ShareStyle.entries.size < stamped)
                    .toggleable(value = nameShown, enabled = enabled, role = Role.Switch, onValueChange = onNameShown),
            ) { color ->
                Box(Modifier.size(MarkSize)) {
                    Canvas(Modifier.fillMaxSize()) { drawCircle(color, style = Stroke(MarkStroke.toPx())) }
                    if (nameShown) Icon(PuzzleIcons.Check, null, Modifier.fillMaxSize().padding(MarkStroke), color)
                }
            }
        }
    }
}

/** Lands from the stamp's scale on the stamp spring while fading in once [stamped]; before that it is not there. Reduced motion: shown. */
private fun Modifier.stampedIn(stamped: Boolean): Modifier = composed {
    val land = remember { Animatable(if (stamped) 1f else 0f) }
    val fade = remember { Animatable(if (stamped) 1f else 0f) }
    LaunchedEffect(stamped) {
        if (!stamped) return@LaunchedEffect
        launch { land.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness)) }
        fade.animateTo(1f, tween(MotionTokens.StampFadeDuration))
    }
    graphicsLayer {
        val scale = MotionTokens.StampFromScale + (1f - MotionTokens.StampFromScale) * land.value
        scaleX = scale
        scaleY = scale
        alpha = fade.value
    }
}

@Composable
private fun RowScope.Chip(
    label: String,
    on: Boolean,
    tag: String,
    modifier: Modifier,
    mark: @Composable (Color) -> Unit,
) {
    val ink = PaletteEntry.Share.ink
    val shape = CircleShape
    Row(
        modifier
            .weight(1f)
            .height(ChipHeight)
            .clip(shape)
            .background(if (on) ink else Color.White.copy(alpha = OffFillAlpha))
            .border(BorderStroke(ChipBorder, ink), shape)
            .padding(horizontal = Spacing.sm)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val content = if (on) PaletteEntry.Share.field else ink
        mark(content)
        Text(
            label,
            Modifier.padding(start = Spacing.xs),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A circle half filled in the style's own colour: the mark that says which look the chip picks. */
@Composable
private fun StyleMark(style: ShareStyle, outline: Color) {
    val palette = sharePalette(style, LocalDarkTheme.current)
    val half = if (style == ShareStyle.Classic) outline else if (style == ShareStyle.Night) palette.hero else palette.ground
    Canvas(Modifier.size(MarkSize)) {
        val circle = Path().apply { addOval(Rect(Offset.Zero, size)) }
        val lower = Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(Path.combine(PathOperation.Intersect, circle, lower), half)
        drawCircle(outline, style = Stroke(MarkStroke.toPx()))
    }
}

private val ChipHeight = 48.dp
private val ChipBorder = 2.dp
private val MarkSize = 18.dp
private val MarkStroke = 2.dp
private const val OffFillAlpha = 0.6f
