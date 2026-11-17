package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard

/**
 * Tango's board before play as a puzzle's page shows it, light and dark: hidden, and plain for whoever may see
 * it. The plain board holds the given suns and moons and the marks between cells, and nothing a player placed.
 */
private val previewEnvelope: Puzzle by lazy {
    val suns = listOf(0, 1, 6, 7, 14, 16, 22, 29, 32)
    val puzzle = TangoPuzzle(
        variant = VariantId("tango/n6"),
        n = 6,
        givens = suns.associateWith { TangoCell.SUN } + (33 to TangoCell.MOON),
        markers = listOf(
            TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
            TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
    )
    Puzzle(TangoDefinition.id, puzzle.variant, TangoCodec.VERSION, TangoCodec.encode(puzzle))
}

@PuzzleBoxPreviews
@Composable
internal fun TangoHiddenBoardBlurredPreview() = HiddenBoardPane(sharp = false)

@PuzzleBoxPreviews
@Composable
internal fun TangoHiddenBoardSharpPreview() = HiddenBoardPane(sharp = true)

@Composable
private fun HiddenBoardPane(sharp: Boolean) {
    val board = TangoReplayBoard.givens(previewEnvelope) ?: return
    PuzzleBoxTheme { Surface { HiddenBoard(board, sharp = sharp, modifier = Modifier.padding(Spacing.lg)) } }
}
