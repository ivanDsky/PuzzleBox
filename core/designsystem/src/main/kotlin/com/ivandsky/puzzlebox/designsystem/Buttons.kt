package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Touch-target floor (spec A8): Material's buttons default to 40 dp tall; every shared
// button is lifted to the 48 dp accessibility minimum.
internal val ButtonMinHeight: Dp = 48.dp

// Press feedback: content scales to 0.97 on a critically damped spring — subtle,
// instant on touch-down, and cleanly interruptible on release. The damping/stiffness
// come from the motion tokens so every pressable surface shares one physics.
@Composable
internal fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = MotionTokens.SpringDampingDefault,
            stiffness = MotionTokens.SpringStiffnessDefault,
        ),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Primary is the taller pill (identity §2.4): 52 dp with the bold 16/20 label.
internal val PrimaryButtonMinHeight: Dp = 52.dp

/** The one filled action on a screen: a 52 dp pill in the primary fill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = PrimaryButtonMinHeight).pressScale(interactionSource),
        enabled = enabled,
        shape = PillShape,
        interactionSource = interactionSource,
    ) {
        Text(text, style = PrimaryButtonTextStyle)
    }
}

/** The quiet counterpart: a tonal pill — the well fill with ink text, no border. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight).pressScale(interactionSource),
        enabled = enabled,
        shape = PillShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
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

@ShowkaseComposable(name = "SecondaryButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun SecondaryButtonPreview() {
    PuzzleBoxTheme {
        SecondaryButton(text = "Cancel", onClick = {})
    }
}
