package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.gameDisplayName
import com.ivandsky.puzzlebox.data.repository.gameHistoryDays
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the GAME-AGNOSTIC merged history ([gameHistoryDays] — every game's completion records
 * UNION the Zip captured days; fix round 5, defect C; tango-05 adds the Tango captured
 * days and the per-game replay hand-off). The route previously mapped records through
 * `puzzleRef as? ZipPuzzleRef`, so every non-Zip record was dropped and the list read
 * "Nothing captured yet" for solved Queens games. Rows render per GameId (display names
 * from :app's aggregated `List<GamePlugin<*, *, *>>` binding — the same aggregation
 * Home's featured game uses). The pane layout is decided once at the app shell (ticket
 * 07 policy) and passed in; UI below this point stays repository-free. The list pages
 * lazily; the merged list still drives the count and the streak teaser, which must see
 * every captured/solved day.
 *
 * Replay (ticket 14 → 13, generalized in tango-05): a Ready launch hands the play route
 * a self-describing [SessionBlob]; [onReplay] is the shell's navigation into the play
 * entry. Replay rides each game's cache seam, so only rows whose game has a captured
 * day offer the action; other games' rows show their record without it. Records key by
 * game+day (ADR-0004: two games share the calendar).
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (ReplayLaunch.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zips = koinInject<ZipDailyRepository>()
    val tangos = koinInject<TangoDailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val koin = getKoin()
    val plugins = remember { koin.getAll<GamePlugin<*, *, *>>() }
    val names = remember(plugins) {
        plugins.map { it.definition.id to it.definition.displayName }
    }

    val merged by remember(zips, completions) { gameHistoryDays(zips, completions) }
        .collectAsState(initial = emptyList())
    val tangoEntries by remember(tangos) { tangos.observeHistory() }.collectAsState(initial = emptyList())
    val namesMap = remember(names) { names.associate { (id, name) -> id to name } }
    val rows = remember(merged, tangoEntries, namesMap) {
        merged.map { day -> day.toRow(gameDisplayName(day.gameId, names)) } +
            tangoEntries.map { it.toRow(gameDisplayName(TangoDefinition.id, names)) }
    }
    val recordsByKey = remember(merged) {
        merged.mapNotNull { day -> day.record?.let { (day.gameId to day.day) to it } }.toMap()
    }
    val lazyDays = remember(zips, tangos, completions) {
        historyDays(zips, tangos, completions, namesMap)
    }.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        allRows = rows,
        recordsByKey = recordsByKey,
        streak = remember(rows) { streakEndingAtNewest(rows.map(HistoryDayRow::day)) },
        // Replay rides each game's cache seam (SessionBlob names the game): only rows
        // whose game has a captured day offer the action — every other game's row
        // shows its record only.
        replayFor = { game, day ->
            val cached = when (game) {
                ZipDefinition.id -> zips.cached(day)
                TangoDefinition.id -> tangos.cached(day)
                else -> null
            }
            replayLaunch(game, day, cached, rows.firstOrNull { it.gameId == game && it.day == day }?.puzzleNumber)
        },
        onReplay = onReplay,
        modifier = modifier,
    )
}
