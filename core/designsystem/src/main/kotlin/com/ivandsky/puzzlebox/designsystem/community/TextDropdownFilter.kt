package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

internal const val TEXT_DROPDOWN_UNDERLINE_TAG = "text.dropdown.underline"

private const val DISABLED_ALPHA = 0.38f
private const val OPEN_ARROW_DEGREES = 180f

/**
 * A filter as plain text, never a pill: [label] ("Any game") with a down arrow while nothing is picked, the picked
 * [value] (blank counts as none) in `onSurface` over a 2dp `secondary` underline once something is. It only reports the tap through [onClick];
 * the caller opens whatever it wants (a bottom sheet, a menu) and says so with [open], which turns the arrow. The tap
 * target is 48dp tall, a screen reader hears a dropdown list that is expanded or collapsed. Reduced motion keeps the
 * colour change and drops the arrow's turn and the press scale. [colors] and [textStyle] default to that look;
 * [TextDropdownFilterDefaults.quietColors] and [TextDropdownFilterDefaults.quietTextStyle] give the quiet one.
 */
@Composable
fun TextDropdownFilter(
    label: String,
    value: String?,
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextDropdownFilterColors = TextDropdownFilterDefaults.colors(),
    textStyle: TextStyle = TextDropdownFilterDefaults.textStyle(),
) {
    val reduced = rememberReducedMotion()
    // A blank value is no value: nothing is picked, so the label shows and there is no underline.
    val shown = value?.takeIf { it.isNotBlank() }
    val set = shown != null
    val interactionSource = remember { MutableInteractionSource() }
    val colorSpec = tween<Color>(MotionTokens.DurationShort)
    val ink by animateColorAsState(colors.contentColor(set), colorSpec, label = "text-dropdown-ink")
    val turn by animateFloatAsState(
        targetValue = if (open && !reduced) OPEN_ARROW_DEGREES else 0f,
        animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
        label = "text-dropdown-arrow",
    )

    Row(
        modifier = modifier
            .pressScale(interactionSource, reduced)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = TextDropdownFilterDefaults.MinHeight)
            .semantics {
                // The picked value replaces the label on screen, so a screen reader is told both.
                if (shown != null) contentDescription = "$label: $shown"
                stateDescription = if (open) "Expanded" else "Collapsed"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Text(
                text = shown ?: label,
                color = ink,
                style = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The unset state keeps the same 2dp, so picking a value never moves the text.
            val underline = colors.underlineColor(set)
            if (underline != null) {
                Spacer(Modifier.fillMaxWidth().height(TextDropdownFilterDefaults.UnderlineHeight).background(underline).testTag(TEXT_DROPDOWN_UNDERLINE_TAG))
            } else {
                Spacer(Modifier.height(TextDropdownFilterDefaults.UnderlineHeight))
            }
        }
        Icon(
            PuzzleIcons.ExpandMore,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(TextDropdownFilterDefaults.ArrowSize).graphicsLayer { rotationZ = turn },
        )
    }
}
