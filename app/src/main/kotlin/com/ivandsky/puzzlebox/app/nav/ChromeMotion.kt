package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlin.math.roundToInt

/** The tab the last composition showed; read to tell a tab switch from a push. */
internal class TabRef(var tab: Any)

internal class ChromeMotion(val enter: EnterTransition, val exit: ExitTransition)

/**
 * How the bar or rail comes and goes. Phone: it rides the page beneath, parallaxing toward
 * the start on the slide clocks. Tablet: the rail fades and shifts out over the lift's first
 * stretch and back late in its return. A tab switch (onto a tab already in a game) fades it
 * on the tab clock; reduced motion is the short fade.
 */
@Composable
internal fun chromeMotion(
    expanded: Boolean,
    tabChanged: Boolean,
    reduceMotion: Boolean,
    layoutDirection: LayoutDirection,
): ChromeMotion {
    val startward = if (layoutDirection == LayoutDirection.Ltr) -1 else 1
    val shiftPx = with(LocalDensity.current) { MotionTokens.LiftNavShift.roundToPx() }
    return when {
        reduceMotion -> ChromeMotion(
            fadeIn(tween(MotionTokens.ReducedFadeDuration)),
            fadeOut(tween(MotionTokens.ReducedFadeDuration)),
        )
        tabChanged -> ChromeMotion(
            fadeIn(tween(MotionTokens.NavTabEnter, MotionTokens.NavTabEnterDelay, MotionTokens.EasingStandard)),
            fadeOut(tween(MotionTokens.NavTabExit, easing = MotionTokens.EasingLinear)),
        )
        expanded -> {
            val outMillis = (MotionTokens.LiftEnter * MotionTokens.LiftNavFraction).roundToInt()
            val inDelay = (MotionTokens.LiftExit * MotionTokens.LiftNavFraction).roundToInt()
            val inMillis = MotionTokens.LiftExit - inDelay
            ChromeMotion(
                fadeIn(tween(inMillis, inDelay, MotionTokens.EasingStandard)) +
                    slideInHorizontally(tween(inMillis, inDelay, MotionTokens.EasingStandard)) { startward * shiftPx },
                fadeOut(tween(outMillis, easing = MotionTokens.EasingStandard)) +
                    slideOutHorizontally(tween(outMillis, easing = MotionTokens.EasingStandard)) { startward * shiftPx },
            )
        }
        else -> {
            val parallax = { width: Int -> startward * (width * MotionTokens.NavParallaxFraction).roundToInt() }
            ChromeMotion(
                slideInHorizontally(tween(MotionTokens.NavFullSlidePop, easing = MotionTokens.EasingStandard), parallax),
                slideOutHorizontally(
                    tween(MotionTokens.NavFullSlideEnter, easing = MotionTokens.EasingEmphasizedDecelerate),
                    parallax,
                ),
            )
        }
    }
}

/** System bars everywhere, except the side the bar or rail holds, which is the chrome's own size. */
@Composable
internal fun chromePadding(expanded: Boolean, barHeight: Dp, railWidth: Dp): PaddingValues {
    val direction = LocalLayoutDirection.current
    val bars = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = if (expanded) railWidth else bars.calculateStartPadding(direction),
        top = bars.calculateTopPadding(),
        end = bars.calculateEndPadding(direction),
        bottom = if (expanded) bars.calculateBottomPadding() else barHeight,
    )
}
