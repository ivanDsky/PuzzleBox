package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

internal const val SORT_MENU_TICK_TAG = "sort.menu.tick"

/** The menu's items fade in over this long once it opens; the window's own exit stays the platform's. */
internal const val SORT_MENU_FADE_MS = 140

private const val OPEN_ARROW_DEGREES = 180f
private val TargetHeight = 48.dp
private val GlyphSize = 20.dp
private val ArrowSize = 18.dp
private val TickSize = 20.dp

// Two arrows on the 24 grid, one up on the left and one down on the right: the ⇅ of the prototype, solid so the Icon
// tint colours it. Built here because the frozen icon set has no sort glyph and this ticket owns only its folder.
private val SortGlyph: ImageVector = ImageVector.Builder(
    name = "SortMenuButton.Glyph",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M8 4L4 8.5H7V20H9V8.5H12ZM16 20L20 15.5H17V4H15V15.5H12Z"),
    fill = SolidColor(Color.Black),
).build()

/**
 * The sort control: the ⇅ glyph, the [selected] option's label and a down arrow, anchored to a menu of [options] with a
 * tick on the current one. Generic over the option so nothing here knows what is being sorted; [optionLabel] names each
 * one. The caller owns [expanded] (the arrow turns with it), [onExpandedChange] asks to open or close, and a pick calls
 * [onSelect] then closes. The tap target is 48dp tall, a screen reader hears a dropdown list that is expanded or
 * collapsed, and the menu's items fade in over [SORT_MENU_FADE_MS].
 */
@Composable
fun <T> SortMenuButton(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val turn by animateFloatAsState(
        targetValue = if (expanded && !reduced) OPEN_ARROW_DEGREES else 0f,
        animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
        label = "sort-menu-arrow",
    )

    // The anchor is the tap target itself, so the caller's modifier (and its test tag) lands on the node a screen reader meets.
    Box(
        modifier = modifier
            .pressScale(interactionSource, reduced)
            .heightIn(min = TargetHeight)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.DropdownList,
                onClickLabel = "Sort",
                onClick = { onExpandedChange(!expanded) },
            )
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SortGlyph, contentDescription = null, tint = ink, modifier = Modifier.size(GlyphSize))
            Text(optionLabel(selected), color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(
                PuzzleIcons.ExpandMore,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(ArrowSize).graphicsLayer { rotationZ = turn },
            )
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            val fade = remember { Animatable(0f) }
            LaunchedEffect(Unit) { fade.animateTo(1f, tween(SORT_MENU_FADE_MS, easing = MotionTokens.EasingStandard)) }
            Column(Modifier.graphicsLayer { alpha = fade.value }) {
                options.forEach { option ->
                    val current = option == selected
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        trailingIcon = {
                            if (current) {
                                Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(TickSize).testTag(SORT_MENU_TICK_TAG))
                            } else {
                                Spacer(Modifier.size(TickSize))
                            }
                        },
                        onClick = {
                            onSelect(option)
                            onExpandedChange(false)
                        },
                        modifier = Modifier.semantics { this.selected = current },
                    )
                }
            }
        }
    }
}
