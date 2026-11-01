package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import org.koin.compose.koinInject

/**
 * Home's DI-bound entry point: observes the Zip compete loop's captured days
 * (ZipDailyRepository — the puzzle_dailies store Play and History write, tickets 12-14;
 * cache-only, never the network — A15/A8). The legacy DailyPuzzleRepository table is not
 * fed by the Phase-1b loop and stayed empty forever (controller device walk, 2026-11-01).
 * The pane layout is decided once at the app shell (width class + orientation) and passed
 * in; UI below this point stays repository-free.
 */
@Composable
fun HomeRoute(
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    onPlayToday: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
) {
    val repository = koinInject<ZipDailyRepository>()
    val history by repository.observeHistory().collectAsState(initial = emptyList())

    val days = remember(history) {
        history.map { entry ->
            HistoryDay(isoDay = entry.day.toString(), headline = formatDay(entry.day.toString()))
        }
    }

    HomeScreen(
        layout = layout,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenHistory = onOpenHistory,
        history = days,
        onPlayToday = onPlayToday,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}
