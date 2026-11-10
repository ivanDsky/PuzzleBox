package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a Hint button waits after every press (F2); null is no wait, today's behavior. The
 * app provides it around its destinations while HintParity is on (spec A20); UI modules never
 * read the flag.
 */
val LocalHintCooldown = staticCompositionLocalOf<Duration?> { null }

/** The wait the app provides: five seconds from each press. */
val HINT_COOLDOWN: Duration = 5.seconds

/**
 * One Hint button's cooldown (F2): [press] runs the game's hint and starts the wait, and does
 * nothing while it runs; [gate] folds it into the game's own enabled state; [ring] is the fill
 * for [com.ivandsky.puzzlebox.designsystem.cooldownRing] (null while idle, under reduced motion,
 * or with no cooldown). It never looks at what a hint does (ADR-0001).
 */
@Stable
class HintCooldown internal constructor(
    private val window: Duration?,
    private val now: () -> Long,
    pressedAt: MutableState<Long?>,
    private val sweeps: Boolean,
) {
    internal var pressedAt by pressedAt
    internal val fill = Animatable(0f)
    private val readFill: () -> Float = { fill.value }

    /** True from a press until [window] has passed on the clock. */
    val coolingDown: Boolean
        get() {
            val start = pressedAt ?: return false
            val total = window ?: return false
            return now() - start < total.inWholeMilliseconds
        }

    val ring: (() -> Float)? get() = readFill.takeIf { sweeps && coolingDown }

    fun gate(enabled: Boolean): Boolean = enabled && !coolingDown

    fun press(hint: () -> Unit) {
        if (window == null) return hint()
        if (coolingDown) return
        pressedAt = now()
        hint()
    }

    /**
     * Runs the current wait out: the clock frees the button once what is left of the wait has
     * passed, and the fill sweeps beside it at real speed, never stretched or shrunk by the
     * animator duration scale, and only ever drawing. The fill goes back to empty before the
     * wait clears, so the next press's ring starts at 0 on its first frame.
     */
    internal suspend fun runOut() {
        val start = pressedAt ?: return
        val total = window?.inWholeMilliseconds ?: return
        val elapsed = (now() - start).coerceIn(0L, total)
        val left = total - elapsed
        coroutineScope {
            val sweep = if (sweeps) {
                launch(RealSpeed) {
                    fill.snapTo(elapsed.toFloat() / total)
                    fill.animateTo(1f, tween(left.toInt(), easing = LinearEasing))
                }
            } else {
                null
            }
            delay(left)
            if (sweep != null) {
                sweep.cancelAndJoin()
                fill.snapTo(0f)
            }
        }
        pressedAt = null
    }
}

/** The cooldown is a wait on the clock; its ring keeps that pace whatever the animator duration scale. */
private object RealSpeed : MotionDurationScale {
    override val scaleFactor = 1f
}
