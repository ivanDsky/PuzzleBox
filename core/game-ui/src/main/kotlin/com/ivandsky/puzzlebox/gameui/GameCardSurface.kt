package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The one clickable hub card shell (hub card handoff §Up-next card, §Tile): [colors]'
 * fill clipped to [shape], [motif] behind [content], both filling the card, and the
 * whole card one click target that scales to [MotionTokens.PressScale] while pressed.
 * Always enabled: an offline card's tap is its retry. Content defaults to [colors]' fg.
 */
@Composable
fun GameCardSurface(
    colors: GameCardColors,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    motif: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionTokens.PressScale else 1f,
        animationSpec = spring(
            dampingRatio = MotionTokens.SpringDampingDefault,
            stiffness = MotionTokens.SpringStiffnessDefault,
        ),
        label = "card-press-scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(colors.fill)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
    ) {
        Box(Modifier.matchParentSize(), content = motif)
        CompositionLocalProvider(LocalContentColor provides colors.fg) {
            content()
        }
    }
}

// Preview: a hero-sized and a tile-sized card in the accent, then the offline tile.
@PuzzleBoxPreviews
@Composable
internal fun GameCardSurfacePreview() {
    PuzzleBoxTheme {
        val brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink)
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val hero = gameCardColors(brand, offline = false)
            GameCardSurface(
                colors = hero,
                shape = AppShapes.extraLarge,
                onClick = {},
                modifier = Modifier.size(380.dp, 152.dp),
                motif = {
                    GameMotif(brand, 210.dp, 1.4f, Alignment.BottomEnd, DpOffset(34.dp, 54.dp), hero.motif, hero.motifAlpha)
                },
            ) {
                Text("Up next", Modifier.align(Alignment.BottomStart).padding(Spacing.xl))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(false, true).forEach { offline ->
                    val tile = gameCardColors(brand, offline)
                    GameCardSurface(
                        colors = tile,
                        shape = AppShapes.large,
                        onClick = {},
                        modifier = Modifier.size(184.dp, 156.dp),
                        motif = {
                            GameMotif(brand, 132.dp, 1.6f, Alignment.TopEnd, DpOffset(30.dp, (-26).dp), tile.motif, tile.motifAlpha)
                        },
                    ) {
                        Text(
                            if (offline) "Offline" else "Tile",
                            Modifier.align(Alignment.BottomStart).padding(Spacing.lg),
                            color = tile.fg2,
                        )
                    }
                }
            }
        }
    }
}
