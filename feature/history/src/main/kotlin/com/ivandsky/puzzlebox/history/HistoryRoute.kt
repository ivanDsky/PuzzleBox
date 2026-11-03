package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.repository.gameDisplayName
import com.ivandsky.puzzlebox.data.repository.gameHistoryDays
import com.ivandsky.puzzlebox.game.GamePlugin
import java.time.LocalDate
import org.koin.compose.getKoin
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the GAME-AGNOSTIC merged history ([gameHistoryDays] — every game's completion records
 * UNION the Zip captured days; fix round 5, defect C). The route previously mapped
 * records through `puzzleRef as? ZipPuzzleRef`, so every non-Zip record was dropped and
 * the list read "Nothing captured yet" for solved Queens games. Rows render per GameId
 * (display names from :app's aggregated `List<GamePlugin<*, *, *>>` binding — the same
 * aggregation Home's featured game uses). The pane layout is decided once at the app
 * shell (ticket 07 policy) and passed in; UI below this point stays repository-free.
 * The list pages lazily; the merged list still drives the count and the streak teaser,
 * which must see every captured/solved day.
 *
 * Replay (ticket 14 → 13): a Ready launch hands the play route a self-describing Zip
 * session blob; [onReplay] is the shell's navigation into the play entry. Replay rides
 * the Zip cache seam, so only rows whose game is Zip (captured + in cache) offer the
 * action; other games' rows show their record without it.
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (ReplayLaunch.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = koinInject<ZipDailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val koin = getKoin()
    val plugins = remember { koin.getAll<GamePlugin<*, *, *>>() }
    val names = remember(plugins) {
        plugins.map { it.definition.id to it.definition.displayName }
    }

    val merged by remember(daily, completions) { gameHistoryDays(daily, completions) }
        .collectAsState(initial = emptyList())
    val entries by remember(daily) { daily.observeHistory() }.collectAsState(initial = emptyList())
    val rows = remember(merged, names) {
        merged.map { day -> day.toRow(gameDisplayName(day.gameId, names)) }
    }
    val recordsByRow = remember(merged) {
        merged.mapNotNull { day -> day.record?.let { RowKey(day.gameId, day.day) to it } }.toMap()
    }
    val lazyDays = remember(daily, completions) {
        historyDays(daily, completions, names.associate { (id, name) -> id to name })
    }.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        rows = rows,
        recordsByRow = recordsByRow,
        streak = remember(rows) { streakEndingAtNewest(rows.map(HistoryDayRow::day)) },
        // Replay rides the Zip cache seam (ReplayZip is a Zip session blob): only rows
        // whose game is Zip AND whose day is cached offer the action — the screen gets
        // the day's Zip entry to gate it; every other game's row shows its record only.
        zipEntryFor = { day -> entries.firstOrNull { it.day == day } },
        replayFor = { day ->
            replayLaunch(daily.cached(day), entries.firstOrNull { it.day == day })
        },
        onReplay = onReplay,
        modifier = modifier,
    )
}
