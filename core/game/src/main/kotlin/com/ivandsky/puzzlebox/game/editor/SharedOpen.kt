package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult

/**
 * Recipient-side re-verification (A19): decodeShare → analyze (solver limit 2) → only Unique →
 * DraftExport.toPuzzle. No game names: a game joins by implementing [EditorShareCodec] and
 * [DraftExport] on its editor. The sender's solution never travels, so the puzzle stored here
 * always carries the one this device found.
 */
object SharedOpen {
    fun verify(editor: GameEditor<*>, shareFormatVersion: Int, payload: ByteArray, budget: Budget): SharedOpenResult =
        verifyTyped(editor, shareFormatVersion, payload, budget)

    /**
     * The casts are erased: a game's editor implements both capabilities over its own [D],
     * the same contract [EditorDraft.toPuzzle] relies on.
     */
    private fun <D : Any> verifyTyped(
        editor: GameEditor<D>,
        shareFormatVersion: Int,
        payload: ByteArray,
        budget: Budget,
    ): SharedOpenResult {
        val codec = editor as? EditorShareCodec<D> ?: return SharedOpenResult.NotShareable
        val export = editor as? DraftExport<D> ?: return SharedOpenResult.NotShareable
        if (shareFormatVersion > codec.shareFormatVersion) {
            return SharedOpenResult.NeedsUpdate(found = shareFormatVersion, maxKnown = codec.shareFormatVersion)
        }
        val draft = when (val decoded = codec.decodeShare(payload)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded.error.toResult()
        }
        val verdict = editor.analyze(draft, budget) as? Verdict.Unique ?: return SharedOpenResult.FailedCheck
        return SharedOpenResult.Verified(
            puzzle = export.toPuzzle(draft, verdict.solution),
            variant = editor.variantOf(draft),
            difficulty = verdict.difficulty,
        )
    }

    private fun DecodeError.toResult(): SharedOpenResult = when (this) {
        is DecodeError.UnsupportedVersion -> SharedOpenResult.NeedsUpdate(found = found, maxKnown = maxKnown)
        else -> SharedOpenResult.FailedCheck
    }
}
