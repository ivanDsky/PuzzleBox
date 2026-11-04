package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.gameDisplayName
import com.ivandsky.puzzlebox.data.repository.gameHistoryDays
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import org.koin.compose.koinInject

/**
 * Home's DI-bound entry point: observes the GAME-AGNOSTIC merged history
 * ([gameHistoryDays] — every game's completion records UNION every registered game's
 * captured days; fix round 5, defect C). Per-game names and the featured game resolve
 * against :app's [GameRegistry], so a new game appears here purely by registering its
 * plugin. The pane layout is decided once at the app shell (width class + orientation)
 * and passed in; UI below this point stays repository-free.
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
    val identityService = koinInject<IdentityService>()
    val daily = koinInject<DailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val plugins = koinInject<GameRegistry>().all
    val names = remember(plugins) {
        plugins.map { it.definition.id to it.definition.displayName }
    }
    val days by remember(daily, plugins, completions) {
        gameHistoryDays(daily, plugins.map { it.definition.id }, completions)
    }.collectAsState(initial = emptyList())
    val identity by identityService.current.collectAsState()

    // The Today card teases the featured game: the first registered plugin. Every
    // current game is daily; cadence-driven selection lands with a second cadence.
    // An empty registry is a wiring bug (DiGraphTest enforces ≥1); the constant is the
    // fallback so a graph that registers no plugins still composes.
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
        identity = identity,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        onOpenHistory = onOpenHistory,
        history = history,
        onPlayToday = onPlayToday,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}
