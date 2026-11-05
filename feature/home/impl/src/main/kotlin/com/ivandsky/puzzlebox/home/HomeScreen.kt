package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode

/**
 * One puzzle day in the recent list: [isoDay] is its unique key, [games] every game
 * played that day in registry order.
 */
internal data class HistoryDay(val isoDay: String, val headline: String, val games: List<GameResult>) {
    /** One game's result chip that day; [time] is the solve time, null when opened but not solved. */
    data class GameResult(val gameName: String, val brand: GameBrand, val time: String?)
}

/**
 * The real Home screen (A10–A12, A15): Today tiles and the one CTA, the streak card, the
 * recent days, the account card. Single-pane on compact widths; list-detail on expanded.
 * The theme choice is hoisted to the composition root — this screen only edits it.
 * Destinations are hoisted to the app shell (ticket 17): [onOpenGame] opens a game (a
 * tile, or the CTA's [HomeUiState.playTarget]; the play restores its save), plus the full
 * history and the account surface. Game titles render from [state]'s rows, never from a
 * hardcoded id; the header's date is the puzzle roll-over day (A9), never the phone's.
 */
@Composable
internal fun HomeScreen(
    layout: HomeLayout,
    state: HomeUiState,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onOpenGame: (GameId) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
) {
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier.fillMaxSize()) {
        if (layout == HomeLayout.TwoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(
                    state = state,
                    onOpenTheme = { showThemeDialog = true },
                    onOpenHistory = onOpenHistory,
                    selectedDay = selectedDay,
                    onDayClick = { selectedDay = it },
                    selectable = true,
                    onOpenGame = onOpenGame,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                )
                DetailPane(
                    selected = selectedDay?.let { iso -> state.history.firstOrNull { it.isoDay == iso } },
                    state = state,
                    onOpenGame = onOpenGame,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                )
            }
        } else {
            ListPane(
                state = state,
                onOpenTheme = { showThemeDialog = true },
                onOpenHistory = onOpenHistory,
                selectedDay = null,
                onDayClick = {},
                selectable = false,
                onOpenGame = onOpenGame,
                onOpenProfile = onOpenProfile,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (showThemeDialog) {
        ThemeDialog(
            current = themeMode,
            onSelect = onSetThemeMode,
            onDismiss = { showThemeDialog = false },
        )
    }
}
