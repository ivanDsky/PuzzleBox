package com.ivandsky.puzzlebox.play

import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** A swipe's release and a programmatic slide settle on the same spring, so ‹ › reads as a finished swipe. */
internal val ArchivePageSpring = spring<Float>(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault)

/**
 * The months the pager can draw: the loaded [month] with its [days], and the ones loaded beside it ([around]). Equal by
 * value, so a recomposition that loads nothing new does not invalidate what reads it.
 */
internal data class ArchiveMonthSource(
    val month: YearMonth,
    val days: List<ArchiveDay>,
    val around: Map<YearMonth, List<ArchiveDay>>,
) {
    /** [of]'s archive days, or null while it is not loaded: such a page draws empty, never another month's marks. */
    fun daysOf(of: YearMonth): List<ArchiveDay>? = if (of == month) days else around[of]
}

/** A far jump's leaving grid, parked on the page next to the target so the months in between are never drawn. */
private class Detour(val page: Int, val month: YearMonth)

/**
 * The swiping calendar's pager, one page per month of the archive ([nav]) from the first month (page 0) to the last. A
 * swipe settling on another month asks for it with `onMonth`; a month asked for elsewhere ([follow]) slides there.
 */
@Stable
internal class ArchiveMonthPagerState(
    private val nav: ArchiveMonthNav,
    val pager: PagerState,
    private val source: State<ArchiveMonthSource>,
    private val reduced: State<Boolean>,
) {
    private var detour by mutableStateOf<Detour?>(null)
    private var dragging = false
    private var dragged = false

    // Months that were loaded: the grid that leaves keeps its marks for the frame after [source] has moved on.
    private val known = HashMap<YearMonth, List<ArchiveDay>>()

    /** [page]'s month: its own, but on a far jump's detour page the grid that is leaving. */
    fun monthAt(page: Int): YearMonth = detour?.takeIf { it.page == page }?.month ?: nav.monthAt(page)

    fun daysAt(page: Int): List<ArchiveDay>? = monthAt(page).let { source.value.daysOf(it) ?: known[it] }

    /** Remembers what [now] holds; called once the composition that drew it is applied. */
    fun keep(now: ArchiveMonthSource) {
        known.putAll(now.around)
        known[now.month] = now.days
    }

    /** The month nearest the middle of the view: the title and numbers change over to it halfway through a swipe. */
    val month: YearMonth get() = monthAt(pager.currentPage)

    val days: List<ArchiveDay> get() = daysAt(pager.currentPage).orEmpty()

    /**
     * Keeps [requested] and the pager in step until cancelled. A swipe that lands on another month reports it through
     * [onMonth]; a [requested] month whose days are loaded slides in, one at a time, so a second ask never cuts the
     * first one's travel short mid-grid.
     */
    suspend fun follow(requested: State<YearMonth>, onMonth: State<(YearMonth) -> Unit>) = coroutineScope {
        launch {
            pager.interactionSource.interactions.collect {
                when (it) {
                    is DragInteraction.Start -> {
                        dragging = true
                        dragged = true
                    }
                    is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
                }
            }
        }
        launch {
            // Only a drag reports: a slide that lands is the answer to an ask, and a newer ask may be waiting behind it.
            snapshotFlow { pager.settledPage }.collect { page ->
                if (dragged) {
                    dragged = false
                    nav.monthAt(page).takeIf { it != requested.value }?.let(onMonth.value)
                }
            }
        }
        launch {
            snapshotFlow { source.value.daysOf(requested.value)?.let { nav.indexOf(requested.value) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { page ->
                    try {
                        slideTo(page)
                    } catch (_: CancellationException) {
                        // A cancelled effect ends here; a slide the finger interrupted is the swipe's to report.
                        currentCoroutineContext().ensureActive()
                    }
                }
        }
    }

    private suspend fun slideTo(page: Int) {
        if (dragging) return
        dragged = false
        val here = pager.currentPage
        when {
            page == here && pager.currentPageOffsetFraction == 0f -> Unit
            reduced.value -> pager.scrollToPage(page)
            abs(page - here) <= 1 -> pager.animateScrollToPage(page, animationSpec = ArchivePageSpring)
            else -> jump(here, page)
        }
    }

    /**
     * A far [target] slides in from the side it lies on like a step does: the grid on screen is parked on the page next to
     * [target] (the same pixels, so nothing moves), then that one page of travel plays. The months between never draw.
     */
    private suspend fun jump(here: Int, target: Int) {
        val near = if (target > here) target - 1 else target + 1
        detour = Detour(near, monthAt(here))
        try {
            pager.scrollToPage(near)
            pager.animateScrollToPage(target, animationSpec = ArchivePageSpring)
        } finally {
            detour = null
        }
    }
}

/** The pager for [nav]'s months, showing [source]'s loaded one first; it follows [requested] and reports swipes to [onMonth]. */
@Composable
internal fun rememberArchiveMonthPager(
    nav: ArchiveMonthNav,
    source: ArchiveMonthSource,
    requested: YearMonth,
    reduced: Boolean,
    onMonth: (YearMonth) -> Unit,
): ArchiveMonthPagerState {
    val pager = rememberPagerState(initialPage = nav.indexOf(source.month)) { nav.monthCount }
    val latestSource = rememberUpdatedState(source)
    val latestReduced = rememberUpdatedState(reduced)
    val latestRequested = rememberUpdatedState(requested)
    val latestOnMonth = rememberUpdatedState(onMonth)
    val state = remember(nav, pager) { ArchiveMonthPagerState(nav, pager, latestSource, latestReduced) }
    SideEffect { state.keep(source) }
    LaunchedEffect(state) { state.follow(latestRequested, latestOnMonth) }
    return state
}
