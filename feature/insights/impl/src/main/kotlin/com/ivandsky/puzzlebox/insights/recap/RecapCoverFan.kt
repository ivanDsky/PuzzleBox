package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.RecapCard
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.plate

/**
 * Five game letters fanned like a hand of cards, the cover's art (prototype `.fanc`): Queens, Tango, Zip, Mini Sudoku and
 * Strands, each in its game's accent, laid out in a 300 × 180 box (560 × 200 when [wide]) scaled to fit the room. Every
 * tile sits in a ring of the cover's plate and then one of its ink, so it reads against the field whatever its colour.
 * From [startMs] on the tiles spread out of one stack, one every [RecapMotion.FanStagger] ms (prototype `@keyframes fan`); under
 * reduced motion they sit fanned. Decoration only, so a screen reader skips it.
 */
@Composable
internal fun RecapCoverFan(modifier: Modifier = Modifier, wide: Boolean = false, startMs: Int = 0) {
    val ink = LocalContentColor.current
    val plate = RecapCard.Cover.plate
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val boxWidth = if (wide) WideBoxWidth else BoxWidth
        val boxHeight = if (wide) WideBoxHeight else BoxHeight
        val scale = minOf(maxWidth / boxWidth, maxHeight / boxHeight, if (wide) WideMaxScale else MaxScale)
        val spread = if (wide) WideSpread else Spread
        Box(Modifier.size(boxWidth * scale, boxHeight * scale)) {
            Letters.forEachIndexed { j, (slot, letter) ->
                val k = j - Letters.lastIndex / 2
                val accent = slot.colors()
                val p by coverEntrance(startMs + j * RecapMotion.FanStagger, tween(RecapMotion.FanMs, easing = RecapMotion.EasingSpring))
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size((TileWidth + Ring * 4) * scale, (TileHeight + Ring * 4) * scale)
                        .graphicsLayer {
                            val rest = p
                            translationX = rest * k * spread.toPx() * scale
                            translationY = rest * k * k * Drop.toPx() * scale + (1f - rest) * Rise.toPx() * scale
                            rotationZ = rest * k * Tilt
                            alpha = rest.coerceIn(0f, 1f)
                            // The layer spans the tile and its rings, so they fade together and the ink never shows through the plate.
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawBehind {
                            val radius = TileRadius.toPx() * scale
                            val ring = Ring.toPx() * scale
                            val tile = Size(size.width - 4 * ring, size.height - 4 * ring)
                            drawRoundRect(ink, Offset.Zero, size, CornerRadius(radius + 2 * ring))
                            drawRoundRect(plate, Offset(ring, ring), Size(size.width - 2 * ring, size.height - 2 * ring), CornerRadius(radius + ring))
                            drawRoundRect(accent.accent, Offset(2 * ring, 2 * ring), tile, CornerRadius(radius))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter,
                        color = accent.onAccent,
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = (LetterSize * scale).sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

private val Letters = listOf(
    AccentSlot.Violet to "Q",
    AccentSlot.Orange to "T",
    AccentSlot.Blue to "Z",
    AccentSlot.Teal to "M",
    AccentSlot.Pink to "S",
)

// Prototype `.fanc` and its `.fitbox` (data-nw/nh, data-wnw/wnh, `--sx`), in dp at scale 1.
private val BoxWidth = 300.dp
private val BoxHeight = 180.dp
private val WideBoxWidth = 560.dp
private val WideBoxHeight = 200.dp
private const val MaxScale = 1.5f
private const val WideMaxScale = 3f
private val TileWidth = 84.dp
private val TileHeight = 120.dp
private val TileRadius = 14.dp
private val Spread = 44.dp
private val WideSpread = 96.dp
private val Drop = 4.dp
private const val Tilt = 12f
private const val LetterSize = 34f

/** The plate ring and the ink ring around a tile are each this wide (prototype `box-shadow: 0 0 0 2px, 0 0 0 4px`). */
private val Ring = 2.dp

// Prototype `@keyframes fan`: each tile rises from [Rise] below, on [RecapMotion]'s fan clock.
private val Rise = 40.dp
