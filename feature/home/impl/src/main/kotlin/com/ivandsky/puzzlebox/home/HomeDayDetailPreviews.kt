package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

private val Zip = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
private val Tango = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)
private val Queens = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
private val Strands = GameBrand(AccentSlot.Pink, GameGlyphs.Strands)
private val Sudoku = GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)

private fun friends(place: Int, count: Int) = HistoryDay.Standing(place, count, amongFriends = true)

private fun result(id: String, name: String, brand: GameBrand, outcome: HistoryDay.Outcome) =
    HistoryDay.GameResult(GameId(id), name, brand, outcome)

private fun solved(timeMs: Long, hints: Int, standing: HistoryDay.Standing?) = HistoryDay.Outcome.Solved(timeMs, hints, standing)

/** Every game solved, with the player's place among friends on each. */
internal val AllPlayedDay = HistoryDay(
    "2026-11-16",
    "November 16",
    listOf(
        result("zip", "Zip", Zip, solved(5_000L, 0, friends(1, 12))),
        result("tango", "Tango", Tango, solved(72_000L, 1, friends(3, 12))),
        result("queens", "Queens", Queens, solved(101_000L, 0, friends(2, 11))),
        result("strands", "Strands", Strands, solved(261_000L, 2, friends(5, 9))),
        result("sudoku", "Mini Sudoku", Sudoku, solved(112_000L, 0, friends(4, 10))),
    ),
)

/** Queens opened but not solved, Strands never played. */
internal val MissedSomeDay = AllPlayedDay.copy(
    isoDay = "2026-11-15",
    headline = "November 15",
    games = AllPlayedDay.games.map {
        when (it.gameId.value) {
            "queens" -> it.copy(outcome = HistoryDay.Outcome.Opened)
            "strands" -> it.copy(outcome = HistoryDay.Outcome.NotPlayed)
            else -> it
        }
    },
)

/** Signed out: no place on any row. */
internal val SignedOutDay = AllPlayedDay.copy(
    games = AllPlayedDay.games.map { game -> game.copy(outcome = (game.outcome as HistoryDay.Outcome.Solved).copy(standing = null)) },
)

/** The widest values the pane must hold: a long date, 12:47, four hints and a four-digit friend count. */
internal val LongestValuesDay = AllPlayedDay.copy(
    isoDay = "2026-09-30",
    headline = "September 30",
    games = AllPlayedDay.games.map {
        if (it.gameId.value == "strands") it.copy(outcome = solved(767_000L, 4, friends(128, 1_204))) else it
    },
)

@Composable
private fun DayPane(day: HistoryDay, signedIn: Boolean = true) {
    PuzzleBoxTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Spacing.sm)) {
            HomeDayDetail(
                day = day,
                signedIn = signedIn,
                onOpenGame = { _, _ -> },
                onSignIn = {},
                onOpenDay = {},
                modifier = Modifier.width(742.dp),
            )
        }
    }
}

// Tablet previews: the day pane at its 0.58 share of a 1280 dp window.
@PuzzleBoxPreviews
@Composable
internal fun HomeDayAllPlayedPreview() = DayPane(AllPlayedDay)

@PuzzleBoxPreviews
@Composable
internal fun HomeDayMissedSomePreview() = DayPane(MissedSomeDay)

@PuzzleBoxPreviews
@Composable
internal fun HomeDaySignedOutPreview() = DayPane(SignedOutDay, signedIn = false)

@PuzzleBoxPreviews
@Composable
internal fun HomeDayLongestValuesPreview() = DayPane(LongestValuesDay)
