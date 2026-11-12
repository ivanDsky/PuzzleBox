package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

private val PreviewToday = LocalDate.parse("2026-11-05")
private val PreviewFirst = LocalDate.parse("2025-03-18")
private val PreviewRange = PreviewFirst..PreviewToday

private fun numberOf(day: LocalDate): Int = (day.toEpochDay() - PreviewFirst.toEpochDay()).toInt() + 1

/** [month]'s archive days in a mix of every state; never-opened past the 20th. */
private fun previewDays(month: YearMonth): List<ArchiveDay> =
    (1..month.lengthOfMonth()).map(month::atDay)
        .filter { it in PreviewRange }
        .map { day ->
            val status = when {
                day.dayOfMonth > 20 -> PastDayStatus.NeedsConnection
                day.dayOfMonth % 5 == 0 -> PastDayStatus.InProgress(62_000L)
                day.dayOfMonth % 7 == 0 -> PastDayStatus.Stored
                day.dayOfMonth % 3 == 0 -> PastDayStatus.NeedsConnection
                else -> PastDayStatus.Solved(41_000L)
            }
            ArchiveDay(day, numberOf(day), status, archived = day.dayOfMonth % 4 == 0, openedOn = PreviewToday.minusDays(2))
        }

/** Every fourth day of [month] has a saved board. */
private fun previewSaved(month: YearMonth): Set<LocalDate> =
    (1..month.lengthOfMonth()).filter { it % 4 == 0 }.mapTo(HashSet(), month::atDay)

private fun previewRow(): LibraryRowModel = LibraryRowModel(
    card = GameCardModel(
        game = GameId("zip"),
        name = "Zip",
        brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
        status = TodayStatus.NotStarted,
        rankOrder = RankOrder.TimeThenHints,
        puzzleNumber = numberOf(PreviewToday),
        best = null,
    ),
    today = numberOf(PreviewToday),
    progress = ArchiveProgress(PreviewRange, numberOf(PreviewToday), 0, 0),
    stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
    rules = gameRules(GameId("zip")),
)

@Composable
private fun PreviewCalendar(month: YearMonth, picked: LocalDate?, offline: Boolean, compact: Boolean) {
    ArchiveMonthCalendar(
        range = PreviewRange,
        month = month,
        days = previewDays(month),
        saved = previewSaved(month),
        picked = picked,
        offline = offline,
        accent = MaterialTheme.colorScheme.primary,
        compact = compact,
        onMonth = {},
        onPick = {},
    )
}

@PuzzleBoxPreviews
@Composable
internal fun ArchiveMonthCalendarPreview() {
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            PreviewCalendar(YearMonth.from(PreviewToday), picked = null, offline = false, compact = false)
            PreviewCalendar(YearMonth.of(2026, 10), picked = LocalDate.parse("2026-10-14"), offline = false, compact = true)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ArchiveMonthCalendarEdgesPreview() {
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            // #1's month: ‹ disabled, days before #1 outside; then offline dims never-opened.
            PreviewCalendar(YearMonth.from(PreviewFirst), picked = null, offline = false, compact = true)
            PreviewCalendar(YearMonth.of(2026, 9), picked = null, offline = true, compact = true)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ArchiveDayDiscPreview() {
    PuzzleBoxTheme {
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            val day = LocalDate.parse("2026-10-14")
            val disc = archiveDiscFor(compact = false)
            listOf(
                PastDayStatus.Solved(41_000L),
                PastDayStatus.InProgress(62_000L),
                PastDayStatus.Stored,
                PastDayStatus.NeedsConnection,
                null,
            ).forEachIndexed { index, status ->
                ArchiveDayDisc(
                    date = day.plusDays(index.toLong()),
                    day = status?.let { ArchiveDay(day.plusDays(index.toLong()), numberOf(day) + index, it, false, null) },
                    // Saved on the first, third and last disc; the outside one never carries a dot.
                    saved = index in setOf(0, 2, 4),
                    today = index == 0,
                    selected = index == 1,
                    offline = false,
                    accent = MaterialTheme.colorScheme.primary,
                    cell = archiveCellFor(disc),
                    disc = disc,
                    reduced = true,
                    onClick = {},
                )
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun ArchivePastDayCardPreview() {
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            val day = LocalDate.parse("2026-10-14")
            val row = previewRow()
            listOf(
                ArchiveDay(day, numberOf(day), PastDayStatus.Solved(41_000L), archived = false, openedOn = null),
                ArchiveDay(day, numberOf(day), PastDayStatus.Solved(41_000L), archived = true, openedOn = PreviewToday),
                ArchiveDay(day, numberOf(day), PastDayStatus.InProgress(62_000L), archived = true, openedOn = PreviewToday),
                ArchiveDay(day, numberOf(day), PastDayStatus.Stored, archived = false, openedOn = null),
                ArchiveDay(day, numberOf(day), PastDayStatus.NeedsConnection, archived = false, openedOn = null),
            ).forEach { past ->
                ArchiveDayCard(row, PreviewToday, past, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {})
            }
            val offline = ArchiveDay(day, numberOf(day), PastDayStatus.NeedsConnection, archived = false, openedOn = null)
            ArchiveDayCard(row, PreviewToday, offline, offline = true, onPlay = {}, onRetry = {}, onBackToToday = {})
        }
    }
}
