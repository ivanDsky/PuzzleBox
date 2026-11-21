package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.recapCardColors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.model.GameId

/**
 * The sticker sheet: a dashed slot for every earned Moment, oldest day first, and a sticker stamped onto each slot once
 * [placed] reaches it. The grid is sized to the board ([stickerGrid]): as large as the room allows (a phone caps them),
 * in the columns that fit, the last row centred, nothing touching the board's edge. Tapping a sticker calls [onPeel];
 * the [peeled] one lifts.
 */
@Composable
internal fun MomentStickerSheet(
    stickers: List<EarnedMoment>,
    gameName: (GameId) -> String,
    placed: Int,
    peeled: Int?,
    onPeel: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // The widest day and the height a day takes under its sticker, gap included, in dp.
    val (dayWidth, dayHeight) = remember(stickers, dayStyle, density) {
        val days = stickers.map { measurer.measure(it.day.shortLabel(), dayStyle).size }
        with(density) { (days.maxOfOrNull { it.width } ?: 0).toDp().value to Spacing.sm.value + (days.maxOfOrNull { it.height } ?: 0).toDp().value }
    }
    BoxWithConstraints(
        modifier.clip(MaterialTheme.shapes.extraLarge).background(PaletteEntry.Moments.plate),
        contentAlignment = Alignment.Center,
    ) {
        // A wide sheet takes the tablet ladder and the height it needs (the card scrolls); a phone's fits its room.
        val wide = maxWidth >= WideSheet
        val grid = remember(stickers.size, maxWidth, maxHeight, dayWidth, dayHeight) {
            stickerGrid(stickers.size, maxWidth.value, maxHeight.value, wide, dayWidth, dayHeight)
        }
        Column(
            Modifier.padding(start = grid.side.dp, end = grid.side.dp, top = grid.top.dp, bottom = grid.side.dp),
            verticalArrangement = Arrangement.spacedBy(StickerGapY.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var index = 0
            stickers.chunked(grid.columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(StickerGapX.dp)) {
                    row.forEach { moment ->
                        val i = index++
                        Sticker(moment, i, grid, dayStyle, gameName, placed > i, peeled == i, { onPeel(i) })
                    }
                }
            }
        }
    }
}

/** One slot and the sticker on it, its day under it when the [grid] has room. The whole cell is one button for touch and for a screen reader. */
@Composable
private fun Sticker(
    moment: EarnedMoment,
    index: Int,
    grid: StickerGrid,
    dayStyle: TextStyle,
    gameName: (GameId) -> String,
    placed: Boolean,
    peeled: Boolean,
    onPeel: () -> Unit,
) {
    val size = grid.size.dp
    val ink = PaletteEntry.Moments.ink
    val reduced = rememberReducedMotion()
    val shape = moment.kind.shape()
    val curlColor = lerpColor(ink, PaletteEntry.Moments.plate, CurlTint)
    val stamp = rememberStamp(placed, reduced || entranceSettled())
    val peel by animateFloatAsState(if (peeled) 1f else 0f, if (reduced) snap() else tween(RecapMotion.PeelDuration, easing = MotionTokens.EasingStandard), label = "peel")
    val curl by animateFloatAsState(if (peeled) 1f else 0f, if (reduced) snap() else tween(RecapMotion.PeelCurl, easing = MotionTokens.EasingEmphasizedDecelerate), label = "curl")
    val day = moment.day.shortLabel()
    Column(
        Modifier
            .width(grid.cellWidth.dp)
            // No ripple: the peel lift is the feedback.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = placed, onClickLabel = "Peel", role = Role.Button, onClick = onPeel)
            .semantics(mergeDescendants = true) {
                contentDescription = "${moment.label(gameName)}, $day"
                selected = peeled
            }
            .testTag("recap.moments.sticker.$index"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size).drawBehind { slotOutline(shape, ink) }, contentAlignment = Alignment.Center) {
            Ring(stamp.ring, shape, ink, Modifier.size(size))
            // The curl is drawn on this lifting layer but outside the face's clip, so a circle's corner is not cut off.
            Box(
                Modifier
                    .size(size)
                    .graphicsLayer {
                        val t = stamp.value
                        val rest = ((index * RestStride) % RestSpread) - RestSpread / 2
                        rotationZ = rest + RecapMotion.StickerTilt * (1f - t) + RecapMotion.PeelTilt * peel
                        val grow = lerp(RecapMotion.StickerFromScale, 1f, t) * lerp(1f, RecapMotion.PeelScale, peel)
                        scaleX = grow
                        scaleY = grow
                        translationY = -RecapMotion.StickerDrop.toPx() * (1f - t) - RecapMotion.PeelLift.toPx() * peel
                        alpha = t.coerceIn(0f, 1f)
                    }
                    .drawWithContent {
                        drawContent()
                        if (curl > 0f) drawCurl(curl, curlColor)
                    },
            ) {
                StickerFace(moment, size, recapCardColors(PaletteEntry.Moments, LocalDarkTheme.current))
            }
        }
        if (grid.dated) {
            Text(
                text = day,
                modifier = Modifier.padding(top = Spacing.sm).clearAndSetSemantics { }.graphicsLayer { alpha = stamp.value.coerceIn(0f, 1f) },
                style = dayStyle,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Each kind has its own shape, so a kind is told apart by more than colour: a squircle, a circle, a rounded square and a drop. */
internal fun EarnedMoment.Kind.shape(): Shape = when (this) {
    EarnedMoment.Kind.PersonalBest -> RoundedCornerShape(30)
    EarnedMoment.Kind.FirstSubX -> CircleShape
    EarnedMoment.Kind.FullSet -> RoundedCornerShape(14)
    EarnedMoment.Kind.Streak -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 14, bottomEndPercent = 50, bottomStartPercent = 50)
}

private val WideSheet = 600.dp
private const val CurlTint = 0.55f
private const val RestStride = 37
private const val RestSpread = 9
