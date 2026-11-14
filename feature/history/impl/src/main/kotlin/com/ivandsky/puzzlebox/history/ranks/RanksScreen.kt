package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
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
 * dots; a horizontal swipe anywhere under the calendar, down to the bottom of the screen however short the board is,
 * reports [onSwipe] (+1 next board, -1 previous) and a vertical drag scrolls the hub or pulls its refresh. The first
 * time the hub opens, a hint and the current dot nudge the swipe, then [onNudgeShown]. On
 * [HistoryLayout.TwoPane] the hub is [RanksTwoPane] instead: no title menu, week strip, dots or swipe. [onBack] (a pushed
 * hub, opened from a game's link) adds a back arrow above either layout. A tab reselect scrolls the hub to the top; one
 * more, with it already there, calls [onReset] (A8). With an [onOpenPlayer] a tap on another player's row reports that
 * player; without one rows take no tap. The screen only hosts the sheet of the [playerSheet] its caller has open
 * ([onChallenge], [onClosePlayer]); its Watch tap closes it and reports [onWatchPlayer]. While [replayOptIn], the first
 * ranked solve's question about sharing replays is up, on either layout ([onShareReplays], [onKeepReplaysPrivate]).
 * A day or window pick keeps the board on screen until the next one answers ([rememberHeldBoard]): only what differs
 * between the two days then changes, each part in place. A held board's rows take no tap, for they are not the picked day's.
 * The whole hub sits in the shown board's world ([RanksWorld]): its accent as a wash and its glyph behind the content,
 * blending to the next board's as the hub moves to it ([BoardScrub]). The same scrub moves the board itself: a swipe
 * (on either layout) or a pick re-ranks the rows in place from the one board to the other ([rememberBoardMorph]).
 */
@Composable
internal fun RanksScreen(
    state: RanksUiState,
    onSignIn: () -> Unit,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    layout: HistoryLayout = HistoryLayout.SinglePane,
    onBack: (() -> Unit)? = null,
    onReset: () -> Unit = {},
    onPickBoard: (RanksBoard) -> Unit = {},
    onNudgeShown: () -> Unit = {},
    onScope: (RankScope) -> Unit = {},
    onWindow: (RanksSelection.Window) -> Unit = {},
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit = { _, _ -> },
    onSelectDay: (LocalDate?) -> Unit = {},
    onPlayAgain: (GameId, LocalDate) -> Unit = { _, _ -> },
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit = { _, _ -> },
    onCompare: (HistoryDayRow) -> Unit = {},
    onWatchPlayer: (PlayerSheetViewModel.Target) -> Unit = {},
    playerSheet: PlayerSheetViewModel.State? = null,
    onOpenPlayer: ((PlayerSheetViewModel.Target) -> Unit)? = null,
    onChallenge: (GameId) -> Unit = {},
    onClosePlayer: () -> Unit = {},
    replayOptIn: Boolean = false,
    onShareReplays: () -> Unit = {},
    onKeepReplaysPrivate: () -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val shown = rememberHeldBoard(state)
    val onRowClick = onOpenPlayer?.takeIf { shown.board === state.board }?.let { open -> { row: DailyBoardRowModel -> open(row.toTarget(state)) } }
    val scroll = rememberScrollState()
    val leftScroll = rememberScrollState()
    val twoPane = layout == HistoryLayout.TwoPane
    ReselectToTop(if (twoPane) listOf(leftScroll, scroll) else listOf(scroll), onReset)
    val scrub = rememberBoardScrub(shown.position)
    val brands = remember(shown.places) { shown.places.map { it.brand } }
    RanksWorld(brands, scrub, wide = twoPane, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (onBack != null) {
                IconButton(onBack, Modifier.testTag("ranks.back")) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
            }
            Box(Modifier.weight(1f)) {
                if (twoPane) {
                    RanksTwoPane(shown, onSignIn, Modifier, onPickBoard, onScope, onWindow, onShowCalendar, onSelectDay, onPlayAgain, onOpenReplay, onCompare, leftScroll, scroll, onRowClick, refreshing, onRefresh, scrub, onSwipe)
                } else {
                    RanksPhone(shown, scrub, scroll, onSignIn, onSwipe, onPickBoard, onNudgeShown, onScope, onWindow, onShowCalendar, onSelectDay, onPlayAgain, onOpenReplay, onCompare, onRowClick, refreshing, onRefresh)
                }
            }
        }
    }
    playerSheet?.let {
        PlayerSheet(
            it,
            onChallenge = onChallenge,
            onDismiss = onClosePlayer,
            onWatch = {
                onClosePlayer()
                onWatchPlayer(it.target)
            },
        )
    }
    if (replayOptIn) ReplayOptInSheet(onShare = onShareReplays, onKeepPrivate = onKeepReplaysPrivate)
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
    scrub: BoardScrub,
    scroll: ScrollState,
    onSignIn: () -> Unit,
    onSwipe: (Int) -> Unit,
    onPickBoard: (RanksBoard) -> Unit,
    onNudgeShown: () -> Unit,
    onScope: (RankScope) -> Unit,
    onWindow: (RanksSelection.Window) -> Unit,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    onPlayAgain: (GameId, LocalDate) -> Unit,
    onOpenReplay: (HistoryDayRow, ReplaySolve) -> Unit,
    onCompare: (HistoryDayRow) -> Unit,
    onRowClick: ((DailyBoardRowModel) -> Unit)?,
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
) {
    var tuning by rememberSaveable { mutableStateOf(false) }
    var howRanksWork by rememberSaveable { mutableStateOf(false) }
    val current = state.places.getOrNull(state.position)
    val nudge = rememberSwipeNudge(show = state.nudge && !tuning && !howRanksWork, onShown = onNudgeShown)
    RanksPullToRefresh(refreshing, onRefresh) {
        Box(Modifier.fillMaxSize()) {
            FillRemainingColumn(Modifier.fillMaxSize().testTag("route.ranks").verticalScroll(scroll)) {
                Column(Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg)) {
                    RanksTopBar(tuneLabel(state.scope, state.window), onTune = { tuning = true }) {
                        RanksTitleMenu(current?.name ?: "Ranks", state.places, current?.board, onPickBoard, onHowRanksWork = { howRanksWork = true }, scrub = scrub)
                    }
                    RanksCalendar(state.calendar, state.stats, onShowCalendar, onSelectDay)
                }
                // The swipe area: everything under the calendar, and whatever is left of the screen below the board.
                BoardSwipe(scrub, state.position, state.boardCount, onSwipe, Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg)) {
                        YourSolveRow(state.yourSolve, onPlayAgain, onOpenReplay, onCompare)
                        PageDots(
                            state.boardCount,
                            state.position,
                            Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"),
                            pulseScale = { dotPulseScale(nudge.pulse.value) },
                            scrub = scrub,
                        )
                        RanksBoardSlot(state.board, state.signIn, onSignIn, onRowClick = onRowClick, morph = rememberBoardMorph(state, scrub))
                    }
                }
            }
            SwipeNudgePill(nudgeText(state.places, state.position), nudge, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.lg))
        }
    }
    if (tuning) TuneSheet(state.scope, state.window, onScope, onWindow, onDismiss = { tuning = false })
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
