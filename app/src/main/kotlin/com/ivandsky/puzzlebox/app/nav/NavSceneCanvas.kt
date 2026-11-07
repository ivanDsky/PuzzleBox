package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.ivandsky.puzzlebox.designsystem.LocalNavLiftVisibility
import com.ivandsky.puzzlebox.designsystem.navLiftTarget
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey

/**
 * The space the window's chrome (status bar, tab bar or rail) takes from a non-immersive
 * scene. The scene leaves that strip unpainted, so the chrome drawn beneath the scenes shows
 * through it and travels, fades or parallaxes on its own clock.
 */
internal val LocalChromePadding = compositionLocalOf { PaddingValues(0.dp) }

/** The move in flight, as chosen by the shell: what each scene's scrim reads. */
internal val LocalNavMotion = compositionLocalOf<State<NavMotion>> { mutableStateOf(NavMotion.Idle) }

/**
 * Every nav scene's canvas. A board or a result ([entryKey] a [GameKey], a friend's
 * [CustomGameKey], a [ResultKey] or a friend's [CustomResultKey]) is
 * full-window and opaque, insetting only its content; a board is also the target its card
 * lifts into. Any other scene paints opaque inside [LocalChromePadding] only. While this scene
 * is the upper page of a scrimmed move it dims what lies beneath: the window strip to its
 * start during a slide, the whole window during a lift.
 */
@Composable
internal fun SceneCanvas(entryKey: Any?, content: @Composable () -> Unit) {
    val scope = LocalNavAnimatedContentScope.current
    val motion by LocalNavMotion.current
    val transition = scope.transition
    val shown by transition.animateFloat(
        transitionSpec = { tween(motion.durationMillis, easing = motion.easing) },
        label = "scrim",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val scrim = MaterialTheme.colorScheme.scrim
    val background = MaterialTheme.colorScheme.background
    val immersive = entryKey is GameKey || entryKey is CustomGameKey ||
        entryKey is ResultKey || entryKey is CustomResultKey
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val running = transition.currentState != transition.targetState
                val incoming = transition.targetState == EnterExitState.Visible
                val upper = incoming == motion.incomingOnTop
                if (!running || !upper || motion.scrimAlpha == 0f) return@drawBehind
                val alpha = motion.scrimAlpha * shown
                if (motion.kind == NavMotionKind.Slide) {
                    val startward = if (layoutDirection == LayoutDirection.Ltr) -size.width else size.width
                    drawRect(scrim, topLeft = Offset(startward, 0f), size = size, alpha = alpha)
                } else {
                    drawRect(scrim, alpha = alpha)
                }
            },
    ) {
        CompositionLocalProvider(LocalNavLiftVisibility provides scope) {
            if (immersive) {
                val lifted = if (entryKey is GameKey) {
                    Modifier.navLiftTarget(entryKey, MaterialTheme.shapes.large)
                } else {
                    Modifier
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(lifted)
                        .background(background)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) { content() }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(LocalChromePadding.current)
                        .background(background),
                ) { content() }
            }
        }
    }
}
