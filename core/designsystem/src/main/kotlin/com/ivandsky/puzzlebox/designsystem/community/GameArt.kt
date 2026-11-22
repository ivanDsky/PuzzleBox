package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.colors

/**
 * The picture on a community card: the game colour at [ShelfCardDefaults.ArtTint] over the quiet
 * surface, a dot pattern, and the game's [glyph] on a [discSize] disc. [modifier] sets the size and
 * clip; [overlay] draws chips over it.
 */
@Composable
internal fun GameArt(
    accent: AccentSlot,
    glyph: ImageVector,
    discSize: Dp,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = accent.colors()
    val face = colors.accent.copy(alpha = ShelfCardDefaults.ArtTint).compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)
    Box(
        modifier = modifier
            .background(face)
            .drawBehind {
                val step = ShelfCardDefaults.PatternStep.toPx()
                val radius = ShelfCardDefaults.PatternDot.toPx()
                val dot = colors.accent.copy(alpha = ShelfCardDefaults.PatternAlpha)
                var y = step / 2
                while (y < size.height) {
                    var x = step / 2
                    while (x < size.width) {
                        drawCircle(dot, radius, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(discSize).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(glyph, contentDescription = null, modifier = Modifier.size(discSize * DISC_GLYPH), tint = colors.onAccent)
        }
        Box(Modifier.fillMaxSize()) { overlay() }
    }
}

// The glyph fills 58% of its disc.
private const val DISC_GLYPH = 0.58f
