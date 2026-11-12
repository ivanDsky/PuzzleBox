package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf

private val PreviewGame = GameId("zip")
private val PreviewToday: LocalDate = LocalDate.of(2026, 11, 5)

private fun previewRow(day: LocalDate, number: Int, archived: Boolean): HistoryDayRow {
    val ref = object : PuzzleRef {
        override val gameId = PreviewGame
        override val key = PuzzleKey.Daily(day, number)
    }
    return HistoryDayRow(
        day = day,
        puzzleNumber = number,
        gameId = PreviewGame,
        gameName = "Zip",
        fetchedAtMs = PreviewToday.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
        record = CompletionRecord(ref, time = 95_000, hints = 1, stats = RecordStats(1, "{}")),
        archived = archived,
        currentYear = PreviewToday.year,
    )
}

private val LiveRow = previewRow(PreviewToday, number = 594, archived = false)
private val ArchivedRow = previewRow(PreviewToday.minusDays(40), number = 554, archived = true)

/** A same-day row next to an archived one: only the latter carries the tag. */
@PuzzleBoxPreviews
@Composable
private fun HistoryRowsPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                listOf(LiveRow, ArchivedRow).forEach { row ->
                    HistoryRowView(
                        row = row,
                        selected = false,
                        onClick = {},
                        cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                }
            }
        }
    }
}

/** An archived day's detail: the note that it was opened later and skips the streak. */
@PuzzleBoxPreviews
@Composable
private fun ArchivedDetailPreview() {
    PuzzleBoxTheme {
        Surface {
            DayDetailPane(
                row = ArchivedRow,
                gameBest = ArchivedRow,
                onBackToDayList = {},
                replays = HistoryReplays.Hidden,
                replayUi = null,
                onOpenReplay = {},
                onCompareReplays = {},
                onPlayAgain = {},
                cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }
}

/** The tab root: the list pane under the large "History" header (as Create's) with no back arrow. */
@PuzzleBoxPreviews
@Composable
private fun HistoryRootPreview() {
    val rows = listOf(LiveRow, previewRow(PreviewToday.minusDays(1), number = 593, archived = false), ArchivedRow)
    val state = HistoryUiState(
        rows = rows,
        stats = GameStats(4, 9, 31, 22, 95_000L, PreviewToday, best = null, rankOrder = RankOrder.TimeThenHints),
        calendarDay = PreviewToday,
        earliestDay = ArchivedRow.day,
        solvedByDay = solvedByDay(rows, listOf(PreviewGame)),
        daysWithRows = rows.map { it.day }.toSet(),
        gamesPerDay = 1,
        today = PreviewToday,
    )
    PuzzleBoxTheme {
        HistoryScreen(
            layout = HistoryLayout.SinglePane,
            onBack = null,
            days = flowOf(PagingData.from(rows)).collectAsLazyPagingItems(),
            state = state,
            onShowCalendar = { _, _ -> },
            onSelectDay = {},
            onPlayAgain = { _, _ -> },
        )
    }
}

/** The empty tab while the first cloud restore runs (A34). */
@PuzzleBoxPreviews
@Composable
private fun HistoryRestoringPreview() {
    PuzzleBoxTheme {
        HistoryScreen(
            layout = HistoryLayout.SinglePane,
            onBack = null,
            days = flowOf(PagingData.empty<HistoryDayRow>()).collectAsLazyPagingItems(),
            state = HistoryUiState(today = PreviewToday, restoring = true),
            onShowCalendar = { _, _ -> },
            onSelectDay = {},
            onPlayAgain = { _, _ -> },
        )
    }
}

/** The expanded month with a day picked: today ringed, the picked day inked, later days dimmed. */
@PuzzleBoxPreviews
@Composable
private fun HistoryMonthCalendarPreview() {
    val rows = listOf(LiveRow, previewRow(PreviewToday.minusDays(1), number = 593, archived = false), ArchivedRow)
    val state = HistoryUiState(
        rows = rows,
        calendarDay = PreviewToday,
        calendarExpanded = true,
        earliestDay = ArchivedRow.day,
        selectedDay = PreviewToday.minusDays(1),
        solvedByDay = solvedByDay(rows, listOf(PreviewGame)),
        daysWithRows = rows.map { it.day }.toSet(),
        gamesPerDay = 1,
        today = PreviewToday,
    )
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg)) {
                CalendarMonth(YearMonth.from(PreviewToday), state, onSelectDay = {})
            }
        }
    }
}
