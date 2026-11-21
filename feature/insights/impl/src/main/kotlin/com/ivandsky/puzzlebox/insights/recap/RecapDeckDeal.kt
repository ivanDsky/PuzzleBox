package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultCameraDistance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The deal on a fresh open: the top card rises from [RecapMotion.DealRise] below, tilted and shrunk, lands face-down
 * and turns face-up [RecapMotion.FlipDelay] ms in, then a sheen crosses it. With reduced motion it only fades in, face-up.
 */
@Stable
internal class DealMotion(dealing: Boolean, reduced: Boolean) {
    private val moves = dealing && !reduced
    val appear = Animatable(if (dealing) 0f else 1f)
    val rise = Animatable(if (moves) 0f else 1f)
    val turn = Animatable(if (moves) RecapMotion.FlipTurn else 0f)
    val sheen = Animatable(if (moves) 0f else 1f)

    /** Past a quarter turn the card shows its back, so its face is not drawn. */
    val faceDown by derivedStateOf { turn.value > RecapMotion.FlipTurn / 2 }

    suspend fun play(reduced: Boolean) = coroutineScope {
        launch { appear.animateTo(1f, tween(MotionTokens.DurationShort, easing = MotionTokens.EasingLinear)) }
        if (reduced) return@coroutineScope
        launch { rise.animateTo(1f, tween(RecapMotion.DealDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) }
        launch {
            delay(RecapMotion.FlipDelay.toLong())
            turn.animateTo(0f, spring(RecapMotion.DeckSpringDamping, RecapMotion.DeckSpringStiffness))
        }
        launch {
            delay((RecapMotion.FlipDelay + RecapMotion.SheenDelay).toLong())
            sheen.animateTo(1f, tween(RecapMotion.SheenDuration, easing = MotionTokens.EasingStandard))
        }
    }
}

/** Plays the deal once for a story that has not been dealt, then marks [deck] dealt so a restore never replays it. */
@Composable
internal fun rememberDeal(deck: RecapDeckState, reduced: Boolean): DealMotion {
    val deal = remember(deck) { DealMotion(deck.dealing, reduced) }
    LaunchedEffect(deal) {
        if (!deck.dealing) return@LaunchedEffect
        deal.play(reduced)
        deck.dealt()
    }
    return deal
}

/** Draws the card [deal] moves: risen, tilted, turned about its vertical axis inside [shape], and the sheen over it. */
internal fun Modifier.dealt(deal: DealMotion, shape: Shape): Modifier = graphicsLayer {
    val rest = 1f - deal.rise.value
    alpha = deal.appear.value
    translationY = rest * RecapMotion.DealRise.toPx()
    rotationZ = rest * RecapMotion.DealTilt
    scaleX = lerp(RecapMotion.DealScaleFrom, 1f, deal.rise.value)
    scaleY = scaleX
    rotationY = deal.turn.value
    // The camera stands as far off, for the card's size, as the prototype's does: one at a fixed distance sits so close
    // to a large tablet's card that the turning card flares past the screen edges.
    cameraDistance = (FlipCameraReach * max(size.width, size.height) / CameraUnitPx).coerceAtLeast(DefaultCameraDistance)
    this.shape = shape
    clip = true
}.drawWithContent {
    drawContent()
    val sweep = deal.sheen.value
    if (sweep <= 0f || sweep >= 1f) return@drawWithContent
    val centre = size.width * (2f * sweep - 0.5f)
    drawRect(
        Brush.linearGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = RecapMotion.SheenAlpha), Color.Transparent),
            start = Offset(centre - size.width / 2, 0f),
            end = Offset(centre + size.width / 2, size.height),
        ),
    )
}

/**
 * The camera's distance in lengths of the card's longer side: the prototype's 1400dp perspective over its 820dp tablet
 * card (its 380 × 791dp phone card gives about the same).
 */
private const val FlipCameraReach = 1400f / 820f

/** A layer's camera distance counts in inches of 72 px (Skia's camera), not in screen pixels. */
private const val CameraUnitPx = 72f
