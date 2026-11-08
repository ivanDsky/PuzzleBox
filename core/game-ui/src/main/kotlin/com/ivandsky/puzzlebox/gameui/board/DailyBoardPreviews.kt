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
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true),
    DailyBoardRowModel(3, "Jonas", 131_000L, 3, isSelf = false),
)

private val StrandsRows = listOf(
    DailyBoardRowModel(1, "Maya", 252_000L, 0, isSelf = false),
    DailyBoardRowModel(2, "You", 185_000L, 2, isSelf = true),
    DailyBoardRowModel(3, "Jonas", 90_000L, 4, isSelf = false),
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
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows))
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
    DailyBoard("Strands · today", RankOrder.HintsThenTime, DailyBoardState.Ranked(StrandsRows))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardPostingPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Posting))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardReplayPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.ReplayFirstTimeStands))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardEmptyPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(emptyList()))
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardLoadingPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Loading)
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardUnavailablePreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Unavailable)
}

@PuzzleBoxPreviews
@Composable
internal fun DailyBoardSignedOutPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.SignedOut, signIn = { SignInSlot() })
}

@Preview(name = "Tablet light", device = TabletSpec, showBackground = true)
@Preview(name = "Tablet dark", device = TabletSpec, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun DailyBoardTabletPreview() = Padded {
    DailyBoard("Queens · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows, DailyBoardNote.Posting))
}

@Preview(name = "Tablet Strands light", device = TabletSpec, showBackground = true)
@Preview(name = "Tablet Strands dark", device = TabletSpec, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun DailyBoardStrandsTabletPreview() = Padded {
    DailyBoard("Strands · today", RankOrder.HintsThenTime, DailyBoardState.Ranked(StrandsRows))
}
