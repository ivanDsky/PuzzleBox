package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * Home's DI-bound entry point: observes the Zip compete loop's captured days
 * (ZipDailyRepository — the puzzle_dailies store Play and History write, tickets 12-14;
 * cache-only, never the network — A15/A8). The legacy DailyPuzzleRepository table is not
 * fed by the Phase-1b loop and stayed empty forever (controller device walk, 2026-11-01).
 * The featured game's identity comes from :app's aggregated
 * `single<List<GamePlugin<*, *, *>>>` binding, so Home holds no game-id constant — a new
 * game appears here purely by registering its plugin. The pane layout is decided once at
 * the app shell (width class + orientation) and passed in; UI below this point stays
 * repository-free.
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
    val koin = getKoin()
    val plugins = remember { koin.getAll<GamePlugin<*, *, *>>() }
    val history by repository.observeHistory().collectAsState(initial = emptyList())

    // The Today card teases the featured game: the first registered plugin. Every
    // current game is daily; cadence-driven selection lands with a second cadence.
    // Empty aggregation is a wiring bug (DiGraphTest enforces ≥1); the constant is the
    // pre-registry fallback so routes that register no plugins still compose.
    val featured = remember(plugins) {
        plugins.firstOrNull()?.let {
            GameSummary(id = it.definition.id, displayName = it.definition.displayName)
        } ?: GameSummary(id = GameId("zip"), displayName = "Zip")
    }

    val days = remember(history, featured) {
        history.map { entry ->
            HistoryDay(
                isoDay = entry.day.toString(),
                headline = formatDay(entry.day.toString()),
                gameName = featured.displayName,
            )
        }
    }

    HomeScreen(
        layout = layout,
        game = featured,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenHistory = onOpenHistory,
        history = days,
        onPlayToday = onPlayToday,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}
