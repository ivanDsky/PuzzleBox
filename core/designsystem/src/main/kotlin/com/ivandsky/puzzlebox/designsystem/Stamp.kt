package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

/**
 * The win entrance for a game glyph (identity §2.6, spec A6): when [visible] turns true,
 * [content] lands from MotionTokens.StampFromScale to 1 on the stamp spring, the app's
 * only bouncy curve, while it fades in. Content that is visible from the start just
 * shows. Reduced motion shows and hides [content] statically.
 */
@Composable
fun Stamp(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (rememberReducedMotion()) {
        if (visible) Box(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(
            animationSpec = spring(
                dampingRatio = MotionTokens.StampSpringDamping,
                stiffness = MotionTokens.StampSpringStiffness,
            ),
            initialScale = MotionTokens.StampFromScale,
        ) + fadeIn(tween(MotionTokens.StampFadeDuration)),
        exit = fadeOut(tween(MotionTokens.StampFadeDuration)),
    ) {
        content()
    }
}

// Previews — Studio tooling only (internal for Showkase's generated index). The stamp's
// end state: the glyph tile at rest, full size. A8: visible from the start, so nothing
// animates and the pane is static.
@ShowkaseComposable(name = "Stamp", group = "Motion")
@PuzzleBoxPreviews
@Composable
internal fun StampPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            Stamp(visible = true) {
                Box(Modifier.size(40.dp).background(AccentSlot.Violet.colors().accent, MaterialTheme.shapes.small))
            }
        }
    }
}
