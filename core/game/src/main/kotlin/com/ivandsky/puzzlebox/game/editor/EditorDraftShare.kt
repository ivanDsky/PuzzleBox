package com.ivandsky.puzzlebox.game.editor

/** True when the editor implements both [EditorShareCodec] and [DraftExport]: only then can the draft be published. */
val EditorDraft<*>.canShare: Boolean
    get() = editor is EditorShareCodec<*> && editor is DraftExport<*>

/**
 * The version this draft's payload is written in ([EditorShareCodec.shareFormatVersionOf]), not the
 * codec's max ([EditorShareCodec.shareFormatVersion]); null when !canShare.
 */
val EditorDraft<*>.shareFormatVersion: Int?
    get() = payloadShareVersion()

/** Share payload of the current board (no history, no solution); null when !canShare. */
fun EditorDraft<*>.encodeShare(): ByteArray? = encodeBoardShare()

/** The cast is erased: a game's editor implements both capabilities over its own [D]. */
private fun <D : Any> EditorDraft<D>.encodeBoardShare(): ByteArray? =
    if (canShare) (editor as EditorShareCodec<D>).encodeShare(state.draft) else null

/** The same erased cast as [encodeBoardShare]. */
private fun <D : Any> EditorDraft<D>.payloadShareVersion(): Int? =
    if (canShare) (editor as EditorShareCodec<D>).shareFormatVersionOf(state.draft) else null
