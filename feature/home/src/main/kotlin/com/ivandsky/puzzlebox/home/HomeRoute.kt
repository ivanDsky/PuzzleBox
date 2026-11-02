package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.model.DailyPuzzle
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.compose.koinInject

/**
 * Home's DI-bound entry point: observes the cached daily history (Room, never the
 * network — A15 solo browsing is offline-first) for every registered game. The plugin
 * list comes from :app's aggregated `single<List<GamePlugin<*, *, *>>>` binding, so
 * Home holds no game-id constant — a new game appears here purely by registering its
 * plugin. The pane layout is decided once at the app shell (width class + orientation)
 * and passed in; UI below this point stays repository-free.
 */
@Composable
fun HomeRoute(
    layout: HomeLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = koinInject<DailyPuzzleRepository>()
    val plugins = koinInject<List<GamePlugin<*, *, *>>>()

    val games = remember(plugins) {
        plugins.map { GameSummary(id = it.definition.id, displayName = it.definition.displayName) }
    }
    // The Today card teases the featured game: the first registered plugin. Every
    // current game is daily; cadence-driven selection lands with a second cadence.
    val featured = remember(games) { games.first() }

    // One collect for every game: per-plugin history flows, combined positionally
    // so days[i] belongs to games[i].
    val perGameHistory by remember(repository, plugins) {
        if (plugins.isEmpty()) {
            flowOf(emptyList<List<DailyPuzzle>>())
        } else {
            combine(plugins.map { repository.observeHistory(it.definition.id.value) }) { perGame ->
                perGame.toList()
            }
        }
    }.collectAsState(initial = plugins.map { emptyList<DailyPuzzle>() })

    val days = remember(perGameHistory, games) {
        perGameHistory.flatMapIndexed { index, dayPuzzles ->
            val gameName = games.getOrNull(index)?.displayName.orEmpty()
            dayPuzzles.map { puzzle ->
                HistoryDay(isoDay = puzzle.day, headline = formatDay(puzzle.day), gameName = gameName)
            }
        }
    }

    HomeScreen(
        layout = layout,
        game = featured,
        themeMode = themeMode,
        onSetThemeMode = onSetThemeMode,
        history = days,
        modifier = modifier,
    )
}
