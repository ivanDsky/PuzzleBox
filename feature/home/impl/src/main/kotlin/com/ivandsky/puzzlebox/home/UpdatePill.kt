package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val PillGlyph = 16.dp

/**
 * "Update available" (spec A7, S8): a quiet tonal pill for Home's top-bar actions slot —
 * the house pill shape on the primary container, label then a trailing chevron (tapping
 * leaves the app for the release page). It never announces itself: no entrance, pulse or
 * badge; the only motion is the house press scale (0.97 on the default spring, none
 * under reduced motion). Surface keeps the 48 dp touch target around the slimmer pill.
 */
@Composable
internal fun UpdatePill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reducedMotion = rememberReducedMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) MotionTokens.PressScale else 1f,
        animationSpec = spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "update-pill-press",
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("home.update_pill")
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = PillShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        interactionSource = interaction,
    ) {
        // Sized for the tightest header (S7): the ~353 dp list pane of an 840 dp TwoPane
        // window. The chevron's own side bearing is the label gap and most of the end inset.
        Row(
            modifier = Modifier.padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Update available",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Icon(PuzzleIcons.Chevron, contentDescription = null, modifier = Modifier.size(PillGlyph))
        }
    }
}
