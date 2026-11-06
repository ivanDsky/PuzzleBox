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
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GameBrand
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

private val PreviewTab = HistoryTab(gameId = PreviewGame, name = "Zip", brand = GameBrand.Neutral)

/** One day header on its card, as a game tab shows it above the day's rows. */
@Composable
private fun DayHeaderPreview(status: PastDayStatus, number: Int? = 554) {
    PuzzleBoxTheme {
        Surface {
            HistoryDayHeader(
                pastDay = PastDay(PreviewGame, PreviewToday.minusDays(40), number, status),
                tab = PreviewTab,
                onPlay = {},
                cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

/** A solved archive day: its time and a tonal "Play again". */
@PuzzleBoxPreviews
@Composable
private fun DayHeaderSolvedPreview() = DayHeaderPreview(PastDayStatus.Solved(timeMs = 95_000))

/** A day with a saved play: "In progress" and "Keep going". */
@PuzzleBoxPreviews
@Composable
private fun DayHeaderInProgressPreview() = DayHeaderPreview(PastDayStatus.InProgress(elapsedMs = 42_000))

/** A day already on the device, never played: "Saved on this device" and "Play". */
@PuzzleBoxPreviews
@Composable
private fun DayHeaderStoredPreview() = DayHeaderPreview(PastDayStatus.Stored)

/** A day never fetched: "Needs a connection the first time" and "Play". */
@PuzzleBoxPreviews
@Composable
private fun DayHeaderNeedsConnectionPreview() = DayHeaderPreview(PastDayStatus.NeedsConnection)

/** A source without numbering: the headline is just the game's name. */
@PuzzleBoxPreviews
@Composable
private fun DayHeaderUnnumberedPreview() = DayHeaderPreview(PastDayStatus.Stored, number = null)
