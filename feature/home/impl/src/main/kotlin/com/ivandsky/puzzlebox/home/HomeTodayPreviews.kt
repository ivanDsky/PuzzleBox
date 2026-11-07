package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

private val PreviewDay = LocalDate.of(2026, 11, 5)

private fun previewRow(id: String, name: String, brand: GameBrand, status: TodayStatus?) =
    HomeUiState.TodayRow(GameId(id), name, status, brand, RankOrder.TimeThenHints)

private val DefaultSet = listOf(
    previewRow("zip", "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), TodayStatus.Solved(9_000L)),
    previewRow("tango", "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), TodayStatus.InProgress(192_000L)),
    previewRow("queens", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens), TodayStatus.NotStarted),
    previewRow("strands", "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), TodayStatus.Solved(261_000L)),
    previewRow("sudoku", "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku), TodayStatus.Solved(112_000L)),
)

private fun previewState(today: List<HomeUiState.TodayRow>, target: String = "tango") = HomeUiState(
    today = today,
    identity = null,
    history = emptyList(),
    puzzleDay = PreviewDay,
    playTarget = GameId(target),
    stats = GameStats(0, 0, 0, 0, 0L, PreviewDay, best = null, rankOrder = RankOrder.TimeThenHints),
    week = emptyList(),
    rolloverAt = Instant.now().plusSeconds(45_472),
)

@Composable
private fun SummaryFrame(state: HomeUiState, width: Int = 380) {
    PuzzleBoxTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            HomeTodaySummary(
                state = state,
                onOpenGame = {},
                tagPrefix = "home.today",
                badgeTagPrefix = "home.badge.",
                modifier = Modifier.width(width.dp),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryPreview() = SummaryFrame(previewState(DefaultSet))

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryAllNewPreview() =
    SummaryFrame(previewState(DefaultSet.map { it.copy(status = TodayStatus.NotStarted) }, target = "zip"))

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryAllSolvedPreview() =
    SummaryFrame(previewState(DefaultSet.map { it.copy(status = TodayStatus.Solved(118_000L)) }, target = "zip"))

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryOfflinePreview() =
    SummaryFrame(previewState(DefaultSet.mapIndexed { i, row -> if (i < 2) row else row.copy(status = TodayStatus.Offline) }))

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryLoadingPreview() =
    SummaryFrame(previewState(DefaultSet.map { it.copy(status = null) }, target = "zip"))

@PuzzleBoxPreviews
@Composable
internal fun HomeTodaySummaryTabletPreview() = SummaryFrame(previewState(DefaultSet), width = 576)

@PuzzleBoxPreviews
@Composable
internal fun TodayBadgePreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (DefaultSet.take(3) + previewRow("strands", "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), TodayStatus.Offline)).forEach { row ->
                TodayBadge(row, next = row.game.value == "tango", size = TodayBadgeSize.Compact, onClick = {}, modifier = Modifier.width(66.dp))
            }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TodayBadgeRingPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(TodayStatus.NotStarted, TodayStatus.InProgress(1L), TodayStatus.Solved(1L), TodayStatus.Offline).forEach {
                TodayBadgeRing(GameBrand(AccentSlot.Blue, GameGlyphs.Zip), it, TodayBadgeSize.Expanded)
            }
        }
    }
}
