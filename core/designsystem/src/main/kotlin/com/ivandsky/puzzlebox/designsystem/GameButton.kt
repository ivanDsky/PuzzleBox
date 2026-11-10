package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Chunky geometry (prototype 07 C): a 16dp-radius face rimmed and underlaid by a lip of
// its own hue; the lip is what the face sinks into.
internal val GameButtonLipDepth: Dp = 4.dp
private val GameButtonRim: Dp = 2.dp
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
 * What the face animates toward. Disabled is a muted onSurface wash; its lip keeps the
 * enabled hue at alpha 0 so the lerp fades out instead of passing through black.
 */
internal fun gameButtonTarget(colors: GameButtonColors, enabled: Boolean, scheme: ColorScheme): GameButtonColors =
    if (enabled) {
        colors
    } else {
        GameButtonColors(
            fill = scheme.onSurface.copy(alpha = DISABLED_FILL_ALPHA),
            content = scheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
            lip = colors.lip.copy(alpha = 0f),
        )
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
        label = label,
        describe = !size.showsLabel,
        size = size,
        modifier = modifier.gameButtonClick(interactionSource, enabled, onClick),
    ) { ink -> GameButtonContent(icon, label.takeIf { size.showsLabel }, ink) }
}

internal fun Modifier.gameButtonClick(interactionSource: MutableInteractionSource, enabled: Boolean, onClick: () -> Unit) =
    clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
    )

private fun <T> gameButtonSpec(reduced: Boolean): AnimationSpec<T> =
    if (reduced) snap() else spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault)

/**
 * The face every game button draws; [modifier] already carries its click or toggle, and
 * [describe] names it [label] when no visible label does. The sink is the press feedback
 * (MotionTokens: Press, chunky); fill, ink and lip ride the same spring. Reduced motion makes
 * both instant. The rim draws over [content], so a fill reaching the edge sits inside it.
 */
@Composable
internal fun GameButtonFace(
    colors: GameButtonColors,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    label: String,
    describe: Boolean,
    size: GameButtonSize,
    modifier: Modifier,
    content: @Composable BoxScope.(ink: Color) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme
    val pressed by interactionSource.collectIsPressedAsState()
    // One spring for sink and all three colours so enable/disable lands as a single motion.
    val dpSpec = remember(reduced) { gameButtonSpec<Dp>(reduced) }
    val colorSpec = remember(reduced) { gameButtonSpec<Color>(reduced) }
    val sink by animateDpAsState(if (pressed || !enabled) GameButtonLipDepth else 0.dp, dpSpec, label = "game-button-sink")
    val target = gameButtonTarget(colors, enabled, scheme)
    val fill by animateColorAsState(target.fill, colorSpec, label = "game-button-fill")
    val ink by animateColorAsState(target.content, colorSpec, label = "game-button-content")
    val lip by animateColorAsState(target.lip, colorSpec, label = "game-button-lip")
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = size.minHeight)
            .widthIn(min = ButtonMinHeight)
            .then(if (describe) Modifier.semantics { contentDescription = label } else Modifier)
            .drawBehind {
                val depth = GameButtonLipDepth.toPx()
                val face = Size(this.size.width, this.size.height - depth)
                translate(top = depth) { drawOutline(shape.createOutline(face, layoutDirection, this), lip) }
            }
            .padding(bottom = GameButtonLipDepth)
            .graphicsLayer { translationY = sink.toPx() }
            .background(fill, shape)
            .border(GameButtonRim, lip, shape),
        contentAlignment = Alignment.Center,
    ) {
        content(ink)
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
