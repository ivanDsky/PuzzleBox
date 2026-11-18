package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

private val DoorsRecent = listOf(
    HistoryDay(
        "2026-11-04",
        "November 4",
        listOf(
            HistoryDay.GameResult(GameId("zip"), "Zip", GameBrand.Neutral, HistoryDay.Outcome.Solved(42_000L, 0)),
            HistoryDay.GameResult(GameId("tango"), "Tango", GameBrand.Neutral, HistoryDay.Outcome.Opened),
        ),
    ),
    HistoryDay("2026-11-03", "November 3", listOf(HistoryDay.GameResult(GameId("zip"), "Zip", GameBrand.Neutral, HistoryDay.Outcome.Solved(65_000L, 0)))),
)

// Preview: the phone streak card and Recent rows with the doors to Ranks, at the 360 dp floor.
@PuzzleBoxPreviews
@Composable
internal fun HomeDoorsPhonePreview() {
    val state = frozenState()
    PuzzleBoxTheme {
        Column(
            Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg).width(360.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HomeStreakSection(
                stats = state.stats,
                loaded = state.isLocalStateLoaded,
                freezesHeld = state.freezesHeld,
                week = state.week,
                total = state.today.size,
                onOpenStreak = {},
                onOpenDay = {},
            )
            HomeRecentSection(days = DoorsRecent, loaded = true, selectedDay = null, onDayClick = {}, selectable = false, onOpenDay = {})
        }
    }
}
