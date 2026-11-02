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
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.koin.compose.koinInject

/**
 * History's DI-bound entry point (HomeRoute/PlayRoute pattern): cache-only reads over
 * the captured days of BOTH games and their records (A8/A4 — the network is never
 * touched). The pane layout is decided once at the app shell (ticket 07 policy) and
 * passed in; UI below this point stays repository-free. The list pages lazily; the
 * merged light row list still drives the count, the streak teaser, and detail
 * selection, which must see every captured day.
 *
 * Replay (ticket 14 → 13, generalized in tango-05): a Ready launch hands the play
 * route a self-describing [SessionBlob]; [onReplay] is the shell's navigation into the
 * play entry. Records key by game+day (ADR-0004: two games share the calendar).
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

    val rows by remember(zips, tangos) { historyRows(zips, tangos) }.collectAsState(initial = emptyList())
    val records by completions.observeRecords().collectAsState(initial = emptyList())
    val lazyRows = remember(zips, tangos) { historyDays(zips, tangos) }.collectAsLazyPagingItems()

    // Records by (game, day): the day a record's ref names, never its replay history.
    val recordsByKey = remember(records) {
        buildMap {
            for (record in records) {
                val key: Pair<GameId, LocalDate>? = when (val ref = record.puzzleRef) {
                    is ZipPuzzleRef -> ZipDefinition.id to ref.date
                    is TangoPuzzleRef -> TangoDefinition.id to ref.date
                    else -> null // a ref no registered game authored keys nothing
                }
                if (key != null) put(key, record)
            }
        }
    }

    HistoryScreen(
        layout = layout,
        onBack = onBack,
        days = lazyRows,
        allRows = rows,
        recordsByKey = recordsByKey,
        streak = remember(rows) { streakEndingAtNewest(rows.map { it.day }) },
        replayFor = { game, day ->
            val puzzleNumber = rows.firstOrNull { it.game == game && it.day == day }?.puzzleNumber
            val cached = when (game) {
                ZipDefinition.id -> zips.cached(day)
                TangoDefinition.id -> tangos.cached(day)
                else -> null
            }
            replayLaunch(game, day, cached, puzzleNumber)
        },
        onReplay = onReplay,
        modifier = modifier,
    )
}
