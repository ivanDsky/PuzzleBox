package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipSolver
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel

/**
 * The hub thumbnail's model ([com.ivandsky.puzzlebox.gameui.GamePlayUi.previewModel]):
 * pure decode, no I/O. Null for anything Zip cannot read — another game's blob or
 * puzzle, corrupt bytes, or a puzzle with no solution to draw. Never throws.
 */
internal object ZipPreviewModels {

    fun of(preview: BoardPreview): ZipRendererModel? = runCatching {
        when (preview) {
            is BoardPreview.InProgress -> inProgress(preview.bytes)
            is BoardPreview.Fresh -> decode(preview.puzzle)?.let { model(it, emptyList()) }
            is BoardPreview.Solved -> decode(preview.puzzle)?.let { puzzle ->
                (puzzle.solution ?: ZipSolver().solve(puzzle))?.let { model(puzzle, it) }
            }
        }
    }.getOrNull()

    /** The same path [ZipPlayController.adoptRestored] takes, after the play route's envelope check. */
    private fun inProgress(bytes: ByteArray): ZipRendererModel? {
        val envelope = SessionBlob.decode(bytes)?.takeIf { it.game == ZipDefinition.id } ?: return null
        val session = (ZipSessionCodec.deserialize(envelope.session) as? DecodeResult.Ok)?.value ?: return null
        return model(session.puzzle, session.path)
    }

    private fun decode(puzzle: Puzzle): ZipPuzzle? {
        if (puzzle.gameId != ZipDefinition.id) return null
        return (ZipCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value
    }

    private fun model(puzzle: ZipPuzzle, path: List<Cell>): ZipRendererModel =
        ZipRendererModel.of(ZipState(puzzle, path), null)
}
