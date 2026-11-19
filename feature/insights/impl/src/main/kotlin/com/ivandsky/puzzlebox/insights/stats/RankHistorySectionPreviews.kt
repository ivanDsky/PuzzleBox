package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import java.time.LocalDate

// The rank history section, light and dark: no kept week yet, one, several, signed out, no friends and a read that cannot load.

private fun week(dayOfMonth: Int, pointsPerDay: Double) = RankHistoryStanding.Kept.Week(LocalDate.of(2026, 11, dayOfMonth), pointsPerDay)

private val SeveralWeeks = RankHistoryStanding.Kept(listOf(week(2, 142.5), week(9, 98.0), week(16, 171.25), week(23, 203.0)))

@Composable
private fun Section(state: SectionState<RankHistoryStanding>, singleGame: Boolean = true) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        RankHistorySection(state, singleGame, Modifier.padding(16.dp))
    }
}

@PuzzleBoxPreviews
@Composable
private fun RankHistoryNoWeeksPreview() = Section(SectionState.Ready(RankHistoryStanding.Kept(emptyList())))

@PuzzleBoxPreviews
@Composable
private fun RankHistoryOneWeekPreview() = Section(SectionState.Ready(RankHistoryStanding.Kept(SeveralWeeks.weeks.take(1))))

@PuzzleBoxPreviews
@Composable
private fun RankHistorySeveralWeeksPreview() = Section(SectionState.Ready(SeveralWeeks))

@PuzzleBoxPreviews
@Composable
private fun RankHistoryAllGamesPreview() = Section(SectionState.Ready(SeveralWeeks), singleGame = false)

@PuzzleBoxPreviews
@Composable
private fun RankHistorySignedOutPreview() = Section(SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.SignIn)))

@PuzzleBoxPreviews
@Composable
private fun RankHistoryNoFriendsPreview() = Section(SectionState.Ready(RankHistoryStanding.Locked(VsFriendsStanding.Unlock.AddFriends)))

@PuzzleBoxPreviews
@Composable
private fun RankHistoryUnavailablePreview() = Section(SectionState.Unavailable)
