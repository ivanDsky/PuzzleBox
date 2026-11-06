package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.ZoneOffset

private val PreviewGame = GameId("zip")
private val PreviewToday: LocalDate = LocalDate.of(2026, 11, 5)

private fun previewRow(day: LocalDate, number: Int, archived: Boolean): HistoryDayRow {
    val ref = object : PuzzleRef {
        override val gameId = PreviewGame
        override val date = day
        override val puzzleNumber: Int? = number
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
                onReplay = {},
                cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
            )
        }
    }
}
