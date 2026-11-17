package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The [UnderlineTabs] strip over a swipeable page per tab: the app's one tab-and-pager pair
 * (Community, Games). [content] draws page `index`; the caller owns each page's scroll state
 * (keyed by the tab) because a page that is not the current one or its neighbour is not composed.
 *
 * The bar follows the pager's fractional position while the finger drags ([UnderlineTabsAt]). A tap
 * on another tab animates to its page over [MotionTokens.DurationLong] on
 * [MotionTokens.EasingEmphasizedDecelerate], and a release settles the same way. A tap on the
 * selected tab only calls [onReselect] with its index (callers scroll to top). The selected page is
 * saved across recreation; [initialPage] is the first one. A host that owns the tab (Games: a request for
 * a tab, back from a game's page) hears each page the pager settles on through [onPageSettled] (not the
 * first one) and passes [selectedPage]: when it changes to a page the pager is not on, the pager moves
 * there the way a tap does. Null leaves the pager its own owner.
 *
 * Under reduced motion nothing slides: pages stay in place and cross-fade over
 * [CrossfadeDuration], the bar jumps to the tab the page is heading for, and a swipe still changes
 * the tab. The pager fills the height left under the strip.
 */
@Composable
fun UnderlineTabPager(
    tabs: List<UnderlineTab>,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    selectedPage: Int? = null,
    onPageSettled: (Int) -> Unit = {},
    content: @Composable (page: Int) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val state = rememberPagerState(initialPage = initialPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) { tabs.size }
    val scope = rememberCoroutineScope()
    val reselect by rememberUpdatedState(onReselect)
    val settled by rememberUpdatedState(onPageSettled)
    val settle = if (reduced) {
        tween<Float>(CrossfadeDuration, easing = MotionTokens.EasingLinear)
    } else {
        tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect { settled(it) }
    }
    LaunchedEffect(selectedPage) {
        if (selectedPage != null && selectedPage in tabs.indices && selectedPage != state.currentPage) {
            state.animateScrollToPage(selectedPage, animationSpec = settle)
        }
    }
    Column(modifier) {
        UnderlineTabsAt(
            tabs = tabs,
            position = {
                if (reduced) state.targetPage.toFloat() else state.currentPage + state.currentPageOffsetFraction
            },
            onSelect = { index ->
                if (index == state.currentPage) reselect(index) else scope.launch { state.animateScrollToPage(index, animationSpec = settle) }
            },
        )
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("underline.pager"),
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = settle,
            ),
        ) { page ->
            Page(page, reduced, positionOf = { state.currentPage + state.currentPageOffsetFraction }, content)
        }
    }
}

/** Fades under reduced motion; otherwise the pager's own slide, untouched. */
@Composable
private fun Page(page: Int, reduced: Boolean, positionOf: () -> Float, content: @Composable (Int) -> Unit) {
    val place = if (reduced) {
        Modifier.graphicsLayer {
            val away = page - positionOf()
            translationX = -away * size.width
            alpha = 1f - abs(away).coerceIn(0f, 1f)
        }
    } else {
        Modifier
    }
    Box(Modifier.fillMaxSize().then(place)) { content(page) }
}

/** How long a page change cross-fades under reduced motion. */
internal const val CrossfadeDuration = 110
