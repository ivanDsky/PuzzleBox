package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import java.time.LocalDate

/**
 * The calendar's grid as a pager of months (backlog #1668): it follows the finger and snaps to the previous or next month on
 * release (a fling counts, a short drag settles back), and at the first and last month it resists and springs back instead
 * of showing a month past the archive. Each page is a whole month ([ArchiveMonthPage]) with its own days, so no frame is
 * empty or a different height, and [state] slides it the same way when ‹ ›, a jump or "Today" asks for a month. The first
 * showing fades in with the page ([EnterFade]); a selected ring overhangs its disc by [SelectedRingRoom], which the layers
 * below the fade would clip, so the pager holds that room on every side ([bleedLayout]) without changing its footprint.
 */
@Composable
internal fun ArchiveMonthPager(
    style: ArchiveMonthStyle,
    state: ArchiveMonthPagerState,
    onPick: (LocalDate) -> Unit,
) {
    EnterFade(0, style.reduced, bleed = SelectedRingRoom) {
        HorizontalPager(
            state = state.pager,
            modifier = Modifier.testTag("library.calendar.grid").bleedLayout(SelectedRingRoom),
            verticalAlignment = Alignment.Top,
            flingBehavior = PagerDefaults.flingBehavior(state.pager, snapAnimationSpec = ArchivePageSpring),
            key = { it },
        ) { page ->
            ArchiveMonthPage(style, state.monthAt(page), state.daysAt(page).orEmpty(), onPick)
        }
    }
}
