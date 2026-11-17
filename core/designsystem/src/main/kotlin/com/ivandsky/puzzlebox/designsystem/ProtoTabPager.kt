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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Prototype 141 (throwaway): the tab pager under [option] A to E. All five keep every page composed
 * (`beyondViewportPageCount` is the whole set), so no page is built mid-swipe and no entrance replays, and all five
 * attach to the screen's [TabSwipeLink] so a right pane and head actions follow the position.
 *
 * - A: the shipped slide and settle ([MotionTokens.DurationLong] on [MotionTokens.EasingEmphasizedDecelerate]).
 * - B: a swipe slides as in A; any tap fades through with a short slide of a twelfth of the width ([TabJump]).
 * - C: the page on the right slides in 1:1 on top, behind a hairline edge; the one on the left moves at
 *   [MotionTokens.NavParallaxFraction] under a scrim that deepens to [MotionTokens.NavScrimAlpha].
 * - D: A, but a tap on a tab two or more away fades through in place ([TabJump] with no travel).
 * - E: nothing slides. A swipe is a scroll-driven fade through: the page on show follows the finger at [FollowShare]
 *   of its travel and is gone by halfway, and the next one comes in over the other half from the same distance on
 *   the far side. The settle runs the rest on A's clock, any tap is B's fade through (the same travel), and the link
 *   fades through too ([TabSwipeLink.through]), so list and pane change together and no two titles overlap.
 *
 * A tap that slides (A, C, D's near tabs) tells the link where it is headed, so the pane goes straight there.
 *
 * Under reduced motion nothing moves: pages change in place (a swipe by its position, a tap in one
 * [MotionTokens.ReducedFadeDuration] fade straight to its tab), the bar jumps to the target tab, and a swipe still
 * changes the tab. A to D crossfade; E keeps its fade through.
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
    DisposableEffect(link, state, option) {
        link?.attach(through = option == TabSwipeOption.E, position)
        onDispose { link?.detach() }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect { settled(it) }
    }
    // A fade through runs in the pager's own scope: the host hears the snap and may change `selectedPage` mid-fade,
    // which must not cut the fade short.
    val moveTo: (Int) -> Unit = { index ->
        val fadeThrough = !reduced && when (option) {
            TabSwipeOption.B, TabSwipeOption.E -> true
            TabSwipeOption.D -> abs(index - state.currentPage) >= FarTabs
            else -> false
        }
        val travel = when (option) {
            TabSwipeOption.B -> width / TravelShare
            TabSwipeOption.E -> width * FollowShare / 2f
            else -> 0f
        }
        jumping?.cancel()
        jumping = scope.launch {
            when {
                reduced -> jump.crossfade(state, link, index)
                fadeThrough -> jump.run(state, link, index, travel)
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
    val edge = MaterialTheme.colorScheme.outlineVariant
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
                    alpha = if (option == TabSwipeOption.E) (1f - 2f * abs(away)).coerceAtLeast(0f) else 1f - abs(away)
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
                // The covering page's leading edge: the scrim alone does not show on a dark background.
                if (away > 0f && away < 1f) drawRect(edge, size = Size(EdgeWidth.toPx(), size.height))
            }
        else -> Modifier.graphicsLayer {
            when {
                jump.active -> {
                    val shown = page == jump.shown
                    alpha = if (shown) jump.alpha else 0f
                    translationX = if (shown) jump.shift else 0f
                }
                option == TabSwipeOption.E -> {
                    // A page a whole page away stays off screen at alpha 0; the two in reach sit near their place.
                    val away = page - positionOf()
                    alpha = (1f - 2f * abs(away)).coerceAtLeast(0f)
                    if (abs(away) < 1f) translationX = -away * size.width * (1f - FollowShare)
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().then(place)) { content(page) }
}

/** A tap this many tabs away, or more, fades through under option D. */
private const val FarTabs = 2

/** Option B's travel is the pager's width over this: the Games list-to-page push's share. */
private const val TravelShare = 12f

/**
 * Option E: the share of a swipe's travel a page follows the finger by. Half of it is on show before the page is
 * gone, which makes a swipe's travel the same as a tap's ([TravelShare]).
 */
private const val FollowShare = 1f / 6f

/** Option C: the width of the covering page's leading edge. */
private val EdgeWidth = 1.dp
