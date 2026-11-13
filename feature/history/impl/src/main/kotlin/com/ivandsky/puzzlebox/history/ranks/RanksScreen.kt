package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.ReplaySolve
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]. The top bar's title is the board menu
 * ([onPickBoard]; its "How ranks work" row opens the [HowRanksWorkSheet]) and its tune icon opens the [TuneSheet]
 * ([onScope], [onWindow]). The calendar (a week strip that expands to the month, [onShowCalendar] / [onSelectDay])
 * leads, then the viewer's solve of the day ([onPlayAgain], [onOpenReplay], [onCompare]), then the board under the page
 * dots; a horizontal swipe on the board reports [onSwipe] (+1 next board, -1 previous) and a vertical drag scrolls
 * the hub. The first time the hub opens, a hint and the current dot nudge the swipe, then [onNudgeShown]. On
 * [HistoryLayout.TwoPane] the hub is [RanksTwoPane] instead: no title menu, week strip, dots or swipe.
 */
@Composable
internal fun RanksScreen(
    state: RanksUiState,
    onSignIn: () -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: HistoryLayout = HistoryLayout.SinglePane,
    onPickBoard: (RanksBoard) -> Unit = {},
    onNudgeShown: () -> Unit = {},
    onScope: (RankScope) -> Unit = {},
    onWindow: (RanksSelection.Window) -> Unit = {},
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit = { _, _ -> },
    onSelectDay: (LocalDate?) -> Unit = {},
    onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> },
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit = { _, _ -> },
    onCompare: (HistoryDayRow) -> Unit = {},
) {
    if (layout == HistoryLayout.TwoPane) {
        RanksTwoPane(state, onSignIn, modifier, onPickBoard, onScope, onWindow, onShowCalendar, onSelectDay, onPlayAgain, onOpenReplay, onCompare)
        return
    }
    var tuning by rememberSaveable { mutableStateOf(false) }
    var howRanksWork by rememberSaveable { mutableStateOf(false) }
    val current = state.places.getOrNull(state.position)
    val nudge = rememberSwipeNudge(show = state.nudge && !tuning && !howRanksWork, onShown = onNudgeShown)
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .testTag("route.ranks")
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            RanksTopBar(tuneLabel(state.scope, state.window), onTune = { tuning = true }) {
                RanksTitleMenu(current?.name ?: "Ranks", state.places, current?.board, onPickBoard, onHowRanksWork = { howRanksWork = true })
            }
            RanksCalendar(state.calendar, state.stats, onShowCalendar, onSelectDay)
            YourSolveRow(state.yourSolve, onPlayAgain, onOpenReplay, onCompare)
            PageDots(
                state.boardCount,
                state.position,
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"),
                pulseScale = { dotPulseScale(nudge.pulse.value) },
            )
            BoardSwipe(onSwipe) { RanksBoardSlot(state.board, state.signIn, onSignIn) }
        }
        SwipeNudgePill(nudgeText(state.places, state.position), nudge, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.lg))
    }
    if (tuning) TuneSheet(state.scope, state.window, onScope, onWindow, onDismiss = { tuning = false })
    if (howRanksWork) HowRanksWorkSheet(onDismiss = { howRanksWork = false })
}
