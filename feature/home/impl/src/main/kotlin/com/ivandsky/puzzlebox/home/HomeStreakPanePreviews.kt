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
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.StreakFreezes
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.heldWhileStreak
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate

private val PaneDay = LocalDate.of(2026, 11, 5)

private fun paneRow(id: String, name: String, brand: GameBrand, order: RankOrder, best: BestScore?) =
    HomeUiState.TodayRow(
        GameId(id),
        name,
        if (best == null) TodayStatus.NotStarted else TodayStatus.Solved(best.timeMs),
        brand,
        order,
        best = best,
    )

private val PaneRows = listOf(
    paneRow("zip", "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), RankOrder.TimeThenHints, BestScore(9_000L, 0)),
    paneRow("tango", "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango), RankOrder.TimeThenHints, BestScore(72_000L, 1)),
    paneRow("queens", "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens), RankOrder.HintsThenTime, BestScore(101_000L, 0)),
    paneRow("strands", "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), RankOrder.HintsThenTime, BestScore(261_000L, 2)),
    paneRow("sudoku", "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku), RankOrder.TimeThenHints, null),
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

private val FrozenDay = PaneDay.minusDays(2)

// A streak holding 3 freezes, one spent on FrozenDay.
private fun frozenState(): HomeUiState {
    val freezes = StreakFreezes(held = 3, frozenDays = setOf(FrozenDay), daysToNext = 4, lastSolve = null)
    val stats = GameStats(12, 21, 1_234, 980, 96_000L, LocalDate.of(2025, 3, 14), best = null, rankOrder = RankOrder.TimeThenHints, freezes = freezes)
    val state = paneState(PaneRows, stats)
    return state.copy(
        week = state.week.map { it.copy(frozen = it.date == FrozenDay) },
        freezesHeld = freezes.heldWhileStreak(stats.currentStreak),
    )
}

// Preview: the tablet pane with 3 freezes held and one frozen day.
@PuzzleBoxPreviews
@Composable
internal fun HomeStreakPaneFrozenPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.sm)) {
            HomeStreakPane(state = frozenState(), modifier = Modifier.width(768.dp))
        }
    }
}

// Preview: the phone streak card with 3 freezes held and one frozen day, at the 360 dp floor.
@PuzzleBoxPreviews
@Composable
internal fun HomeStreakCardFrozenPreview() {
    val state = frozenState()
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg).width(360.dp)) {
            HomeStreakSection(stats = state.stats, freezesHeld = state.freezesHeld, week = state.week, total = state.today.size)
        }
    }
}
