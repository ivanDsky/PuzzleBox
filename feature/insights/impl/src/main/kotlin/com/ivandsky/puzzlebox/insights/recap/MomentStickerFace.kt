package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A Moment sticker as the Moments sheet and a month card's stamp board both draw it: the face in a card's colors, the
// stamp spring that brings it down, and the ring that spreads after it.

/** A sticker's face, [size] across in its kind's shape: [colors] ink body, plate rim and the face text in the field. */
@Composable
internal fun StickerFace(moment: EarnedMoment, size: Dp, colors: RecapCardColors, modifier: Modifier = Modifier) {
    val shape = moment.kind.shape()
    Box(
        modifier
            .fillMaxSize()
            .shadow(StickerShadow, shape)
            .clip(shape)
            .background(colors.ink)
            .border(StickerRim, colors.plate, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = moment.face(),
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.titleLarge.tabular().copy(fontSize = (size.value * FaceScale).sp, fontWeight = FontWeight.ExtraBold),
            color = colors.field,
            maxLines = 1,
        )
    }
}

/** How far a sticker's stamp has come: [value] runs 0 to 1 on the Stamp spring (and past it, settling); [ring] runs the ring that spreads after it. */
internal class Stamp(private val spring: Animatable<Float, AnimationVector1D>, val ring: () -> Float) {
    val value: Float get() = spring.value
}

/** Stamps once [placed] turns true; with [settled] (reduced motion, a preview) the sticker is down at once. */
@Composable
internal fun rememberStamp(placed: Boolean, settled: Boolean): Stamp {
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

/** The ring that spreads from a stamped sticker and fades: [progress] 0 to 1, drawn [stroke] wide. */
@Composable
internal fun Ring(progress: () -> Float, shape: Shape, color: Color, modifier: Modifier, stroke: Dp = RingStroke) {
    Box(
        modifier
            .graphicsLayer {
                val p = progress()
                val grow = lerp(1f, RecapMotion.RingScale, p)
                scaleX = grow
                scaleY = grow
                alpha = RecapMotion.RingAlphaFrom * (1f - p)
            }
            .border(BorderStroke(stroke, color), shape),
    )
}

private val StickerShadow = 3.dp
private val StickerRim = 2.dp
private val RingStroke = 3.dp
private const val FaceScale = 0.3f
