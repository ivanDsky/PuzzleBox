package com.ivandsky.puzzlebox.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the captured days (A8 — the network is never touched). The pane layout is decided
 * once at the app shell (ticket 07 policy) and passed in; UI below this point stays
 * repository-free.
 */
@Composable
fun HistoryRoute(
    layout: HistoryLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val daily = koinInject<ZipDailyRepository>()
    val entries by daily.observeHistory().collectAsState(initial = emptyList())
    val rows = remember(entries) { entries.map { it.toRow() } }

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        rows = rows,
        modifier = modifier,
    )
}
