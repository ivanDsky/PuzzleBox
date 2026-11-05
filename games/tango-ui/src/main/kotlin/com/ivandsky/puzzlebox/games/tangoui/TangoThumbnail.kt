package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tango.tangoPlugin

/**
 * A hub card's Tango board ([TangoPlayUi.previewModel]): a save (its SessionBlob
 * envelope, Tango's only) decodes through the session codec [TangoPlayController.adoptRestored] uses, today's puzzle through the
 * codec [TangoPlayController.adoptToday] uses, and a solved day fills every cell from
 * [TangoUniqueness.solve] (Tango ships no solution). Pure; anything unreadable is null.
 */
internal object TangoThumbnail {

    fun model(preview: BoardPreview): TangoRendererModel? = runCatching {
        val state = when (preview) {
            is BoardPreview.InProgress -> inProgress(preview.bytes)
            is BoardPreview.Fresh -> decode(preview.puzzle)?.let { TangoState(it, List(it.cellCount, it.givens::get)) }
            is BoardPreview.Solved -> decode(preview.puzzle)?.let { puzzle ->
                TangoUniqueness.solve(puzzle)?.let { solution -> TangoState(puzzle, solution) }
            }
        }
        state?.let { TangoRendererModel.of(it, hint = null) }
    }.getOrNull()

    private fun inProgress(bytes: ByteArray): TangoState? {
        val envelope = SessionBlob.decode(bytes)?.takeIf { it.game == TangoDefinition.id } ?: return null
        return (TangoSessionCodec.deserialize(envelope.session) as? DecodeResult.Ok)?.value?.let { tangoPlugin.stateOf(it) }
    }

    private fun decode(puzzle: Puzzle): TangoPuzzle? {
        if (puzzle.gameId != TangoDefinition.id) return null
        return (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value
    }
}
