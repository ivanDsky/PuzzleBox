package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Chunky geometry (prototype 07 C): a 16dp-radius face rimmed and underlaid by a lip of
// its own hue; the lip is what the face sinks into.
internal val GameButtonLipDepth: Dp = 4.dp
private val GameButtonRim: Dp = 2.dp
private val GameButtonIconSize: Dp = 24.dp
private val GameButtonIconGap: Dp = 2.dp
private const val DISABLED_FILL_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

/** One game button state's face fill, its ink, and the lip under it. */
@Immutable
internal data class GameButtonColors(val fill: Color, val content: Color, val lip: Color)

/** [accent] null is the neutral well on [neutralLip]; a slot fills with its accent on its own lip. */
internal fun gameButtonColors(accent: GameAccent?, scheme: ColorScheme, neutralLip: Color): GameButtonColors =
    if (accent == null) {
        GameButtonColors(scheme.surfaceContainerHigh, scheme.onSurface, neutralLip)
    } else {
        GameButtonColors(accent.accent, accent.onAccent, accent.lip)
    }

/**
 * The in-game button (spec A12, A14): a face on a 4dp lip of the same hue, icon over a
 * short label. [accent] null is the neutral well (Undo, Erase); a game's slot fills it
 * with that accent (Hint). A press sinks the face into its lip; disabled sits flat
 * and muted. [size] sets the height floor and whether the label shows.
 */
@Composable
fun GameButton(
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: AccentSlot? = null,
    size: GameButtonSize = GameButtonSize.Regular,
) {
    val interactionSource = remember { MutableInteractionSource() }
    GameButtonFace(
        colors = gameButtonColors(accent?.colors(), MaterialTheme.colorScheme, LocalNeutralLip.current),
        enabled = enabled,
        interactionSource = interactionSource,
        icon = icon,
        label = label,
        size = size,
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

/**
 * The face every game button draws; [modifier] already carries its click or toggle.
 * The sink is the press feedback (MotionTokens: Press, chunky); colour changes
 * crossfade like Select. Reduced motion makes both instant.
 */
@Composable
internal fun GameButtonFace(
    colors: GameButtonColors,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    icon: ImageVector,
    label: String,
    size: GameButtonSize,
    modifier: Modifier,
) {
    val reduced = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val pressed by interactionSource.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed || !enabled) GameButtonLipDepth else 0.dp,
        animationSpec = if (reduced) snap() else spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "game-button-sink",
    )
    val crossfade: AnimationSpec<Color> = if (reduced) snap() else tween(MotionTokens.SelectDuration, easing = MotionTokens.EasingStandard)
    val fill by animateColorAsState(if (enabled) colors.fill else scheme.onSurface.copy(alpha = DISABLED_FILL_ALPHA), crossfade, label = "game-button-fill")
    val content by animateColorAsState(if (enabled) colors.content else scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA), crossfade, label = "game-button-content")
    val lip by animateColorAsState(if (enabled) colors.lip else Color.Transparent, crossfade, label = "game-button-lip")
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = size.minHeight)
            .widthIn(min = ButtonMinHeight)
            .then(if (size.showsLabel) Modifier else Modifier.semantics { contentDescription = label })
            .drawBehind {
                val depth = GameButtonLipDepth.toPx()
                val face = Size(this.size.width, this.size.height - depth)
                translate(top = depth) { drawOutline(shape.createOutline(face, layoutDirection, this), lip) }
            }
            .padding(bottom = GameButtonLipDepth)
            .graphicsLayer { translationY = sink.toPx() }
            .background(fill, shape)
            .border(GameButtonRim, lip, shape)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameButtonIconGap),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(GameButtonIconSize), tint = content)
            if (size.showsLabel) {
                Text(text = label, style = GameButtonTextStyle, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// Previews — light + dark in PuzzleBoxTheme: neutral, accent, disabled and the compact
// icon-only tile at rest; the sink is touch-driven, so interactive mode shows it.
@ShowkaseComposable(name = "GameButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun GameButtonPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            GameButton(onClick = {}, icon = PuzzleIcons.Undo, label = "Undo", size = GameButtonSize.Tall)
            GameButton(onClick = {}, icon = PuzzleIcons.Hint, label = "Hint", accent = AccentSlot.Teal, size = GameButtonSize.Tall)
            GameButton(onClick = {}, icon = PuzzleIcons.Redo, label = "Redo", enabled = false, size = GameButtonSize.Tall)
            GameButton(onClick = {}, icon = PuzzleIcons.Erase, label = "Erase")
            GameButton(onClick = {}, icon = PuzzleIcons.Erase, label = "Erase", size = GameButtonSize.Compact)
        }
    }
}
