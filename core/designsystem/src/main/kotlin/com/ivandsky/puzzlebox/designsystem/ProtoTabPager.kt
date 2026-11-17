package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Prototype 141 (throwaway): the tab pager under [option] A to D. All four keep every page composed
 * (`beyondViewportPageCount` is the whole set), so no page is built mid-swipe and no entrance replays, and all four
 * attach to the screen's [TabSwipeLink] so a right pane and head actions follow the position.
 *
 * - A: the shipped slide and settle ([MotionTokens.DurationLong] on [MotionTokens.EasingEmphasizedDecelerate]).
 * - B: a swipe slides as in A; any tap fades through with a short slide of a twelfth of the width ([TabJump]).
 * - C: the page on the right slides in 1:1 on top; the one on the left moves at [MotionTokens.NavParallaxFraction]
 *   under a scrim that deepens to [MotionTokens.NavScrimAlpha].
 * - D: A, but a tap on a tab two or more away fades through in place ([TabJump] with no travel).
 *
 * A tap that slides (A, C, D's near tabs) tells the link where it is headed, so the pane goes straight there.
 *
 * Under reduced motion every option is the same: pages crossfade in place (a swipe by its position, a tap in one
 * [MotionTokens.ReducedFadeDuration] fade straight to its tab), the bar jumps to the target tab, and a swipe still
 * changes the tab.
 */
@Composable
internal fun ProtoTabPager(
    option: TabSwipeOption,
    state: PagerState,
    tabs: List<UnderlineTab>,
    onReselect: (Int) -> Unit,
    modifier: Modifier,
    selectedPage: Int?,
    onPageSettled: (Int) -> Unit,
    content: @Composable (page: Int) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val reselect by rememberUpdatedState(onReselect)
    val settled by rememberUpdatedState(onPageSettled)
    val settle = if (reduced) {
        tween<Float>(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear)
    } else {
        tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate)
    }
    val link = LocalTabSwipeLink.current
    val jump = remember { TabJump() }
    var jumping by remember { mutableStateOf<Job?>(null) }
    var width by remember { mutableIntStateOf(0) }
    val position = { state.currentPage + state.currentPageOffsetFraction }
    DisposableEffect(link, state) {
        link?.attach(position)
        onDispose { link?.detach() }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect { settled(it) }
    }
    // A fade through runs in the pager's own scope: the host hears the snap and may change `selectedPage` mid-fade,
    // which must not cut the fade short.
    val moveTo: (Int) -> Unit = { index ->
        val fadeThrough = !reduced && when (option) {
            TabSwipeOption.B -> true
            TabSwipeOption.D -> abs(index - state.currentPage) >= FarTabs
            else -> false
        }
        jumping?.cancel()
        jumping = scope.launch {
            when {
                reduced -> jump.crossfade(state, link, index)
                fadeThrough -> jump.run(state, link, index, travel = if (option == TabSwipeOption.B) width / TravelShare else 0f)
                else -> {
                    link?.glide(state.currentPage, index)
                    try {
                        state.animateScrollToPage(index, animationSpec = settle)
                    } finally {
                        link?.land()
                    }
                }
            }
        }
    }
    LaunchedEffect(selectedPage) {
        if (selectedPage != null && selectedPage in tabs.indices && selectedPage != state.currentPage) moveTo(selectedPage)
    }
    Column(modifier) {
        UnderlineTabsAt(
            tabs = tabs,
            position = {
                when {
                    jump.active -> jump.bar.value
                    reduced -> state.targetPage.toFloat()
                    else -> position()
                }
            },
            onSelect = { index -> if (index == state.currentPage) reselect(index) else moveTo(index) },
        )
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("underline.pager").onSizeChanged { width = it.width },
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = (tabs.size - 1).coerceAtLeast(0),
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = settle,
            ),
        ) { page ->
            ProtoPage(page, option, reduced, position, jump, content)
        }
    }
}

/** One page under [option]: where it is drawn and how opaque, read per frame from the pager's position or the [jump]. */
@Composable
private fun ProtoPage(
    page: Int,
    option: TabSwipeOption,
    reduced: Boolean,
    positionOf: () -> Float,
    jump: TabJump,
    content: @Composable (Int) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val scrim = MaterialTheme.colorScheme.scrim
    val place = when {
        // A page a whole page away stays where the pager put it, off screen, so it takes no touch from the one on show.
        reduced -> Modifier.graphicsLayer {
            val away = page - positionOf()
            when {
                jump.active -> {
                    alpha = when (page) {
                        jump.shown -> jump.alpha
                        jump.arriving -> 1f - jump.alpha
                        else -> 0f
                    }
                    if (page == jump.arriving) translationX = -away * size.width
                }
                abs(away) < 1f -> {
                    translationX = -away * size.width
                    alpha = 1f - abs(away)
                }
                else -> alpha = 0f
            }
        }
        option == TabSwipeOption.C -> Modifier
            .graphicsLayer {
                val away = page - positionOf()
                if (away > -1f && away < 0f) translationX = -away * size.width * (1f - MotionTokens.NavParallaxFraction)
            }
            .background(background)
            .drawWithContent {
                drawContent()
                val away = page - positionOf()
                if (away > -1f && away < 0f) drawRect(scrim, alpha = -away * MotionTokens.NavScrimAlpha)
            }
        else -> Modifier.graphicsLayer {
            if (jump.active) {
                val shown = page == jump.shown
                alpha = if (shown) jump.alpha else 0f
                translationX = if (shown) jump.shift else 0f
            }
        }
    }
    Box(Modifier.fillMaxSize().then(place)) { content(page) }
}

/** A tap this many tabs away, or more, fades through under option D. */
private const val FarTabs = 2

/** Option B's travel is the pager's width over this: the Games list-to-page push's share. */
private const val TravelShare = 12f
