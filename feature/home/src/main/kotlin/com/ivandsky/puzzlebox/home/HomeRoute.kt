package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.gameDisplayName
import com.ivandsky.puzzlebox.data.repository.gameHistoryDays
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * Home's DI-bound entry point: observes the GAME-AGNOSTIC merged history
 * ([gameHistoryDays] — every game's completion records UNION the Zip compete loop's
 * captured days; fix round 5, defect C). The teaser previously watched only the Zip day
 * store, so a solved Queens game's record row stayed invisible ("No games yet" with a
 * Room row on disk — the same bug class its KDoc once recorded as fixed for Zip).
 * Per-game names resolve against :app's aggregated `single<List<GamePlugin<*, *, *>>>`
 * binding, so a new game appears here purely by registering its plugin. The pane layout
 * is decided once at the app shell (width class + orientation) and passed in; UI below
 * this point stays repository-free.
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
    val koin = getKoin()
    val zip = koinInject<ZipDailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val plugins = remember { koin.getAll<GamePlugin<*, *, *>>() }
    val names = remember(plugins) {
        plugins.map { it.definition.id to it.definition.displayName }
    }
    val days by remember(zip, completions) { gameHistoryDays(zip, completions) }
        .collectAsState(initial = emptyList())

    // The Today card teases the featured game: the first registered plugin. Every
    // current game is daily; cadence-driven selection lands with a second cadence.
    // Empty aggregation is a wiring bug (DiGraphTest enforces ≥1); the constant is the
    // pre-registry fallback so routes that register no plugins still compose.
    val featured = remember(plugins) {
        plugins.firstOrNull()?.let {
            GameSummary(id = it.definition.id, displayName = it.definition.displayName)
        } ?: GameSummary(id = GameId("zip"), displayName = "Zip")
    }

    val history = remember(days, names) {
        days.map { day ->
            HistoryDay(
                isoDay = day.day.toString(),
                headline = formatDay(day.day.toString()),
                gameName = gameDisplayName(day.gameId, names),
            )
        }
    }

    HomeScreen(
        layout = layout,
        game = featured,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenHistory = onOpenHistory,
        history = history,
        onPlayToday = onPlayToday,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}
