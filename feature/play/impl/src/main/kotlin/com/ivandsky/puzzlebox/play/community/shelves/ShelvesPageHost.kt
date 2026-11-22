package com.ivandsky.puzzlebox.play.community.shelves

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private const val ENTER_MILLIS = 280
private const val ENTER_SHIFT = 0.24f
private const val EXIT_MILLIS = 200
private const val EXIT_SHIFT = 0.16f

private val EnterEasing = MotionTokens.EasingEmphasizedDecelerate

/**
 * The shelves [home] with the open [page] (null: none) over it. A page slides in from [ENTER_SHIFT] of its width while it
 * fades in over [ENTER_MILLIS] ms (emphasized decelerate) and leaves by sliding [EXIT_SHIFT] while it fades over
 * [EXIT_MILLIS] ms; under reduced motion it just appears and goes. The page is opaque and takes the touches, and system
 * back asks [onBack]. The same host fills the phone's screen and the tablet's list pane.
 */
@Composable
internal fun ShelvesPageHost(
    page: ShelvesPage?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    home: @Composable () -> Unit,
    content: @Composable (ShelvesPage) -> Unit,
) {
    val reduced = rememberReducedMotion()
    // The last page stays drawn while it animates out.
    var last by remember { mutableStateOf(page) }
    if (page != null) last = page
    BackHandler(enabled = page != null, onBack = onBack)
    Box(modifier) {
        home()
        AnimatedVisibility(
            visible = page != null,
            enter = if (reduced) EnterTransition.None else slideInHorizontally(tween(ENTER_MILLIS, easing = EnterEasing)) { (it * ENTER_SHIFT).toInt() } +
                fadeIn(tween(ENTER_MILLIS, easing = EnterEasing)),
            exit = if (reduced) ExitTransition.None else slideOutHorizontally(tween(EXIT_MILLIS, easing = MotionTokens.EasingStandard)) { (it * EXIT_SHIFT).toInt() } +
                fadeOut(tween(EXIT_MILLIS, easing = MotionTokens.EasingStandard)),
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pointerInput(Unit) {}) {
                last?.let { content(it) }
            }
        }
    }
}
