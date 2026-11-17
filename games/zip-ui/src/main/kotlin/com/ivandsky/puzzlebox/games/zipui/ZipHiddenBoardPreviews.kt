package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard

/**
 * Zip's board before play as a puzzle's page shows it, light and dark: hidden, and plain for whoever may see it.
 * The puzzle travels with its path, as a shared one does; [ZipReplayBoard.givens] leaves the path behind.
 */
private val previewEnvelope: Puzzle by lazy {
    val path = listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)
    val walls = ((0..19).toSet() - setOf(4, 5, 14, 15)).map { Wall(Cell(it), WallDirection.DOWN) }.toSet()
    val board = ZipBoard(n = 5, walls = walls, waypoints = listOf(Cell(0), Cell(12), Cell(24)))
    val puzzle = ZipPuzzle(variant = VariantId("zip/n5"), board = board, solution = path.map(::Cell))
    Puzzle(ZipDefinition.id, puzzle.variant, ZipCodec.VERSION, ZipCodec.encode(puzzle))
}

@PuzzleBoxPreviews
@Composable
internal fun ZipHiddenBoardBlurredPreview() = HiddenBoardPane(sharp = false)

@PuzzleBoxPreviews
@Composable
internal fun ZipHiddenBoardSharpPreview() = HiddenBoardPane(sharp = true)

@Composable
private fun HiddenBoardPane(sharp: Boolean) {
    val board = ZipReplayBoard.givens(previewEnvelope) ?: return
    PuzzleBoxTheme { Surface { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) } }
}
