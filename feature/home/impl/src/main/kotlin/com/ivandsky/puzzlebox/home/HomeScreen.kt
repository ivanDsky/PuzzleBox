package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.model.GameId

/**
 * The real Home screen (A10–A12, A15): Today tiles and the one CTA, the streak card, the
 * recent days. Single-pane on compact widths; list-detail on expanded,
 * where the streak moves out of the list into the streak & stats pane.
 * Destinations are hoisted to the app shell (ticket 17): [onOpenGame] opens a game (a
 * tile, or the CTA's [HomeUiState.playTarget]; the play restores its save). [onSelectDay]
 * picks a recent day for the detail pane ([HomeUiState.selectedDay], two-pane only); a null
 * pick clears it. Tapping the picked day again restores the streak pane. Game titles
 * render from [state]'s rows, never from a hardcoded id; the header's date is the puzzle
 * roll-over day (A9), never the phone's.
 * A non-null [HomeUiState.updateUrl] shows the Update available pill in the header (spec A7);
 * its tap calls [onOpenUpdate] with that URL. The picked day's Finish and Play call [onOpenGameDay] with the
 * game and the ISO day; its signed-out link calls [onSignIn]. [summaries] adds the rank and duels rows.
 */
@Composable
internal fun HomeScreen(
    layout: HomeLayout,
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    onSelectDay: (String?) -> Unit,
    summaries: HomeSummaries,
    title: String,
    modifier: Modifier = Modifier,
    onOpenUpdate: (String) -> Unit = {},
    onOpenGameDay: (GameId, String) -> Unit = { _, _ -> },
    onSignIn: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        if (layout == HomeLayout.TwoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(
                    state = state,
                    selectedDay = state.selectedDay,
                    onDayClick = { day -> onSelectDay(day.takeUnless { it == state.selectedDay }) },
                    selectable = true,
                    showStreak = false,
                    pillBelowHeader = false,
                    onOpenGame = onOpenGame,
                    summaries = summaries,
                    title = title,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    onOpenUpdate = onOpenUpdate,
                )
                DetailPane(
                    selected = state.selectedDay?.let { iso -> state.history.firstOrNull { it.isoDay == iso } },
                    state = state,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                    onOpenStreak = summaries.onOpenStreak,
                    onOpenDay = summaries.onOpenDay,
                    onOpenGameDay = onOpenGameDay,
                    onSignIn = onSignIn,
                )
            }
        } else {
            ListPane(
                state = state,
                selectedDay = null,
                onDayClick = {},
                selectable = false,
                showStreak = true,
                pillBelowHeader = true,
                onOpenGame = onOpenGame,
                summaries = summaries,
                title = title,
                modifier = Modifier.fillMaxSize(),
                onOpenUpdate = onOpenUpdate,
            )
        }
    }
}
