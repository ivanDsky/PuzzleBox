package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.history.HistoryLayout

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]; every tap is reported through [actions].
 * On [HistoryLayout.TwoPane] the hub is [RanksTwoPane] around the same stack, with no title menu, week strip or dots.
 * A day or window pick keeps the held board on screen until the next one answers ([rememberHeldBoard]); a held board takes no tap.
 * The whole hub sits in the shown board's world ([RanksWorld]), which blends to the next board's as the hub moves to it.
 */
@Composable
internal fun RanksScreen(
    state: RanksUiState,
    actions: RanksActions,
    modifier: Modifier = Modifier,
    layout: HistoryLayout = HistoryLayout.SinglePane,
    playerSheet: PlayerSheetViewModel.State? = null,
    replayOptIn: Boolean = false,
    refreshing: Boolean = false,
) {
    val shown = rememberHeldBoard(state)
    val onRowClick = actions.onOpenPlayer?.takeIf { shown.board === state.board }?.let { open -> { row: DailyBoardRowModel -> open(row.toTarget(state)) } }
    val scroll = rememberScrollState()
    val leftScroll = rememberScrollState()
    val twoPane = layout == HistoryLayout.TwoPane
    ReselectToTop(if (twoPane) listOf(leftScroll, scroll) else listOf(scroll), actions.onReset)
    val scrub = rememberBoardScrub(shown.position)
    val brands = remember(shown.places) { shown.places.map { it.brand } }
    RanksWorld(brands, scrub, wide = twoPane, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (actions.onBack != null) {
                IconButton(actions.onBack, Modifier.testTag("ranks.back")) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
            }
            Box(Modifier.weight(1f)) {
                if (twoPane) {
                    RanksTwoPane(shown, actions, scrub, leftScroll, scroll, onRowClick, refreshing)
                } else {
                    RanksPhone(shown, actions, scrub, scroll, onRowClick, refreshing)
                }
            }
        }
    }
    playerSheet?.let {
        PlayerSheet(
            it,
            onChallenge = actions.onChallenge,
            onDismiss = actions.onClosePlayer,
            onWatch = {
                actions.onClosePlayer()
                actions.onWatchPlayer(it.target)
            },
        )
    }
    if (replayOptIn) ReplayOptInSheet(onShare = actions.onShareReplays, onKeepPrivate = actions.onKeepReplaysPrivate)
}

/** Reselecting the tab: the first tap brings every scrolled pane in [scrolls] to its top; with all there already, [onReset]. */
@Composable
private fun ReselectToTop(scrolls: List<ScrollState>, onReset: () -> Unit) {
    OnTabReselect {
        val scrolled = scrolls.filter { it.value > 0 }
        if (scrolled.isEmpty()) onReset() else scrolled.forEach { it.animateScrollTo(0) }
    }
}

@Composable
private fun RanksPhone(
    state: RanksUiState,
    actions: RanksActions,
    scrub: BoardScrub,
    scroll: ScrollState,
    onRowClick: ((DailyBoardRowModel) -> Unit)?,
    refreshing: Boolean,
) {
    var tuning by rememberSaveable { mutableStateOf(false) }
    var howRanksWork by rememberSaveable { mutableStateOf(false) }
    val current = state.places.getOrNull(state.position)
    val nudge = rememberSwipeNudge(show = state.nudge && !tuning && !howRanksWork, onShown = actions.onNudgeShown)
    RanksPullToRefresh(refreshing, actions.onRefresh) {
        Box(Modifier.fillMaxSize()) {
            FillRemainingColumn(Modifier.fillMaxSize().testTag("route.ranks").verticalScroll(scroll)) {
                Column(Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg)) {
                    RanksTopBar(tuneLabel(state.scope, state.window), onTune = { tuning = true }) {
                        RanksTitleMenu(current?.name ?: "Ranks", state.places, current?.board, actions.onPickBoard, onHowRanksWork = { howRanksWork = true }, scrub = scrub)
                    }
                    RanksCalendar(state.calendar, state.stats, actions.onShowCalendar, actions.onSelectDay)
                }
                // The swipe area: everything under the calendar, and whatever is left of the screen below the board.
                RanksBoardStack(
                    state,
                    scrub,
                    actions,
                    onRowClick,
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                    dotsPulse = { dotPulseScale(nudge.pulse.value) },
                )
            }
            SwipeNudgePill(nudgeText(state.places, state.position), nudge, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.lg))
        }
    }
    if (tuning) TuneSheet(state.scope, state.window, actions.onScope, actions.onWindow, onDismiss = { tuning = false })
    if (howRanksWork) HowRanksWorkSheet(onDismiss = { howRanksWork = false })
}

/** The row as the sheet's target: the board's game (none on Overall) and, on a Day window only, its day (a week or month has no single puzzle). */
private fun DailyBoardRowModel.toTarget(state: RanksUiState): PlayerSheetViewModel.Target {
    val game = (state.places.getOrNull(state.position)?.board as? RanksBoard.Game)?.id
    return PlayerSheetViewModel.Target(
        uid = playerId,
        name = name,
        place = if (beyondPage) Standing.Beyond else Standing.Place(rank),
        game = game,
        day = state.calendar.selectedDay.takeIf { game != null && state.window == RanksSelection.Window.Day },
    )
}
