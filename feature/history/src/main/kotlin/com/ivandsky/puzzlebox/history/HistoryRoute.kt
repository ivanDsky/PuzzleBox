package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.gameDisplayName
import com.ivandsky.puzzlebox.data.repository.gameHistoryDays
import com.ivandsky.puzzlebox.game.GameRegistry
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the GAME-AGNOSTIC merged history ([gameHistoryDays] — every game's completion records
 * UNION every registered game's captured days; fix round 5, defect C). Rows render per
 * GameId (display names from :app's [GameRegistry] — the same lookup Home's featured
 * game uses). The pane layout is decided once at the app shell (ticket 07 policy) and
 * passed in; UI below this point stays repository-free. The list pages lazily; the
 * merged list still drives the count and the streak teaser, which must see every
 * captured/solved day.
 *
 * Replay (ticket 14 → 13, generalized in tango-05): a Ready launch hands the play route
 * a self-describing [SessionBlob]; [onReplay] is the shell's navigation into the play
 * entry. Replay rides the daily cache, so only rows whose game has a captured day offer
 * the action; other rows show their record without it. Records key by game+day
 * (ADR-0004: two games share the calendar).
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    onReplay: (ReplayLaunch.Ready) -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = koinInject<DailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val plugins = koinInject<GameRegistry>().all
    val games = remember(plugins) { plugins.map { it.definition.id } }
    val names = remember(plugins) {
        plugins.map { it.definition.id to it.definition.displayName }
    }

    val merged by remember(daily, games, completions) { gameHistoryDays(daily, games, completions) }
        .collectAsState(initial = emptyList())
    val namesMap = remember(names) { names.associate { (id, name) -> id to name } }
    val rows = remember(merged, namesMap) {
        merged.map { day -> day.toRow(gameDisplayName(day.gameId, names)) }
    }
    val recordsByKey = remember(merged) {
        merged.mapNotNull { day -> day.record?.let { (day.gameId to day.day) to it } }.toMap()
    }
    val lazyDays = remember(daily, games, completions, namesMap) {
        historyDays(daily, games, completions, namesMap)
    }.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        allRows = rows,
        recordsByKey = recordsByKey,
        streak = remember(rows) { streakEndingAtNewest(rows.map(HistoryDayRow::day)) },
        // Replay rides the daily cache (SessionBlob names the game): only rows whose
        // game has a captured day offer the action — every other row shows its record only.
        replayFor = { game, day ->
            replayLaunch(
                game,
                day,
                daily.cached(game, day),
                rows.firstOrNull { it.gameId == game && it.day == day }?.puzzleNumber,
            )
        },
        onReplay = onReplay,
        modifier = modifier,
    )
}
