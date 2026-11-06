package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

private val PreviewToday = LocalDate.parse("2026-11-05")

private fun previewRow(
    id: String,
    name: String,
    accent: AccentSlot,
    status: TodayStatus?,
    total: Int,
    solved: Int,
    order: RankOrder = RankOrder.TimeThenHints,
): LibraryRowModel {
    val start = PreviewToday.minusDays(total - 1L)
    val stats = if (solved == 0) {
        GameStats(0, 0, 0, 0, null, null, null, order)
    } else {
        GameStats(4, 21, solved, solved * 8 / 9, 45_000L, start.plusDays(3), BestScore(16_000L, 0), order)
    }
    return LibraryRowModel(
        card = GameCardModel(
            game = GameId(id),
            name = name,
            brand = GameBrand.Neutral.copy(accent = accent),
            status = status,
            rankOrder = order,
            puzzleNumber = total,
            best = stats.best,
        ),
        today = total,
        progress = ArchiveProgress(start..PreviewToday, total, solved, solved / 10),
        stats = stats,
        rules = gameRules(GameId(id)),
    )
}

private fun previewState(
    statuses: List<TodayStatus?> = listOf(
        TodayStatus.Solved(38_000L),
        TodayStatus.InProgress(72_000L),
        TodayStatus.NotStarted,
        TodayStatus.NotStarted,
        TodayStatus.Solved(110_000L),
    ),
    solvedScale: Int = 1,
): LibraryUiState {
    val rows = listOf(
        previewRow("zip", "Zip", AccentSlot.Teal, statuses[0], 598, 531 * solvedScale),
        previewRow("tango", "Tango", AccentSlot.Orange, statuses[1], 431, 240 * solvedScale),
        previewRow("queens", "Queens", AccentSlot.Pink, statuses[2], 571, 112 * solvedScale),
        previewRow("strands", "Strands", AccentSlot.Violet, statuses[3], 612, 54 * solvedScale, RankOrder.HintsThenTime),
        previewRow("minisudoku", "Mini Sudoku", AccentSlot.Blue, statuses[4], 210, 18 * solvedScale),
    )
    return LibraryUiState(rows, rows.first().card.game, offline = statuses.any { it == TodayStatus.Offline })
}

@Composable
private fun PreviewDayCard(row: LibraryRowModel) {
    ArchiveDayCard(row, PreviewToday, past = null, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {})
}

@Composable
private fun PreviewScreen(state: LibraryUiState, layout: PlayLayout) {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            LibraryScreen(
                state = state,
                page = null,
                month = null,
                layout = layout,
                hasLesson = { true },
                onSelect = {},
                onPlay = { _, _ -> },
                onRetry = {},
                onOpenLesson = {},
                calendar = CalendarActions(),
            )
        }
    }
}

// Preview: the phone list, a mixed day.
@PuzzleBoxPreviews
@Composable
internal fun LibraryPhonePreview() {
    PreviewScreen(previewState(), PlayLayout.SinglePane)
}

// Preview: the phone list offline; rows stay brand-filled and the pill shows.
@PuzzleBoxPreviews
@Composable
internal fun LibraryPhoneOfflinePreview() {
    PreviewScreen(previewState(List(5) { TodayStatus.Offline }), PlayLayout.SinglePane)
}

// Preview: the phone list loading, every slot a skeleton.
@PuzzleBoxPreviews
@Composable
internal fun LibraryPhoneLoadingPreview() {
    PreviewScreen(previewState(List(5) { null }), PlayLayout.SinglePane)
}

// Preview: the tablet list beside Zip's page.
@Preview(name = "Tablet", widthDp = 1400, heightDp = 876)
@Composable
internal fun LibraryTabletPreview() {
    PreviewScreen(previewState(), PlayLayout.TwoPane)
}

// Preview: the tablet on a first day, no solves anywhere, offline.
@Preview(name = "Tablet empty offline", widthDp = 1400, heightDp = 876)
@Composable
internal fun LibraryTabletEmptyPreview() {
    PreviewScreen(previewState(List(5) { TodayStatus.Offline }, solvedScale = 0), PlayLayout.TwoPane)
}

// Preview: Strands' page pushed on the phone, hints-first best.
@PuzzleBoxPreviews
@Composable
internal fun GamePagePhonePreview() {
    val row = previewState().rows[3]
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            GamePage(
                row = row,
                layout = PlayLayout.SinglePane,
                dayCard = { PreviewDayCard(row) },
                calendar = {},
                onBack = {},
                onOpenLesson = {},
            )
        }
    }
}

// Preview: rows and day cards in every today state, then the empty stats card.
@PuzzleBoxPreviews
@Composable
internal fun LibraryPartsPreview() {
    val rows = previewState(
        listOf(TodayStatus.Solved(38_000L), TodayStatus.InProgress(72_000L), TodayStatus.NotStarted, TodayStatus.Offline, null),
    ).rows
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            rows.forEachIndexed { index, row ->
                LibraryRow(row, selected = index == 0, compact = false, onClick = {})
            }
            rows.take(4).forEach { PreviewDayCard(it) }
            GameStatsCard(previewRow("zip", "Zip", AccentSlot.Teal, TodayStatus.NotStarted, 598, 0))
            HowToPlayCard(rows[0], hasLesson = true, onOpenLesson = {})
            OfflinePill()
        }
    }
}
