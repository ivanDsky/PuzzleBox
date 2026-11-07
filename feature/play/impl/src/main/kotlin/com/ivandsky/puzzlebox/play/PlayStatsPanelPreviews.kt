package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.WeekDay
import java.time.LocalDate

private val PreviewToday = LocalDate.parse("2026-11-06")
private val PreviewBrand = GameBrand.Neutral.copy(accent = AccentSlot.Blue)

// A 30% side pane on a 1280dp-wide tablet.
private val PreviewPaneWidth = 384.dp

private fun previewWeek(solvedBack: Set<Long>): List<WeekDay> = (6L downTo 0L).map { back ->
    WeekDay(PreviewToday.minusDays(back), if (back in solvedBack) listOf(PreviewBrand) else emptyList(), isToday = back == 0L)
}

private val Loaded = PlayStatsState(
    gameName = "Zip",
    solvedToday = 2,
    gamesToday = 5,
    streak = 12,
    week = previewWeek(setOf(1, 2, 3, 4, 5, 6)),
    best = BestScore(41_000L, 0),
    rankOrder = RankOrder.TimeThenHints,
    averageMs = 58_000L,
    times = PlayTimes.Board(
        listOf("Mia", "Jonas", "Priya", "Alexandria-Charlotte Whitfield", "Tom", "Sam", "Ivan", "Lee")
            .mapIndexed { index, name -> PlayTimeRow(index + 1, name, 30_000L + index * 9_000L, you = name == "Ivan") },
    ),
)

private val Loading = Loaded.copy(
    solvedToday = null,
    streak = 0,
    week = previewWeek(emptySet()),
    best = null,
    averageMs = null,
    times = PlayTimes.Loading,
)

@Composable
private fun PanelPreview(state: PlayStatsState, elapsedMs: Long?) {
    PuzzleBoxTheme {
        Column(Modifier.width(PreviewPaneWidth).background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
            PlayStatsPanel(state, elapsedMs = { elapsedMs })
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelLoadedPreview() = PanelPreview(Loaded, elapsedMs = 31_000L)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelLoadingPreview() = PanelPreview(Loading, elapsedMs = null)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelSignedOutPreview() = PanelPreview(Loaded.copy(streak = 0, times = PlayTimes.SignedOut), elapsedMs = 31_000L)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelNotPostedPreview() = PanelPreview(
    Loaded.copy(times = PlayTimes.Board(Loaded.times.let { it as PlayTimes.Board }.rows.map { it.copy(you = false) })),
    elapsedMs = 95_000L,
)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelEmptyBoardPreview() = PanelPreview(Loaded.copy(times = PlayTimes.Board(emptyList())), elapsedMs = 4_000L)
