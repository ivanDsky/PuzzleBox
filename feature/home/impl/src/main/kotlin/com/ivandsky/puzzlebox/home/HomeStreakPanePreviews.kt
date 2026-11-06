package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

private val PaneDay = LocalDate.of(2026, 11, 5)

private fun paneRow(id: String, name: String, accent: AccentSlot, order: RankOrder, best: BestScore?) =
    HomeUiState.TodayRow(
        GameId(id),
        name,
        if (best == null) TodayStatus.NotStarted else TodayStatus.Solved(best.timeMs),
        GameBrand(accent, GameBrand.Neutral.glyph),
        order,
        best = best,
    )

private val PaneRows = listOf(
    paneRow("zip", "Zip", AccentSlot.Orange, RankOrder.TimeThenHints, BestScore(9_000L, 0)),
    paneRow("tango", "Tango", AccentSlot.Blue, RankOrder.TimeThenHints, BestScore(72_000L, 1)),
    paneRow("queens", "Queens", AccentSlot.Violet, RankOrder.HintsThenTime, BestScore(101_000L, 0)),
    paneRow("strands", "Strands", AccentSlot.Teal, RankOrder.HintsThenTime, BestScore(261_000L, 2)),
    paneRow("sudoku", "Mini Sudoku", AccentSlot.Pink, RankOrder.TimeThenHints, null),
)

private fun paneState(rows: List<HomeUiState.TodayRow>, stats: GameStats) = HomeUiState(
    today = rows,
    identity = null,
    history = emptyList(),
    puzzleDay = PaneDay,
    playTarget = rows.firstOrNull()?.game,
    stats = stats,
    week = (6L downTo 0L).map { back ->
        WeekDay(
            date = PaneDay.minusDays(back),
            solved = rows.take(((6 - back) % 4).toInt()).map { it.brand },
            isToday = back == 0L,
        )
    },
    rolloverAt = Instant.EPOCH,
)

// Preview: a week of play, every best filled but Sudoku's.
@PuzzleBoxPreviews
@Composable
internal fun HomeStreakPanePreview() {
    val stats = GameStats(12, 21, 1_234, 980, 96_000L, LocalDate.of(2025, 3, 14), best = null, rankOrder = RankOrder.TimeThenHints)
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.sm)) {
            HomeStreakPane(state = paneState(PaneRows, stats), modifier = Modifier.width(768.dp))
        }
    }
}

// Preview: a first launch — no streak, no solves, every best still empty.
@PuzzleBoxPreviews
@Composable
internal fun HomeStreakPaneEmptyPreview() {
    val stats = GameStats(0, 0, 0, 0, null, null, best = null, rankOrder = RankOrder.TimeThenHints)
    val rows = PaneRows.map { it.copy(best = null, status = TodayStatus.NotStarted) }
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.sm)) {
            HomeStreakPane(state = paneState(rows, stats).copy(week = paneState(rows, stats).week.map { it.copy(solved = emptyList()) }), modifier = Modifier.width(768.dp))
        }
    }
}

// Preview: the bests card alone, both rank orders and an unsolved game.
@PuzzleBoxPreviews
@Composable
internal fun HomeBestsListPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            HomeBestsList(rows = PaneRows, modifier = Modifier.width(480.dp))
        }
    }
}
