package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensState
import com.ivandsky.puzzlebox.games.queens.queensPlugin

/** 8×8 board, regions = rows, queens on a knight-step answer (thumbnail panes only). */
private val thumbPuzzle = QueensPuzzle(
    size = 8,
    regions = List(64) { it / 8 },
    solution = listOf(0, 12, 23, 29, 34, 46, 49, 59).map(::Cell),
)

private fun thumb(queens: List<Int>) =
    queensPlugin.rendererModel(QueensState(thumbPuzzle, queens.map(::Cell).toSet()), null, null)

/** The hub's three thumbnail states at 112dp: in progress, fresh, solved. */
@PuzzleBoxPreviews
@Preview(name = "Thumbnails", widthDp = 400, heightDp = 140, showBackground = true)
@Composable
internal fun QueensBoardThumbnailPreview() {
    PuzzleBoxTheme {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QueensBoardThumbnail(thumb(listOf(0, 12, 23)), Modifier.size(112.dp))
            QueensBoardThumbnail(thumb(emptyList()), Modifier.size(112.dp))
            QueensBoardThumbnail(thumb(thumbPuzzle.solution!!.map { it.idx }), Modifier.size(112.dp))
        }
    }
}
