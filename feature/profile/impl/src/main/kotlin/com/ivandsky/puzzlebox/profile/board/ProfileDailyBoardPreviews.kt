package com.ivandsky.puzzlebox.profile.board

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.app.screens.ProfileUiState
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId

// Previews of ProfileDailyBoard — internal. Phone (light + dark via PuzzleBoxPreviews) and tablet.

private const val TabletSpec = "spec:width=1280dp,height=800dp,dpi=240"

private val Zip = GameId("zip")
private val Strands = GameId("strands")

private val Games = listOf(
    ProfileUiState.GameRow(Zip, "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip), 20, null, RankOrder.TimeThenHints),
    ProfileUiState.GameRow(Strands, "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands), 22, null, RankOrder.HintsThenTime),
)

private val Rows = listOf(
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true),
)

@Composable
private fun Section(board: ProfileUiState.PerGameBoard) = PuzzleBoxTheme {
    Box(Modifier.padding(Spacing.md)) { ProfileDailyBoard(Games, board, onSelect = {}) }
}

@PuzzleBoxPreviews
@Composable
internal fun ProfileDailyBoardRankedPreview() =
    Section(ProfileUiState.PerGameBoard(Zip, RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows)))

@PuzzleBoxPreviews
@Composable
internal fun ProfileDailyBoardLoadingPreview() =
    Section(ProfileUiState.PerGameBoard(null, RankOrder.TimeThenHints, DailyBoardState.Loading))

@Preview(name = "Tablet light", device = TabletSpec, showBackground = true)
@Preview(name = "Tablet dark", device = TabletSpec, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
internal fun ProfileDailyBoardTabletPreview() =
    Section(ProfileUiState.PerGameBoard(Strands, RankOrder.HintsThenTime, DailyBoardState.Unavailable))
