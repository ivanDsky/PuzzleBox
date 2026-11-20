package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sticker sheet: a dashed slot for every earned Moment, oldest day first, and a sticker stamped onto each slot once
 * [placed] reaches it. Stickers share the sheet's room: as large as it allows (a phone caps them), in as many columns as
 * the count and the width call for, the last row centred. Tapping a sticker calls [onPeel]; the [peeled] one lifts.
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
    BoxWithConstraints(
        modifier.clip(MaterialTheme.shapes.extraLarge).background(PaletteEntry.Moments.plate),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= WideSheet
        val columns = columnsFor(stickers.size, wide)
        val rows = stickers.chunked(columns)
        val cellWidth = (maxWidth - SheetPad * 2 - GapX * (columns - 1)) / columns
        // A wide sheet takes the prototype's tablet ladder and the height it needs (the card scrolls); a phone's fits its room.
        val size = if (wide) {
            minOf(tabletSize(stickers.size), cellWidth).coerceAtLeast(MinSize)
        } else {
            val cellHeight = (maxHeight - SheetPad * 2 - GapY * (rows.size - 1)) / rows.size - LabelHeight
            minOf(if (stickers.size <= 2) AloneSize else MultiSize, cellWidth, cellHeight).coerceAtLeast(MinSize)
        }
        val pad = if (wide) Modifier.padding(SheetPad) else Modifier
        Column(pad, verticalArrangement = Arrangement.spacedBy(GapY), horizontalAlignment = Alignment.CenterHorizontally) {
            var index = 0
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GapX)) {
                    row.forEach { moment ->
                        val i = index++
                        Sticker(moment, i, size, gameName, placed > i, peeled == i, { onPeel(i) })
                    }
                }
            }
        }
    }
}

/** One slot and the sticker on it. The whole cell, sticker and day, is one button for touch and for a screen reader. */
@Composable
private fun Sticker(
    moment: EarnedMoment,
    index: Int,
    size: Dp,
    gameName: (GameId) -> String,
    placed: Boolean,
    peeled: Boolean,
    onPeel: () -> Unit,
) {
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
            .width(size)
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .shadow(StickerShadow, shape)
                        .clip(shape)
                        .background(ink)
                        .border(StickerRim, PaletteEntry.Moments.plate, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = moment.face(),
                        modifier = Modifier.clearAndSetSemantics { },
                        style = MaterialTheme.typography.titleLarge.tabular().copy(fontSize = (size.value * FaceScale).sp, fontWeight = FontWeight.ExtraBold),
                        color = PaletteEntry.Moments.field,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = day,
            modifier = Modifier.padding(top = Spacing.sm).clearAndSetSemantics { }.graphicsLayer { alpha = stamp.value.coerceIn(0f, 1f) },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = ink,
            maxLines = 1,
        )
    }
}

/** How far a sticker's stamp has come: [value] runs 0 to 1 on the Stamp spring (and past it, settling); [ring] runs the ring that spreads after it. */
private class Stamp(private val spring: Animatable<Float, AnimationVector1D>, val ring: () -> Float) {
    val value: Float get() = spring.value
}

/** Stamps once [placed] turns true; with [settled] (reduced motion, a preview) the sticker is down at once. */
@Composable
private fun rememberStamp(placed: Boolean, settled: Boolean): Stamp {
    val spring = remember { Animatable(if (placed && settled) 1f else 0f) }
    val ring = remember { Animatable(1f) }
    LaunchedEffect(placed, settled) {
        when {
            !placed -> spring.snapTo(0f)
            settled -> spring.snapTo(1f)
            else -> {
                launch {
                    delay(RecapMotion.RingStagger.toLong())
                    ring.snapTo(0f)
                    ring.animateTo(1f, tween(RecapMotion.RingDuration, easing = MotionTokens.Trace.EasingOut))
                }
                spring.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness))
            }
        }
    }
    return remember(spring, ring) { Stamp(spring) { ring.value } }
}

/** The ring that spreads from a stamped sticker and fades: [progress] 0 to 1. */
@Composable
private fun Ring(progress: () -> Float, shape: Shape, color: Color, modifier: Modifier) {
    Box(
        modifier
            .graphicsLayer {
                val p = progress()
                val grow = lerp(1f, RecapMotion.RingScale, p)
                scaleX = grow
                scaleY = grow
                alpha = RecapMotion.RingAlphaFrom * (1f - p)
            }
            .border(BorderStroke(RingStroke, color), shape),
    )
}

/** Each kind has its own shape, so a kind is told apart by more than colour: a squircle, a circle, a rounded square and a drop. */
internal fun EarnedMoment.Kind.shape(): Shape = when (this) {
    EarnedMoment.Kind.PersonalBest -> RoundedCornerShape(30)
    EarnedMoment.Kind.FirstSubX -> CircleShape
    EarnedMoment.Kind.FullSet -> RoundedCornerShape(14)
    EarnedMoment.Kind.Streak -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 14, bottomEndPercent = 50, bottomStartPercent = 50)
}

/** Columns for [count] stickers: one row up to four, three for five or six, then as many as the width holds. */
private fun columnsFor(count: Int, wide: Boolean): Int = when {
    count <= 4 -> count.coerceAtLeast(1)
    count <= 6 -> 3
    count <= 8 -> 4
    wide -> 7
    else -> 5
}

/** The prototype's tablet ladder: the sticker size for [count] stickers, as large as 176dp alone down to 92dp past eight. */
private fun tabletSize(count: Int): Dp = when {
    count <= 2 -> 176.dp
    count <= 4 -> 144.dp
    count <= 8 -> 128.dp
    else -> 92.dp
}

private val WideSheet = 600.dp
private val SheetPad = 12.dp
private val GapX = 10.dp
private val GapY = 6.dp
private val LabelHeight = 28.dp
private val MinSize = 48.dp
private val AloneSize = 120.dp
private val MultiSize = 68.dp
private val StickerShadow = 3.dp
private val StickerRim = 2.dp
private val RingStroke = 3.dp
private const val CurlTint = 0.55f
private const val FaceScale = 0.3f
private const val RestStride = 37
private const val RestSpread = 9
