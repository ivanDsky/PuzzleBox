package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The one progress a tab change fades through (ticket 142): the [UnderlineTabPager]'s list pages and anything that
 * changes with them (the tablet's right pane, Community's filter slot) read it, so they are never out of step.
 *
 * Every tab has a place on a line of pages, and the change moves a viewport along it: under a finger the viewport is the
 * pager's own position; on a tap it runs from the tab it leaves toward the one it goes to, never over the tabs between. A
 * page is [shown] fully with the viewport on it and not at all half a page away, so the page leaving is gone at half way
 * and the one coming in has not begun, and no frame shows two tabs' content.
 *
 * The pager attaches itself; a pane that follows it passes the same link to [TabFadePane], and the pager and a host
 * share one through [LocalTabFadeLink]. Until a pager attaches the viewport rests on the host's [home].
 */
@Stable
class TabFadeLink(home: Int = 0) {
    /** The tab the viewport rests on while no pager is attached (before the first frame, or a host with none): the host's. */
    var home: Int by mutableIntStateOf(home)

    internal var pager: PagerState? by mutableStateOf(null)
    internal var tap: TapFade? by mutableStateOf(null)

    /** How far a tap has run, 0 to 1: the page it leaves is fading out up to a half, the page it goes to in after it. */
    internal var share: Float by mutableFloatStateOf(0f)

    internal val tapActive: Boolean get() = tap != null

    /** The viewport's place on the line of pages. */
    internal fun at(): Float {
        val tap = tap
        if (tap != null) return tap.from + tap.toward * share
        val pager = pager ?: return home.toFloat()
        return pager.currentPage + pager.currentPageOffsetFraction
    }

    /** How many pages [page]'s own place is from the viewport; null for a page the tap neither leaves nor goes to. */
    internal fun away(page: Int): Float? {
        val tap = tap ?: return page - at()
        val place = when (page) {
            tap.from -> tap.from
            tap.to -> tap.from + tap.toward
            else -> return null
        }
        return place - at()
    }

    /** How much of [page] shows, 0 to 1: all of it under the viewport, none of it half a page away. */
    fun shown(page: Int): Float {
        val away = away(page) ?: return 0f
        return (1f - 2f * abs(away)).coerceAtLeast(0f)
    }

    /** Whether [page] is one a change is on or between: the only pages that are composed. */
    internal fun isLive(page: Int): Boolean {
        val tap = tap
        if (tap != null) return page == if (share < TAP_HALF) tap.from else tap.to
        val at = at()
        return page == floor(at).toInt() || page == ceil(at).toInt()
    }

    /** The pages that are [isLive], in order. */
    internal fun live(): List<Int> {
        val tap = tap
        if (tap != null) return listOf(if (share < TAP_HALF) tap.from else tap.to)
        val at = at()
        val last = ((pager?.pageCount ?: (home + 1)) - 1).coerceAtLeast(0)
        val low = floor(at).toInt().coerceIn(0, last)
        val high = ceil(at).toInt().coerceIn(0, last)
        return if (low == high) listOf(low) else listOf(low, high)
    }

    /** Whether a tap is running or a finger or a settle is moving the pager: a page that composes now is fading in. */
    internal val changing: Boolean
        get() = Snapshot.withoutReadObservation { tap != null || pager?.isScrollInProgress == true }

    internal companion object {
        /** A tap's share where the page it leaves is gone and the page it goes to has yet to show. */
        const val TAP_HALF = 0.5f
    }
}

/**
 * The [TabFadeLink] a screen shares with the pager it hosts: a pager reads it when it is not handed one, so a host that
 * owns the right pane or the head gives the list below it the progress it follows without passing it down.
 */
val LocalTabFadeLink = staticCompositionLocalOf<TabFadeLink?> { null }

/**
 * True for the content of a page that began to compose while its tab was fading in, which is its entrance: rows and
 * panes start settled rather than run a stagger behind the fade. False on the first open of a screen.
 */
val LocalTabFadingIn = staticCompositionLocalOf { false }
