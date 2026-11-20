package com.ivandsky.puzzlebox.insights.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The poster's games as a hand of tiles (prototype 15 B): one tile per game in [games], in the game's accent with its
 * glyph, count and name, wider the more it was played, fanned like the cover's letters: the most played in the middle,
 * the rest ranked outward, each tilted and dropped by its distance from the middle ([handSlots]). One game is one wide
 * tile; no game draws nothing. Every tile is ringed in the [palette]'s opaque track and then its ink, and the accents are
 * those of the style's own theme ([SharePalette.accentsDark]), never the viewer's. A [clock] (ms since the hand started)
 * fans the tiles out of one stack and counts them up, for the deck's arrival overlay; without one the hand is at rest,
 * which is what the exported image draws.
 */
@Composable
internal fun ShareGamesHand(
    games: List<RecapShareContent.GameCount>,
    palette: SharePalette,
    modifier: Modifier = Modifier,
    clock: (() -> Float)? = null,
) {
    if (games.isEmpty()) return
    PuzzleBoxTheme(darkTheme = palette.accentsDark) {
        if (games.size == 1) {
            val wide = HandSlot(games.single(), ShareStripe.WideWidth, ShareStripe.WideHeight, 0f, 0f, 0f)
            Box(modifier.fillMaxWidth().padding(vertical = (ShareStripe.WidePad - ShareStripe.Ring * 2).dp), Alignment.Center) {
                HandTile(wide, 0, palette, clock, ShareStripe.WideRadius) { WideFace(wide.game, it) }
            }
        } else {
            Row(
                modifier
                    .fillMaxWidth()
                    .padding(top = (ShareStripe.PadTop - ShareStripe.Ring * 2).dp, bottom = (ShareStripe.PadBottom - ShareStripe.Ring * 2).dp),
                horizontalArrangement = Arrangement.spacedBy((ShareStripe.TileGap - ShareStripe.Ring * 4).dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                handSlots(games).forEachIndexed { j, slot ->
                    HandTile(slot, j, palette, clock, ShareStripe.TileRadius, Modifier.zIndex(-slot.width.toFloat())) { TileFace(slot, it) }
                }
            }
        }
    }
}

/**
 * [slot]'s tile in its rings (ink outside, the opaque track inside it, as the cover fan's), hatched and glossed. With a
 * [clock] it starts in the stack, [ShareStripe.Rise] px low, at [ShareStripe.StackScale] and unseen, and fans to its slot
 * on the spring, fading in over the first quarter of the way. [face] gets the count to show.
 */
@Composable
private fun HandTile(
    slot: HandSlot,
    index: Int,
    palette: SharePalette,
    clock: (() -> Float)?,
    radius: Int,
    modifier: Modifier = Modifier,
    face: @Composable (Int) -> Unit,
) {
    val accent = LocalGameBrands.current(slot.game.game).accent.colors().accent
    val ring = ShareStripe.Ring
    Box(
        modifier
            .size((slot.width + ring * 4).dp, (slot.height + ring * 4).dp)
            .graphicsLayer {
                val p = clock?.let { fanProgress(it(), index) } ?: 1f
                transformOrigin = TransformOrigin(0.5f, 1f)
                translationX = (1f - p) * slot.fromX.dp.toPx()
                translationY = (slot.drop + (1f - p) * ShareStripe.Rise).dp.toPx()
                rotationZ = p * slot.tilt
                scaleX = lerp(ShareStripe.StackScale, 1f, p)
                scaleY = scaleX
                alpha = (p / ShareStripe.FadeShare).coerceIn(0f, 1f)
                // The layer spans the tile and its rings, so they fade together and the ink never shows through the track.
                if (clock != null) compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawBehind {
                val r = radius.dp.toPx()
                val w = ring.dp.toPx()
                drawRoundRect(palette.ink, cornerRadius = CornerRadius(r + 2 * w))
                drawRoundRect(palette.trackSolid, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), CornerRadius(r + w))
            }
            .padding((ring * 2).dp)
            .clip(RoundedCornerShape(radius.dp))
            .drawBehind {
                drawRect(accent)
                hatch()
            },
        contentAlignment = Alignment.Center,
    ) {
        face(clock?.let { (slot.game.solved * countProgress(it(), index)).toInt() } ?: slot.game.solved)
    }
}

/** A hand tile's glyph, [count] and name, stacked and centred, sized by the tile's width. */
@Composable
private fun TileFace(slot: HandSlot, count: Int) {
    val brand = LocalGameBrands.current(slot.game.game)
    val on = brand.accent.colors().onAccent
    val countSize = max(ShareStripe.CountMin, (slot.width * ShareStripe.CountShare).roundToInt())
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ShareStripe.FaceGap.dp)) {
        Icon(brand.glyph, null, Modifier.size((slot.width * ShareStripe.GlyphShare).roundToInt().dp), tint = on)
        FitText(
            text = count.toString(),
            fit = slot.game.solved.toString(),
            style = countStyle(countSize, TextAlign.Center),
            color = on,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = slot.game.name,
            modifier = Modifier.padding(horizontal = ShareStripe.NamePad.dp),
            style = nameStyle(ShareStripe.NameSize, ShareStripe.NameLine),
            color = on,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one game's wide tile: its glyph on the left, its [count] over its name on the right. */
@Composable
private fun WideFace(game: RecapShareContent.GameCount, count: Int) {
    val brand = LocalGameBrands.current(game.game)
    val on = brand.accent.colors().onAccent
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ShareStripe.WideInset.dp),
        horizontalArrangement = Arrangement.spacedBy(ShareStripe.WideGap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(brand.glyph, null, Modifier.size(ShareStripe.WideGlyph.dp), tint = on)
        Column(Modifier.weight(1f)) {
            FitText(
                text = count.toString(),
                fit = game.solved.toString(),
                style = countStyle(ShareStripe.WideCount, TextAlign.Start),
                color = on,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(game.name, style = nameStyle(ShareStripe.WideName, ShareStripe.WideNameLine), color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun countStyle(size: Int, align: TextAlign) =
    MaterialTheme.typography.displayMedium.tabular().copy(fontSize = size.sp, lineHeight = (size * ShareStripe.CountLine).sp, textAlign = align)

@Composable
private fun nameStyle(size: Int, line: Int) =
    MaterialTheme.typography.titleLarge.copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight.SemiBold)

/** The tiles' texture: thin dark diagonal lines, then a white gloss fading out down the top of the tile. */
private fun DrawScope.hatch() {
    val period = ShareStripe.HatchPeriod / sqrt(2f)
    val line = ShareStripe.HatchLine / ShareStripe.HatchPeriod
    val dark = Color.Black.copy(alpha = ShareStripe.HatchAlpha)
    drawRect(
        Brush.linearGradient(
            0f to dark, line to dark, line to Color.Transparent, 1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(period, period),
            tileMode = TileMode.Repeated,
        ),
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = ShareStripe.GlossAlpha),
            ShareStripe.GlossShare to Color.White.copy(alpha = 0f),
            endY = size.height,
        ),
    )
}
