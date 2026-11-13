package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]. The calendar (a week strip that
 * expands to the month, [onShowCalendar] / [onSelectDay]) leads, then the viewer's solve of the day ([onPlayAgain],
 * [onOpenReplay], [onCompare]), then the board under the page dots; a horizontal swipe on the board reports [onSwipe]
 * (+1 next board, -1 previous) and a vertical drag scrolls the hub. The top bar and sheets join as their own slot composables.
 */
@Composable
internal fun RanksScreen(
    state: RanksUiState,
    onSignIn: () -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit = { _, _ -> },
    onSelectDay: (LocalDate?) -> Unit = {},
    onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> },
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit = { _, _ -> },
    onCompare: (HistoryDayRow) -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .testTag("route.ranks")
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        RanksCalendar(state.calendar, state.stats, onShowCalendar, onSelectDay)
        YourSolveRow(state.yourSolve, onPlayAgain, onOpenReplay, onCompare)
        PageDots(state.boardCount, state.position, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"))
        BoardSwipe(onSwipe) { RanksBoardSlot(state.board, state.signIn, onSignIn) }
    }
}
