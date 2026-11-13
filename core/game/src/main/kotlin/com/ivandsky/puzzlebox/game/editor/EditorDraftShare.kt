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

private fun <D : Any> EditorDraft<D>.encodeBoardShare(): ByteArray? =
    if (canShare) shareCodec().encodeShare(state.draft) else null

private fun <D : Any> EditorDraft<D>.payloadShareVersion(): Int? =
    if (canShare) shareCodec().shareFormatVersionOf(state.draft) else null

/** A wiring bug (canShare without the codec) must fail loudly, not read as "not shareable". */
private fun <D : Any> EditorDraft<D>.shareCodec(): EditorShareCodec<D> =
    checkNotNull(editor.capability<D, EditorShareCodec<D>>()) { "${editor::class} lacks EditorShareCodec" }
