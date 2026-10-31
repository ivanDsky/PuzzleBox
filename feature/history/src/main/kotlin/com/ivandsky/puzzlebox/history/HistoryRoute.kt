package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the captured days and their records (A8/A4 — the network is never touched). The pane
 * layout is decided once at the app shell (ticket 07 policy) and passed in; UI below
 * this point stays repository-free. The list pages lazily; the light day list still
 * drives the count and the streak teaser, which must see every captured day.
 *
 * Replay (ticket 14 → 13): a Ready launch hands the play route a self-describing
 * session blob; [onReplay] is the shell's navigation into the play entry.
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
    val scope = rememberCoroutineScope()

    val entries by daily.observeHistory().collectAsState(initial = emptyList())
    val records by completions.observeRecords().collectAsState(initial = emptyList())
    val lazyDays = remember(daily) { historyDays(daily, scope) }.collectAsLazyPagingItems()
    val recordsByDay = remember(records) {
        records.mapNotNull { record ->
            (record.puzzleRef as? ZipPuzzleRef)?.let { ref -> ref.date to record }
        }.toMap()
    }

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        entries = entries,
        recordsByDay = recordsByDay,
        streak = remember(entries) { streakEndingAtNewest(entries.map(ZipDailyEntry::day)) },
        replayFor = { day -> replayLaunch(daily.cached(day), entries.firstOrNull { it.day == day }) },
        onReplay = onReplay,
        modifier = modifier,
    )
}
