package com.ivandsky.puzzlebox.compete.create

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** Reveals the Customize panel's [content] when [open]; it grows in, and goes at once when closed. */
@Composable
internal fun CustomizeBody(open: Boolean, content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedVisibility(
        visible = open,
        enter = if (reduced) {
            fadeIn(tween(MotionTokens.ReducedFadeDuration))
        } else {
            expandVertically(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                fadeIn(tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        },
        exit = ExitTransition.None,
    ) { content() }
}
