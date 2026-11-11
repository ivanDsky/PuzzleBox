package com.ivandsky.puzzlebox.gameui.board

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder

// Previews of DailyBoard and DailyBoardRow — internal. Phone (light + dark) per state, a Strands board, and tablet (light + dark).

private const val TabletSpec = "spec:width=1280dp,height=800dp,dpi=240"

private val Rows = listOf(
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    DailyBoardRowModel(3, "Jonas", 131_000L, 3, isSelf = false, playerId = "jonas"),
)

private val StrandsRows = listOf(
    DailyBoardRowModel(1, "Maya", 252_000L, 0, isSelf = false, playerId = "maya"),
    DailyBoardRowModel(2, "You", 185_000L, 2, isSelf = true, playerId = "you"),
    DailyBoardRowModel(3, "Jonas", 90_000L, 4, isSelf = false, playerId = "jonas"),
)

private val Ghosts = listOf(
    GhostRowModel("ref-ada", "Ada Quill", 68_000L, hintFree = true),
    GhostRowModel("ref-theo", "Theo Marsh", null, hintFree = false),
)

@Composable
private fun Padded(content: @Composable () -> Unit) {
    PuzzleBoxTheme { Box(Modifier.padding(Spacing.md)) { content() } }
}

@Composable
private fun SignInSlot() = Text("Sign in with Google")

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardRankedPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows))
}

// The player is outside the top rows: a divider, then their own row pinned underneath.
@PuzzleBoxPreviews
@Composable
internal fun DailyBoardPinnedPreview() = Padded {
    DailyBoard(
        dailyBoardTitle("Queens"),
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked(
            rows = listOf(
                DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
                DailyBoardRowModel(2, "Jonas", 70_000L, 0, isSelf = false, playerId = "jonas"),
                DailyBoardRowModel(3, "Priya", 81_000L, 1, isSelf = false, playerId = "priya"),
                DailyBoardRowModel(4, "Sam", 94_000L, 1, isSelf = false, playerId = "sam"),
                DailyBoardRowModel(5, "Lee", 103_000L, 2, isSelf = false, playerId = "lee"),
            ),
            pinned = DailyBoardRowModel(12, "You", 188_000L, 3, isSelf = true, playerId = "you"),
        ),
    )
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardRowPreview() = Padded {
    Column {
        DailyBoardRow(Rows[0], RankOrder.TimeThenHints)
        DailyBoardRow(Rows[1], RankOrder.TimeThenHints)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardStrandsPreview() = Padded {
    DailyBoard(dailyBoardTitle("Strands"), RankOrder.HintsThenTime, DailyBoardState.Ranked(StrandsRows))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardPostingPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Posting))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardReplayPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.ReplayFirstTimeStands))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardDeferredNotePreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Deferred))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardRejectedNotePreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Rejected))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardPostedOutsideTopPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.PostedOutsideTop))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardEmptyPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardLoadingPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Loading)
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardUnavailablePreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Unavailable)
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardSignedOutPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.SignedOut, signIn = { SignInSlot() })
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardGhostsPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows), ghosts = Ghosts)
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardSignedOutGhostsPreview() = Padded {
    DailyBoard(
        dailyBoardTitle("Queens"),
        RankOrder.TimeThenHints,
        DailyBoardState.SignedOut,
        ghosts = Ghosts,
        signIn = { SignInSlot() },
    )
}

// Preview: the player is pinned below the top rows, and their place counts the connections ahead of them.
@PuzzleBoxPreviews
@Composable
internal fun DailyBoardPinnedGhostsPreview() = Padded {
    val rows = listOf(
        DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
        DailyBoardRowModel(2, "Jonas", 70_000L, 0, isSelf = false, playerId = "jonas"),
        DailyBoardRowModel(3, "Priya", 81_000L, 1, isSelf = false, playerId = "priya"),
        DailyBoardRowModel(4, "Sam", 94_000L, 1, isSelf = false, playerId = "sam"),
        DailyBoardRowModel(5, "Lee", 103_000L, 2, isSelf = false, playerId = "lee"),
    )
    val pinned = DailyBoardRowModel(6, "You", 188_000L, 3, isSelf = true, playerId = "you")
    val ghosts = listOf(Ghosts[0], GhostRowModel("ref-noor", "Noor Vale", 150_000L, hintFree = true))
    DailyBoard(
        dailyBoardTitle("Queens"),
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked(
            rows = rows,
            pinned = pinned,
            lines = rankWithGhosts(rows + pinned, ghosts, RankOrder.TimeThenHints).take(6),
            pinnedPlace = 8,
        ),
    )
}

// One list for every board: PuzzleBox rows and a connection share one row design, the player's own row pinned under a divider.
@PuzzleBoxPreviews
@Composable
internal fun DailyBoardListPreview() = Padded {
    val lines = rankWithGhosts(Rows.take(1) + Rows.drop(2), Ghosts, RankOrder.TimeThenHints)
    DailyBoardList(lines, RankOrder.TimeThenHints, pinned = DailyBoardLine.Real(Rows[1], place = 5))
}

// The animated list as the LinkedIn account draws it; a preview shows the settled rows, motion needs a device.
@PuzzleBoxPreviews
@Composable
internal fun DailyBoardAnimatedListPreview() = Padded {
    val lines = rankWithGhosts(Rows.take(1) + Rows.drop(2), Ghosts, RankOrder.TimeThenHints)
    DailyBoardList(lines, RankOrder.TimeThenHints, pinned = DailyBoardLine.Real(Rows[1], place = 5), animated = true)
}

@PuzzleBoxPreviews
@Composable
internal fun GhostRowPreview() = Padded {
    Column {
        GhostRow(Ghosts[0], 3, RankOrder.HintsThenTime)
        GhostRow(Ghosts[1], null, RankOrder.HintsThenTime)
    }
}

// The player's own row with a LinkedIn time carries the LinkedIn dot too, between a PuzzleBox row and a connection.
@PuzzleBoxPreviews
@Composable
internal fun DailyBoardLinkedInTimePreview() = Padded {
    Column {
        DailyBoardRow(Rows[0], RankOrder.TimeThenHints)
        DailyBoardRow(Rows[1].copy(timeFromLinkedIn = true), RankOrder.TimeThenHints, place = 2)
        GhostRow(Ghosts[0], 3, RankOrder.TimeThenHints)
    }
}

@Preview(name = "Tablet light", device = TabletSpec, showBackground = true)
@Preview(name = "Tablet dark", device = TabletSpec, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun DailyBoardTabletPreview() = Padded {
    DailyBoard(dailyBoardTitle("Queens"), RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Posting))
}

@Preview(name = "Tablet Strands light", device = TabletSpec, showBackground = true)
@Preview(name = "Tablet Strands dark", device = TabletSpec, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun DailyBoardStrandsTabletPreview() = Padded {
    DailyBoard(dailyBoardTitle("Strands"), RankOrder.HintsThenTime, DailyBoardState.Ranked(StrandsRows))
}
