package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId

// Previews of the hub's cards and picker over stand-in games whose thumbnail is a plain
// grid with a few cells filled in the game's accent.

private const val PREVIEW_SIDE = 5

/** A stand-in board: the [filled] cell indices of a [PREVIEW_SIDE] square grid. */
private data class PreviewBoard(val filled: Set<Int>) : RendererModel

private class PreviewGame(override val gameId: GameId, override val brand: GameBrand) : GamePlayUi {
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController = error("previews never play")

    override fun previewModel(preview: BoardPreview): RendererModel? = null

    @Composable
    override fun BoardThumbnail(model: RendererModel, modifier: Modifier) {
        val filled = (model as PreviewBoard).filled
        val accent = brand.accent.colors()
        Column(modifier.aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(PREVIEW_SIDE) { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(PREVIEW_SIDE) { column ->
                        val on = row * PREVIEW_SIDE + column in filled
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(if (on) accent.accent else accent.container, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

private val zip = PreviewGame(GameId("zip"), GameBrand.Neutral.copy(accent = AccentSlot.Blue))
private val tango = PreviewGame(GameId("tango"), GameBrand.Neutral.copy(accent = AccentSlot.Orange))
private val queens = PreviewGame(GameId("queens"), GameBrand.Neutral.copy(accent = AccentSlot.Violet))
private val previewGames = GamePlayRegistry(listOf(zip, tango, queens))

private val previewState = HubUiState(
    rows = listOf(
        HubRow(zip.gameId, "Zip", zip.brand, 592, TodayStatus.Solved(36_000L)),
        HubRow(tango.gameId, "Tango", tango.brand, 431, TodayStatus.InProgress(42_000L)),
        HubRow(queens.gameId, "Queens", queens.brand, 918, TodayStatus.NotStarted),
        HubRow(GameId("pinpoint"), "Pinpoint", status = TodayStatus.Offline),
    ),
    bestByGame = mapOf(zip.gameId to 31_000L),
    doneCount = 1,
    next = tango.gameId,
    previews = mapOf(
        zip.gameId to PreviewBoard((0 until PREVIEW_SIDE * PREVIEW_SIDE).toSet()),
        tango.gameId to PreviewBoard(setOf(0, 1, 2, 7, 12, 13)),
        queens.gameId to PreviewBoard(emptySet()),
    ),
)

private val pinpoint = PreviewGame(GameId("pinpoint"), GameBrand.Neutral.copy(accent = AccentSlot.Teal))
private val crossclimb = PreviewGame(GameId("crossclimb"), GameBrand.Neutral.copy(accent = AccentSlot.Pink))
private val wordle = PreviewGame(GameId("wordle"), GameBrand.Neutral.copy(accent = AccentSlot.Violet))
private val sudoku = PreviewGame(GameId("sudoku"), GameBrand.Neutral.copy(accent = AccentSlot.Blue))
private val nonogram = PreviewGame(GameId("nonogram"), GameBrand.Neutral.copy(accent = AccentSlot.Orange))
private val manyGames = GamePlayRegistry(listOf(zip, tango, queens, pinpoint, crossclimb, wordle, sudoku, nonogram))

/** Eight games, enough for the phone grid of tiles: three solved, one in progress and next, one offline. */
private val manyState = HubUiState(
    rows = listOf(
        HubRow(zip.gameId, "Zip", zip.brand, 592, TodayStatus.Solved(36_000L)),
        HubRow(tango.gameId, "Tango", tango.brand, 431, TodayStatus.Solved(58_000L)),
        HubRow(queens.gameId, "Queens", queens.brand, 918, TodayStatus.InProgress(42_000L)),
        HubRow(pinpoint.gameId, "Pinpoint", pinpoint.brand, status = TodayStatus.Offline),
        HubRow(crossclimb.gameId, "Crossclimb", crossclimb.brand, 377, TodayStatus.Solved(95_000L)),
        HubRow(wordle.gameId, "Wordle", wordle.brand, 1_604, TodayStatus.NotStarted),
        HubRow(sudoku.gameId, "Sudoku", sudoku.brand, 88, TodayStatus.NotStarted),
        HubRow(nonogram.gameId, "Nonogram", nonogram.brand, 12, TodayStatus.NotStarted),
    ),
    bestByGame = mapOf(zip.gameId to 31_000L, tango.gameId to 50_000L, crossclimb.gameId to 80_000L),
    doneCount = 3,
    next = queens.gameId,
    previews = mapOf(
        zip.gameId to PreviewBoard((0 until PREVIEW_SIDE * PREVIEW_SIDE).toSet()),
        tango.gameId to PreviewBoard((0 until PREVIEW_SIDE * PREVIEW_SIDE).toSet()),
        queens.gameId to PreviewBoard(setOf(0, 1, 2, 7, 12, 13)),
        crossclimb.gameId to PreviewBoard((0 until PREVIEW_SIDE * PREVIEW_SIDE).toSet()),
        wordle.gameId to PreviewBoard(emptySet()),
        sudoku.gameId to PreviewBoard(emptySet()),
    ),
)

@PuzzleBoxPreviews
@Composable
private fun HubCardPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            HubCard(
                entry = previewState.rows[1],
                best = 42_000L,
                isNext = true,
                thumbnail = { tango.BoardThumbnail(PreviewBoard(setOf(0, 1, 2, 7, 12, 13)), it) },
                onPlay = {},
            )
            HubCard(
                entry = previewState.rows[3],
                best = null,
                isNext = false,
                thumbnail = null,
                onPlay = {},
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun GamePickerPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = previewState,
            games = previewGames,
            layout = PlayLayout.SinglePane,
            onPick = {},
            onOpenHistory = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun GamePickerGridPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = previewState,
            games = previewGames,
            layout = PlayLayout.TwoPane,
            onPick = {},
            onOpenHistory = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun GamePickerTilesPreview() {
    PuzzleBoxTheme {
        GamePicker(
            state = manyState,
            games = manyGames,
            layout = PlayLayout.SinglePane,
            onPick = {},
            onOpenHistory = {},
        )
    }
}
