package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec

/** Preview fixture: a uniquely solvable 6×6 day with = and × signs. */
private val thumbnailPuzzle = TangoPuzzle(
    variant = VariantId("tango/n6"),
    n = 6,
    givens = listOf(0, 1, 6, 7, 14, 16, 22, 29, 32).associateWith { TangoCell.SUN } + (33 to TangoCell.MOON),
    markers = listOf(
        TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
        TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
        TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
    ),
)

private val thumbnailEnvelope = Puzzle(
    gameId = TangoDefinition.id,
    variant = thumbnailPuzzle.variant,
    formatVersion = TangoCodec.VERSION,
    payload = TangoCodec.encode(thumbnailPuzzle),
)

private val thumbnailSave = TangoSessionCodec.serialize(
    TangoSession(
        ref = null,
        puzzle = thumbnailPuzzle,
        placements = mapOf(2 to TangoCell.MOON, 3 to TangoCell.SUN, 8 to TangoCell.MOON, 12 to TangoCell.MOON),
    ),
)

// The hub card's thumbnail at 112dp: a save in progress, today's fresh puzzle, the solved day.
@PuzzleBoxPreviews
@Composable
internal fun TangoBoardThumbnailPreview() {
    PuzzleBoxTheme {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val ui = TangoPlayUi()
            listOf(
                BoardPreview.InProgress(thumbnailSave),
                BoardPreview.Fresh(thumbnailEnvelope),
                BoardPreview.Solved(thumbnailEnvelope),
            ).forEach { preview ->
                ui.previewModel(preview)?.let { ui.BoardThumbnail(it, Modifier.size(112.dp)) }
            }
        }
    }
}
