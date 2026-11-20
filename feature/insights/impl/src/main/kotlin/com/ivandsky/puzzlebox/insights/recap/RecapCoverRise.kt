package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.designsystem.ContainerTransform
import com.ivandsky.puzzlebox.designsystem.LocalNavLift
import com.ivandsky.puzzlebox.designsystem.LocalNavLiftVisibility
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.delay

/**
 * The cover's content while the story grows out of its tile. [grown]: this open is that grow, so the cover's words wait
 * half the grow ([waiting]) and then rise in ([modifier]) and the deck skips its deal, the grow being the arrival. Any
 * other open shows the cover at once.
 */
@Stable
internal class CoverRise(val grown: Boolean) {
    var waiting by mutableStateOf(grown)
        private set
    private val shown = Animatable(if (grown) 0f else 1f)

    val modifier: Modifier = Modifier.graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * MotionTokens.EnterRise.toPx()
    }

    suspend fun play() {
        if (!waiting) return
        delay(ContainerTransform.enterDurationMillis / 2L)
        waiting = false
        shown.animateTo(1f, tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
}

/** This scene's [CoverRise]: grown when the nav is lifting it in out of a tile right now. */
@Composable
internal fun rememberCoverRise(): CoverRise {
    val lift = LocalNavLift.current
    val scene = LocalNavLiftVisibility.current
    val rise = remember { CoverRise(lift != null && scene != null && lift.isLiftingIn(scene)) }
    LaunchedEffect(rise) { rise.play() }
    return rise
}
