package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult

/**
 * Capability: the draft can travel as a share payload. A game opts into sharing by implementing
 * this next to [DraftExport] on its editor; the recipient re-derives the solution ([SharedOpen]).
 */
interface EditorShareCodec<D : Any> {
    /**
     * The highest version this build can read; [SharedOpen] rejects anything above it. Not the
     * version of a given payload: see [shareFormatVersionOf].
     */
    val shareFormatVersion: Int

    /**
     * The version [encodeShare] writes for [draft]; ≤ [shareFormatVersion]. A game writes an older
     * version for boards older builds can open, so their recipients still get them.
     */
    fun shareFormatVersionOf(draft: D): Int = shareFormatVersion

    /** Share codec v1: board only, solution stripped (ADR-0020). */
    fun encodeShare(draft: D): ByteArray
    fun decodeShare(bytes: ByteArray): DecodeResult<D>
}
