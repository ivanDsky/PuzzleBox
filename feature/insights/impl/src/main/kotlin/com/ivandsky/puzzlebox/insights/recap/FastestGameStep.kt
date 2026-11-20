package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The Fastest card's step between games, the prototype's `go`: the card on top leaves (forward thrown out left, back
 * dropped down and away) while the next one comes in (forward promoted up from under it, back thrown in from the left,
 * over it). A drag moves the card on top; a step starts from wherever the drag left it. Every value is read in a layer
 * block, so a frame redraws and never recomposes.
 */
@Stable
internal class GameStep(first: Int) {
    /** The game on top, and the key its card is composed under (a new one each step, so the slam replays). */
    var game by mutableIntStateOf(first)
        private set
    var key by mutableIntStateOf(0)
        private set

    /** The game still leaving, if any, under its own key. */
    var leaving by mutableStateOf<Int?>(null)
        private set
    var leavingKey by mutableIntStateOf(0)
        private set
    var forward by mutableStateOf(true)
        private set

    private var drag by mutableFloatStateOf(0f)
    private var enter by mutableFloatStateOf(1f)
    private var exit by mutableFloatStateOf(1f)
    private var exitFrom = 0f
    private var job: Job? = null
    private var settle: Job? = null

    fun dragBy(dx: Float) {
        settle?.cancel()
        drag += dx
    }

    /** Steps to [next], or springs the dragged card back when it is the game on top already. */
    fun go(scope: CoroutineScope, next: Int, reduced: Boolean) {
        settle?.cancel()
        if (next == game) {
            settle = scope.launch { animate(drag, 0f, animationSpec = SwipeMotion.Settle) { v, _ -> drag = v } }
            return
        }
        job?.cancel()
        exitFrom = drag
        drag = 0f
        forward = next > game
        leaving = game
        leavingKey = key
        key++
        game = next
        enter = 0f
        exit = 0f
        val enterMs = if (reduced) MotionTokens.ReducedFadeDuration else if (forward) GamePromoteDuration else GameBackEnterDuration
        val exitMs = if (reduced) MotionTokens.ReducedFadeDuration else if (forward) GameThrowExit else GameThrowBack
        val enterEasing: Easing = if (reduced) MotionTokens.EasingLinear else MotionTokens.EasingEmphasizedDecelerate
        val exitEasing: Easing = if (reduced) MotionTokens.EasingLinear else MotionTokens.EasingEmphasizedAccelerate
        job = scope.launch {
            launch { animate(0f, 1f, animationSpec = tween(enterMs, easing = enterEasing)) { v, _ -> enter = v } }
            animate(0f, 1f, animationSpec = tween(exitMs, easing = exitEasing)) { v, _ -> exit = v }
            leaving = null
        }
    }

    /** The card leaving: from where the drag left it to thrown out left (forward) or dropped down and away (back). */
    fun GraphicsLayerScope.leavingLayer(reduced: Boolean) {
        val p = exit
        alpha = 1f - p
        if (reduced) return
        val width = size.width
        val tilt = exitFrom / width * RecapMotion.DragTilt
        transformOrigin = TransformOrigin(0.5f, 1f)
        if (forward) {
            translationX = lerp(exitFrom, RecapMotion.ThrowTravel * width, p)
            rotationZ = lerp(tilt, RecapMotion.ThrowTilt, p)
        } else {
            translationX = exitFrom * (1f - p)
            translationY = RecapMotion.DealRise.toPx() * p
            rotationZ = lerp(tilt, BackDropTilt, p)
            scaleX = lerp(1f, BackDropScale, p)
            scaleY = scaleX
        }
    }

    /** The card on top: following the drag, and on a step promoted up from under (forward) or thrown in (back). */
    fun GraphicsLayerScope.enteringLayer(reduced: Boolean) {
        val q = enter
        if (reduced) {
            alpha = q
            return
        }
        val width = size.width
        transformOrigin = TransformOrigin(0.5f, 1f)
        translationX = drag
        rotationZ = drag / width * RecapMotion.DragTilt
        if (forward) {
            translationY = (1f - q) * PeekRise1.toPx()
            scaleX = lerp(PeekScale1, 1f, q)
            scaleY = scaleX
        } else {
            translationX += (1f - q) * RecapMotion.ThrowTravel * width
            rotationZ += (1f - q) * RecapMotion.ThrowTilt
            alpha = q
        }
    }
}

// The prototype's step values that motion.md has no token for, named after the prototype's variables.
private const val GameThrowExit = 220
private const val GameThrowBack = 180
private const val GamePromoteDuration = 260
private const val GameBackEnterDuration = 380
private val PeekRise1 = 14.dp
private const val PeekScale1 = 0.955f

/** The back step's leaving card: `rotate(4deg) scale(.92)` as it drops. */
private const val BackDropTilt = 4f
private const val BackDropScale = 0.92f
