package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Touch-target floor (spec A8): Material's buttons default to 40 dp tall; every shared
// button is lifted to the 48 dp accessibility minimum.
internal val ButtonMinHeight: Dp = 48.dp

// Primary is the taller pill (identity §2.4): 52 dp with the bold 16/20 label.
internal val PrimaryButtonMinHeight: Dp = 52.dp

/**
 * The one filled action on a screen: a 52 dp pill in the primary fill, or in the error fill
 * with its onError label when [destructive] (a confirm that removes something for good).
 *
 * While [loading] the work it started is running: the pill morphs into a circle as tall as
 * itself, centred where it stood, with a spinner that announces [text]; it keeps its fill and
 * takes no taps. Motion: a **Container transform** (DurationExpressive on the standard curve),
 * the spinner fading in after DurationShort; reduced motion swaps at once, the spinner still
 * turning since it carries the state.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    destructive: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val reduced = rememberReducedMotion()
    val toCircle by animateFloatAsState(
        targetValue = if (loading) 1f else 0f,
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationExpressive, easing = MotionTokens.EasingStandard),
        label = "primaryButtonToCircle",
    )
    val busy = if (loading) {
        Modifier.clearAndSetSemantics {
            contentDescription = text
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            disabled()
        }
    } else {
        Modifier
    }
    Button(
        onClick = { if (!loading) onClick() },
        modifier = modifier
            .heightIn(min = PrimaryButtonMinHeight)
            .narrowedToCircle { toCircle }
            .pressScale(interactionSource, reduced = false)
            .then(busy),
        enabled = enabled || loading,
        shape = PillShape,
        contentPadding = if (loading) PaddingValues() else ButtonDefaults.ContentPadding,
        interactionSource = interactionSource,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        if (loading) Spinner(reduced) else Text(text, style = PrimaryButtonTextStyle)
    }
}

/** The loading pill's spinner in the content colour, fading in once the morph is under way. */
@Composable
private fun Spinner(reduced: Boolean) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            1f,
            if (reduced) tween(MotionTokens.ReducedFadeDuration) else tween(MotionTokens.DurationShort, delayMillis = MotionTokens.DurationShort),
        )
    }
    CircularProgressIndicator(
        modifier = Modifier.size(SpinnerSize).graphicsLayer { this.alpha = alpha.value },
        color = LocalContentColor.current,
        strokeWidth = SpinnerStroke,
    )
}

/**
 * Narrows the button from its own width to a circle as [progress] goes 0 → 1, centred in the
 * width it was given; at 0 it measures exactly as without it.
 */
private fun Modifier.narrowedToCircle(progress: () -> Float): Modifier = layout { measurable, constraints ->
    val fraction = progress()
    if (fraction == 0f) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val full = if (constraints.hasFixedWidth) {
        constraints.maxWidth
    } else {
        measurable.maxIntrinsicWidth(constraints.minHeight).coerceIn(constraints.minWidth, constraints.maxWidth)
    }
    val side = constraints.minHeight
    val width = (full + (side - full) * fraction).roundToInt().coerceAtLeast(side)
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    val slot = constraints.constrainWidth(width)
    layout(slot, placeable.height) { placeable.place((slot - placeable.width) / 2, 0) }
}

private val SpinnerSize: Dp = 24.dp
private val SpinnerStroke: Dp = 2.5.dp
private val SecondaryIconSize: Dp = 18.dp

/** The quiet counterpart: a tonal pill — the well fill with ink text, no border; an optional [icon] leads the label. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight).pressScale(interactionSource, reduced = false),
        enabled = enabled,
        shape = PillShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(SecondaryIconSize))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(text)
    }
}

// Previews — Studio tooling only, zero public API surface (internal, not private, so
// Showkase's generated index can access them; ticket 00 visibility findings). Exactly
// light + dark, always inside PuzzleBoxTheme (spec A1/A5). A8: the press-scale spring
// is touch-driven from an interaction source the component owns — there is no
// programmatic trigger a preview could start, so the static panes show the resting
// state and interactive mode runs the animation on press.

@ShowkaseComposable(name = "PrimaryButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun PrimaryButtonPreview() {
    PuzzleBoxTheme {
        PrimaryButton(text = "Confirm", onClick = {})
    }
}

/** Loading: the circle with its spinner, in the width a full-width pill had. */
@ShowkaseComposable(name = "PrimaryButton loading", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun PrimaryButtonLoadingPreview() {
    PuzzleBoxTheme {
        PrimaryButton(text = "Publishing", onClick = {}, modifier = Modifier.fillMaxWidth(), loading = true)
    }
}

/** Destructive: the error fill, resting and loading. */
@ShowkaseComposable(name = "PrimaryButton destructive", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun PrimaryButtonDestructivePreview() {
    PuzzleBoxTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrimaryButton(text = "Unpublish", onClick = {}, destructive = true)
            PrimaryButton(text = "Unpublish", onClick = {}, destructive = true, loading = true)
        }
    }
}

@ShowkaseComposable(name = "SecondaryButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun SecondaryButtonPreview() {
    PuzzleBoxTheme {
        SecondaryButton(text = "Cancel", onClick = {})
    }
}
