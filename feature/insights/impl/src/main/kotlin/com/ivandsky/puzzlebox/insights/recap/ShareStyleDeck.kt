package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.detectSwipe
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.swipeStep
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareImage
import com.ivandsky.puzzlebox.insights.share.ScaledToFit
import com.ivandsky.puzzlebox.insights.share.ShareImageHeightPx
import com.ivandsky.puzzlebox.insights.share.ShareImageWidthPx
import com.ivandsky.puzzlebox.insights.share.ShareStyle
import com.ivandsky.puzzlebox.insights.share.sharePalette
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The three posters of [ShareStyle] as a deck: on arrival their backs gather, fan out and flip up one after another
 * (sharefly); then the picked poster stands full size with its neighbours smaller and fainter beside it, and a swipe
 * or a chip ([onStyle]) picks another (shareswipe). Only the picked poster records into [layer], so what Share captures
 * is that very poster at final values, never the deck's motion. Until [arrived] the deck takes no swipe; [onArrived]
 * is called when the last poster has flipped. A swipe the deck cannot take (back from Classic, on from Night) is left
 * to the story. Reduced motion shows the final posters after one fade and the pick jumps.
 */
@Composable
internal fun ShareStyleDeck(
    content: RecapShareContent,
    style: ShareStyle,
    onStyle: (ShareStyle) -> Unit,
    layer: GraphicsLayer,
    arrived: Boolean,
    onArrived: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val last = ShareStyle.entries.lastIndex
    val settled = arrived || reduced
    val pos = remember { Animatable(style.ordinal.toFloat()) }
    val gather = remember { Animatable(if (settled) 1f else 0f) }
    val fan = remember { Animatable(if (settled) 1f else 0f) }
    val flips = remember { List(last + 1) { Animatable(if (settled) 1f else 0f) } }
    val sheen = remember { Animatable(0f) }
    val fade = remember { Animatable(if (reduced && !arrived) 0f else 1f) }
    
    LaunchedEffect(Unit) {
        if (arrived) return@LaunchedEffect
        if (reduced) {
            fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear))
            onArrived()
            return@LaunchedEffect
        }
        coroutineScope {
            launch {
                gather.animateTo(1f, tween(GatherDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
                fan.animateTo(1f, tween(FanDuration, easing = MotionTokens.EasingStandard))
            }
            val turned = flips.mapIndexed { i, flip ->
                launch {
                    delay((FlipStart + i * FlipStep).toLong())
                    flip.animateTo(1f, DeckSpring)
                }
            }
            launch {
                delay(SheenStart.toLong())
                sheen.animateTo(1f, tween(RecapMotion.SheenDuration, easing = MotionTokens.EasingStandard))
            }
            turned.joinAll()
            onArrived()
        }
    }
    LaunchedEffect(style, reduced) {
        if (reduced) {
            pos.snapTo(style.ordinal.toFloat())
        } else {
            pos.animateTo(style.ordinal.toFloat(), DeckSpring)
            if (arrived) {
                sheen.snapTo(0f)
                sheen.animateTo(1f, tween(RecapMotion.SheenDuration, easing = MotionTokens.EasingStandard))
            }
        }
    }

    BoxWithConstraints(modifier.alpha(fade.value), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val scale = minOf((maxWidth - PosterInset * 2) / ShareImageWidthPx, maxHeight / ShareImageHeightPx)
        val posterWidth = scale * ShareImageWidthPx
        val posterHeight = scale * ShareImageHeightPx
        val spacing = posterWidth * NeighbourGap
        val spacingPx = with(density) { spacing.toPx() }
        val commitPx = with(density) { SwipeMotion.CommitCap.toPx() }
        val flingPx = with(density) { SwipeMotion.FlingVelocity.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val swipe = Modifier.pointerInput(style, arrived, reduced) {
            if (!arrived) return@pointerInput
            detectSwipe(
                canTake = { travel -> if (travel < 0) style.ordinal < last else style.ordinal > 0 },
                onDrag = { dx -> if (!reduced) scope.launch { pos.snapTo((pos.value - dx / spacingPx).coerceIn(0f, last.toFloat())) } },
                onEnd = { drag, velocity ->
                    val step = swipeStep(drag, velocity, widthPx, flingPx, min(widthPx * SwipeMotion.CommitFraction, commitPx))
                    val next = (style.ordinal + step).coerceIn(0, last)
                    if (next != style.ordinal) {
                        onStyle(ShareStyle.entries[next])
                    } else {
                        scope.launch { if (reduced) pos.snapTo(style.ordinal.toFloat()) else pos.animateTo(style.ordinal.toFloat(), DeckSpring) }
                    }
                },
            )
        }
        Box(Modifier.matchParentSize().then(swipe), contentAlignment = Alignment.Center) {
            ShareStyle.entries.forEach { entry ->
                val i = entry.ordinal
                val picked = entry == style
                val ink = PaletteEntry.Share.ink
                val back = sharePalette(entry, LocalDarkTheme.current).ground
                Box(
                    Modifier
                        .size(posterWidth, posterHeight)
                        .zIndex(if (picked) 1f else 0f)
                        .graphicsLayer {
                            val flip = flips[i].value
                            val d = i - pos.value
                            val near = min(abs(d), 1f)
                            // The pager's place for this poster, and the fan's; the flip carries it from one to the other.
                            val restScale = 1f - (1f - NeighbourScale) * near
                            val restX = d * spacingPx
                            val fanX = (i - 1) * (with(density) { FanX.toPx() } * fan.value + with(density) { GatherX.toPx() } * (1f - gather.value))
                            val fanTilt = (i - 1) * FanTilt * fan.value
                            translationX = fanX + (restX - fanX) * flip
                            rotationZ = fanTilt * (1f - flip)
                            rotationY = FlipTurn * (1f - flip)
                            cameraDistance = CameraDistance * density.density
                            scaleX = 1f + (restScale - 1f) * flip
                            scaleY = scaleX
                            alpha = 1f - (1f - NeighbourAlpha) * near * flip
                        }
                        .clip(PosterShape)
                        .border(PosterBorder, ink, PosterShape)
                        .then(if (picked) Modifier else Modifier.clearAndSetSemantics {})
                        .drawWithContent {
                            drawContent()
                            if (flips[i].value < 0.5f) drawRect(back)
                            if (picked) drawSheen(sheen.value)
                        },
                ) {
                    ScaledToFitImage(content, entry, picked, layer, holoShift = { (pos.value - i) * (1f - HoloParallax) })
                }
            }
        }
    }
}

@Composable
private fun ScaledToFitImage(
    content: RecapShareContent,
    style: ShareStyle,
    picked: Boolean,
    layer: GraphicsLayer,
    holoShift: () -> Float,
) = ScaledToFit {
    RecapShareImage(
        content,
        style,
        if (picked) {
            Modifier.drawWithContent {
                layer.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
        } else {
            Modifier
        },
        holoShift,
    )
}

/** A diagonal band of light crossing the poster once as [progress] goes 0 to 1; nothing at either end. */
private fun DrawScope.drawSheen(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val band = size.width * SheenWidth
    val left = -band + (size.width + band) * progress
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = RecapMotion.SheenAlpha), Color.Transparent),
            start = Offset(left, 0f),
            end = Offset(left + band, size.height * SheenSlant),
        ),
    )
}

private val DeckSpring = spring<Float>(RecapMotion.DeckSpringDamping, RecapMotion.DeckSpringStiffness)

// sharefly (contract/motion.md): backs gather from ±GatherX, the fan opens to ±FanX and ±FanTilt, posters flip from
// FlipStart, FlipStep apart; the sheen starts at SheenStart.
private val GatherX = 220.dp
private val FanX = 150.dp
private const val FanTilt = 6f
private const val GatherDuration = 380
private const val FanDuration = 420
private const val FlipStart = 720
private const val FlipStep = 150
private const val SheenStart = 1380
private const val FlipTurn = 180f

// shareswipe: neighbours at lerp(1, .88) scale and lerp(1, .7) alpha; the holo layer drifts at parallax .95.
private const val NeighbourScale = 0.88f
private const val NeighbourAlpha = 0.7f
private const val HoloParallax = 0.95f

/** Neighbour centres sit this many poster widths from the picked one. */
private const val NeighbourGap = 1.0f
private val PosterInset = 28.dp
private const val CameraDistance = 12f
private const val SheenWidth = 0.5f
private const val SheenSlant = 0.2f
private val PosterShape = RoundedCornerShape(12.dp)
private val PosterBorder = 2.dp
