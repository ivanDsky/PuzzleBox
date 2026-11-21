package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

/**
 * A month's stamp board: the month's Moments as stickers in its [colors], oldest day first, scattered by
 * [stampPlacement] and each dated underneath. Up to [MaxPlaced] show; past that, the first five and "+N more". The
 * stickers slam down one after another and each throws a ring, the Moments card's motion; reduced motion has them at
 * rest at once. A month with none shows a dashed slot and "No stamps this month". Stickers are not tappable here (the
 * Moments card owns the peel); the board reads as one group, "4 stamps earned", then each sticker with its date.
 */
@Composable
internal fun MonthStampBoard(
    moments: List<EarnedMoment>,
    colors: RecapCardColors,
    gameName: (GameId) -> String,
    floor: Dp,
    modifier: Modifier = Modifier,
) {
    val shown = if (moments.size > MaxPlaced) moments.take(MaxPlaced - 1) else moments
    val settled = rememberReducedMotion() || entranceSettled()
    var placed by remember(moments) { mutableIntStateOf(if (settled) shown.size else 0) }
    LaunchedEffect(moments, settled) {
        if (settled || shown.isEmpty()) {
            placed = shown.size
        } else {
            placed = 0
            delay(StampsFrom.toLong())
            val step = minOf(RecapMotion.StickerStepMax, RecapMotion.StickerStepBudget / shown.size)
            repeat(shown.size) {
                placed = it + 1
                delay(step.toLong())
            }
        }
    }
    val label = if (moments.isEmpty()) NoStamps else "${moments.size} ${if (moments.size == 1) "stamp" else "stamps"} earned"
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = floor)
            .semantics {
                contentDescription = label
                isTraversalGroup = true
            }
            .testTag("recap.month.stamps"),
    ) {
        // The card gives the board what its column leaves; measured on its own (a preview), it is its minimum.
        val height = if (constraints.hasBoundedHeight) maxHeight else floor
        if (shown.isEmpty()) {
            EmptySlot(colors, boardUnit(maxWidth.value, height.value).dp * PlaceholderScale, maxWidth, height)
            Text(
                text = NoStamps,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).clearAndSetSemantics { },
                style = BoardNote.copy(fontWeight = FontWeight.SemiBold),
            )
            return@BoxWithConstraints
        }
        val spots = stampPlacement(shown.size, maxWidth.value, height.value)
        shown.zip(spots).forEachIndexed { index, (moment, spot) ->
            BoardSticker(moment, spot, index, placed = index < placed, settled = settled, colors = colors, gameName = gameName)
        }
        if (moments.size > shown.size) {
            Text(
                text = "+${moments.size - shown.size} more",
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp).testTag("recap.month.stamps.more"),
                style = BoardNote.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** One sticker centred on its [spot] at its rest tilt, dated underneath; it slams down once [placed]. */
@Composable
private fun BoardSticker(
    moment: EarnedMoment,
    spot: PlacedStamp,
    index: Int,
    placed: Boolean,
    settled: Boolean,
    colors: RecapCardColors,
    gameName: (GameId) -> String,
) {
    val stamp = rememberStamp(placed, settled)
    val shape = moment.kind.shape()
    val size = spot.size.dp
    val day = moment.day.shortLabel()
    Box(
        Modifier
            .offset((spot.cx - spot.size / 2f).dp, (spot.cy - spot.size / 2f).dp)
            .size(size)
            .rotate(spot.tilt)
            .semantics { contentDescription = "${moment.label(gameName)}, $day" }
            .testTag("recap.month.stamp.$index"),
    ) {
        Ring(stamp.ring, shape, colors.ink, Modifier.size(size), stroke = 2.dp)
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val t = stamp.value
                    rotationZ = RecapMotion.StickerTilt * (1f - t)
                    val grow = lerp(RecapMotion.StickerFromScale, 1f, t)
                    scaleX = grow
                    scaleY = grow
                    translationY = -RecapMotion.StickerDrop.toPx() * (1f - t)
                    alpha = t.coerceIn(0f, 1f)
                },
        ) {
            StickerFace(moment, size, colors)
        }
    }
    val caption = spot.caption
    Text(
        text = day,
        modifier = Modifier
            .offset(caption.left.dp, caption.top.dp)
            .width(CaptionWidth.dp)
            .wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = stamp.value.coerceIn(0f, 1f) },
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = colors.ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/** The dashed personal-best slot of a board with no stamps: [size] across, at 50% / 42% of the board, tilted a little. */
@Composable
private fun EmptySlot(colors: RecapCardColors, size: Dp, width: Dp, height: Dp) {
    val shape = EarnedMoment.Kind.PersonalBest.shape()
    Box(
        Modifier
            .offset(width / 2 - size / 2, height * PlaceholderY - size / 2)
            .size(size)
            .rotate(PlaceholderTilt)
            .drawBehind { slotOutline(shape, colors.ink, alpha = PlaceholderAlpha) }
            .testTag("recap.month.stamps.empty"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PuzzleIcons.Trophy, contentDescription = null, tint = colors.ink, modifier = Modifier.size(size * PlaceholderGlyph))
    }
}

/** The board's own lines, "+2 more" and "No stamps this month": 14/20. */
private val BoardNote
    @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp)

/** The first sticker lands this long after the card is dealt: the card's 180ms beat, then 480ms for the count to start. */
private const val StampsFrom = 660
private const val PlaceholderScale = 1.25f
private const val PlaceholderY = 0.42f
private const val PlaceholderTilt = -4f
private const val PlaceholderAlpha = 0.7f
private const val PlaceholderGlyph = 0.3f
