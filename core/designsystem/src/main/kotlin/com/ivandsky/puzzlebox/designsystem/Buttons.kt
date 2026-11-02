package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// Press feedback: content scales to 0.97 on a critically damped spring — subtle,
// instant on touch-down, and cleanly interruptible on release. The damping/stiffness
// come from the motion tokens so every pressable surface shares one physics.
@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
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

/** The one filled action on a screen. */
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
        modifier = modifier.pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource,
    ) {
        Text(text)
    }
}

/** The quiet counterpart: outline border, primary text, no fill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(
            // The primary hue's accessible TEXT role, not colorScheme.primary: the
            // light primary is a fill green that fails AA as text (ColorTokens.kt).
            contentColor = LocalPrimaryText.current,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(text)
    }
}

/** Catalog demo: the two button roles, enabled and disabled. */
@Composable
internal fun ButtonsCatalogSection() {
    CatalogSection(title = "Buttons") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PrimaryButton(text = "Start puzzle", onClick = {})
            SecondaryButton(text = "View rules", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
                SecondaryButton(text = "Disabled", onClick = {}, enabled = false)
            }
        }
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
