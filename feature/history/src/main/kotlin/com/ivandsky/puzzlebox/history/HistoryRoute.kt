package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the captured days (A8 — the network is never touched). The pane layout is decided
 * once at the app shell (ticket 07 policy) and passed in; UI below this point stays
 * repository-free. The list pages lazily; the light day list still drives the count
 * and the streak teaser, which must see every captured day.
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = koinInject<ZipDailyRepository>()
    val scope = rememberCoroutineScope()
    val entries by daily.observeHistory().collectAsState(initial = emptyList())
    val lazyDays = remember(daily) { historyDays(daily, scope) }.collectAsLazyPagingItems()

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyDays,
        streak = remember(entries) { streakEndingAtNewest(entries.map(ZipDailyEntry::day)) },
        capturedCount = entries.size,
        modifier = modifier,
    )
}
