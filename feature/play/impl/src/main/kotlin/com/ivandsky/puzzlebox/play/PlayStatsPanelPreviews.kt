package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
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
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.data.leaderboard.SubmitStatus
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.resultui.board.DailyBoardLine
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.GhostRowModel

// A 30% side pane on a 1280dp-wide tablet.
private val PreviewPaneWidth = 384.dp

private fun real(place: Int, name: String, timeMs: Long, hints: Int = 0, you: Boolean = false, rank: Int = place) =
    DailyBoardLine.Real(DailyBoardRowModel(rank, name, timeMs, hints, isSelf = you, playerId = name), place)

private fun ghost(place: Int, name: String, timeMs: Long, hintFree: Boolean = true) =
    DailyBoardLine.Ghost(GhostRowModel("ref-$name", name, timeMs, hintFree), place)

private val Loaded = PlayStatsState(
    gameName = "Zip",
    solvedToday = 2,
    gamesToday = 5,
    times = PlayTimes.Board(
        listOf("Mia", "Jonas", "Priya", "Alexandria-Charlotte Whitfield", "Tom", "Sam", "Ivan", "Lee")
            .mapIndexed { index, name -> real(index + 1, name, 30_000L + index * 9_000L, you = name == "Ivan") },
        solved = true,
    ),
)

private val Loading = Loaded.copy(
    solvedToday = null,
    times = PlayTimes.Loading,
)

@Composable
private fun PanelPreview(state: PlayStatsState) {
    PuzzleBoxTheme {
        Column(Modifier.width(PreviewPaneWidth).background(MaterialTheme.colorScheme.surface).padding(Spacing.lg)) {
            PlayStatsPanel(state, signIn = BoardSignIn.Idle, onSignIn = {})
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelLoadedPreview() = PanelPreview(Loaded)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelLoadingPreview() = PanelPreview(Loading)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelSignedOutPreview() = PanelPreview(Loaded.copy(times = PlayTimes.SignedOut))

private val OthersOnly = (Loaded.times as PlayTimes.Board).rows.map { line ->
    if (line is DailyBoardLine.Real) line.copy(row = line.row.copy(isSelf = false)) else line
}

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelNotPostedPreview() = PanelPreview(
    Loaded.copy(times = PlayTimes.Board(OthersOnly, solved = false)),
)

/** Solved, own time not on the board yet: the times card reads "Posting your time…". */
@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelPostingPreview() = PanelPreview(
    Loaded.copy(times = PlayTimes.Board(OthersOnly, solved = true)),
)

@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelEmptyBoardPreview() =
    PanelPreview(Loaded.copy(times = PlayTimes.Board(emptyList(), solved = false)))

/** LinkedIn connected: connections' times rank in among the PuzzleBox times, outlined and marked. */
@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelConnectionsPreview() = PanelPreview(
    Loaded.copy(
        times = PlayTimes.Board(
            listOf(
                real(1, "Mia", 30_000L),
                ghost(2, "Ada Lin", 35_000L),
                real(3, "Jonas", 39_000L, rank = 2),
                ghost(4, "Cy Park", 44_000L, hintFree = false),
                real(5, "Ivan", 48_000L, you = true, rank = 3),
            ),
            solved = true,
        ),
    ),
)

/** Per-game board on, hints-first game: rows lead with hints and the label names the rule. */
@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelHintsFirstPreview() = PanelPreview(
    Loaded.copy(
        times = (Loaded.times as PlayTimes.Board).let { board ->
            board.copy(
                rows = board.rows.mapIndexed { index, line -> if (line is DailyBoardLine.Real) line.copy(row = line.row.copy(hints = index / 2)) else line },
                order = RankOrder.HintsThenTime,
            )
        },
    ),
)

/** Per-game board on, own post deferred: the times card reads the saved-offline note. */
@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelDeferredPreview() = PanelPreview(
    Loaded.copy(
        times = (Loaded.times as PlayTimes.Board).copy(
            rows = OthersOnly,
            solved = true,
            order = RankOrder.TimeThenHints,
            post = SubmitStatus.Deferred,
        ),
    ),
)

/** Per-game board on, own post rejected: the times card reads the failure note. */
@PuzzleBoxPreviews
@Composable
internal fun PlayStatsPanelRejectedPreview() = PanelPreview(
    Loaded.copy(
        times = (Loaded.times as PlayTimes.Board).copy(
            rows = OthersOnly,
            solved = true,
            order = RankOrder.TimeThenHints,
            post = SubmitStatus.Rejected,
        ),
    ),
)
