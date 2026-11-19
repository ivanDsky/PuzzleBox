package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance

/**
 * The [UnderlineTabs] strip over a swipeable page per tab: the app's one tab-and-pager pair
 * (Community, Games). [content] draws page `index`; the caller owns each page's scroll state
 * (keyed by the tab) because only the page a change is on, and the one it is heading for, are composed.
 *
 * A tab changes with one fade through, on the [link]'s progress ([TabFadeLink]). Under a finger the page follows it a
 * sixth of the way ([MotionTokens.TabFollowShare]) fading out, and is gone at half a page; the next one fades in over
 * the second half from a twelfth of the width to the side it comes from. A tap runs the same picture on a clock
 * ([TabTapRunner]) from the tab it leaves straight to the one it goes to: a tab in between is never composed or drawn.
 * A release settles over [MotionTokens.DurationLong] on [MotionTokens.EasingEmphasizedDecelerate], and the bar
 * follows the finger, or glides to a tapped tab over [MotionTokens.DurationMedium]. A tap on the selected tab only
 * calls [onReselect] with its index (callers scroll to top). The selected page is saved across recreation;
 * [initialPage] is the first one. A host that owns the tab (Games: a request for a tab, back from a game's page) hears
 * each page the pager settles on through [onPageSettled] (not the first one) and passes [selectedPage]: when it changes
 * to a page the pager is not on, the pager moves there the way a tap does. Null leaves the pager its own owner. A finger
 * that takes the pager during a tap ends the tap, and the drag goes on from the page that shows.
 *
 * The shifted page is clipped at the pager's edges, which are the column's own; [clipBleed] widens them on each side
 * (the page keeps its place) for a column with a gutter the page may shift into. Under reduced motion nothing shifts: a
 * swipe is the same fade through in place, a tap fades over [MotionTokens.ReducedFadeDuration] (at once at animator
 * scale 0), the bar jumps to the tab the page is heading for, and a swipe still changes the tab. Content that composes
 * as its tab fades in starts settled ([LocalTabFadingIn]). The pager fills the height left under the strip.
 */
@Composable
fun UnderlineTabPager(
    tabs: List<UnderlineTab>,
    onReselect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    selectedPage: Int? = null,
    onPageSettled: (Int) -> Unit = {},
    clipBleed: Dp = 0.dp,
    link: TabFadeLink = LocalTabFadeLink.current ?: remember { TabFadeLink(initialPage) },
    content: @Composable (page: Int) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val state = rememberPagerState(initialPage = initialPage.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))) { tabs.size }
    val scope = rememberCoroutineScope()
    val runner = remember(state, link) { TabTapRunner(link, state, scope) }
    val reselect by rememberUpdatedState(onReselect)
    val settled by rememberUpdatedState(onPageSettled)
    // The page last reported, so a host that echoes it back as [selectedPage] is not asked to move there.
    var reported by remember { mutableIntStateOf(state.settledPage) }
    val settle = RestingSettle(
        if (reduced) {
            tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear)
        } else {
            tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate)
        },
    )
    DisposableEffect(state, link) {
        link.pager = state
        onDispose { if (link.pager === state) link.pager = null }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.drop(1).collect {
            reported = it
            settled(it)
        }
    }
    // A finger that takes the pager during a tap ends the tap where it is: the drag goes on from the page that shows.
    LaunchedEffect(state, runner) {
        state.interactionSource.interactions.filterIsInstance<DragInteraction.Start>().collect { runner.interrupt() }
    }
    LaunchedEffect(selectedPage) {
        if (selectedPage != null && selectedPage in tabs.indices && selectedPage != reported) runner.go(selectedPage, reduced)
    }
    Column(modifier) {
        UnderlineTabsAt(
            tabs = tabs,
            position = {
                when {
                    reduced -> (link.tap?.to ?: state.targetPage).toFloat()
                    link.tapActive || runner.bar.isRunning -> runner.bar.value
                    else -> state.currentPage + state.currentPageOffsetFraction
                }
            },
            onSelect = { index ->
                if (!link.tapActive && index == state.currentPage) reselect(index) else runner.go(index, reduced)
            },
        )
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxWidth().weight(1f).bleedSides(clipBleed).testTag("underline.pager"),
            verticalAlignment = Alignment.Top,
            beyondViewportPageCount = 0,
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                pagerSnapDistance = PagerSnapDistance.atMost(1),
                snapAnimationSpec = settle,
            ),
        ) { page ->
            FadingPage(page, link, state, reduced, clipBleed) { content(page) }
        }
    }
}

/**
 * Page [page] of the pager, drawn where the [link] puts it. The pager lays its pages out side by side, so each is moved
 * back over the viewport and given its own fade and shift; its content composes only while the page is one a change is on
 * or between, so a neighbour the pager kept from a swipe that went back is not in the tree.
 */
@Composable
private fun FadingPage(page: Int, link: TabFadeLink, state: PagerState, reduced: Boolean, bleed: Dp, content: @Composable () -> Unit) {
    val live = remember(link, page) { derivedStateOf { link.isLive(page) } }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val shift = if (reduced) 0f else (link.away(page) ?: 0f) * (size.width - 2 * bleed.toPx()) * MotionTokens.TabFollowShare
                translationX = (state.currentPage + state.currentPageOffsetFraction - page) * size.width + shift
                alpha = link.shown(page)
            }
            .padding(horizontal = bleed),
    ) {
        if (live.value) {
            val fadingIn = remember { link.changing }
            CompositionLocalProvider(LocalTabFadingIn provides fadingIn) { content() }
        }
    }
}
