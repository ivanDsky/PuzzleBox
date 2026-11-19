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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.RanksTuneDefaults
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel

/**
 * The Ranks hub as a stack of slots, each reading only its own slice of [state]; every tap is reported through [actions].
 * On [HistoryLayout.TwoPane] the hub is [RanksTwoPane] around the same stack, with no title menu, week strip or dots.
 * A day or window pick keeps the held board on screen until the next one answers ([rememberHeldBoard]); a held board's
 * rows open against the day and window it was picked from, not the new pick.
 * [tuneDefaults] is handed to the Tune sheet; null leaves the tune icon out, as there is nothing else in the sheet.
 * With [RanksUiState.largeBar] the tab's hub opens on Today's large top bar ([RanksLargeBar]).
 * The Tune and How ranks work sheets are owned here, so [RanksActions.onSheetOpen] hears whether any Ranks sheet (those two, a filter chip's or the [playerSheet]) is up.
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
    tuneDefaults: RanksTuneDefaults?,
) {
    val shown = rememberHeldBoard(state)
    var tuning by rememberSaveable { mutableStateOf(false) }
    var howRanksWork by rememberSaveable { mutableStateOf(false) }
    // The filter chips' sheets are counted, not flagged: Who and When can each be up on their own.
    var chipSheetsOpen by remember { mutableIntStateOf(0) }
    val onFilterOpenChange = { open: Boolean -> chipSheetsOpen += if (open) 1 else -1 }
    val sheetOpen = tuning || howRanksWork || chipSheetsOpen > 0
    // With no Defaults block, Tune would be an empty sheet: the chips are all of the tuning.
    val onTune = { tuning = true }.takeUnless { tuneDefaults == null }
    val anySheetOpen = sheetOpen || playerSheet != null
    LaunchedEffect(anySheetOpen) { actions.onSheetOpen(anySheetOpen) }
    val source = remember(shown.board) { state }
    val onRowClick = actions.onOpenPlayer?.let { open -> { row: DailyBoardRowModel -> open(row.toTarget(if (shown.board === state.board) state else source)) } }
    val scroll = rememberScrollState()
    val leftScroll = rememberScrollState()
    val twoPane = layout == HistoryLayout.TwoPane
    ReselectToTop(if (twoPane) listOf(leftScroll, scroll) else listOf(scroll), actions.onReset)
    val scrub = rememberBoardScrub(shown.position)
    val brands = remember(shown.places) { shown.places.map { it.brand } }
    // The tab's own hub only: the hub pushed from a game page keeps its back row and its compact bar.
    val largeBar = state.largeBar && actions.onBack == null
    RanksWorld(brands, scrub, wide = twoPane, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (actions.onBack != null) {
                IconButton(actions.onBack, Modifier.testTag("ranks.back")) { Icon(PuzzleIcons.Back, contentDescription = "Back") }
            }
            Box(Modifier.weight(1f)) {
                if (twoPane) {
                    RanksTwoPane(shown, actions, scrub, leftScroll, scroll, onRowClick, refreshing, onTune, onFilterOpenChange, onHowRanksWork = { howRanksWork = true }, largeBar = largeBar)
                } else {
                    RanksPhone(shown, actions, scrub, scroll, onRowClick, refreshing, sheetOpen, onTune, onFilterOpenChange, onHowRanksWork = { howRanksWork = true }, largeBar = largeBar)
                }
            }
        }
    }
    if (tuning && tuneDefaults != null) TuneSheet(tuneDefaults, onDismiss = { tuning = false })
    if (howRanksWork) HowRanksWorkSheet(onDismiss = { howRanksWork = false })
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
    sheetOpen: Boolean,
    onTune: (() -> Unit)?,
    onFilterOpenChange: (Boolean) -> Unit,
    onHowRanksWork: () -> Unit,
    largeBar: Boolean,
) {
    val current = state.places.getOrNull(state.position)
    val nudge = rememberSwipeNudge(show = state.nudge && !sheetOpen, onShown = actions.onNudgeShown)
    RanksPullToRefresh(refreshing, actions.onRefresh) {
        Box(Modifier.fillMaxSize()) {
            FillRemainingColumn(Modifier.fillMaxSize().testTag("route.ranks").verticalScroll(scroll)) {
                val titleMenu = @Composable { large: Boolean ->
                    RanksTitleMenu(current?.name ?: "Ranks", state.places, current?.board, actions.onPickBoard, onHowRanksWork = onHowRanksWork, scrub = scrub, large = large)
                }
                if (largeBar) RanksLargeBar(state, onTune) { titleMenu(true) }
                Column(Modifier.padding(start = Spacing.lg, top = if (largeBar) 0.dp else Spacing.lg, end = Spacing.lg)) {
                    if (!largeBar) RanksTopBar(onTune = onTune) { titleMenu(false) }
                    RanksFilterChips(state, actions, onFilterOpenChange)
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
