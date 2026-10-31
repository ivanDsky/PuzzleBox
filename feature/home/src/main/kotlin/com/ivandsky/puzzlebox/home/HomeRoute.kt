package com.ivandsky.puzzlebox.home

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import org.koin.compose.koinInject

// The game id the home teaser lists. Phase 1 owns the real gameId taxonomy; this stays
// private until then.
private const val ZIP_GAME_ID = "zip"

/**
 * Home's DI-bound entry point: observes the cached daily history (Room, never the
 * network — A15 solo browsing is offline-first) and derives the pane layout from the
 * window width. UI below this point stays repository-free.
 */
@Composable
fun HomeRoute(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<DailyPuzzleRepository>()
    val history by repository.observeHistory(ZIP_GAME_ID).collectAsState(initial = emptyList())

    val days = remember(history) {
        history.map { puzzle -> HistoryDay(isoDay = puzzle.day, headline = formatDay(puzzle.day)) }
    }

    HomeScreen(
        layout = homeLayoutFor(windowWidthSizeClass),
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        history = days,
        modifier = modifier,
    )
}
